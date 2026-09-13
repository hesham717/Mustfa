package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class CacheIndexTest {

  private static final long MB = 1024L * 1024L;

  @Test
  public void tracksUsageAgainstTheBudget() {
    CacheIndex index = new CacheIndex(10 * MB);
    assertTrue(index.touch("a", 4 * MB).isEmpty());
    assertTrue(index.touch("b", 4 * MB).isEmpty());
    assertEquals(8 * MB, index.usedBytes());
    assertEquals(2 * MB, index.freeBytes());
    assertEquals(2, index.size());
  }

  @Test
  public void evictsLeastRecentlyUsedFirst() {
    CacheIndex index = new CacheIndex(10 * MB);
    index.touch("a", 4 * MB);
    index.touch("b", 4 * MB);
    // Reading "a" promotes it, so "b" is the eviction candidate.
    assertTrue(index.contains("a"));

    List<String> evicted = index.touch("c", 4 * MB);
    assertEquals(1, evicted.size());
    assertEquals("b", evicted.get(0));
    assertTrue(index.contains("a"));
    assertTrue(index.contains("c"));
    assertEquals(8 * MB, index.usedBytes());
  }

  @Test
  public void evictsAsManyEntriesAsNeeded() {
    CacheIndex index = new CacheIndex(10 * MB);
    index.touch("a", 3 * MB);
    index.touch("b", 3 * MB);
    index.touch("c", 3 * MB);

    List<String> evicted = index.touch("d", 9 * MB);
    assertEquals(3, evicted.size());
    assertEquals("a", evicted.get(0));
    assertEquals("c", evicted.get(2));
    assertEquals(9 * MB, index.usedBytes());
    assertEquals(1, index.size());
  }

  @Test
  public void refusesObjectsLargerThanTheWholeCache() {
    CacheIndex index = new CacheIndex(4 * MB);
    index.touch("small", 1 * MB);
    assertTrue(index.touch("huge", 8 * MB).isEmpty());
    assertFalse(index.contains("huge"));
    assertTrue("the existing entry must survive a refused insert", index.contains("small"));
  }

  @Test
  public void updatingAnEntryReplacesItsSize() {
    CacheIndex index = new CacheIndex(10 * MB);
    index.touch("a", 2 * MB);
    index.touch("a", 5 * MB);
    assertEquals(5 * MB, index.usedBytes());
    assertEquals(5 * MB, index.sizeOf("a"));
    assertEquals(1, index.size());
  }

  @Test
  public void explicitRemoveFreesBudget() {
    CacheIndex index = new CacheIndex(10 * MB);
    index.touch("a", 6 * MB);
    assertTrue(index.remove("a"));
    assertFalse(index.remove("a"));
    assertEquals(0L, index.usedBytes());
    assertEquals(10 * MB, index.freeBytes());
  }

  @Test
  public void clearResetsEverything() {
    CacheIndex index = new CacheIndex(10 * MB);
    index.touch("a", 6 * MB);
    index.clear();
    assertEquals(0, index.size());
    assertEquals(0L, index.usedBytes());
  }

  @Test
  public void ignoresBadInput() {
    CacheIndex index = new CacheIndex(10 * MB);
    assertTrue(index.touch(null, 1 * MB).isEmpty());
    assertTrue(index.touch("a", -1L).isEmpty());
    assertEquals(-1L, index.sizeOf("missing"));
    index.remove(null);
    assertEquals(0, index.size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsANonPositiveBudget() {
    new CacheIndex(0L);
  }
}
