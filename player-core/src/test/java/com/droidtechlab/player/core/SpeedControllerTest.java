package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeedControllerTest {

  @Test
  public void startsAtNormalSpeed() {
    SpeedController controller = new SpeedController();
    assertEquals(1f, controller.current(), 0.0001f);
    assertTrue(controller.isDefault());
    assertEquals("1x", controller.label());
  }

  @Test
  public void snapsToTheNearestPreset() {
    SpeedController controller = new SpeedController();
    assertEquals(1.25f, controller.set(1.24f), 0.0001f);
    assertEquals("1.25x", controller.label());
    assertEquals(1.5f, controller.set(1.5f), 0.0001f);
    assertFalse(controller.isDefault());
  }

  @Test
  public void clampsToTheSupportedRange() {
    SpeedController controller = new SpeedController();
    assertEquals(SpeedController.MIN_SPEED, controller.set(0.01f), 0.0001f);
    assertEquals(SpeedController.MAX_SPEED, controller.set(99f), 0.0001f);
  }

  @Test
  public void honoursValuesThatAreNotCloseToAPreset() {
    SpeedController controller = new SpeedController();
    assertEquals(1.13f, controller.set(1.13f), 0.0001f);
  }

  @Test
  public void cyclesThroughBasePresetsOnlyOnLowTierDevices() {
    SpeedController controller = new SpeedController();
    float[] expected = {1.25f, 1.5f, 2f, 0.5f, 0.75f, 1f};
    for (float expectedSpeed : expected) {
      float speed = controller.next(false);
      assertEquals(expectedSpeed, speed, 0.0001f);
      assertTrue("preset " + speed + " must be inside the base set",
          contains(SpeedController.BASE_PRESETS, speed));
    }
    assertEquals("wraps past the last base preset", 1.25f, controller.next(false), 0.0001f);
  }

  @Test
  public void cyclesBackwardsThroughBasePresets() {
    SpeedController controller = new SpeedController();
    assertEquals(0.75f, controller.previous(false), 0.0001f);
    assertEquals(0.5f, controller.previous(false), 0.0001f);
    assertEquals("wraps to the fastest base preset", 2f, controller.previous(false), 0.0001f);
  }

  @Test
  public void extendedPresetsAreOnlyOfferedWhenAllowed() {
    assertEquals(SpeedController.BASE_PRESETS.length,
        SpeedController.availablePresets(false).length);
    assertEquals(
        SpeedController.BASE_PRESETS.length + SpeedController.EXTENDED_PRESETS.length,
        SpeedController.availablePresets(true).length);
  }

  @Test
  public void labelsAreTrimmed() {
    assertEquals("0.5x", SpeedController.label(0.5f));
    assertEquals("2x", SpeedController.label(2f));
    assertEquals("1.75x", SpeedController.label(1.75f));
  }

  @Test
  public void pitchCorrectionDefaultsToOn() {
    SpeedController controller = new SpeedController();
    assertTrue(controller.isPitchCorrectionEnabled());
    controller.setPitchCorrection(false);
    assertFalse(controller.isPitchCorrectionEnabled());
  }

  private static boolean contains(float[] values, float value) {
    for (float candidate : values) {
      if (Math.abs(candidate - value) < 0.0001f) {
        return true;
      }
    }
    return false;
  }
}
