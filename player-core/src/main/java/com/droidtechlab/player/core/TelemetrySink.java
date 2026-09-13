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
 * Sink for playback telemetry.
 *
 * <p>Implementations must be cheap and must never block the playback thread: the production Android
 * implementation hands events to a background worker, the debug implementation appends to an in-app
 * overlay, and tests use {@link InMemoryTelemetrySink}.
 */
public interface TelemetrySink {

  void record(PlaybackEvent event);

  /** Flushes buffered events; called on pause, on backgrounding and before release. */
  void flush();
}
