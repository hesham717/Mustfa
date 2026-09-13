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

import java.util.Locale;

/**
 * Playback speed model.
 *
 * <p>Speeds above 2x are only advertised when the caller says the pipeline can sustain them: audio
 * time stretching is the expensive part, and on low tier devices 3x/4x causes dropouts. The host
 * therefore decides with {@link #availablePresets(boolean)}.
 */
public final class SpeedController {

  /** Presets always offered. */
  public static final float[] BASE_PRESETS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
  /** Extra presets for devices that can stretch audio at 3x+ without underruns. */
  public static final float[] EXTENDED_PRESETS = {0.25f, 2.5f, 3f, 4f};

  public static final float MIN_SPEED = 0.25f;
  public static final float MAX_SPEED = 4f;

  private static final float EPSILON = 0.001f;

  private float speed = 1f;
  private boolean pitchCorrection = true;

  public float current() {
    return speed;
  }

  /** {@code true} when running at 1x, i.e. no time stretching is applied. */
  public boolean isDefault() {
    return Math.abs(speed - 1f) < EPSILON;
  }

  /**
   * Applies a speed, snapping it to the nearest preset and clamping it to the supported range.
   *
   * @return the effective speed, which may differ from the requested one
   */
  public float set(float requested) {
    float clamped = Math.max(MIN_SPEED, Math.min(MAX_SPEED, requested));
    float best = clamped;
    float bestDistance = Float.MAX_VALUE;
    for (float preset : allPresets()) {
      float distance = Math.abs(preset - clamped);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = preset;
      }
    }
    // Only snap when the request is close to a preset; otherwise honour the exact value so a
    // "fine tune" slider keeps working.
    speed = bestDistance <= 0.05f ? best : clamped;
    return speed;
  }

  /** Cycles to the next preset and wraps around. */
  public float next(boolean allowExtended) {
    return step(allowExtended, 1);
  }

  /** Cycles to the previous preset and wraps around. */
  public float previous(boolean allowExtended) {
    return step(allowExtended, -1);
  }

  private float step(boolean allowExtended, int direction) {
    float[] presets = allowExtended ? allPresets() : BASE_PRESETS;
    int nearest = 0;
    float bestDistance = Float.MAX_VALUE;
    for (int i = 0; i < presets.length; i++) {
      float distance = Math.abs(presets[i] - speed);
      if (distance < bestDistance) {
        bestDistance = distance;
        nearest = i;
      }
    }
    int target = (nearest + direction + presets.length) % presets.length;
    speed = presets[target];
    return speed;
  }

  /** Pitch correction keeps voices intelligible at speeds above 1x. */
  public boolean isPitchCorrectionEnabled() {
    return pitchCorrection;
  }

  public void setPitchCorrection(boolean enabled) {
    this.pitchCorrection = enabled;
  }

  /** Label rendered on the speed chip, e.g. {@code 1.25x}. */
  public String label() {
    return label(speed);
  }

  public static String label(float value) {
    String formatted = String.format(Locale.US, "%.2f", value);
    while (formatted.endsWith("0")) {
      formatted = formatted.substring(0, formatted.length() - 1);
    }
    if (formatted.endsWith(".")) {
      formatted = formatted.substring(0, formatted.length() - 1);
    }
    return formatted + "x";
  }

  private static float[] allPresets() {
    float[] all = new float[BASE_PRESETS.length + EXTENDED_PRESETS.length];
    System.arraycopy(BASE_PRESETS, 0, all, 0, BASE_PRESETS.length);
    System.arraycopy(EXTENDED_PRESETS, 0, all, BASE_PRESETS.length, EXTENDED_PRESETS.length);
    java.util.Arrays.sort(all);
    return all;
  }

  /** Presets to render for the given device class. */
  public static float[] availablePresets(boolean allowExtended) {
    return allowExtended ? allPresets() : BASE_PRESETS.clone();
  }
}
