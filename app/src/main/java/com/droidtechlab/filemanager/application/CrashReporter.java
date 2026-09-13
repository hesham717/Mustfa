/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 */

package com.droidtechlab.filemanager.application;

import android.os.Build;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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
    String timestamp =
        new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
    String reportName = "crash-" + timestamp + ".txt";
    String report = createReport(application, thread, throwable, reportName);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeToDownloads(application.getContentResolver(), reportName, report);
      } else {
        writeToLegacyDownloads(reportName, report);
      }
    } catch (Exception ignored) {
      // Crash reporting must never replace the original crash.
    }
  }

  private static String createReport(
      AppConfig application, Thread thread, Throwable throwable, String reportName) {
    StringWriter output = new StringWriter();
    try (PrintWriter writer = new PrintWriter(output)) {
      writer.println("package=" + application.getPackageName());
      writer.println("thread=" + thread.getName());
      writer.println("android=" + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")");
      writer.println("manufacturer=" + Build.MANUFACTURER);
      writer.println("model=" + Build.MODEL);
      writer.println("location=Download/ESFileManager-Crash/" + reportName);
      writer.println();
      throwable.printStackTrace(writer);
    }
    return output.toString();
  }

  private static void writeToDownloads(ContentResolver resolver, String name, String report)
      throws Exception {
    ContentValues values = new ContentValues();
    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
    values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ESFileManager-Crash");
    values.put(MediaStore.Downloads.IS_PENDING, 1);

    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    if (uri == null) throw new IllegalStateException("Unable to create crash report in Downloads");
    try (OutputStream output = resolver.openOutputStream(uri)) {
      if (output == null) throw new IllegalStateException("Unable to open crash report");
      output.write(report.getBytes("UTF-8"));
      output.flush();
    }
    values.clear();
    values.put(MediaStore.Downloads.IS_PENDING, 0);
    resolver.update(uri, values, null, null);
  }

  @SuppressWarnings("deprecation")
  private static void writeToLegacyDownloads(String name, String report) throws Exception {
    File directory =
        new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ESFileManager-Crash");
    if (!directory.exists() && !directory.mkdirs()) return;
    File reportFile = new File(directory, name);
    try (FileWriter writer = new FileWriter(reportFile)) {
      writer.write(report);
    }
  }
}