/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
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

package com.droidtechlab.filemanager.ui.views;

import static com.droidtechlab.filemanager.ui.views.PlayerGestureTracker.Direction.HORIZONTAL;
import static com.droidtechlab.filemanager.ui.views.PlayerGestureTracker.Direction.NONE;
import static com.droidtechlab.filemanager.ui.views.PlayerGestureTracker.Direction.VERTICAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class PlayerGestureTrackerTest {

  private static final float SLOP = 24f;
  private PlayerGestureTracker tracker;

  @Before
  public void setUp() {
    tracker = new PlayerGestureTracker(SLOP);
    tracker.onDown(500, 500);
  }

  @Test
  public void tapWithoutMovementIsNotAGesture() {
    assertFalse(tracker.isGestureActive());
    assertTrue(tracker.onUp());
  }

  @Test
  public void jitterInsideSlopIsIgnoredAndStillCountsAsTap() {
    assertEquals(NONE, tracker.onMove(510, 495));
    assertEquals(NONE, tracker.onMove(490, 512));
    assertEquals(NONE, tracker.onMove(520, 480));
    assertFalse(tracker.isGestureActive());
    assertTrue(tracker.onUp());
  }

  @Test
  public void shortHorizontalDragJustBeyondSlopLocksHorizontal() {
    assertEquals(HORIZONTAL, tracker.onMove(530, 502));
    assertTrue(tracker.isGestureActive());
    assertFalse(tracker.onUp());
  }

  @Test
  public void longHorizontalDragStaysHorizontalEvenWhenDriftingVertically() {
    assertEquals(HORIZONTAL, tracker.onMove(540, 500));
    // Finger drifts a lot vertically later on – direction must stay locked.
    assertEquals(HORIZONTAL, tracker.onMove(700, 700));
    assertEquals(HORIZONTAL, tracker.onMove(900, 200));
    assertEquals(200f, tracker.getDeltaX(), 0.01f);
  }

  @Test
  public void shortVerticalDragLocksVertical() {
    assertEquals(VERTICAL, tracker.onMove(503, 470));
    assertEquals(-30f, tracker.getDeltaY(), 0.01f);
  }

  @Test
  public void longVerticalDragStaysVerticalEvenWhenDriftingHorizontally() {
    assertEquals(VERTICAL, tracker.onMove(500, 440));
    assertEquals(VERTICAL, tracker.onMove(800, 300));
    assertEquals(VERTICAL, tracker.getDirection());
  }

  @Test
  public void diagonalMoveIsNotAcceptedAsDirection() {
    // 45° beyond slop – ambiguous, must not lock.
    assertEquals(NONE, tracker.onMove(535, 535));
    assertFalse(tracker.isGestureActive());
  }

  @Test
  public void diagonalMoveThatStraightensOutLocksLater() {
    assertEquals(NONE, tracker.onMove(535, 535));
    // Then the user commits horizontally before the reject threshold.
    assertEquals(HORIZONTAL, tracker.onMove(600, 540));
  }

  @Test
  public void persistentDiagonalMoveRejectsTheTouch() {
    assertEquals(NONE, tracker.onMove(535, 535));
    assertEquals(NONE, tracker.onMove(600, 600));
    assertTrue(tracker.isRejected());
    // Even a later clearly horizontal motion must not resurrect the gesture.
    assertEquals(NONE, tracker.onMove(900, 610));
    assertFalse(tracker.isGestureActive());
    // A rejected touch is not reported as a tap either.
    assertFalse(tracker.onUp());
  }

  @Test
  public void cancelStopsTracking() {
    tracker.onMove(560, 500);
    tracker.cancel();
    assertFalse(tracker.isGestureActive());
    assertEquals(NONE, tracker.onMove(900, 500));
  }

  @Test
  public void newDownResetsState() {
    tracker.onMove(535, 535);
    tracker.onMove(600, 600);
    assertTrue(tracker.isRejected());
    tracker.onDown(100, 100);
    assertFalse(tracker.isRejected());
    assertEquals(HORIZONTAL, tracker.onMove(160, 102));
  }

  @Test
  public void seekOffsetIsBoundedForLongVideos() {
    long twoHours = 2 * 60 * 60 * 1000L;
    // Full width swipe seeks at most MAX_SEEK_WINDOW_MS, never 2 hours.
    assertEquals(PlayerGestureTracker.MAX_SEEK_WINDOW_MS,
        PlayerGestureTracker.seekOffsetMs(1080, 1080, twoHours));
    assertEquals(-PlayerGestureTracker.MAX_SEEK_WINDOW_MS / 2,
        PlayerGestureTracker.seekOffsetMs(-540, 1080, twoHours));
  }

  @Test
  public void seekOffsetHasMinimumWindowForShortVideos() {
    long fiveMinutes = 5 * 60 * 1000L;
    // duration/10 = 30s < min window → min window (60s) is used.
    assertEquals(PlayerGestureTracker.MIN_SEEK_WINDOW_MS,
        PlayerGestureTracker.seekOffsetMs(1000, 1000, fiveMinutes));
    // But never more than the duration itself.
    assertEquals(20_000L, PlayerGestureTracker.seekOffsetMs(1000, 1000, 20_000L));
    assertEquals(0L, PlayerGestureTracker.seekOffsetMs(1000, 1000, 0L));
  }

  @Test
  public void verticalFractionIsLessSensitiveThanFullRange() {
    // Swiping up the whole height changes by VERTICAL_FULL_SWIPE_FRACTION, positive direction.
    assertEquals(PlayerGestureTracker.VERTICAL_FULL_SWIPE_FRACTION,
        PlayerGestureTracker.verticalFraction(-1000, 1000), 0.0001f);
    assertEquals(-PlayerGestureTracker.VERTICAL_FULL_SWIPE_FRACTION / 4,
        PlayerGestureTracker.verticalFraction(250, 1000), 0.0001f);
    assertEquals(0f, PlayerGestureTracker.verticalFraction(250, 0), 0.0001f);
  }
}
