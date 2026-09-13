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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What the current device can actually decode.
 *
 * <p>Populated at startup from {@code MediaCodecList} on Android and from a static capability table
 * plus {@code VTIsHardwareDecodeSupported} on iOS. It is a plain value object so the decision logic
 * in {@link DecoderPolicy} stays testable.
 */
public final class DeviceCapabilities {

  private final Set<CodecId> hardwareCodecs;
  private final Set<CodecId> softwareCodecs;
  private final boolean secureDecoderSupported;
  private final boolean hardwareHdrSupported;

  public DeviceCapabilities(
      Set<CodecId> hardwareCodecs,
      Set<CodecId> softwareCodecs,
      boolean secureDecoderSupported,
      boolean hardwareHdrSupported) {
    this.hardwareCodecs =
        hardwareCodecs == null ? EnumSet.noneOf(CodecId.class) : EnumSet.copyOf(hardwareCodecs);
    this.softwareCodecs =
        softwareCodecs == null ? EnumSet.noneOf(CodecId.class) : EnumSet.copyOf(softwareCodecs);
    this.secureDecoderSupported = secureDecoderSupported;
    this.hardwareHdrSupported = hardwareHdrSupported;
  }

  /** A conservative baseline: hardware H.264/HEVC/VP9 plus a bundled software decoder. */
  public static DeviceCapabilities baseline() {
    return new DeviceCapabilities(
        EnumSet.of(CodecId.H264, CodecId.H265, CodecId.VP9),
        EnumSet.of(CodecId.H264, CodecId.VP8, CodecId.MPEG4),
        /* secureDecoderSupported= */ true,
        /* hardwareHdrSupported= */ false);
  }

  /** A device where nothing is known: software H.264 only. */
  public static DeviceCapabilities none() {
    return new DeviceCapabilities(
        EnumSet.noneOf(CodecId.class),
        EnumSet.of(CodecId.H264),
        /* secureDecoderSupported= */ false,
        /* hardwareHdrSupported= */ false);
  }

  public Set<CodecId> hardwareCodecs() {
    return Collections.unmodifiableSet(hardwareCodecs);
  }

  public Set<CodecId> softwareCodecs() {
    return Collections.unmodifiableSet(softwareCodecs);
  }

  public boolean isHardwareSupported(CodecId codec) {
    return hardwareCodecs.contains(codec);
  }

  public boolean isSoftwareSupported(CodecId codec) {
    return softwareCodecs.contains(codec);
  }

  public boolean isSecureDecoderSupported() {
    return secureDecoderSupported;
  }

  public boolean isHardwareHdrSupported() {
    return hardwareHdrSupported;
  }
}
