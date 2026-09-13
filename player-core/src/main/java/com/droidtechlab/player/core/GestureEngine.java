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
 * Translates raw touch coordinates into playback intents.
 *
 * <p>Why this lives in the core instead of in an {@code onTouchEvent}:
 *
 * <ul>
 *   <li>The interaction rules (slop, axis lock, zones, seek span, incremental deltas) are the part
 *     users feel, and they must be identical on Android and iOS.
 *   <li>They are pure functions of coordinates, so they can be covered by unit tests instead of only
 *     by manual poking at a device.
 * </ul>
 *
 * <p>Two properties are load-bearing:
 *
 * <ul>
 *   <li><b>Axis lock.</b> Once the dominant axis is decided it never flips mid-gesture, so a slightly
 *     diagonal scrub does not suddenly start changing the volume.
 *   <li><b>Incremental deltas.</b> Brightness and volume results carry the change since the previous
 *     event, not since the gesture start. Applying a cumulative value on every move event makes the
 *     adjustment run away, which is the classic "one swipe and the screen is black" bug.
 * </ul>
 *
 * <p>Coordinates are in view pixels; the viewport size is supplied at construction.
 */
public final class GestureEngine {

  private final float viewportWidth;
  private final float viewportHeight;
  private final GestureConfig config;

  private float startX;
  private float startY;
  private float lastY;
  private long startPositionMs;
  private long durationMs = -1L;

  private boolean decided;
  private boolean horizontal;
  private boolean locked;

  public GestureEngine(float viewportWidth, float viewportHeight) {
    this(viewportWidth, viewportHeight, GestureConfig.defaults());
  }

  public GestureEngine(float viewportWidth, float viewportHeight, GestureConfig config) {
    this.viewportWidth = Math.max(1f, viewportWidth);
    this.viewportHeight = Math.max(1f, viewportHeight);
    this.config = config == null ? GestureConfig.defaults() : config;
  }

  /** Duration of the current media; a horizontal scrub needs it to map pixels to time. */
  public void setDurationMs(long durationMs) {
    this.durationMs = durationMs;
  }

  /** While locked, every gesture is swallowed. */
  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  public boolean isLocked() {
    return locked;
  }

  /** Called on {@code ACTION_DOWN}. */
  public GestureResult onDown(float x, float y, long currentPositionMs) {
    startX = x;
    startY = y;
    lastY = y;
    startPositionMs = Math.max(0L, currentPositionMs);
    decided = false;
    horizontal = false;
    return GestureResult.none();
  }

  /** Called on {@code ACTION_MOVE}. */
  public GestureResult onMove(float x, float y) {
    if (locked) {
      return GestureResult.none();
    }
    float totalX = x - startX;
    float totalY = y - startY;
    if (!decided) {
      if (Math.abs(totalX) < config.slopPx() && Math.abs(totalY) < config.slopPx()) {
        return GestureResult.none();
      }
      decided = true;
      horizontal = Math.abs(totalX) >= Math.abs(totalY);
      lastY = startY;
    }

    if (horizontal) {
      if (!config.isHorizontalSeekEnabled() || durationMs <= 0) {
        return GestureResult.none();
      }
      float fraction = totalX / viewportWidth;
      long deltaMs = Math.round(fraction * config.seekSpanMs());
      long clampedDelta = clamp(deltaMs, -config.seekSpanMs(), config.seekSpanMs());
      long target = clamp(startPositionMs + clampedDelta, 0L, durationMs);
      return GestureResult.seekPreview(target, target - startPositionMs);
    }

    if (!config.isVerticalAdjustEnabled()) {
      return GestureResult.none();
    }
    float delta = -(y - lastY) / viewportHeight;
    lastY = y;
    boolean leftSide = startX < viewportWidth * 0.5f;
    return leftSide ? GestureResult.brightness(delta) : GestureResult.volume(delta);
  }

  /** Called on {@code ACTION_UP} or {@code ACTION_CANCEL}. */
  public GestureResult onUp() {
    decided = false;
    horizontal = false;
    return GestureResult.none();
  }

  /** Called when a single tap is confirmed. */
  public GestureResult onSingleTap() {
    if (locked) {
      return GestureResult.none();
    }
    return GestureResult.toggleControls();
  }

  /** Called when a double tap is confirmed. */
  public GestureResult onDoubleTap(float x, float y) {
    if (locked) {
      return GestureResult.none();
    }
    if (x < viewportWidth * config.backZoneFraction()) {
      return GestureResult.seekBy(-config.seekSpanMs() / 12L);
    }
    if (x > viewportWidth * config.forwardZoneFraction()) {
      return GestureResult.seekBy(config.seekSpanMs() / 12L);
    }
    return GestureResult.playPause();
  }

  /** Seek step used by the double tap zones, in milliseconds. */
  public long doubleTapStepMs() {
    return config.seekSpanMs() / 12L;
  }

  private static long clamp(long value, long min, long max) {
    return Math.max(min, Math.min(max, value));
  }
}
