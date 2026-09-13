package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FrameStepPlannerTest {

  @Test
  public void frameDurationFor25Fps() {
    FrameStepPlanner planner = new FrameStepPlanner(25d);
    assertTrue(planner.isSupported());
    assertEquals(40_000L, planner.frameDurationUs());
  }

  @Test
  public void frameDurationFor23_976Fps() {
    FrameStepPlanner planner = new FrameStepPlanner(24000d / 1001d);
    // 1_000_000 / 23.976 = 41708.3 -> 41708 us
    assertEquals(41_708L, planner.frameDurationUs());
  }

  @Test
  public void unsupportedFrameRatesLeaveThePositionUntouched() {
    FrameStepPlanner planner = new FrameStepPlanner(0d);
    assertFalse(planner.isSupported());
    assertEquals(0L, planner.frameDurationUs());
    // Without a frame rate there is no frame to step to, so the position must not move.
    assertEquals(5_000L, planner.stepForward(5_000L, -1L));
    assertEquals(5_000L, planner.stepBackward(5_000L));
    assertEquals(0L, new FrameStepPlanner(-1d).stepBackward(-500L));
  }

  @Test
  public void steppingForwardAlignsToTheNextFrameBoundary() {
    FrameStepPlanner planner = new FrameStepPlanner(25d);
    assertEquals(40_000L, planner.stepForward(0L, -1L));
    // Mid-frame position snaps up to the following boundary.
    assertEquals(80_000L, planner.stepForward(50_000L, -1L));
    assertEquals(80_000L, planner.stepForward(79_999L, -1L));
  }

  @Test
  public void steppingBackwardNeverGoesNegative() {
    FrameStepPlanner planner = new FrameStepPlanner(30d);
    long frame = planner.frameDurationUs();
    assertEquals(0L, planner.stepBackward(frame / 2));
    assertEquals(0L, planner.stepBackward(frame));
    assertEquals(frame, planner.stepBackward(frame * 2));
  }

  @Test
  public void steppingForwardClampsAtTheDuration() {
    FrameStepPlanner planner = new FrameStepPlanner(30d);
    long duration = 1_000_000L;
    assertEquals(duration, planner.stepForward(999_999L, duration));
  }

  @Test
  public void frameIndexIsDerivedFromTheFrameDuration() {
    FrameStepPlanner planner = new FrameStepPlanner(50d);
    assertEquals(0L, planner.frameIndexAt(0L));
    assertEquals(0L, planner.frameIndexAt(19_999L));
    assertEquals(1L, planner.frameIndexAt(20_000L));
    assertEquals(50L, planner.frameIndexAt(1_000_000L));
  }

  @Test
  public void oneSecondContainsTheExpectedFrameCount() {
    for (double fps : new double[] {24d, 25d, 30d, 60d}) {
      FrameStepPlanner planner = new FrameStepPlanner(fps);
      long frame = planner.frameDurationUs();
      assertTrue("frame duration must be sane for " + fps, frame > 10_000L && frame < 100_000L);
      long frames = FrameStepPlanner.MICROS_PER_SECOND / frame;
      assertTrue("fps " + fps + " gave " + frames + " frames per second",
          Math.abs(frames - Math.round(fps)) <= 1);
    }
  }
}
