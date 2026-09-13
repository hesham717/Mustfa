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

/** Video codecs the SDK makes first-class decisions about. */
public enum CodecId {
  H264("video/avc"),
  H265("video/hevc"),
  VP9("video/x-vnd.on2.vp9"),
  AV1("video/av01"),
  VP8("video/x-vnd.on2.vp8"),
  MPEG4("video/mp4v-es"),
  UNKNOWN("");

  private final String mimeType;

  CodecId(String mimeType) {
    this.mimeType = mimeType;
  }

  /** MIME type as reported by {@code MediaFormat} / {@code CMVideoFormatDescription}. */
  public String mimeType() {
    return mimeType;
  }

  /** Resolves a codec from a MIME type; unknown types map to {@link #UNKNOWN}. */
  public static CodecId fromMimeType(String mimeType) {
    if (mimeType == null) {
      return UNKNOWN;
    }
    for (CodecId codec : values()) {
      if (codec.mimeType.equalsIgnoreCase(mimeType)) {
        return codec;
      }
    }
    return UNKNOWN;
  }

  /**
   * Software decoding of these codecs is prohibitively expensive on phones; a software fallback is
   * only offered for the rest.
   */
  public boolean isSoftwareFallbackViable() {
    switch (this) {
      case H264:
      case VP8:
      case MPEG4:
        return true;
      case H265:
      case VP9:
        // Software HEVC/VP9 decode exists (FFmpeg/dav1d) but burns 2-4 cores at 720p+.
        return false;
      case AV1:
      case UNKNOWN:
      default:
        return false;
    }
  }
}
