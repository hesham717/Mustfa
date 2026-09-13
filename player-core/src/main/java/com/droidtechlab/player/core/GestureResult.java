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
 * Outcome of feeding a touch event into {@link GestureEngine}.
 *
 * <p>The host applies it. Deltas are <em>incremental</em> (relative to the previous event), which is
 * what makes repeated move events additive instead of compounding.
 */
public final class GestureResult {

  private static final GestureResult NONE = new GestureResult(GestureType.NONE, 0L, 0L, 0f);

  private final GestureType type;
  private final long seekTargetMs;
  private final long seekDeltaMs;
  private final float delta;

  private GestureResult(GestureType type, long seekTargetMs, long seekDeltaMs, float delta) {
    this.type = type;
    this.seekTargetMs = seekTargetMs;
    this.seekDeltaMs = seekDeltaMs;
    this.delta = delta;
  }

  public static GestureResult none() {
    return NONE;
  }

  public static GestureResult toggleControls() {
    return new GestureResult(GestureType.TOGGLE_CONTROLS, 0L, 0L, 0f);
  }

  public static GestureResult playPause() {
    return new GestureResult(GestureType.PLAY_PAUSE, 0L, 0L, 0f);
  }

  public static GestureResult seekBy(long deltaMs) {
    GestureType type = deltaMs < 0 ? GestureType.SEEK_BACKWARD : GestureType.SEEK_FORWARD;
    return new GestureResult(type, 0L, deltaMs, 0f);
  }

  public static GestureResult seekPreview(long targetMs, long deltaMs) {
    return new GestureResult(GestureType.SEEK_PREVIEW, targetMs, deltaMs, 0f);
  }

  public static GestureResult brightness(float delta) {
    return new GestureResult(GestureType.BRIGHTNESS, 0L, 0L, delta);
  }

  public static GestureResult volume(float delta) {
    return new GestureResult(GestureType.VOLUME, 0L, 0L, delta);
  }

  public GestureType type() {
    return type;
  }

  /** Absolute position to seek to, valid only for {@link GestureType#SEEK_PREVIEW}. */
  public long seekTargetMs() {
    return seekTargetMs;
  }

  /** Signed seek offset requested by the gesture. */
  public long seekDeltaMs() {
    return seekDeltaMs;
  }

  /** Incremental change in [0, 1] of the full range, for brightness and volume. */
  public float delta() {
    return delta;
  }

  public boolean isSeek() {
    return type == GestureType.SEEK_PREVIEW
        || type == GestureType.SEEK_BACKWARD
        || type == GestureType.SEEK_FORWARD;
  }

  @Override
  public String toString() {
    return type + " target=" + seekTargetMs + " deltaMs=" + seekDeltaMs + " delta=" + delta;
  }
}
