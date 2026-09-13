package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class MediaQueueTest {

  private static MediaQueue threeItemQueue() {
    return MediaQueue.of(Arrays.asList("a.mp4", "b.mp4", "c.mp4"), 0);
  }

  @Test
  public void startsAtTheRequestedIndex() {
    MediaQueue queue = MediaQueue.of(Arrays.asList("a.mp4", "b.mp4", "c.mp4"), 2);
    assertEquals("c.mp4", queue.current());
    assertEquals(2, queue.currentIndex());
  }

  @Test
  public void clampsAnOutOfRangeStartIndex() {
    MediaQueue queue = MediaQueue.of(Arrays.asList("a.mp4", "b.mp4"), 9);
    assertEquals("b.mp4", queue.current());
  }

  @Test
  public void walksForwardAndBackwards() {
    MediaQueue queue = threeItemQueue();
    assertEquals("b.mp4", queue.next());
    assertEquals("c.mp4", queue.next());
    assertNull("queue must not loop when repeat is off", queue.next());
    assertEquals("b.mp4", queue.previous());
    assertEquals("a.mp4", queue.previous());
    assertNull(queue.previous());
  }

  @Test
  public void repeatAllWrapsBothWays() {
    MediaQueue queue = threeItemQueue();
    queue.setRepeatMode(RepeatMode.ALL);
    assertTrue(queue.hasNext());
    queue.next();
    queue.next();
    assertEquals("a.mp4", queue.next());
    assertEquals("c.mp4", queue.previous());
  }

  @Test
  public void repeatOneKeepsTheCurrentItem() {
    MediaQueue queue = threeItemQueue();
    queue.setRepeatMode(RepeatMode.ONE);
    assertTrue(queue.shouldReplayCurrentOnEnd());
    assertEquals("a.mp4", queue.next());
    assertEquals("a.mp4", queue.previous());
    assertEquals(0, queue.currentIndex());
  }

  @Test
  public void singleItemQueueDoesNotLoopWhenRepeatIsOff() {
    MediaQueue queue = MediaQueue.ofSingle("only.mp4");
    assertFalse(queue.hasNext());
    assertNull(queue.next());
    queue.setRepeatMode(RepeatMode.ALL);
    assertNull("a one item queue has nothing to wrap to", queue.next());
  }

  @Test
  public void jumpToMovesInsideTheUnderlyingList() {
    MediaQueue queue = threeItemQueue();
    assertTrue(queue.jumpTo(2));
    assertEquals("c.mp4", queue.current());
    assertFalse(queue.jumpTo(7));
    assertEquals("c.mp4", queue.current());
  }

  @Test
  public void shuffleKeepsTheCurrentItemFirstAndIsStableForTheSameSeed() {
    MediaQueue queue = MediaQueue.of(Arrays.asList("a", "b", "c", "d", "e"), 1);
    queue.setShuffleEnabled(true, 42L);
    assertEquals("the item being played must not change when shuffle is turned on",
        "b", queue.current());

    MediaQueue twin = MediaQueue.of(Arrays.asList("a", "b", "c", "d", "e"), 1);
    twin.setShuffleEnabled(true, 42L);
    assertEquals("same seed must produce the same order", orderOf(queue), orderOf(twin));
  }

  @Test
  public void shuffleVisitsEveryItemExactlyOnce() {
    MediaQueue queue = MediaQueue.of(Arrays.asList("a", "b", "c", "d"), 0);
    queue.setShuffleEnabled(true, 7L);
    java.util.Set<String> visited = new java.util.HashSet<>();
    visited.add(queue.current());
    for (int i = 0; i < 3; i++) {
      visited.add(queue.next());
    }
    assertEquals(4, visited.size());
  }

  @Test
  public void disablingShuffleRestoresSequentialOrder() {
    MediaQueue queue = MediaQueue.of(Arrays.asList("a", "b", "c", "d"), 0);
    queue.setShuffleEnabled(true, 5L);
    queue.next();
    queue.setShuffleEnabled(false, 5L);
    assertEquals("a", queue.current());
    assertEquals("b", queue.next());
  }

  @Test
  public void repeatModeParsingIsForgiving() {
    assertEquals(RepeatMode.ALL, RepeatMode.fromName("all"));
    assertEquals(RepeatMode.OFF, RepeatMode.fromName(null));
    assertEquals(RepeatMode.OFF, RepeatMode.fromName("bogus"));
    assertEquals(RepeatMode.ONE, RepeatMode.OFF.next().next());
  }

  private static String orderOf(MediaQueue queue) {
    StringBuilder order = new StringBuilder(queue.current());
    for (int i = 1; i < queue.size(); i++) {
      order.append(queue.next());
    }
    return order.toString();
  }
}
