/*
 * Copyright (C) 2014-$YEAR Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.droidtechlab.filemanager.filesystem.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FolderCoverResolverTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void resolvesCoverBeforeFolderAndPoster() throws IOException {
    File folder = temporaryFolder.newFolder("Movies");
    File poster = new File(folder, "poster.webp");
    File folderCover = new File(folder, "folder.png");
    File cover = new File(folder, "cover.JPG");
    poster.createNewFile();
    folderCover.createNewFile();
    cover.createNewFile();

    assertEquals(cover, FolderCoverResolver.resolve(folder));
  }

  @Test
  public void ignoresUnlistedImageNames() throws IOException {
    File folder = temporaryFolder.newFolder("Movies");
    new File(folder, "random-image.jpg").createNewFile();

    assertNull(FolderCoverResolver.resolve(folder));
  }

  @Test
  public void supportsAllConfiguredExtensions() throws IOException {
    File folder = temporaryFolder.newFolder("Movies");
    File cover = new File(folder, "cover.WebP");
    cover.createNewFile();

    assertEquals(cover, FolderCoverResolver.resolve(folder));
  }
}