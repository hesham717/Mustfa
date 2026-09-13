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
 * Concrete tuning values derived from a {@link DeviceTier}.
 *
 * <p>These numbers are the "performance budget" of the player expressed as configuration. They map
 * one-to-one onto {@code DefaultLoadControl} buffer durations, {@code DefaultTrackSelector} max video
 * size and the disk cache size on Android, and onto {@code AVPlayerItem} preferred peak bit rate and
 * the file cache budget on iOS.
 *
 * <p>Rationale for the numbers (see docs/player/04-performance-and-metrics.md):
 *
 * <ul>
 *   <li>Startup uses a small "play as soon as possible" watermark ({@code bufferForPlaybackMs})
 *     because time-to-first-frame is the headline metric, not buffer depth.
 *   <li>{@code maxBufferMs} stays modest: every extra second of 8 Mbit/s video is ~1 MB of heap, and
 *     on a low tier device that competes with the decoder's own output buffers.
 *   <li>Cache budget is capped at a fraction of free storage, never above these values.
 * </ul>
 */
public final class PlaybackTuning {

  /** Mirrors {@code DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF}. */
  public static final int EXTENSION_RENDERER_OFF = 0;
  /** Mirrors {@code DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON}. */
  public static final int EXTENSION_RENDERER_ON = 1;
  /** Mirrors {@code DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER}. */
  public static final int EXTENSION_RENDERER_PREFER = 2;

  private final DeviceTier tier;
  private final int minBufferMs;
  private final int maxBufferMs;
  private final int bufferForPlaybackMs;
  private final int bufferForPlaybackAfterRebufferMs;
  private final int maxVideoWidth;
  private final int maxVideoHeight;
  private final long cacheBytes;
  private final int extensionRendererMode;
  private final int decodeThreads;
  private final boolean allowExtendedSpeeds;

  private PlaybackTuning(
      DeviceTier tier,
      int minBufferMs,
      int maxBufferMs,
      int bufferForPlaybackMs,
      int bufferForPlaybackAfterRebufferMs,
      int maxVideoWidth,
      int maxVideoHeight,
      long cacheBytes,
      int extensionRendererMode,
      int decodeThreads,
      boolean allowExtendedSpeeds) {
    this.tier = tier;
    this.minBufferMs = minBufferMs;
    this.maxBufferMs = maxBufferMs;
    this.bufferForPlaybackMs = bufferForPlaybackMs;
    this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
    this.maxVideoWidth = maxVideoWidth;
    this.maxVideoHeight = maxVideoHeight;
    this.cacheBytes = cacheBytes;
    this.extensionRendererMode = extensionRendererMode;
    this.decodeThreads = decodeThreads;
    this.allowExtendedSpeeds = allowExtendedSpeeds;
  }

  /** Default tuning table for a device tier. */
  public static PlaybackTuning forTier(DeviceTier tier) {
    switch (tier) {
      case LOW:
        return new PlaybackTuning(
            DeviceTier.LOW,
            /* minBufferMs= */ 15_000,
            /* maxBufferMs= */ 30_000,
            /* bufferForPlaybackMs= */ 800,
            /* bufferForPlaybackAfterRebufferMs= */ 2_000,
            /* maxVideoWidth= */ 1280,
            /* maxVideoHeight= */ 720,
            /* cacheBytes= */ 64L * 1024 * 1024,
            EXTENSION_RENDERER_OFF,
            /* decodeThreads= */ 2,
            /* allowExtendedSpeeds= */ false);
      case MID:
        return new PlaybackTuning(
            DeviceTier.MID,
            30_000,
            60_000,
            1_000,
            2_500,
            1920,
            1080,
            128L * 1024 * 1024,
            EXTENSION_RENDERER_ON,
            3,
            false);
      case HIGH:
      default:
        return new PlaybackTuning(
            DeviceTier.HIGH,
            50_000,
            120_000,
            1_250,
            3_000,
            3840,
            2160,
            256L * 1024 * 1024,
            EXTENSION_RENDERER_PREFER,
            4,
            true);
    }
  }

  public DeviceTier tier() {
    return tier;
  }

  public int minBufferMs() {
    return minBufferMs;
  }

  public int maxBufferMs() {
    return maxBufferMs;
  }

  public int bufferForPlaybackMs() {
    return bufferForPlaybackMs;
  }

  public int bufferForPlaybackAfterRebufferMs() {
    return bufferForPlaybackAfterRebufferMs;
  }

  public int maxVideoWidth() {
    return maxVideoWidth;
  }

  public int maxVideoHeight() {
    return maxVideoHeight;
  }

  public long cacheBytes() {
    return cacheBytes;
  }

  public int extensionRendererMode() {
    return extensionRendererMode;
  }

  public int decodeThreads() {
    return decodeThreads;
  }

  public boolean allowExtendedSpeeds() {
    return allowExtendedSpeeds;
  }

  /** {@code true} when a stream of the given size is inside the resolution ceiling. */
  public boolean isWithinResolutionCeiling(int width, int height) {
    if (width <= 0 || height <= 0) {
      return true;
    }
    return width <= maxVideoWidth && height <= maxVideoHeight;
  }

  @Override
  public String toString() {
    return "PlaybackTuning{"
        + tier
        + " buffer="
        + minBufferMs
        + "/"
        + maxBufferMs
        + "ms maxVideo="
        + maxVideoWidth
        + "x"
        + maxVideoHeight
        + " cache="
        + (cacheBytes / (1024 * 1024))
        + "MB threads="
        + decodeThreads
        + '}';
  }
}
