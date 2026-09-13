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
 * Persistence boundary for playback progress.
 *
 * <p>The core only knows this interface. Android implements it over {@code SharedPreferences} (today)
 * or Room (phase 2), iOS over a plist/Keychain-free store, and tests over {@link InMemoryResumeStore}.
 */
public interface ResumeStore {

  /** Stores the position for a media id; a position of 0 removes the entry. */
  void save(String mediaId, long positionMs);

  /** Returns the stored position, or 0 when nothing is stored. */
  long load(String mediaId);

  /** Drops the stored position, e.g. after the file is deleted. */
  void remove(String mediaId);
}
