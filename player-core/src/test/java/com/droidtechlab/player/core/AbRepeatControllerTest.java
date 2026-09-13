package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AbRepeatControllerTest {

  @Test
  public void isInactiveUntilBothMarksExist() {
    AbRepeatController controller = new AbRepeatController();
    assertFalse(controller.isActive());
    controller.setPointA(1000L);
    assertFalse(controller.isActive());
    assertNull("a single mark never seeks", controller.tick(60_000L));
    controller.setPointB(5000L);
    assertTrue(controller.isActive());
  }

  @Test
  public void loopsBackToAWhenBIsReached() {
    AbRepeatController controller = new AbRepeatController();
    controller.setPointA(1000L);
    controller.setPointB(5000L);
    assertNull(controller.tick(4999L));
    assertEquals(Long.valueOf(1000L), controller.tick(5000L));
    assertEquals(Long.valueOf(1000L), controller.tick(5400L));
  }

  @Test
  public void settingBBeforeASwapsTheMarks() {
    AbRepeatController controller = new AbRepeatController();
    controller.setPointA(9000L);
    controller.setPointB(2000L);
    assertEquals(2000L, controller.pointAMs());
    assertEquals(9000L, controller.pointBMs());
    assertTrue(controller.isActive());
  }

  @Test
  public void settingABeforeBWithoutAStartsTheLoop() {
    AbRepeatController controller = new AbRepeatController();
    controller.setPointB(4000L);
    assertEquals(4000L, controller.pointAMs());
    assertFalse(controller.isActive());
  }

  @Test
  public void movingABeforeBInvalidatesTheLoop() {
    AbRepeatController controller = new AbRepeatController();
    controller.setPointA(1000L);
    controller.setPointB(5000L);
    controller.setPointA(6000L);
    assertFalse("B now precedes A, so the loop must be dropped", controller.isActive());
    assertEquals(-1L, controller.pointBMs());
  }

  @Test
  public void clearResetsBothMarks() {
    AbRepeatController controller = new AbRepeatController();
    controller.setPointA(1000L);
    controller.setPointB(5000L);
    controller.clear();
    assertFalse(controller.isActive());
    assertEquals(-1L, controller.pointAMs());
    assertEquals(-1L, controller.pointBMs());
  }

  @Test
  public void loopProgressIsNormalised() {
    AbRepeatController controller = new AbRepeatController();
    controller.setPointA(1000L);
    controller.setPointB(3000L);
    assertEquals(0f, controller.loopProgress(1000L), 0.0001f);
    assertEquals(0.5f, controller.loopProgress(2000L), 0.0001f);
    assertEquals(1f, controller.loopProgress(3000L), 0.0001f);
    assertEquals(1f, controller.loopProgress(9000L), 0.0001f);
    assertEquals(-1f, new AbRepeatController().loopProgress(10L), 0.0001f);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNegativeMarks() {
    new AbRepeatController().setPointA(-1L);
  }
}
