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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LRU index for the on-device media cache.
 *
 * <p>Only the accounting lives here; the platform layer owns the actual files. Splitting them keeps
 * the eviction policy (the part that gets wrong under memory pressure and gets blamed for "the player
 * eats my storage") testable on the JVM.
 *
 * <p>Access order is LRU: reading an entry promotes it, so a video being streamed is never evicted
 * under its own writes.
 */
public final class CacheIndex {

  private final long capacityBytes;
  private final LinkedHashMap<String, Long> entries;

  private long usedBytes;
  private final List<String> lastEvicted = new ArrayList<>();

  public CacheIndex(long capacityBytes) {
    if (capacityBytes <= 0) {
      throw new IllegalArgumentException("capacityBytes must be positive");
    }
    this.capacityBytes = capacityBytes;
    this.entries = new LinkedHashMap<>(16, 0.75f, true);
  }

  public long capacityBytes() {
    return capacityBytes;
  }

  public synchronized long usedBytes() {
    return usedBytes;
  }

  public synchronized int size() {
    return entries.size();
  }

  /** Remaining budget; never negative. */
  public synchronized long freeBytes() {
    return Math.max(0L, capacityBytes - usedBytes);
  }

  public synchronized boolean contains(String key) {
    return entries.containsKey(key);
  }

  /** Bytes accounted for a key, or -1 when absent. */
  public synchronized long sizeOf(String key) {
    Long value = entries.get(key);
    return value == null ? -1L : value;
  }

  /**
   * Inserts or updates an entry and evicts least recently used entries until the budget is met.
   *
   * @return the keys evicted by this call, in eviction order (empty when nothing was evicted)
   */
  public synchronized List<String> touch(String key, long sizeBytes) {
    if (key == null || sizeBytes < 0) {
      return Collections.emptyList();
    }
    if (sizeBytes > capacityBytes) {
      // A single object larger than the whole budget would thrash the cache; refuse it.
      return Collections.emptyList();
    }
    Long previous = entries.remove(key);
    if (previous != null) {
      usedBytes -= previous;
    }
    lastEvicted.clear();
    while (usedBytes + sizeBytes > capacityBytes && !entries.isEmpty()) {
      Map.Entry<String, Long> eldest = entries.entrySet().iterator().next();
      entries.remove(eldest.getKey());
      usedBytes -= eldest.getValue();
      lastEvicted.add(eldest.getKey());
    }
    entries.put(key, sizeBytes);
    usedBytes += sizeBytes;
    return Collections.unmodifiableList(new ArrayList<>(lastEvicted));
  }

  /** Removes an entry explicitly (e.g. the file was deleted). */
  public synchronized boolean remove(String key) {
    Long previous = entries.remove(key);
    if (previous == null) {
      return false;
    }
    usedBytes -= previous;
    return true;
  }

  public synchronized void clear() {
    entries.clear();
    usedBytes = 0L;
  }

  /** Snapshot of the index ordered from least to most recently used. */
  public synchronized Map<String, Long> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }
}
