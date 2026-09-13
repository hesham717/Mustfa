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
 * Error taxonomy for the player.
 *
 * <p>Engine error codes (ExoPlayer {@code PlaybackException.ERROR_CODE_*}, AVPlayer
 * {@code NSError.code}) are mapped into this taxonomy by the platform adapter. Each value carries the
 * recovery strategy, so the UI does not have to guess: it shows the action the enum prescribes.
 */
public enum PlayerErrorCode {
  /** The file disappeared (SD card ejected, cloud file unlinked, SAF permission revoked). */
  SOURCE_NOT_FOUND(Recovery.RESELECT_SOURCE, false),
  /** Container or codec combination this build cannot parse. */
  SOURCE_UNSUPPORTED(Recovery.SWITCH_DECODER, false),
  /** {@code MediaCodec.configure()} failed; retrying on the software decoder usually works. */
  DECODER_INIT_FAILED(Recovery.SWITCH_DECODER, true),
  /** No decoder at all for this codec/resolution/profile. */
  DECODER_UNSUPPORTED(Recovery.SWITCH_DECODER, false),
  /** License server refused, expired or was unreachable. */
  DRM_LICENSE_FAILED(Recovery.RETRY, true),
  /** The device cannot provide a secure output path for the requested protection level. */
  DRM_DEVICE_NOT_SECURE(Recovery.NONE, false),
  /** No connectivity or a request timed out. */
  NETWORK_TIMEOUT(Recovery.CHECK_NETWORK, true),
  /** The server rejected the {@code Range} header; fall back to sequential reads. */
  NETWORK_RANGE_UNSUPPORTED(Recovery.RETRY, true),
  /** Cache write failed (storage full, sandbox revoked). */
  CACHE_IO_ERROR(Recovery.DISABLE_CACHE, true),
  /** The output surface was destroyed mid-playback. */
  RENDER_SURFACE_LOST(Recovery.RETRY, true),
  /** Another app owns audio focus and playback was abandoned. */
  AUDIO_FOCUS_LOST(Recovery.NONE, false),
  /** Everything else; reported with the underlying engine code as an attribute. */
  UNKNOWN(Recovery.RETRY, true);

  /** What the UI should offer the user. */
  public enum Recovery {
    /** Show a "Retry" action and retry automatically once. */
    RETRY,
    /** Show a "Try software decoding" action. */
    SWITCH_DECODER,
    /** Show a connectivity hint. */
    CHECK_NETWORK,
    /** Ask the user to pick another file or re-grant access. */
    RESELECT_SOURCE,
    /** Turn the cache off and continue. */
    DISABLE_CACHE,
    /** Nothing to do but explain. */
    NONE
  }

  private final Recovery recovery;
  private final boolean retryAutomatically;

  PlayerErrorCode(Recovery recovery, boolean retryAutomatically) {
    this.recovery = recovery;
    this.retryAutomatically = retryAutomatically;
  }

  public Recovery recovery() {
    return recovery;
  }

  /** {@code true} when the controller should retry once before surfacing an error. */
  public boolean retryAutomatically() {
    return retryAutomatically;
  }

  /** Stable name used as the {@code error_code} telemetry attribute. */
  public String telemetryName() {
    return name().toLowerCase(java.util.Locale.US);
  }
}
