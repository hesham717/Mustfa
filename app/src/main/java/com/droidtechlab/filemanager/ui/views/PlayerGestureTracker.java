/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
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

package com.droidtechlab.filemanager.ui.views;

/**
 * Pure state machine that turns raw pointer coordinates into stable player gestures.
 *
 * <p>It is intentionally free of Android dependencies so it can be unit tested. The rules are:
 *
 * <ul>
 *   <li>Nothing happens until the pointer travels farther than the touch slop.
 *   <li>The direction (horizontal / vertical) is decided once, at the moment the slop is exceeded,
 *       and stays locked for the rest of the touch.
 *   <li>Movements whose angle is too close to the diagonal are not accepted as a direction. If the
 *       pointer keeps travelling diagonally beyond {@code slop * REJECT_FACTOR} the whole touch is
 *       rejected and nothing will trigger until the finger is lifted.
 *   <li>A touch that never exceeded the slop is a tap, not a gesture.
 * </ul>
 */
public final class PlayerGestureTracker {

  public enum Direction {
    NONE,
    HORIZONTAL,
    VERTICAL
  }

  /** The direction is only accepted when the dominant axis is at least this many times larger. */
  static final float AXIS_DOMINANCE = 1.75f;

  /** Diagonal movement beyond {@code slop * REJECT_FACTOR} rejects the touch entirely. */
  static final float REJECT_FACTOR = 3f;

  private final float slop;

  private float startX;
  private float startY;
  private float lastX;
  private float lastY;
  private boolean tracking;
  private boolean rejected;
  private Direction direction = Direction.NONE;

  public PlayerGestureTracker(float touchSlopPx) {
    this.slop = Math.max(1f, touchSlopPx);
  }

  /** Call on ACTION_DOWN. */
  public void onDown(float x, float y) {
    startX = lastX = x;
    startY = lastY = y;
    tracking = true;
    rejected = false;
    direction = Direction.NONE;
  }

  /**
   * Call on ACTION_MOVE.
   *
   * @return the locked direction, or {@link Direction#NONE} while the move is still within the
   *     slop / ambiguous, or if the touch has been rejected.
   */
  public Direction onMove(float x, float y) {
    if (!tracking || rejected) return Direction.NONE;
    lastX = x;
    lastY = y;
    if (direction != Direction.NONE) return direction;

    float dx = Math.abs(x - startX);
    float dy = Math.abs(y - startY);
    if (dx <= slop && dy <= slop) return Direction.NONE;

    if (dx >= dy * AXIS_DOMINANCE) {
      direction = Direction.HORIZONTAL;
    } else if (dy >= dx * AXIS_DOMINANCE) {
      direction = Direction.VERTICAL;
    } else if (dx > slop * REJECT_FACTOR || dy > slop * REJECT_FACTOR) {
      // Clearly diagonal: give up on this touch so that nothing flickers.
      rejected = true;
    }
    return direction;
  }

  /** Call on ACTION_CANCEL or when a second pointer goes down. */
  public void cancel() {
    tracking = false;
    rejected = true;
    direction = Direction.NONE;
  }

  /**
   * Call on ACTION_UP.
   *
   * @return true if the touch never became a gesture (i.e. it should be treated as a plain tap by
   *     whoever cares about taps).
   */
  public boolean onUp() {
    boolean wasTap = tracking && !rejected && direction == Direction.NONE;
    tracking = false;
    direction = Direction.NONE;
    return wasTap;
  }

  public Direction getDirection() {
    return direction;
  }

  public boolean isGestureActive() {
    return tracking && !rejected && direction != Direction.NONE;
  }

  public boolean isRejected() {
    return rejected;
  }

  /** Total horizontal travel since ACTION_DOWN (positive = right). */
  public float getDeltaX() {
    return lastX - startX;
  }

  /** Total vertical travel since ACTION_DOWN (positive = down). */
  public float getDeltaY() {
    return lastY - startY;
  }

  public float getStartX() {
    return startX;
  }

  public float getStartY() {
    return startY;
  }

  /**
   * Converts horizontal travel into a seek offset. Instead of mapping the whole screen width to the
   * whole video, a full-width swipe covers a bounded window so long files are not hypersensitive.
   *
   * @param deltaX pixels travelled horizontally
   * @param widthPx width of the surface
   * @param durationMs media duration
   * @return offset in milliseconds (may be negative)
   */
  public static long seekOffsetMs(float deltaX, int widthPx, long durationMs) {
    if (durationMs <= 0 || widthPx <= 0) return 0;
    long window = Math.max(MIN_SEEK_WINDOW_MS, Math.min(MAX_SEEK_WINDOW_MS, durationMs / 10));
    window = Math.min(window, durationMs);
    return (long) (deltaX / widthPx * window);
  }

  /**
   * Converts vertical travel into a fractional change (0..1 scale) for volume/brightness. A
   * full-height swipe changes the value by {@link #VERTICAL_FULL_SWIPE_FRACTION}.
   *
   * @param deltaY pixels travelled vertically (positive = down)
   * @param heightPx height of the surface
   * @return change to apply, positive when swiping up
   */
  public static float verticalFraction(float deltaY, int heightPx) {
    if (heightPx <= 0) return 0f;
    return -deltaY / heightPx * VERTICAL_FULL_SWIPE_FRACTION;
  }

  /** A full-width swipe seeks at least this far. */
  static final long MIN_SEEK_WINDOW_MS = 60_000L;

  /** A full-width swipe never seeks farther than this. */
  static final long MAX_SEEK_WINDOW_MS = 300_000L;

  /** A full-height swipe changes volume/brightness by this fraction of the range. */
  static final float VERTICAL_FULL_SWIPE_FRACTION = 0.8f;
}
