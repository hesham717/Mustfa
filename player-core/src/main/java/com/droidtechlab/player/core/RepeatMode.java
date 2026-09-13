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

/** Queue repetition behaviour. Mirrors {@code Player.REPEAT_MODE_*} on Android. */
public enum RepeatMode {
  /** Stop at the end of the last item. */
  OFF,
  /** Loop the whole queue. */
  ALL,
  /** Loop the current item. */
  ONE;

  /** Cycles OFF -> ALL -> ONE -> OFF, which is the order used by the on-screen toggle. */
  public RepeatMode next() {
    switch (this) {
      case OFF:
        return ALL;
      case ALL:
        return ONE;
      case ONE:
      default:
        return OFF;
    }
  }

  /** Parses a persisted value, defaulting to {@link #OFF}. */
  public static RepeatMode fromName(String name) {
    if (name == null) {
      return OFF;
    }
    for (RepeatMode mode : values()) {
      if (mode.name().equalsIgnoreCase(name)) {
        return mode;
      }
    }
    return OFF;
  }
}
