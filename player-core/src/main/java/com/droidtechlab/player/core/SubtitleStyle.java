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
 * Subtitle rendering style.
 *
 * <p>Fractional sizes (a share of the viewport height) are used instead of absolute points so a
 * style picked on a phone stays readable on a tablet and in picture-in-picture. The platform layer
 * maps this onto {@code CaptionStyleCompat}/{@code SubtitleView} on Android and onto
 * {@code AVPlayerItemLegibleOutput} styling keys on iOS.
 */
public final class SubtitleStyle {

  /** Edge treatment around glyphs. */
  public enum EdgeType {
    NONE,
    OUTLINE,
    DROP_SHADOW
  }

  public static final float MIN_TEXT_FRACTION = 0.030f;
  public static final float MAX_TEXT_FRACTION = 0.120f;
  /** Step used by the "A/AA" size control; three presses cover the whole usable range. */
  public static final float SIZE_STEP = 0.018f;
  public static final float DEFAULT_TEXT_FRACTION = 0.052f;

  private final float textFraction;
  private final int textColorArgb;
  private final int backgroundColorArgb;
  private final float backgroundAlpha;
  private final EdgeType edgeType;
  private final float edgeFraction;
  private final long offsetMs;
  private final String fontFamily;
  private final boolean bold;
  private final boolean italic;

  private SubtitleStyle(Builder builder) {
    this.textFraction = builder.textFraction;
    this.textColorArgb = builder.textColorArgb;
    this.backgroundColorArgb = builder.backgroundColorArgb;
    this.backgroundAlpha = builder.backgroundAlpha;
    this.edgeType = builder.edgeType;
    this.edgeFraction = builder.edgeFraction;
    this.offsetMs = builder.offsetMs;
    this.fontFamily = builder.fontFamily;
    this.bold = builder.bold;
    this.italic = builder.italic;
  }

  /** A legible default: white on a translucent box with a thin outline. */
  public static SubtitleStyle defaults() {
    return new Builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .textFraction(textFraction)
        .textColorArgb(textColorArgb)
        .backgroundColorArgb(backgroundColorArgb)
        .backgroundAlpha(backgroundAlpha)
        .edgeType(edgeType)
        .edgeFraction(edgeFraction)
        .offsetMs(offsetMs)
        .fontFamily(fontFamily)
        .bold(bold)
        .italic(italic);
  }

  public float textFraction() {
    return textFraction;
  }

  public int textColorArgb() {
    return textColorArgb;
  }

  public int backgroundColorArgb() {
    return backgroundColorArgb;
  }

  public float backgroundAlpha() {
    return backgroundAlpha;
  }

  public EdgeType edgeType() {
    return edgeType;
  }

  public float edgeFraction() {
    return edgeFraction;
  }

  /** Sync offset applied to subtitle presentation times; positive delays the subtitle. */
  public long offsetMs() {
    return offsetMs;
  }

  public String fontFamily() {
    return fontFamily;
  }

  public boolean isBold() {
    return bold;
  }

  public boolean isItalic() {
    return italic;
  }

  /** Builder with clamping, so persisted values from older versions can never be out of range. */
  public static final class Builder {
    private float textFraction = DEFAULT_TEXT_FRACTION;
    private int textColorArgb = 0xFFFFFFFF;
    private int backgroundColorArgb = 0xFF000000;
    private float backgroundAlpha = 0.5f;
    private EdgeType edgeType = EdgeType.OUTLINE;
    private float edgeFraction = 0.004f;
    private long offsetMs = 0L;
    private String fontFamily = "sans-serif";
    private boolean bold;
    private boolean italic;

    public Builder textFraction(float value) {
      this.textFraction = Math.max(MIN_TEXT_FRACTION, Math.min(MAX_TEXT_FRACTION, value));
      return this;
    }

    /** Steps the size by {@link #SIZE_STEP}; {@code steps} may be negative. */
    public Builder stepSize(int steps) {
      return textFraction(textFraction + steps * SIZE_STEP);
    }

    public Builder textColorArgb(int value) {
      this.textColorArgb = value;
      return this;
    }

    public Builder backgroundColorArgb(int value) {
      this.backgroundColorArgb = value;
      return this;
    }

    public Builder backgroundAlpha(float value) {
      this.backgroundAlpha = Math.max(0f, Math.min(1f, value));
      return this;
    }

    public Builder edgeType(EdgeType value) {
      this.edgeType = value == null ? EdgeType.NONE : value;
      return this;
    }

    public Builder edgeFraction(float value) {
      this.edgeFraction = Math.max(0f, Math.min(0.02f, value));
      return this;
    }

    public Builder offsetMs(long value) {
      this.offsetMs = Math.max(-30_000L, Math.min(30_000L, value));
      return this;
    }

    public Builder fontFamily(String value) {
      this.fontFamily = value == null || value.isEmpty() ? "sans-serif" : value;
      return this;
    }

    public Builder bold(boolean value) {
      this.bold = value;
      return this;
    }

    public Builder italic(boolean value) {
      this.italic = value;
      return this;
    }

    public SubtitleStyle build() {
      return new SubtitleStyle(this);
    }
  }
}
