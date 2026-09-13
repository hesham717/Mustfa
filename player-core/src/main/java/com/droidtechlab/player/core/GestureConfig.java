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

/** Tunable constants for {@link GestureEngine}. */
public final class GestureConfig {

  /** Default drag distance before a gesture is recognised, in pixels. */
  public static final float DEFAULT_SLOP_PX = 18f;
  /** Full-width horizontal drag maps to this seek span. */
  public static final long DEFAULT_SEEK_SPAN_MS = 120_000L;
  /** Fraction of the width that belongs to the "seek backwards" double tap zone. */
  public static final float DEFAULT_BACK_ZONE = 0.34f;
  /** Fraction of the width above which a double tap seeks forwards. */
  public static final float DEFAULT_FORWARD_ZONE = 0.66f;

  private final float slopPx;
  private final long seekSpanMs;
  private final float backZoneFraction;
  private final float forwardZoneFraction;
  private final boolean horizontalSeekEnabled;
  private final boolean verticalAdjustEnabled;

  private GestureConfig(Builder builder) {
    this.slopPx = builder.slopPx;
    this.seekSpanMs = builder.seekSpanMs;
    this.backZoneFraction = builder.backZoneFraction;
    this.forwardZoneFraction = builder.forwardZoneFraction;
    this.horizontalSeekEnabled = builder.horizontalSeekEnabled;
    this.verticalAdjustEnabled = builder.verticalAdjustEnabled;
  }

  public static GestureConfig defaults() {
    return new Builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public float slopPx() {
    return slopPx;
  }

  public long seekSpanMs() {
    return seekSpanMs;
  }

  public float backZoneFraction() {
    return backZoneFraction;
  }

  public float forwardZoneFraction() {
    return forwardZoneFraction;
  }

  public boolean isHorizontalSeekEnabled() {
    return horizontalSeekEnabled;
  }

  public boolean isVerticalAdjustEnabled() {
    return verticalAdjustEnabled;
  }

  /** Builder; the two "enabled" flags back the accessibility and one-handed preferences. */
  public static final class Builder {
    private float slopPx = DEFAULT_SLOP_PX;
    private long seekSpanMs = DEFAULT_SEEK_SPAN_MS;
    private float backZoneFraction = DEFAULT_BACK_ZONE;
    private float forwardZoneFraction = DEFAULT_FORWARD_ZONE;
    private boolean horizontalSeekEnabled = true;
    private boolean verticalAdjustEnabled = true;

    public Builder slopPx(float value) {
      this.slopPx = Math.max(4f, value);
      return this;
    }

    public Builder seekSpanMs(long value) {
      this.seekSpanMs = Math.max(1_000L, value);
      return this;
    }

    public Builder backZoneFraction(float value) {
      this.backZoneFraction = Math.max(0.1f, Math.min(0.45f, value));
      return this;
    }

    public Builder forwardZoneFraction(float value) {
      this.forwardZoneFraction = Math.max(0.55f, Math.min(0.9f, value));
      return this;
    }

    public Builder horizontalSeekEnabled(boolean value) {
      this.horizontalSeekEnabled = value;
      return this;
    }

    public Builder verticalAdjustEnabled(boolean value) {
      this.verticalAdjustEnabled = value;
      return this;
    }

    public GestureConfig build() {
      return new GestureConfig(this);
    }
  }
}
