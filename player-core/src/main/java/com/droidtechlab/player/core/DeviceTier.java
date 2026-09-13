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
 * Device performance class.
 *
 * <p>Everything expensive in the player is gated on this tier: buffer sizes, decode thread count,
 * cache budget, maximum resolution, whether 3x/4x speed is offered and whether software decoding is
 * allowed as a fallback.
 */
public enum DeviceTier {
  /** Entry level: 1 GB RAM class, 2-4 cores, or an OS release older than Android 5.0. */
  LOW,
  /** Mainstream: 2-4 GB RAM class, 4-6 cores. */
  MID,
  /** Flagship: 4 GB+ RAM class, 6+ cores, modern OS. */
  HIGH;

  /**
   * Classifies a device.
   *
   * @param cpuCores available cores, e.g. {@code Runtime.availableProcessors()}
   * @param ramMegabytes total RAM in megabytes
   * @param osApiLevel Android API level, or the equivalent iOS major version offset (see docs)
   */
  public static DeviceTier classify(int cpuCores, long ramMegabytes, int osApiLevel) {
    if (osApiLevel < 21 || ramMegabytes <= 0) {
      return LOW;
    }
    if (ramMegabytes < 2048 || cpuCores <= 2) {
      return LOW;
    }
    if (ramMegabytes < 4096 || cpuCores <= 4) {
      return MID;
    }
    return HIGH;
  }
}
