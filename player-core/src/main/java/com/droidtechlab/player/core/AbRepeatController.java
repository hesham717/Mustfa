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
 * A-B loop controller.
 *
 * <p>State is intentionally minimal: two optional marks. The controller never seeks by itself, it
 * only answers {@link #tick(long)} so the host can decide how to seek (and can therefore batch the
 * seek with its own throttling).
 */
public final class AbRepeatController {

  /** Marks are stored in milliseconds on the media timeline. */
  private long pointAMs = -1L;
  private long pointBMs = -1L;

  /** Sets the loop start. Clears point B if it now falls before A. */
  public void setPointA(long positionMs) {
    if (positionMs < 0) {
      throw new IllegalArgumentException("A-B marks must be non-negative");
    }
    pointAMs = positionMs;
    if (pointBMs >= 0 && pointBMs <= pointAMs) {
      pointBMs = -1L;
    }
  }

  /** Sets the loop end. Setting B before A swaps the two marks. */
  public void setPointB(long positionMs) {
    if (positionMs < 0) {
      throw new IllegalArgumentException("A-B marks must be non-negative");
    }
    if (pointAMs < 0) {
      // Only B was set: treat it as the loop start and wait for A.
      pointAMs = positionMs;
      return;
    }
    if (positionMs <= pointAMs) {
      long swap = pointAMs;
      pointAMs = positionMs;
      pointBMs = swap;
    } else {
      pointBMs = positionMs;
    }
  }

  public void clear() {
    pointAMs = -1L;
    pointBMs = -1L;
  }

  /** {@code true} when both marks are set, which is the only state in which the loop is active. */
  public boolean isActive() {
    return pointAMs >= 0 && pointBMs > pointAMs;
  }

  public long pointAMs() {
    return pointAMs;
  }

  public long pointBMs() {
    return pointBMs;
  }

  /**
   * Called on every position update.
   *
   * @return the position to seek to, or {@code null} when the loop does not need to act
   */
  public Long tick(long positionMs) {
    if (!isActive()) {
      return null;
    }
    if (positionMs >= pointBMs) {
      return pointAMs;
    }
    return null;
  }

  /** Progress in the loop expressed in [0, 1]; {@code -1} when inactive. */
  public float loopProgress(long positionMs) {
    if (!isActive()) {
      return -1f;
    }
    long span = pointBMs - pointAMs;
    if (span <= 0) {
      return 0f;
    }
    long elapsed = Math.max(0L, Math.min(span, positionMs - pointAMs));
    return (float) elapsed / (float) span;
  }

  /** Human readable summary used by the on-screen overlay. */
  public String describe() {
    if (!isActive()) {
      return pointAMs >= 0 ? "A…" : "A-B";
    }
    return "A-B " + pointAMs + "-" + pointBMs;
  }
}
