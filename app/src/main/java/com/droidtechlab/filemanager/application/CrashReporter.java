/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 */

package com.droidtechlab.filemanager.application;

import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes uncaught Java crashes to app-private storage before delegating to Android. */
public final class CrashReporter {

  private CrashReporter() {}

  public static void install(AppConfig application) {
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, throwable) -> {
          write(application, thread, throwable);
          if (previous != null) previous.uncaughtException(thread, throwable);
        });
  }

  private static void write(AppConfig application, Thread thread, Throwable throwable) {
    File directory = new File(application.getFilesDir(), "crashes");
    if (!directory.exists() && !directory.mkdirs()) return;

    String timestamp =
        new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
    File report = new File(directory, "crash-" + timestamp + ".txt");
    try (PrintWriter writer = new PrintWriter(new FileWriter(report))) {
      writer.println("package=" + application.getPackageName());
      writer.println("thread=" + thread.getName());
      writer.println("android=" + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")");
      writer.println("manufacturer=" + Build.MANUFACTURER);
      writer.println("model=" + Build.MODEL);
      writer.println();
      throwable.printStackTrace(writer);
    } catch (Exception ignored) {
      // Crash reporting must never replace the original crash.
    }
  }
}