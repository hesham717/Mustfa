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
 * Decides what to do with a stored playback position.
 *
 * <p>Rules, in order:
 *
 * <ol>
 *   <li>Positions at or below {@link #RESUME_THRESHOLD_MS} are noise from an accidental tap: start
 *       over.
 *   <li>Positions inside the last {@link #COMPLETION_TAIL_MS} of the media, or past {@link
 *       #COMPLETION_RATIO} of it, mean the user already watched it: start over and let them re-watch
 *       from the beginning instead of resuming into the credits.
 *   <li>Everything else is a genuine resume.
 * </ol>
 */
public final class ResumePolicy {

  public static final long RESUME_THRESHOLD_MS = 5_000L;
  public static final long COMPLETION_TAIL_MS = 30_000L;
  public static final float COMPLETION_RATIO = 0.95f;

  /** Outcome of {@link #decide(long, long)}. */
  public enum Action {
    START_FROM_BEGINNING,
    RESUME
  }

  /** Small immutable result carrying both the action and the position to seek to. */
  public static final class Decision {
    private final Action action;
    private final long positionMs;

    Decision(Action action, long positionMs) {
      this.action = action;
      this.positionMs = positionMs;
    }

    public Action action() {
      return action;
    }

    public long positionMs() {
      return positionMs;
    }

    public boolean isResume() {
      return action == Action.RESUME;
    }

    @Override
    public String toString() {
      return action + "@" + positionMs + "ms";
    }
  }

  /**
   * @param savedMs previously stored position, or 0 when nothing was stored
   * @param durationMs media duration in milliseconds, or 0/a negative value when still unknown
   */
  public static Decision decide(long savedMs, long durationMs) {
    if (savedMs <= RESUME_THRESHOLD_MS) {
      return new Decision(Action.START_FROM_BEGINNING, 0L);
    }
    if (durationMs > 0) {
      if (durationMs - savedMs <= COMPLETION_TAIL_MS) {
        return new Decision(Action.START_FROM_BEGINNING, 0L);
      }
      if ((float) savedMs / (float) durationMs >= COMPLETION_RATIO) {
        return new Decision(Action.START_FROM_BEGINNING, 0L);
      }
    }
    return new Decision(Action.RESUME, savedMs);
  }

  /**
   * Whether the position is worth persisting at all. Persisting every second of a 4 KB file is
   * pointless I/O and bloats the resume table on low end devices.
   */
  public static boolean shouldPersist(long savedMs) {
    return savedMs > RESUME_THRESHOLD_MS;
  }
}
