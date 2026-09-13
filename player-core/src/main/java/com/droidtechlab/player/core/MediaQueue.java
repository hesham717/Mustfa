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
import java.util.Random;

/**
 * Ordered playback queue with repeat and shuffle semantics.
 *
 * <p>The queue is deliberately independent from the media engine: it only knows about opaque media
 * identifiers (paths, {@code content://} URIs or https URLs). That keeps playlist behaviour
 * identical on Android and iOS and makes it unit-testable without a decoder.
 *
 * <p>Not thread safe: instances are owned by a single playback controller.
 */
public final class MediaQueue {

  /** Seed used when the caller does not care about shuffle stability. */
  public static final long DEFAULT_SHUFFLE_SEED = 0x5EEDL;

  private final List<String> items = new ArrayList<>();
  private final List<Integer> order = new ArrayList<>();

  private RepeatMode repeatMode = RepeatMode.OFF;
  private boolean shuffleEnabled;
  private long shuffleSeed = DEFAULT_SHUFFLE_SEED;
  private int orderPosition;

  private MediaQueue(List<String> mediaIds, int startIndex) {
    if (mediaIds == null || mediaIds.isEmpty()) {
      throw new IllegalArgumentException("queue needs at least one item");
    }
    items.addAll(mediaIds);
    applyOrder(startIndex, DEFAULT_SHUFFLE_SEED);
  }

  /** Builds a queue starting at {@code startIndex} (clamped into range). */
  public static MediaQueue of(List<String> mediaIds, int startIndex) {
    return new MediaQueue(mediaIds, startIndex);
  }

  /** Builds a single item queue. */
  public static MediaQueue ofSingle(String mediaId) {
    return new MediaQueue(Collections.singletonList(mediaId), 0);
  }

  public int size() {
    return items.size();
  }

  /** Position of the current item inside the underlying list (not the shuffle order). */
  public int currentIndex() {
    return order.get(orderPosition);
  }

  /** Current media id. */
  public String current() {
    return items.get(currentIndex());
  }

  public List<String> items() {
    return Collections.unmodifiableList(items);
  }

  public RepeatMode repeatMode() {
    return repeatMode;
  }

  public void setRepeatMode(RepeatMode mode) {
    this.repeatMode = mode == null ? RepeatMode.OFF : mode;
  }

  public boolean isShuffleEnabled() {
    return shuffleEnabled;
  }

  /**
   * Enables or disables shuffle. The seed keeps the shuffle order stable across configuration
   * changes (rotation) so playback does not jump when the UI is rebuilt.
   */
  public void setShuffleEnabled(boolean enabled, long seed) {
    this.shuffleSeed = seed;
    if (this.shuffleEnabled == enabled) {
      // Same seed, same order: rebuilding the UI must not reshuffle.
      return;
    }
    this.shuffleEnabled = enabled;
    applyOrder(order.get(orderPosition), seed);
  }

  /** Jumps to an absolute index in the underlying list. Out of range values are ignored. */
  public boolean jumpTo(int index) {
    if (index < 0 || index >= items.size()) {
      return false;
    }
    orderPosition = order.indexOf(index);
    return true;
  }

  /** Whether another item follows in the current order and repeat configuration. */
  public boolean hasNext() {
    if (repeatMode == RepeatMode.ONE) {
      return true;
    }
    if (orderPosition + 1 < order.size()) {
      return true;
    }
    return repeatMode == RepeatMode.ALL && order.size() > 1;
  }

  /** Whether a previous item exists in the current order and repeat configuration. */
  public boolean hasPrevious() {
    if (repeatMode == RepeatMode.ONE) {
      return true;
    }
    if (orderPosition > 0) {
      return true;
    }
    return repeatMode == RepeatMode.ALL && order.size() > 1;
  }

  /**
   * Advances the queue and returns the new current media id, or {@code null} when the queue is
   * exhausted. With {@link RepeatMode#ONE} the current item is returned without moving.
   */
  public String next() {
    if (repeatMode == RepeatMode.ONE) {
      return current();
    }
    if (orderPosition + 1 < order.size()) {
      orderPosition++;
      return current();
    }
    if (repeatMode == RepeatMode.ALL && order.size() > 1) {
      orderPosition = 0;
      return current();
    }
    return null;
  }

  /** Moves backwards and returns the new current media id, or {@code null} at the start. */
  public String previous() {
    if (repeatMode == RepeatMode.ONE) {
      return current();
    }
    if (orderPosition > 0) {
      orderPosition--;
      return current();
    }
    if (repeatMode == RepeatMode.ALL && order.size() > 1) {
      orderPosition = order.size() - 1;
      return current();
    }
    return null;
  }

  /**
   * What to do when the current item reaches its end: {@code true} means the same item must be
   * replayed, {@code false} means the controller should advance with {@link #next()}.
   */
  public boolean shouldReplayCurrentOnEnd() {
    return repeatMode == RepeatMode.ONE;
  }

  private void applyOrder(int startIndex, long seed) {
    int clamped = Math.max(0, Math.min(startIndex, items.size() - 1));
    order.clear();
    for (int i = 0; i < items.size(); i++) {
      order.add(i);
    }
    if (shuffleEnabled && items.size() > 1) {
      Collections.shuffle(order, new Random(seed));
      int position = order.indexOf(clamped);
      // Keep the requested item first so "shuffle" never changes what is playing right now.
      Collections.swap(order, 0, position);
      orderPosition = 0;
    } else {
      orderPosition = clamped;
    }
  }
}
