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
 * The only playback states the player SDK is allowed to expose.
 *
 * <p>Keeping the state set small and explicit is what lets the Android and iOS surfaces share one
 * state machine, one telemetry schema and one set of tests. Anything engine specific (ExoPlayer
 * {@code STATE_BUFFERING}, AVPlayer {@code status}, MediaCodec flush semantics) is translated into
 * this vocabulary by the platform adapter and never leaks into the UI layer.
 */
public enum PlaybackState {
  /** No media is loaded and no resources are held. */
  IDLE,
  /** Media is being probed/parsed; no frame has been decoded yet. */
  PREPARING,
  /** First frame is decoded and the player can start without further loading. */
  READY,
  /** Actively rendering frames. */
  PLAYING,
  /** Rendering suspended by the user, the system, or a transient loss of audio focus. */
  PAUSED,
  /** Rendering suspended because the decode/render pipeline starved. */
  BUFFERING,
  /** Reached the end of the current item. */
  ENDED,
  /** Unrecoverable for the current item; the user must be told what to do. */
  ERROR
}
