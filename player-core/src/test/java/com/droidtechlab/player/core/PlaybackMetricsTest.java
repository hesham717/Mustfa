package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackMetricsTest {

  @Test
  public void measuresTimeToFirstFrame() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onPrepareStarted(1_000L);
    metrics.onFirstFrame(1_180L);
    assertEquals(180L, metrics.snapshot().timeToFirstFrameMs());
  }

  @Test
  public void onlyTheFirstFrameCountsTowardsStartup() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onPrepareStarted(0L);
    metrics.onFirstFrame(200L);
    metrics.onFirstFrame(9_000L);
    assertEquals(200L, metrics.snapshot().timeToFirstFrameMs());
  }

  @Test
  public void reportsMinusOneWhenPlaybackNeverStarted() {
    assertEquals(-1L, new PlaybackMetrics().snapshot().timeToFirstFrameMs());
  }

  @Test
  public void accumulatesRebuffering() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onBufferingStarted(10_000L);
    metrics.onBufferingEnded(11_500L);
    metrics.onBufferingStarted(60_000L);
    metrics.onBufferingEnded(62_000L);

    PlaybackMetrics.Snapshot snapshot = metrics.snapshot();
    assertEquals(2, snapshot.rebufferCount());
    assertEquals(3_500L, snapshot.rebufferDurationMs());
  }

  @Test
  public void ignoresUnbalancedBufferingCallbacks() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onBufferingEnded(500L);
    assertEquals(0, metrics.snapshot().rebufferCount());
    metrics.onBufferingStarted(1_000L);
    metrics.onBufferingStarted(1_500L);
    assertEquals(1, metrics.snapshot().rebufferCount());
  }

  @Test
  public void rebufferRatioIsStalledTimeOverSessionTime() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onPosition(0L);
    metrics.onPosition(10_000L);
    metrics.onBufferingStarted(10_000L);
    metrics.onBufferingEnded(12_500L);

    PlaybackMetrics.Snapshot snapshot = metrics.snapshot();
    assertEquals(10_000L, snapshot.playedDurationMs());
    assertEquals(2_500L, snapshot.rebufferDurationMs());
    // 2.5 s stalled out of 12.5 s total.
    assertEquals(0.2f, snapshot.rebufferRatio(), 0.0001f);
  }

  @Test
  public void seekingBackwardsDoesNotCountAsPlayedTime() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onPosition(0L);
    metrics.onPosition(30_000L);
    metrics.onPosition(5_000L);
    metrics.onPosition(10_000L);
    assertEquals(35_000L, metrics.snapshot().playedDurationMs());
  }

  @Test
  public void tracksDroppedFrameRatio() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onFrames(950L, 50L);
    PlaybackMetrics.Snapshot snapshot = metrics.snapshot();
    assertEquals(950L, snapshot.renderedFrames());
    assertEquals(0.05f, snapshot.droppedFrameRatio(), 0.0001f);
    assertEquals(0f, new PlaybackMetrics().snapshot().droppedFrameRatio(), 0.0001f);
  }

  @Test
  public void tracksSeekLatency() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onSeek(100L);
    metrics.onSeek(300L);
    metrics.onSeek(-50L);

    PlaybackMetrics.Snapshot snapshot = metrics.snapshot();
    assertEquals(3, snapshot.seekCount());
    assertEquals(400L, snapshot.seekLatencyMsTotal());
    assertEquals(300L, snapshot.seekLatencyMsMax());
    assertEquals(133L, snapshot.seekLatencyMsAverage());
  }

  @Test
  public void averagesBitrateByTimeWeight() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onBitrate(1_000L, 10_000L);
    metrics.onBitrate(4_000L, 30_000L);
    // (1000*10 + 4000*30) / 40 = 3250 kbps
    assertEquals(3_250L, metrics.snapshot().averageBitrateKbps());
  }

  @Test
  public void countsErrorsWithTheLastCode() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onError("decoder_init_failed");
    metrics.onError("source_not_found");
    PlaybackMetrics.Snapshot snapshot = metrics.snapshot();
    assertEquals(2, snapshot.errorCount());
    assertEquals("source_not_found", snapshot.lastErrorCode());
    assertNull(new PlaybackMetrics().snapshot().lastErrorCode());
  }

  @Test
  public void snapshotSummaryIsHumanReadable() {
    PlaybackMetrics metrics = new PlaybackMetrics();
    metrics.onPrepareStarted(0L);
    metrics.onFirstFrame(150L);
    String summary = metrics.snapshot().toString();
    assertTrue(summary, summary.contains("ttff=150ms"));
  }
}
