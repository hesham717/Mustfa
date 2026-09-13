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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded in-memory {@link ResumeStore} used by tests and as the first-level cache in front of disk.
 *
 * <p>The bound matters: a file manager walks huge directory trees and the resume table must not grow
 * without limit on a 2 GB device. Eviction is LRU by insertion/access order.
 */
public final class InMemoryResumeStore implements ResumeStore {

  public static final int DEFAULT_MAX_ENTRIES = 512;

  private final LinkedHashMap<String, Long> entries;

  public InMemoryResumeStore() {
    this(DEFAULT_MAX_ENTRIES);
  }

  public InMemoryResumeStore(final int maxEntries) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive");
    }
    this.entries =
        new LinkedHashMap<String, Long>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > maxEntries;
          }
        };
  }

  @Override
  public synchronized void save(String mediaId, long positionMs) {
    if (mediaId == null) {
      return;
    }
    if (positionMs <= 0) {
      entries.remove(mediaId);
      return;
    }
    entries.put(mediaId, positionMs);
  }

  @Override
  public synchronized long load(String mediaId) {
    if (mediaId == null) {
      return 0L;
    }
    Long value = entries.get(mediaId);
    return value == null ? 0L : value;
  }

  @Override
  public synchronized void remove(String mediaId) {
    if (mediaId != null) {
      entries.remove(mediaId);
    }
  }

  public synchronized int size() {
    return entries.size();
  }

  /** Snapshot for debugging; ordered from least to most recently used. */
  public synchronized Map<String, Long> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }
}
