package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackTuningTest {

  @Test
  public void classifiesByMemoryAndCores() {
    // 1 GB, 4 cores, Android 9 -> low end.
    assertEquals(DeviceTier.LOW, DeviceTier.classify(4, 1024L, 28));
    // 3 GB, 6 cores -> mid range.
    assertEquals(DeviceTier.MID, DeviceTier.classify(6, 3072L, 29));
    // 8 GB, 8 cores -> flagship.
    assertEquals(DeviceTier.HIGH, DeviceTier.classify(8, 8192L, 31));
  }

  @Test
  public void oldOsLevelsAreAlwaysLowTier() {
    assertEquals(DeviceTier.LOW, DeviceTier.classify(8, 8192L, 19));
  }

  @Test
  public void twoCoreDevicesAreLowTierEvenWithMemory() {
    assertEquals(DeviceTier.LOW, DeviceTier.classify(2, 6144L, 30));
  }

  @Test
  public void unknownRamIsLowTier() {
    assertEquals(DeviceTier.LOW, DeviceTier.classify(8, 0L, 31));
  }

  @Test
  public void startupWatermarkStaysSmallOnEveryTier() {
    for (DeviceTier tier : DeviceTier.values()) {
      PlaybackTuning tuning = PlaybackTuning.forTier(tier);
      assertTrue(tier + " must start playing quickly",
          tuning.bufferForPlaybackMs() <= 1_500);
      assertTrue(tier + " rebuffer watermark must be small",
          tuning.bufferForPlaybackAfterRebufferMs() <= 3_000);
    }
  }

  @Test
  public void bufferAndCacheBudgetGrowWithTheTier() {
    PlaybackTuning low = PlaybackTuning.forTier(DeviceTier.LOW);
    PlaybackTuning mid = PlaybackTuning.forTier(DeviceTier.MID);
    PlaybackTuning high = PlaybackTuning.forTier(DeviceTier.HIGH);

    assertTrue(low.maxBufferMs() < mid.maxBufferMs());
    assertTrue(mid.maxBufferMs() < high.maxBufferMs());
    assertTrue(low.cacheBytes() < mid.cacheBytes());
    assertTrue(mid.cacheBytes() < high.cacheBytes());
    assertTrue(low.decodeThreads() <= mid.decodeThreads());
    assertTrue(mid.decodeThreads() <= high.decodeThreads());
  }

  @Test
  public void lowTierDevicesCapTheResolutionAndHideFastSpeeds() {
    PlaybackTuning low = PlaybackTuning.forTier(DeviceTier.LOW);
    assertEquals(1280, low.maxVideoWidth());
    assertEquals(720, low.maxVideoHeight());
    assertFalse(low.allowExtendedSpeeds());
    assertTrue(PlaybackTuning.forTier(DeviceTier.HIGH).allowExtendedSpeeds());
  }

  @Test
  public void lowTierCacheStaysUnder128Mb() {
    // A file manager competes with copies and extractions for the same storage.
    assertTrue(PlaybackTuning.forTier(DeviceTier.LOW).cacheBytes() <= 128L * 1024 * 1024);
  }

  @Test
  public void resolutionCeilingIgnoresUnknownSizes() {
    PlaybackTuning low = PlaybackTuning.forTier(DeviceTier.LOW);
    assertTrue(low.isWithinResolutionCeiling(0, 0));
    assertTrue(low.isWithinResolutionCeiling(1280, 720));
    assertFalse(low.isWithinResolutionCeiling(3840, 2160));
  }

  @Test
  public void rendererExtensionModeIsOnlyEnabledAboveLowTier() {
    assertEquals(PlaybackTuning.EXTENSION_RENDERER_OFF,
        PlaybackTuning.forTier(DeviceTier.LOW).extensionRendererMode());
    assertEquals(PlaybackTuning.EXTENSION_RENDERER_ON,
        PlaybackTuning.forTier(DeviceTier.MID).extensionRendererMode());
    assertEquals(PlaybackTuning.EXTENSION_RENDERER_PREFER,
        PlaybackTuning.forTier(DeviceTier.HIGH).extensionRendererMode());
  }

  @Test
  public void tuningDescribesItselfForDebugOverlays() {
    String description = PlaybackTuning.forTier(DeviceTier.MID).toString();
    assertTrue(description, description.contains("MID"));
    assertTrue(description, description.contains("1920x1080"));
  }
}
