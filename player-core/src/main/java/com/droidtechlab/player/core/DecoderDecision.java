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

/** How the player decided to decode a stream, and why. */
public final class DecoderDecision {

  /** Where decoding happens. */
  public enum Mode {
    /** Hardware decoder on a secure path (DRM L1). */
    HARDWARE_SECURE,
    /** Hardware decoder, clear output. */
    HARDWARE,
    /** Bundled software decoder. */
    SOFTWARE,
    /** No usable decoder: surface an actionable error instead of a black screen. */
    UNSUPPORTED
  }

  private final Mode mode;
  private final String reason;

  public DecoderDecision(Mode mode, String reason) {
    this.mode = mode;
    this.reason = reason;
  }

  public Mode mode() {
    return mode;
  }

  /** Machine readable reason, reported to telemetry (never shown verbatim to users). */
  public String reason() {
    return reason;
  }

  /** {@code true} when the platform must configure a secure/protected output path. */
  public boolean requiresSecureOutput() {
    return mode == Mode.HARDWARE_SECURE;
  }

  public boolean isPlayable() {
    return mode != Mode.UNSUPPORTED;
  }

  @Override
  public String toString() {
    return mode + " (" + reason + ")";
  }
}
