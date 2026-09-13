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
 * Accumulates the quality-of-experience counters for one playback session.
 *
 * <p>These are the numbers the redesign is judged on: time to first frame, rebuffer ratio, dropped
 * frame rate, seek latency and error rate. Everything is derived from wall clock values supplied by
 * the caller, so the class has no clock and no threads and can be tested deterministically.
 */
public final class PlaybackMetrics {

  /** Immutable view of the counters. */
  public static final class Snapshot {
    private final long timeToFirstFrameMs;
    private final int rebufferCount;
    private final long rebufferDurationMs;
    private final long playedDurationMs;
    private final long renderedFrames;
    private final long droppedFrames;
    private final int seekCount;
    private final long seekLatencyMsTotal;
    private final long seekLatencyMsMax;
    private final long averageBitrateKbps;
    private final int errorCount;
    private final String lastErrorCode;

    Snapshot(
        long timeToFirstFrameMs,
        int rebufferCount,
        long rebufferDurationMs,
        long playedDurationMs,
        long renderedFrames,
        long droppedFrames,
        int seekCount,
        long seekLatencyMsTotal,
        long seekLatencyMsMax,
        long averageBitrateKbps,
        int errorCount,
        String lastErrorCode) {
      this.timeToFirstFrameMs = timeToFirstFrameMs;
      this.rebufferCount = rebufferCount;
      this.rebufferDurationMs = rebufferDurationMs;
      this.playedDurationMs = playedDurationMs;
      this.renderedFrames = renderedFrames;
      this.droppedFrames = droppedFrames;
      this.seekCount = seekCount;
      this.seekLatencyMsTotal = seekLatencyMsTotal;
      this.seekLatencyMsMax = seekLatencyMsMax;
      this.averageBitrateKbps = averageBitrateKbps;
      this.errorCount = errorCount;
      this.lastErrorCode = lastErrorCode;
    }

    /** Milliseconds from "user pressed play" to "first frame on screen"; -1 when never reached. */
    public long timeToFirstFrameMs() {
      return timeToFirstFrameMs;
    }

    public int rebufferCount() {
      return rebufferCount;
    }

    public long rebufferDurationMs() {
      return rebufferDurationMs;
    }

    public long playedDurationMs() {
      return playedDurationMs;
    }

    public long renderedFrames() {
      return renderedFrames;
    }

    public long droppedFrames() {
      return droppedFrames;
    }

    public int seekCount() {
      return seekCount;
    }

    public long seekLatencyMsTotal() {
      return seekLatencyMsTotal;
    }

    public long seekLatencyMsMax() {
      return seekLatencyMsMax;
    }

    public long seekLatencyMsAverage() {
      return seekCount == 0 ? 0L : seekLatencyMsTotal / seekCount;
    }

    public long averageBitrateKbps() {
      return averageBitrateKbps;
    }

    public int errorCount() {
      return errorCount;
    }

    public String lastErrorCode() {
      return lastErrorCode;
    }

    /** Stalled time as a share of total session time, in [0, 1]. */
    public float rebufferRatio() {
      long total = playedDurationMs + rebufferDurationMs;
      if (total <= 0) {
        return 0f;
      }
      return (float) rebufferDurationMs / (float) total;
    }

    /** Dropped frames as a share of scheduled frames, in [0, 1]. */
    public float droppedFrameRatio() {
      long total = renderedFrames + droppedFrames;
      if (total <= 0) {
        return 0f;
      }
      return (float) droppedFrames / (float) total;
    }

    @Override
    public String toString() {
      return "ttff="
          + timeToFirstFrameMs
          + "ms rebuffer="
          + rebufferCount
          + "x/"
          + rebufferDurationMs
          + "ms ratio="
          + rebufferRatio()
          + " dropped="
          + droppedFrames
          + "/"
          + (renderedFrames + droppedFrames)
          + " seeks="
          + seekCount
          + " avgSeek="
          + seekLatencyMsAverage()
          + "ms bitrate="
          + averageBitrateKbps
          + "kbps errors="
          + errorCount;
    }
  }

  private long prepareStartMs = -1L;
  private long timeToFirstFrameMs = -1L;

  private int rebufferCount;
  private long rebufferDurationMs;
  private long rebufferStartMs = -1L;

  private long playedDurationMs;
  private long lastPositionMs = -1L;

  private long renderedFrames;
  private long droppedFrames;

  private int seekCount;
  private long seekLatencyMsTotal;
  private long seekLatencyMsMax;

  private long bitrateWeightedSum;
  private long bitrateWeightTotal;

  private int errorCount;
  private String lastErrorCode;

  /** Called when the pipeline starts loading a media item. */
  public void onPrepareStarted(long elapsedMs) {
    prepareStartMs = elapsedMs;
    timeToFirstFrameMs = -1L;
  }

  /** Called when the first frame is presented. Only the first call of a session counts. */
  public void onFirstFrame(long elapsedMs) {
    if (timeToFirstFrameMs >= 0) {
      return;
    }
    timeToFirstFrameMs = prepareStartMs < 0 ? 0L : Math.max(0L, elapsedMs - prepareStartMs);
  }

  public void onBufferingStarted(long elapsedMs) {
    if (rebufferStartMs >= 0) {
      return;
    }
    rebufferStartMs = elapsedMs;
    rebufferCount++;
  }

  public void onBufferingEnded(long elapsedMs) {
    if (rebufferStartMs < 0) {
      return;
    }
    rebufferDurationMs += Math.max(0L, elapsedMs - rebufferStartMs);
    rebufferStartMs = -1L;
  }

  /**
   * Advances the played-time counter.
   *
   * @param positionMs current media position; backwards jumps (seeks) do not add time
   */
  public void onPosition(long positionMs) {
    if (lastPositionMs >= 0 && positionMs > lastPositionMs) {
      playedDurationMs += positionMs - lastPositionMs;
    }
    lastPositionMs = positionMs;
  }

  /** @param renderedDelta frames rendered since the previous call */
  public void onFrames(long renderedDelta, long droppedDelta) {
    renderedFrames += Math.max(0L, renderedDelta);
    droppedFrames += Math.max(0L, droppedDelta);
  }

  /** @param latencyMs time from the seek request to the first frame after the seek */
  public void onSeek(long latencyMs) {
    long clamped = Math.max(0L, latencyMs);
    seekCount++;
    seekLatencyMsTotal += clamped;
    if (clamped > seekLatencyMsMax) {
      seekLatencyMsMax = clamped;
    }
  }

  /** @param weightMs how long the given bit rate was in effect */
  public void onBitrate(long bitrateKbps, long weightMs) {
    long weight = Math.max(0L, weightMs);
    bitrateWeightedSum += Math.max(0L, bitrateKbps) * weight;
    bitrateWeightTotal += weight;
  }

  public void onError(String code) {
    errorCount++;
    lastErrorCode = code;
  }

  public Snapshot snapshot() {
    long averageBitrate = bitrateWeightTotal == 0 ? 0L : bitrateWeightedSum / bitrateWeightTotal;
    return new Snapshot(
        timeToFirstFrameMs,
        rebufferCount,
        rebufferDurationMs,
        playedDurationMs,
        renderedFrames,
        droppedFrames,
        seekCount,
        seekLatencyMsTotal,
        seekLatencyMsMax,
        averageBitrate,
        errorCount,
        lastErrorCode);
  }
}
