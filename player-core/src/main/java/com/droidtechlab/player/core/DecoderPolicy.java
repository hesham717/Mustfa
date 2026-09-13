/*
 * Copyright (C) 2014-2026 Arpit Khurana, Vishal Nehra, Emmanuel Messulam, Raymond Lai
 * and Contributors.
 *
 * This file is part of the ES File Manager player core.
 *
 * The player core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.droidtechlab.player.core;

/**
 * Chooses hardware, software or secure decoding for a stream.
 *
 * <p>Decision order matters and is fixed on purpose:
 *
 * <ol>
 *   <li>DRM content never falls back to software: a Widevine/FairPlay license is bound to the secure
 *     hardware path, so a software fallback would mean a license failure, not a degraded stream.
 *   <li>Hardware is preferred whenever the codec is listed as hardware supported, because it is
 *     roughly an order of magnitude cheaper in CPU and battery.
 *   <li>Software is used only when the codec is in the bundled software set; on low tier devices the
 *     resolution ceiling is applied first so we do not burn the battery on a stream that will drop
 *     frames anyway.
 *   <li>Otherwise the answer is {@code UNSUPPORTED} and the UI must say so, with a reason.
 * </ol>
 */
public final class DecoderPolicy {

  private DecoderPolicy() {}

  /**
   * @param codec stream codec
   * @param width video width in pixels, or 0 when unknown
   * @param height video height in pixels, or 0 when unknown
   * @param requiresSecure {@code true} when the stream carries a DRM configuration
   */
  public static DecoderDecision decide(
      CodecId codec,
      int width,
      int height,
      boolean requiresSecure,
      DeviceCapabilities capabilities,
      PlaybackTuning tuning) {
    if (codec == null || codec == CodecId.UNKNOWN) {
      return new DecoderDecision(DecoderDecision.Mode.UNSUPPORTED, "codec_unknown");
    }
    if (capabilities == null || tuning == null) {
      return new DecoderDecision(DecoderDecision.Mode.UNSUPPORTED, "capabilities_unavailable");
    }

    boolean hardware = capabilities.isHardwareSupported(codec);

    if (requiresSecure) {
      if (!hardware || !capabilities.isSecureDecoderSupported()) {
        return new DecoderDecision(
            DecoderDecision.Mode.UNSUPPORTED,
            hardware ? "drm_no_secure_path" : "drm_no_hardware_decoder");
      }
      return new DecoderDecision(DecoderDecision.Mode.HARDWARE_SECURE, "drm_secure_path");
    }

    if (hardware) {
      if (!tuning.isWithinResolutionCeiling(width, height)) {
        // The device can decode it, but the tier says this resolution will not hold a frame rate.
        // Prefer a lower rendition (adaptive streaming) or software at reduced size.
        if (codec.isSoftwareFallbackViable() && capabilities.isSoftwareSupported(codec)) {
          return new DecoderDecision(
              DecoderDecision.Mode.SOFTWARE, "hardware_above_resolution_ceiling");
        }
        return new DecoderDecision(
            DecoderDecision.Mode.UNSUPPORTED, "resolution_above_device_ceiling");
      }
      return new DecoderDecision(DecoderDecision.Mode.HARDWARE, "hardware_supported");
    }

    if (codec.isSoftwareFallbackViable() && capabilities.isSoftwareSupported(codec)) {
      if (!tuning.isWithinResolutionCeiling(width, height)) {
        return new DecoderDecision(
            DecoderDecision.Mode.UNSUPPORTED, "software_above_resolution_ceiling");
      }
      return new DecoderDecision(DecoderDecision.Mode.SOFTWARE, "software_fallback");
    }

    return new DecoderDecision(DecoderDecision.Mode.UNSUPPORTED, "no_decoder_for_codec");
  }
}
