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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guarded playback state machine shared by every platform adapter.
 *
 * <p>The machine exists for three reasons:
 *
 * <ul>
 *   <li>UI code can depend on a total of {@link PlaybackState#values().length} states instead of
 *       three different engine state vocabularies.
 *   <li>Illegal transitions (for example {@code ENDED -> PLAYING} without a re-prepare) are turned
 *       into loud {@link IllegalStateException}s in debug builds instead of silent black screens.
 *   <li>Every transition is an auditable event for the QoE telemetry pipeline.
 * </ul>
 *
 * <p>Instances are thread safe; player callbacks arrive on the platform playback thread while the
 * UI reads the state from the main thread.
 */
public final class PlaybackStateMachine {

  /** Notified on every accepted transition, on the thread that performed it. */
  public interface Listener {
    void onStateChanged(PlaybackState from, PlaybackState to);
  }

  private static final Map<PlaybackState, Set<PlaybackState>> ALLOWED;

  static {
    Map<PlaybackState, Set<PlaybackState>> allowed = new EnumMap<>(PlaybackState.class);
    allowed.put(PlaybackState.IDLE, EnumSet.of(PlaybackState.PREPARING, PlaybackState.ERROR));
    allowed.put(
        PlaybackState.PREPARING,
        EnumSet.of(
            PlaybackState.READY, PlaybackState.BUFFERING, PlaybackState.ERROR, PlaybackState.IDLE));
    allowed.put(
        PlaybackState.READY,
        EnumSet.of(
            PlaybackState.PLAYING, PlaybackState.BUFFERING, PlaybackState.ERROR, PlaybackState.IDLE));
    allowed.put(
        PlaybackState.PLAYING,
        EnumSet.of(
            PlaybackState.PAUSED,
            PlaybackState.BUFFERING,
            PlaybackState.ENDED,
            PlaybackState.ERROR,
            PlaybackState.IDLE));
    allowed.put(
        PlaybackState.PAUSED,
        EnumSet.of(
            PlaybackState.PLAYING, PlaybackState.BUFFERING, PlaybackState.ERROR, PlaybackState.IDLE));
    allowed.put(
        PlaybackState.BUFFERING,
        EnumSet.of(
            PlaybackState.PLAYING,
            PlaybackState.PAUSED,
            PlaybackState.ENDED,
            PlaybackState.ERROR,
            PlaybackState.IDLE));
    allowed.put(
        PlaybackState.ENDED, EnumSet.of(PlaybackState.IDLE, PlaybackState.PREPARING, PlaybackState.ERROR));
    allowed.put(PlaybackState.ERROR, EnumSet.of(PlaybackState.IDLE));
    ALLOWED = Collections.unmodifiableMap(allowed);
  }

  private final List<Listener> listeners = new ArrayList<>();

  private PlaybackState state = PlaybackState.IDLE;
  private PlaybackState stateBeforeBuffering = PlaybackState.IDLE;
  private int transitionCount;

  /** Current state; never {@code null}. */
  public synchronized PlaybackState current() {
    return state;
  }

  /** Number of accepted transitions since construction; useful in tests and telemetry. */
  public synchronized int transitionCount() {
    return transitionCount;
  }

  /** {@code true} when the machine sits in {@link PlaybackState#ERROR}. */
  public synchronized boolean isFailed() {
    return state == PlaybackState.ERROR;
  }

  /** {@code true} when no media is loaded and no decoder is held. */
  public synchronized boolean isIdle() {
    return state == PlaybackState.IDLE;
  }

  /** Whether {@link #transitionTo(PlaybackState)} would be accepted right now. */
  public synchronized boolean canTransitionTo(PlaybackState target) {
    if (target == null) {
      return false;
    }
    Set<PlaybackState> targets = ALLOWED.get(state);
    return targets != null && targets.contains(target);
  }

  /**
   * Moves to {@code target} or throws.
   *
   * @throws IllegalStateException if the transition is not allowed from the current state
   */
  public synchronized void transitionTo(PlaybackState target) {
    if (target == null) {
      throw new IllegalArgumentException("target state must not be null");
    }
    if (target == state) {
      // Idempotent: engines re-emit the same state on every seek; do not spam listeners.
      return;
    }
    if (!canTransitionTo(target)) {
      throw new IllegalStateException("Illegal playback transition: " + state + " -> " + target);
    }
    PlaybackState from = state;
    state = target;
    transitionCount++;
    for (Listener listener : new ArrayList<>(listeners)) {
      listener.onStateChanged(from, target);
    }
  }

  /**
   * Records a starvation event and remembers whether playback was running, so {@link
   * #onBufferingEnded()} can restore the right state instead of blindly resuming.
   */
  public synchronized void onBufferingStarted() {
    if (state == PlaybackState.PLAYING || state == PlaybackState.PAUSED) {
      stateBeforeBuffering = state;
      transitionTo(PlaybackState.BUFFERING);
    } else if (state == PlaybackState.READY || state == PlaybackState.PREPARING) {
      transitionTo(PlaybackState.BUFFERING);
    }
  }

  /** Restores the state that was active before the last {@link #onBufferingStarted()}. */
  public synchronized void onBufferingEnded() {
    if (state != PlaybackState.BUFFERING) {
      return;
    }
    transitionTo(
        stateBeforeBuffering == PlaybackState.PAUSED ? PlaybackState.PAUSED : PlaybackState.PLAYING);
  }

  public synchronized void addListener(Listener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  public synchronized void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /** Immutable snapshot of the legal successor states; used by tests and debug overlays. */
  public static Set<PlaybackState> allowedFrom(PlaybackState from) {
    Set<PlaybackState> targets = ALLOWED.get(from);
    return targets == null ? Collections.<PlaybackState>emptySet() : Collections.unmodifiableSet(targets);
  }
}
