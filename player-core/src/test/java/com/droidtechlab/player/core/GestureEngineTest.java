package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Screen geometry used by the tests: a 1000x500 px player surface. */
public class GestureEngineTest {

  private static final float WIDTH = 1000f;
  private static final float HEIGHT = 500f;
  private static final long DURATION_MS = 600_000L;

  private static GestureEngine engine() {
    GestureEngine engine = new GestureEngine(WIDTH, HEIGHT);
    engine.setDurationMs(DURATION_MS);
    return engine;
  }

  @Test
  public void movementBelowTheSlopIsIgnored() {
    GestureEngine engine = engine();
    engine.onDown(500f, 250f, 0L);
    assertEquals(GestureType.NONE, engine.onMove(510f, 255f).type());
  }

  @Test
  public void horizontalDragSeeksProportionally() {
    GestureEngine engine = engine();
    engine.onDown(100f, 250f, 100_000L);
    // 250 px of 1000 px = 25% of the 120 s seek span = 30 s forward.
    GestureResult result = engine.onMove(350f, 260f);
    assertEquals(GestureType.SEEK_PREVIEW, result.type());
    assertEquals(30_000L, result.seekDeltaMs());
    assertEquals(130_000L, result.seekTargetMs());
  }

  @Test
  public void horizontalSeekClampsToTheMediaBounds() {
    GestureEngine engine = engine();
    // Full width backwards is a 120 s jump from 9:50, which lands at 7:50.
    engine.onDown(900f, 250f, 590_000L);
    GestureResult result = engine.onMove(0f, 250f);
    assertEquals(-108_000L, result.seekDeltaMs());
    assertEquals(482_000L, result.seekTargetMs());

    // The same span forwards must stop at the end of the media.
    GestureEngine forward = engine();
    forward.onDown(0f, 250f, 590_000L);
    assertEquals(DURATION_MS, forward.onMove(1000f, 250f).seekTargetMs());

    // Near the start, a backwards scrub never goes below zero.
    GestureEngine start = engine();
    start.onDown(900f, 250f, 20_000L);
    assertEquals(0L, start.onMove(0f, 250f).seekTargetMs());
  }

  @Test
  public void axisIsLockedForTheWholeGesture() {
    GestureEngine engine = engine();
    engine.onDown(200f, 200f, 0L);
    assertEquals(GestureType.SEEK_PREVIEW, engine.onMove(260f, 210f).type());
    // A large vertical component afterwards must not switch to volume.
    assertEquals(GestureType.SEEK_PREVIEW, engine.onMove(280f, 480f).type());
  }

  @Test
  public void verticalDragOnTheLeftChangesBrightnessIncrementally() {
    GestureEngine engine = engine();
    engine.onDown(200f, 400f, 0L);
    GestureResult first = engine.onMove(200f, 350f);
    assertEquals(GestureType.BRIGHTNESS, first.type());
    assertEquals(0.1f, first.delta(), 0.0001f);

    GestureResult second = engine.onMove(200f, 300f);
    assertEquals("deltas must be incremental, not cumulative",
        0.1f, second.delta(), 0.0001f);
    assertEquals(0.2f, first.delta() + second.delta(), 0.0001f);
  }

  @Test
  public void verticalDragOnTheRightChangesVolume() {
    GestureEngine engine = engine();
    engine.onDown(800f, 250f, 0L);
    GestureResult result = engine.onMove(800f, 300f);
    assertEquals(GestureType.VOLUME, result.type());
    assertEquals(-0.1f, result.delta(), 0.0001f);
  }

  @Test
  public void doubleTapZonesMatchThePlayerLayout() {
    GestureEngine engine = engine();
    assertEquals(GestureType.SEEK_BACKWARD, engine.onDoubleTap(100f, 250f).type());
    assertEquals(GestureType.PLAY_PAUSE, engine.onDoubleTap(500f, 250f).type());
    assertEquals(GestureType.SEEK_FORWARD, engine.onDoubleTap(900f, 250f).type());
    assertEquals(10_000L, engine.doubleTapStepMs());
    assertEquals(-10_000L, engine.onDoubleTap(100f, 250f).seekDeltaMs());
  }

  @Test
  public void singleTapTogglesControls() {
    GestureEngine engine = engine();
    assertEquals(GestureType.TOGGLE_CONTROLS, engine.onSingleTap().type());
  }

  @Test
  public void lockSwallowsEveryGesture() {
    GestureEngine engine = engine();
    engine.setLocked(true);
    assertTrue(engine.isLocked());
    engine.onDown(200f, 200f, 0L);
    assertEquals(GestureType.NONE, engine.onMove(900f, 480f).type());
    assertEquals(GestureType.NONE, engine.onSingleTap().type());
    assertEquals(GestureType.NONE, engine.onDoubleTap(900f, 250f).type());

    engine.setLocked(false);
    assertEquals(GestureType.TOGGLE_CONTROLS, engine.onSingleTap().type());
  }

  @Test
  public void seekIsDisabledWithoutADuration() {
    GestureEngine engine = new GestureEngine(WIDTH, HEIGHT);
    engine.onDown(100f, 250f, 0L);
    assertEquals(GestureType.NONE, engine.onMove(600f, 250f).type());
  }

  @Test
  public void accessibilityFlagsCanDisableWholeGestures() {
    GestureConfig config =
        GestureConfig.builder().horizontalSeekEnabled(false).verticalAdjustEnabled(false).build();
    GestureEngine engine = new GestureEngine(WIDTH, HEIGHT, config);
    engine.setDurationMs(DURATION_MS);
    engine.onDown(100f, 250f, 0L);
    assertEquals(GestureType.NONE, engine.onMove(600f, 250f).type());

    engine = new GestureEngine(WIDTH, HEIGHT, config);
    engine.setDurationMs(DURATION_MS);
    engine.onDown(100f, 250f, 0L);
    assertEquals(GestureType.NONE, engine.onMove(100f, 400f).type());
  }

  @Test
  public void seekSpanIsConfigurable() {
    GestureConfig config = GestureConfig.builder().seekSpanMs(60_000L).build();
    GestureEngine engine = new GestureEngine(WIDTH, HEIGHT, config);
    engine.setDurationMs(DURATION_MS);
    engine.onDown(0f, 250f, 0L);
    // Full width now maps to 60 s instead of 120 s.
    assertEquals(60_000L, engine.onMove(1000f, 250f).seekDeltaMs());
    assertEquals(5_000L, engine.doubleTapStepMs());
  }

  @Test
  public void onUpResetsTheGesture() {
    GestureEngine engine = engine();
    engine.onDown(100f, 250f, 0L);
    assertEquals(GestureType.SEEK_PREVIEW, engine.onMove(300f, 250f).type());
    assertEquals(GestureType.NONE, engine.onUp().type());

    // A new gesture starts from a clean state: no axis is inherited from the previous one.
    engine.onDown(100f, 250f, 50_000L);
    assertEquals(GestureType.NONE, engine.onMove(105f, 252f).type());
    assertEquals(GestureType.BRIGHTNESS, engine.onMove(100f, 300f).type());
  }

  @Test
  public void tinyViewportsDoNotProduceExtremeDeltas() {
    GestureEngine engine = new GestureEngine(20f, 20f);
    engine.setDurationMs(DURATION_MS);
    engine.onDown(0f, 0f, 0L);
    GestureResult seek = engine.onMove(30f, 0f);
    assertEquals(GestureType.SEEK_PREVIEW, seek.type());
    assertTrue("seek must stay inside the configured span",
        Math.abs(seek.seekDeltaMs()) <= GestureConfig.DEFAULT_SEEK_SPAN_MS);

    engine.onDown(0f, 0f, 0L);
    engine.onUp();
    engine.onDown(0f, 0f, 0L);
    GestureResult vertical = engine.onMove(0f, 30f);
    assertEquals(GestureType.BRIGHTNESS, vertical.type());
    assertTrue("brightness deltas are a share of the viewport",
        Math.abs(vertical.delta()) <= 2f);
  }
}
