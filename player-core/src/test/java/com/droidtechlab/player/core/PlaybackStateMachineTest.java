package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PlaybackStateMachineTest {

  @Test
  public void startsIdle() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    assertEquals(PlaybackState.IDLE, machine.current());
    assertTrue(machine.isIdle());
    assertEquals(0, machine.transitionCount());
  }

  @Test
  public void followsTheHappyPath() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    machine.transitionTo(PlaybackState.PREPARING);
    machine.transitionTo(PlaybackState.READY);
    machine.transitionTo(PlaybackState.PLAYING);
    machine.transitionTo(PlaybackState.PAUSED);
    machine.transitionTo(PlaybackState.PLAYING);
    machine.transitionTo(PlaybackState.ENDED);
    machine.transitionTo(PlaybackState.IDLE);
    // PREPARING, READY, PLAYING, PAUSED, PLAYING, ENDED, IDLE
    assertEquals(7, machine.transitionCount());
  }

  @Test
  public void rejectsJumpingFromIdleToPlaying() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    assertFalse(machine.canTransitionTo(PlaybackState.PLAYING));
    try {
      machine.transitionTo(PlaybackState.PLAYING);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("IDLE -> PLAYING"));
    }
    // The machine stays where it was.
    assertEquals(PlaybackState.IDLE, machine.current());
  }

  @Test
  public void rejectsResumingFromEndedWithoutRePrepare() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    machine.transitionTo(PlaybackState.PREPARING);
    machine.transitionTo(PlaybackState.READY);
    machine.transitionTo(PlaybackState.PLAYING);
    machine.transitionTo(PlaybackState.ENDED);
    assertFalse(machine.canTransitionTo(PlaybackState.PLAYING));
    assertTrue(machine.canTransitionTo(PlaybackState.PREPARING));
  }

  @Test
  public void sameStateTransitionIsIdempotent() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    machine.transitionTo(PlaybackState.PREPARING);
    machine.transitionTo(PlaybackState.PREPARING);
    assertEquals(1, machine.transitionCount());
  }

  @Test
  public void bufferingRestoresTheStateItInterrupted() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    machine.transitionTo(PlaybackState.PREPARING);
    machine.transitionTo(PlaybackState.READY);
    machine.transitionTo(PlaybackState.PLAYING);

    machine.onBufferingStarted();
    assertEquals(PlaybackState.BUFFERING, machine.current());
    machine.onBufferingEnded();
    assertEquals(PlaybackState.PLAYING, machine.current());

    machine.transitionTo(PlaybackState.PAUSED);
    machine.onBufferingStarted();
    machine.onBufferingEnded();
    assertEquals("a paused seek must not resume playback by itself",
        PlaybackState.PAUSED, machine.current());
  }

  @Test
  public void bufferingWhileReadyGoesToBuffering() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    machine.transitionTo(PlaybackState.PREPARING);
    machine.transitionTo(PlaybackState.READY);
    machine.onBufferingStarted();
    assertEquals(PlaybackState.BUFFERING, machine.current());
  }

  @Test
  public void errorIsOnlyRecoverableThroughIdle() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    machine.transitionTo(PlaybackState.PREPARING);
    machine.transitionTo(PlaybackState.ERROR);
    assertTrue(machine.isFailed());
    assertFalse(machine.canTransitionTo(PlaybackState.PLAYING));
    machine.transitionTo(PlaybackState.IDLE);
    assertFalse(machine.isFailed());
  }

  @Test
  public void listenersSeeEveryTransition() {
    PlaybackStateMachine machine = new PlaybackStateMachine();
    final StringBuilder seen = new StringBuilder();
    machine.addListener(
        (from, to) -> seen.append(from).append("->").append(to).append(';'));
    machine.transitionTo(PlaybackState.PREPARING);
    machine.transitionTo(PlaybackState.READY);
    assertEquals("IDLE->PREPARING;PREPARING->READY;", seen.toString());
  }

  @Test
  public void everyStateHasALegalWayOut() {
    for (PlaybackState state : PlaybackState.values()) {
      assertFalse("no legal transition out of " + state,
          PlaybackStateMachine.allowedFrom(state).isEmpty());
    }
  }
}
