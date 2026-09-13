package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SubtitleStyleTest {

  @Test
  public void defaultsAreLegible() {
    SubtitleStyle style = SubtitleStyle.defaults();
    assertEquals(0xFFFFFFFF, style.textColorArgb());
    assertEquals(SubtitleStyle.DEFAULT_TEXT_FRACTION, style.textFraction(), 0.0001f);
    assertEquals(SubtitleStyle.EdgeType.OUTLINE, style.edgeType());
    assertEquals(0L, style.offsetMs());
    assertFalse(style.isBold());
  }

  @Test
  public void sizesAreClampedToTheReadableRange() {
    SubtitleStyle tiny = SubtitleStyle.builder().textFraction(0.001f).build();
    assertEquals(SubtitleStyle.MIN_TEXT_FRACTION, tiny.textFraction(), 0.0001f);

    SubtitleStyle huge = SubtitleStyle.builder().textFraction(1f).build();
    assertEquals(SubtitleStyle.MAX_TEXT_FRACTION, huge.textFraction(), 0.0001f);
  }

  @Test
  public void steppingStaysInsideTheRange() {
    SubtitleStyle style = SubtitleStyle.defaults();
    for (int i = 0; i < 10; i++) {
      style = style.toBuilder().stepSize(1).build();
    }
    assertEquals(SubtitleStyle.MAX_TEXT_FRACTION, style.textFraction(), 0.0001f);

    for (int i = 0; i < 20; i++) {
      style = style.toBuilder().stepSize(-1).build();
    }
    assertEquals(SubtitleStyle.MIN_TEXT_FRACTION, style.textFraction(), 0.0001f);
  }

  @Test
  public void syncOffsetIsBounded() {
    assertEquals(-30_000L, SubtitleStyle.builder().offsetMs(-600_000L).build().offsetMs());
    assertEquals(30_000L, SubtitleStyle.builder().offsetMs(600_000L).build().offsetMs());
    assertEquals(250L, SubtitleStyle.builder().offsetMs(250L).build().offsetMs());
  }

  @Test
  public void alphaAndEdgeAreNormalised() {
    SubtitleStyle style =
        SubtitleStyle.builder().backgroundAlpha(9f).edgeFraction(1f).edgeType(null).build();
    assertEquals(1f, style.backgroundAlpha(), 0.0001f);
    assertEquals(0.02f, style.edgeFraction(), 0.0001f);
    assertEquals(SubtitleStyle.EdgeType.NONE, style.edgeType());
  }

  @Test
  public void missingFontFallsBackToSansSerif() {
    assertEquals("sans-serif", SubtitleStyle.builder().fontFamily(null).build().fontFamily());
    assertEquals("sans-serif", SubtitleStyle.builder().fontFamily("").build().fontFamily());
    assertEquals("monospace", SubtitleStyle.builder().fontFamily("monospace").build().fontFamily());
  }

  @Test
  public void toBuilderRoundTripsEveryField() {
    SubtitleStyle original =
        SubtitleStyle.builder()
            .textFraction(0.07f)
            .textColorArgb(0xFF00FF00)
            .backgroundColorArgb(0xFF112233)
            .backgroundAlpha(0.25f)
            .edgeType(SubtitleStyle.EdgeType.DROP_SHADOW)
            .edgeFraction(0.008f)
            .offsetMs(-500L)
            .fontFamily("serif")
            .bold(true)
            .italic(true)
            .build();
    SubtitleStyle copy = original.toBuilder().build();

    assertEquals(original.textFraction(), copy.textFraction(), 0.0001f);
    assertEquals(original.textColorArgb(), copy.textColorArgb());
    assertEquals(original.backgroundColorArgb(), copy.backgroundColorArgb());
    assertEquals(original.backgroundAlpha(), copy.backgroundAlpha(), 0.0001f);
    assertEquals(original.edgeType(), copy.edgeType());
    assertEquals(original.edgeFraction(), copy.edgeFraction(), 0.0001f);
    assertEquals(original.offsetMs(), copy.offsetMs());
    assertEquals(original.fontFamily(), copy.fontFamily());
    assertTrue(copy.isBold());
    assertTrue(copy.isItalic());
  }

  @Test
  public void errorTaxonomyPrescribesARecoveryForEveryCode() {
    for (PlayerErrorCode code : PlayerErrorCode.values()) {
      assertTrue(code.name(), code.recovery() != null);
      assertTrue(code.name(), code.telemetryName().equals(code.name().toLowerCase()));
    }
    assertEquals(PlayerErrorCode.Recovery.SWITCH_DECODER,
        PlayerErrorCode.DECODER_INIT_FAILED.recovery());
    assertTrue(PlayerErrorCode.DECODER_INIT_FAILED.retryAutomatically());
    assertFalse(PlayerErrorCode.SOURCE_NOT_FOUND.retryAutomatically());
    assertEquals(PlayerErrorCode.Recovery.RESELECT_SOURCE,
        PlayerErrorCode.SOURCE_NOT_FOUND.recovery());
    assertEquals(PlayerErrorCode.Recovery.NONE, PlayerErrorCode.AUDIO_FOCUS_LOST.recovery());
  }
}
