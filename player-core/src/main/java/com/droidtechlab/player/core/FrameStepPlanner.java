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
 * Frame stepping arithmetic.
 *
 * <p>Frame stepping is a decoder level operation (flush + decode one picture), but the *target
 * timestamps* are pure arithmetic and shared by both platforms. Working in microseconds matches
 * {@code MediaCodec} presentation timestamps and {@code CMTime} on iOS.
 */
public final class FrameStepPlanner {

  /** Microseconds per second, matching the MediaCodec/AVFoundation timescale convention. */
  public static final long MICROS_PER_SECOND = 1_000_000L;

  private final double framesPerSecond;

  /** @param framesPerSecond container frame rate; values &lt;= 0 disable stepping */
  public FrameStepPlanner(double framesPerSecond) {
    this.framesPerSecond = framesPerSecond;
  }

  /** {@code true} when the container exposes a usable frame rate. */
  public boolean isSupported() {
    return framesPerSecond > 0.001d;
  }

  /** Duration of a single frame in microseconds, or 0 when unsupported. */
  public long frameDurationUs() {
    if (!isSupported()) {
      return 0L;
    }
    return Math.round(MICROS_PER_SECOND / framesPerSecond);
  }

  public double framesPerSecond() {
    return framesPerSecond;
  }

  /** Index of the frame displayed at {@code positionUs}. */
  public long frameIndexAt(long positionUs) {
    if (!isSupported()) {
      return 0L;
    }
    return (long) Math.floor((double) Math.max(0L, positionUs) / (double) frameDurationUs());
  }

  /**
   * Target position after stepping forward, aligned to a frame boundary.
   *
   * @param durationUs stream duration used to clamp, or a negative value to disable clamping
   */
  public long stepForward(long positionUs, long durationUs) {
    if (!isSupported()) {
      return Math.max(0L, positionUs);
    }
    long aligned = (frameIndexAt(positionUs) + 1L) * frameDurationUs();
    if (durationUs >= 0 && aligned > durationUs) {
      aligned = durationUs;
    }
    return Math.max(0L, aligned);
  }

  /** Target position after stepping backward, aligned to a frame boundary and clamped at zero. */
  public long stepBackward(long positionUs) {
    if (!isSupported()) {
      return Math.max(0L, positionUs);
    }
    long index = frameIndexAt(positionUs);
    // If we are exactly on a boundary, the previous frame is the one before it.
    long target = (index - 1L) * frameDurationUs();
    return Math.max(0L, target);
  }
}
