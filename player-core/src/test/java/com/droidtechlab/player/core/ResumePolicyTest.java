package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResumePolicyTest {

  @Test
  public void tinyPositionsStartOver() {
    assertEquals(ResumePolicy.Action.START_FROM_BEGINNING,
        ResumePolicy.decide(0L, 600_000L).action());
    assertEquals(ResumePolicy.Action.START_FROM_BEGINNING,
        ResumePolicy.decide(4_999L, 600_000L).action());
    assertEquals(0L, ResumePolicy.decide(2_000L, 600_000L).positionMs());
  }

  @Test
  public void midStreamPositionsResume() {
    ResumePolicy.Decision decision = ResumePolicy.decide(120_000L, 600_000L);
    assertTrue(decision.isResume());
    assertEquals(120_000L, decision.positionMs());
  }

  @Test
  public void positionsInsideTheTailAreTreatedAsWatched() {
    // 10 minutes long, saved at 9:45 -> 15 s from the end.
    assertEquals(ResumePolicy.Action.START_FROM_BEGINNING,
        ResumePolicy.decide(585_000L, 600_000L).action());
  }

  @Test
  public void positionsPastTheCompletionRatioStartOver() {
    // 100 minutes long: the tail rule does not apply, but 96% does.
    long duration = 6_000_000L;
    assertEquals(ResumePolicy.Action.START_FROM_BEGINNING,
        ResumePolicy.decide(5_800_000L, duration).action());
  }

  @Test
  public void justBeforeTheTailStillResumes() {
    // 100 minutes long, saved at 90 minutes: 10 minutes of tail left.
    ResumePolicy.Decision decision = ResumePolicy.decide(5_400_000L, 6_000_000L);
    assertTrue(decision.isResume());
  }

  @Test
  public void unknownDurationFallsBackToTheThresholdOnly() {
    ResumePolicy.Decision decision = ResumePolicy.decide(60_000L, 0L);
    assertTrue("without a duration the tail rules cannot be applied", decision.isResume());
    assertEquals(60_000L, decision.positionMs());
  }

  @Test
  public void persistenceIsSkippedForNoise() {
    assertFalse(ResumePolicy.shouldPersist(0L));
    assertFalse(ResumePolicy.shouldPersist(5_000L));
    assertTrue(ResumePolicy.shouldPersist(5_001L));
  }

  @Test
  public void decisionIsReadable() {
    assertTrue(ResumePolicy.decide(60_000L, 600_000L).toString().contains("RESUME"));
  }
}
