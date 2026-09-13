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

/** The intents a touch gesture can produce. */
public enum GestureType {
  /** Nothing happened (below the touch slop, or controls are locked). */
  NONE,
  /** Single tap: show or hide the transport controls. */
  TOGGLE_CONTROLS,
  /** Double tap in the centre zone. */
  PLAY_PAUSE,
  /** Double tap on the left third. */
  SEEK_BACKWARD,
  /** Double tap on the right third. */
  SEEK_FORWARD,
  /** Horizontal drag: scrub with a live preview. */
  SEEK_PREVIEW,
  /** Vertical drag on the left half. */
  BRIGHTNESS,
  /** Vertical drag on the right half. */
  VOLUME
}
