package com.droidtechlab.player.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class TelemetrySinkTest {

  private static PlaybackEvent event(String name) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("codec", "video/hevc");
    return new PlaybackEvent(name, 1_000L, attributes);
  }

  @Test
  public void batchesEventsInsteadOfSendingThemOneByOne() {
    InMemoryTelemetrySink sink = new InMemoryTelemetrySink(3);
    sink.record(event("playback_started"));
    sink.record(event("first_frame"));
    assertTrue(sink.flushedBatches().isEmpty());
    assertEquals(2, sink.pending().size());

    sink.record(event("rebuffer"));
    assertEquals(1, sink.flushedBatches().size());
    assertEquals(3, sink.flushedBatches().get(0).size());
    assertTrue(sink.pending().isEmpty());
  }

  @Test
  public void flushEmitsAPartialBatch() {
    InMemoryTelemetrySink sink = new InMemoryTelemetrySink(10);
    sink.record(event("playback_started"));
    sink.flush();
    assertEquals(1, sink.flushedBatches().size());
    sink.flush();
    assertEquals("a second flush must not duplicate the batch", 1, sink.flushedBatches().size());
  }

  @Test
  public void countsRecordsAndDrops() {
    InMemoryTelemetrySink sink = new InMemoryTelemetrySink(2);
    for (int i = 0; i < 5; i++) {
      sink.record(event("tick"));
    }
    assertEquals(5, sink.recordedCount());
    assertEquals(2, sink.flushedBatches().size());
    assertEquals(1, sink.pending().size());
    assertEquals(4, sink.allFlushed().size());
  }

  @Test
  public void ignoresNullEvents() {
    InMemoryTelemetrySink sink = new InMemoryTelemetrySink(2);
    sink.record(null);
    assertEquals(0, sink.recordedCount());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsANonPositiveBatchSize() {
    new InMemoryTelemetrySink(0);
  }

  @Test
  public void eventsSerialiseToEscapedJson() {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("path", "C:\\movies\\\"best\".mkv");
    attributes.put("note", "line1\nline2");
    String json = new PlaybackEvent("playback_error", 42L, attributes).toJson();

    assertTrue(json, json.startsWith("{\"event\":\"playback_error\",\"ts\":42"));
    assertTrue(json, json.contains("\\\"best\\\""));
    assertTrue(json, json.contains("\\\\movies"));
    assertTrue(json, json.contains("\\n"));
    assertTrue(json, json.endsWith("}}"));
  }

  @Test
  public void eventsWithoutAttributesStayCompact() {
    assertEquals("{\"event\":\"tick\",\"ts\":1}", new PlaybackEvent("tick", 1L, null).toJson());
  }

  @Test
  public void attributesAreImmutable() {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("a", "1");
    PlaybackEvent event = new PlaybackEvent("tick", 1L, attributes);
    attributes.put("a", "mutated");
    assertEquals("1", event.attribute("a"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void eventsNeedAName() {
    new PlaybackEvent("", 1L, null);
  }
}
