package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;

public class DecoderPolicyTest {

  private static final DeviceCapabilities BASELINE = DeviceCapabilities.baseline();
  private static final PlaybackTuning HIGH = PlaybackTuning.forTier(DeviceTier.HIGH);
  private static final PlaybackTuning LOW = PlaybackTuning.forTier(DeviceTier.LOW);

  @Test
  public void prefersHardwareWhenAvailable() {
    DecoderDecision decision =
        DecoderPolicy.decide(CodecId.H265, 1920, 1080, false, BASELINE, HIGH);
    assertEquals(DecoderDecision.Mode.HARDWARE, decision.mode());
    assertTrue(decision.isPlayable());
    assertFalse(decision.requiresSecureOutput());
    assertEquals("hardware_supported", decision.reason());
  }

  @Test
  public void drmContentNeverFallsBackToSoftware() {
    DeviceCapabilities softwareOnly =
        new DeviceCapabilities(
            EnumSet.noneOf(CodecId.class),
            EnumSet.of(CodecId.H264),
            /* secureDecoderSupported= */ false,
            /* hardwareHdrSupported= */ false);
    DecoderDecision decision =
        DecoderPolicy.decide(CodecId.H264, 1920, 1080, true, softwareOnly, HIGH);
    assertEquals(DecoderDecision.Mode.UNSUPPORTED, decision.mode());
    assertEquals("drm_no_hardware_decoder", decision.reason());
    assertFalse(decision.isPlayable());
  }

  @Test
  public void drmWithoutASecurePathIsRejected() {
    DeviceCapabilities noSecure =
        new DeviceCapabilities(
            EnumSet.of(CodecId.H264),
            EnumSet.of(CodecId.H264),
            /* secureDecoderSupported= */ false,
            /* hardwareHdrSupported= */ false);
    DecoderDecision decision =
        DecoderPolicy.decide(CodecId.H264, 1920, 1080, true, noSecure, HIGH);
    assertEquals(DecoderDecision.Mode.UNSUPPORTED, decision.mode());
    assertEquals("drm_no_secure_path", decision.reason());
  }

  @Test
  public void drmOnASecureDeviceUsesTheProtectedPath() {
    DecoderDecision decision =
        DecoderPolicy.decide(CodecId.H264, 1920, 1080, true, BASELINE, HIGH);
    assertEquals(DecoderDecision.Mode.HARDWARE_SECURE, decision.mode());
    assertTrue(decision.requiresSecureOutput());
  }

  @Test
  public void softwareFallbackIsOnlyForViableCodecs() {
    DeviceCapabilities swHevc =
        new DeviceCapabilities(
            EnumSet.noneOf(CodecId.class),
            EnumSet.of(CodecId.H264, CodecId.H265),
            false,
            false);
    assertEquals(
        "HEVC software decode is too expensive to offer as a fallback",
        DecoderDecision.Mode.UNSUPPORTED,
        DecoderPolicy.decide(CodecId.H265, 1280, 720, false, swHevc, HIGH).mode());
    assertEquals(
        DecoderDecision.Mode.SOFTWARE,
        DecoderPolicy.decide(CodecId.H264, 1280, 720, false, swHevc, HIGH).mode());
  }

  @Test
  public void resolutionAboveTheTierCeilingIsRejectedOnLowTier() {
    DecoderDecision decision =
        DecoderPolicy.decide(CodecId.H264, 3840, 2160, false, BASELINE, LOW);
    assertEquals(DecoderDecision.Mode.SOFTWARE, decision.mode());
    assertEquals("hardware_above_resolution_ceiling", decision.reason());

    DecoderDecision hevc =
        DecoderPolicy.decide(CodecId.H265, 3840, 2160, false, BASELINE, LOW);
    assertEquals(DecoderDecision.Mode.UNSUPPORTED, hevc.mode());
    assertEquals("resolution_above_device_ceiling", hevc.reason());
  }

  @Test
  public void unknownCodecsAreReportedAsUnsupported() {
    DecoderDecision decision =
        DecoderPolicy.decide(CodecId.UNKNOWN, 1920, 1080, false, BASELINE, HIGH);
    assertEquals(DecoderDecision.Mode.UNSUPPORTED, decision.mode());
    assertEquals("codec_unknown", decision.reason());
  }

  @Test
  public void missingCapabilitiesNeverCrash() {
    assertEquals(
        DecoderDecision.Mode.UNSUPPORTED,
        DecoderPolicy.decide(CodecId.H264, 0, 0, false, null, HIGH).mode());
    assertEquals(
        DecoderDecision.Mode.UNSUPPORTED,
        DecoderPolicy.decide(CodecId.H264, 0, 0, false, BASELINE, null).mode());
    assertEquals(
        DecoderDecision.Mode.UNSUPPORTED,
        DecoderPolicy.decide(null, 0, 0, false, BASELINE, HIGH).mode());
  }

  @Test
  public void codecMimeTypesRoundTrip() {
    assertEquals(CodecId.H265, CodecId.fromMimeType("video/hevc"));
    assertEquals(CodecId.AV1, CodecId.fromMimeType("VIDEO/AV01"));
    assertEquals(CodecId.UNKNOWN, CodecId.fromMimeType(null));
    assertEquals(CodecId.UNKNOWN, CodecId.fromMimeType("video/x-matroska-unknown"));
    assertFalse(CodecId.AV1.isSoftwareFallbackViable());
    assertTrue(CodecId.H264.isSoftwareFallbackViable());
  }

  @Test
  public void baselineCapabilitiesDescribeA2018Phone() {
    assertTrue(BASELINE.isHardwareSupported(CodecId.VP9));
    assertTrue(BASELINE.isSecureDecoderSupported());
    assertFalse(BASELINE.isHardwareSupported(CodecId.AV1));
    assertFalse(DeviceCapabilities.none().isHardwareSupported(CodecId.H264));
  }
}
