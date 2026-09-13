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

package com.droidtechlab.filemanager.stability;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Regression guard for the Android 10 startup crash: bumping speed-dial to 3.3.0 drags in a
 * material version whose components refuse to inflate under our AppCompat themes, which crashed
 * MainActivity while inflating SpeedDialView. Keep the pins below in sync with app/build.gradle.
 */
public class DependencyPinsTest {

  private static String readBuildGradle() throws Exception {
    File file = new File("build.gradle");
    if (!file.exists()) file = new File("app/build.gradle");
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }

  @Test
  public void speedDialStaysOnKnownGoodVersion() throws Exception {
    assertTrue(readBuildGradle().contains("fabSpeedDialVersion = '3.2.0'"));
  }

  @Test
  public void materialAndAppCompatMatchSpeedDialRequirements() throws Exception {
    String gradle = readBuildGradle();
    assertTrue(gradle.contains("com.google.android.material:material:1.3.0"));
    assertTrue(gradle.contains("androidx.appcompat:appcompat:1.3.0"));
  }
}
