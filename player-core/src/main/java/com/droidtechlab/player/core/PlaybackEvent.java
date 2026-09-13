/*
 * Copyright (C) 2014-2026 Arpit Khurana, Vishal Nehra, Emmanuel Messulam, Raymond Lai
 * and Contributors.
 *
 * This file is part of the ES File Manager player core.
 *
 * The player core is free software: you can redistribute it and/or modify
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

package com.droidtechlab.player.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One QoE telemetry event.
 *
 * <p>The event schema is the contract with analytics; it is defined here so Android and iOS emit
 * identical payloads. Attribute keys are listed in {@code docs/player/04-performance-and-metrics.md}.
 */
public final class PlaybackEvent {

  private final String name;
  private final long timestampMs;
  private final Map<String, String> attributes;

  public PlaybackEvent(String name, long timestampMs, Map<String, String> attributes) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("event name is required");
    }
    this.name = name;
    this.timestampMs = timestampMs;
    this.attributes =
        attributes == null
            ? Collections.<String, String>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
  }

  public String name() {
    return name;
  }

  public long timestampMs() {
    return timestampMs;
  }

  public Map<String, String> attributes() {
    return attributes;
  }

  public String attribute(String key) {
    return attributes.get(key);
  }

  /** Minimal JSON serialization with escaping; avoids pulling a JSON dependency into the core. */
  public String toJson() {
    StringBuilder json = new StringBuilder(64);
    json.append("{\"event\":\"").append(escape(name)).append("\",\"ts\":").append(timestampMs);
    if (!attributes.isEmpty()) {
      json.append(",\"attributes\":{");
      boolean first = true;
      for (Map.Entry<String, String> entry : attributes.entrySet()) {
        if (!first) {
          json.append(',');
        }
        first = false;
        json.append('"').append(escape(entry.getKey())).append("\":\"");
        json.append(escape(entry.getValue() == null ? "" : entry.getValue())).append('"');
      }
      json.append('}');
    }
    json.append('}');
    return json.toString();
  }

  private static String escape(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          escaped.append("\\\"");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        default:
          if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
          } else {
            escaped.append(c);
          }
      }
    }
    return escaped.toString();
  }

  @Override
  public String toString() {
    return toJson();
  }
}
