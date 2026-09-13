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
import java.util.List;

/**
 * Batching telemetry sink.
 *
 * <p>Events are buffered and flushed in batches, because emitting one analytics record per rendered
 * frame would be far more expensive than the playback itself. The batch size is the knob that trades
 * analytics latency against CPU wake-ups.
 */
public final class InMemoryTelemetrySink implements TelemetrySink {

  public static final int DEFAULT_BATCH_SIZE = 20;

  private final int batchSize;
  private final List<PlaybackEvent> pending = new ArrayList<>();
  private final List<List<PlaybackEvent>> flushedBatches = new ArrayList<>();

  private int recordedCount;
  private int droppedCount;

  public InMemoryTelemetrySink() {
    this(DEFAULT_BATCH_SIZE);
  }

  public InMemoryTelemetrySink(int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.batchSize = batchSize;
  }

  @Override
  public synchronized void record(PlaybackEvent event) {
    if (event == null) {
      return;
    }
    recordedCount++;
    if (flushedBatches.size() >= 1000) {
      // Hard stop so a long session with a broken uploader cannot grow the heap.
      droppedCount++;
      return;
    }
    pending.add(event);
    if (pending.size() >= batchSize) {
      flushedBatches.add(new ArrayList<>(pending));
      pending.clear();
    }
  }

  @Override
  public synchronized void flush() {
    if (!pending.isEmpty()) {
      flushedBatches.add(new ArrayList<>(pending));
      pending.clear();
    }
  }

  public synchronized List<PlaybackEvent> pending() {
    return Collections.unmodifiableList(new ArrayList<>(pending));
  }

  /** Events that reached the uploader, in batches. */
  public synchronized List<List<PlaybackEvent>> flushedBatches() {
    return Collections.unmodifiableList(flushedBatches);
  }

  /** Every event ever flushed, in order; convenience for tests. */
  public synchronized List<PlaybackEvent> allFlushed() {
    List<PlaybackEvent> all = new ArrayList<>();
    for (List<PlaybackEvent> batch : flushedBatches) {
      all.addAll(batch);
    }
    return all;
  }

  public synchronized int recordedCount() {
    return recordedCount;
  }

  public synchronized int droppedCount() {
    return droppedCount;
  }
}
