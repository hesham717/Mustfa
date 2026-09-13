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

package com.droidtechlab.filemanager.ui.activities;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.droidtechlab.filemanager.R;
import com.droidtechlab.filemanager.ui.activities.superclasses.ThemedActivity;
import com.droidtechlab.filemanager.ui.fragments.preference_fragments.PreferencesConstants;
import com.droidtechlab.filemanager.ui.icons.Icons;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

/** Full-screen local video player with gesture controls and track selection. */
public class VideoPlayerActivity extends ThemedActivity {

  private static final String EXTRA_PATH = "video_path";
  private static final String EXTRA_QUEUE = "video_queue";
  private static final int PICK_SUBTITLE = 41;
  private static final long CONTROLS_TIMEOUT_MS = 3200L;
  private static final long OVERLAY_TIMEOUT_MS = 900L;
  private static final String POSITION_PREFIX = "video_position_";

  private final Handler handler = new Handler();
  private final ArrayList<File> videoFiles = new ArrayList<>();
  private final String[] speedValues = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
  private final float[] speedNumbers = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

  private PlayerView playerView;
  private SimpleExoPlayer player;
  private DefaultTrackSelector trackSelector;
  private DefaultDataSourceFactory dataSourceFactory;
  private View controls;
  private TextView titleView, positionView, durationView, gestureOverlay;
  private SeekBar seekBar;
  private ImageButton playButton, fullScreenButton;
  private Button speedButton, aspectButton;
  private AudioManager audioManager;
  private GestureDetector gestureDetector;
  private Runnable hideControlsRunnable;
  private Runnable hideOverlayRunnable;
  private Uri selectedSubtitleUri;
  private File currentFile;
  private int currentIndex;
  private long gestureStartPosition;
  private boolean seekingByGesture;
  private boolean controlsLocked;
  private boolean fullscreen;
  private boolean orientationLocked;
  private boolean autoNext = true;
  private boolean userSeeking;
  private float initialBrightness;
  private int aspectMode;

  private final BroadcastReceiver storageReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
              || Intent.ACTION_MEDIA_REMOVED.equals(action)
              || Intent.ACTION_MEDIA_BAD_REMOVAL.equals(action)) {
            savePosition();
            if (player != null) player.stop();
            showGestureMessage(getString(R.string.player_source_unavailable));
            Toast.makeText(
                    VideoPlayerActivity.this,
                    getString(R.string.player_source_unavailable),
                    Toast.LENGTH_LONG)
                .show();
          }
        }
      };

  public static void open(Context context, File file) {
    open(context, file, null);
  }

  public static void open(Context context, File file, @Nullable ArrayList<String> orderedPaths) {
    Intent intent = new Intent(context, VideoPlayerActivity.class);
    intent.putExtra(EXTRA_PATH, file.getAbsolutePath());
    if (orderedPaths != null) intent.putStringArrayListExtra(EXTRA_QUEUE, orderedPaths);
    context.startActivity(intent);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.activity_video_player);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    initialBrightness = getWindow().getAttributes().screenBrightness;
    if (initialBrightness < 0) initialBrightness = 0.5f;

    bindViews();
    currentFile = new File(getIntent().getStringExtra(EXTRA_PATH));
    autoNext = getPrefs().getBoolean(PreferencesConstants.PREFERENCE_VIDEO_AUTO_NEXT, true);
    if (!currentFile.exists() || !currentFile.isFile()) {
      Toast.makeText(this, R.string.player_source_unavailable, Toast.LENGTH_LONG).show();
      finish();
      return;
    }

    buildVideoQueue();
    currentIndex = videoFiles.indexOf(currentFile);
    if (currentIndex < 0) currentIndex = 0;
    dataSourceFactory =
        new DefaultDataSourceFactory(this, Util.getUserAgent(this, getString(R.string.app_name)));
    trackSelector = new DefaultTrackSelector(this);
    player = new SimpleExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
    player.setAudioAttributes(
        new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.CONTENT_TYPE_MOVIE).build(),
        true);
    playerView.setPlayer(player);
    player.addListener(playerListener);

    configureGestures();
    prepareCurrentVideo(true);
    registerStorageReceiver();
  }

  private void bindViews() {
    playerView = findViewById(R.id.player_view);
    controls = findViewById(R.id.player_controls);
    titleView = findViewById(R.id.player_title);
    positionView = findViewById(R.id.player_position);
    durationView = findViewById(R.id.player_duration);
    gestureOverlay = findViewById(R.id.player_gesture_overlay);
    seekBar = findViewById(R.id.player_seek);
    playButton = findViewById(R.id.player_play);
    fullScreenButton = findViewById(R.id.player_fullscreen);
    speedButton = findViewById(R.id.player_speed);
    aspectButton = findViewById(R.id.player_aspect);

    ImageButton closeButton = findViewById(R.id.player_close);
    ImageButton lockButton = findViewById(R.id.player_lock);
    ImageButton moreButton = findViewById(R.id.player_more);
    ImageButton previousButton = findViewById(R.id.player_previous);
    ImageButton nextButton = findViewById(R.id.player_next);
    ImageButton rewindButton = findViewById(R.id.player_rewind);
    ImageButton forwardButton = findViewById(R.id.player_forward);
    Button audioButton = findViewById(R.id.player_audio);
    Button subtitleButton = findViewById(R.id.player_subtitle);
    Button unlockButton = findViewById(R.id.player_unlock);

    closeButton.setOnClickListener(v -> finish());
    lockButton.setOnClickListener(v -> setControlsLocked(true));
    unlockButton.setOnClickListener(v -> setControlsLocked(false));
    moreButton.setOnClickListener(v -> showMoreMenu());
    previousButton.setOnClickListener(v -> playNeighbour(-1));
    nextButton.setOnClickListener(v -> playNeighbour(1));
    rewindButton.setOnClickListener(v -> seekBy(-getSeekStepSeconds() * 1000L));
    forwardButton.setOnClickListener(v -> seekBy(getSeekStepSeconds() * 1000L));
    playButton.setOnClickListener(v -> togglePlayback());
    fullScreenButton.setOnClickListener(v -> setFullscreen(!fullscreen));
    audioButton.setOnClickListener(v -> showAudioTracks());
    subtitleButton.setOnClickListener(v -> showSubtitleMenu());
    speedButton.setOnClickListener(v -> showSpeedMenu());
    aspectButton.setOnClickListener(v -> showAspectMenu());

    seekBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            if (fromUser && player != null && player.getDuration() > 0) {
              positionView.setText(formatTime(progress));
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar bar) {
            userSeeking = true;
            showControls();
          }

          @Override
          public void onStopTrackingTouch(SeekBar bar) {
            userSeeking = false;
            if (player != null && player.getDuration() > 0) {
              player.seekTo((long) bar.getProgress());
            }
            scheduleHideControls();
          }
        });

    hideControlsRunnable = () -> setControlsVisible(false);
    hideOverlayRunnable = () -> gestureOverlay.setVisibility(View.GONE);
  }

  private void configureGestures() {
    gestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              private float startX;
              private float startY;
              private boolean horizontal;
              private boolean decided;

              @Override
              public boolean onDown(MotionEvent event) {
                startX = event.getX();
                startY = event.getY();
                horizontal = false;
                decided = false;
                gestureStartPosition = player == null ? 0 : player.getCurrentPosition();
                return true;
              }

              @Override
              public boolean onSingleTapConfirmed(MotionEvent event) {
                if (!controlsLocked) toggleControls();
                return true;
              }

              @Override
              public boolean onDoubleTap(MotionEvent event) {
                if (controlsLocked) return true;
                if (event.getX() < playerView.getWidth() * 0.34f) {
                  seekBy(-getSeekStepSeconds() * 1000L);
                } else if (event.getX() > playerView.getWidth() * 0.66f) {
                  seekBy(getSeekStepSeconds() * 1000L);
                } else {
                  togglePlayback();
                }
                return true;
              }

              @Override
              public boolean onScroll(
                  MotionEvent first, MotionEvent current, float distanceX, float distanceY) {
                if (controlsLocked) return true;
                float totalX = current.getX() - startX;
                float totalY = current.getY() - startY;
                if (!decided && (Math.abs(totalX) > 18 || Math.abs(totalY) > 18)) {
                  decided = true;
                  horizontal = Math.abs(totalX) >= Math.abs(totalY);
                  if (horizontal) seekingByGesture = true;
                }
                if (!decided) return true;

                if (horizontal && player != null && player.getDuration() > 0) {
                  long offset =
                      (long) (totalX / Math.max(1, playerView.getWidth()) * player.getDuration());
                  long target = Math.max(0, Math.min(player.getDuration(), gestureStartPosition + offset));
                  player.seekTo(target);
                  showGestureMessage((offset >= 0 ? "+" : "") + formatTime(offset) + "  " + formatTime(target));
                } else {
                  boolean left = startX < playerView.getWidth() / 2f;
                  if (left) {
                    changeBrightness(-totalY / playerView.getHeight());
                  } else {
                    changeVolume(-totalY / playerView.getHeight());
                  }
                }
                return true;
              }

              @Override
              public boolean onFling(
                  MotionEvent first, MotionEvent current, float velocityX, float velocityY) {
                seekingByGesture = false;
                return true;
              }
            });
    playerView.setOnTouchListener(
        (view, event) -> {
          if (controlsLocked && event.getAction() == MotionEvent.ACTION_UP) return true;
          gestureDetector.onTouchEvent(event);
          if (event.getAction() == MotionEvent.ACTION_UP) {
            seekingByGesture = false;
            scheduleHideControls();
          }
          return true;
        });
  }

  private void prepareCurrentVideo(boolean offerResume) {
    if (player == null || currentFile == null || !currentFile.exists()) return;
    titleView.setText(currentFile.getName());
    selectedSubtitleUri = findSubtitle(currentFile);
    long position = getSavedPosition(currentFile);
    MediaSource source = createMediaSource(currentFile, selectedSubtitleUri);
    player.setMediaSource(source);
    player.prepare();
    player.setPlayWhenReady(!(offerResume && position > 5000));
    if (offerResume && position > 5000) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.player_continue_title)
          .setMessage(getString(R.string.player_continue_message, formatTime(position)))
          .setPositiveButton(
              R.string.player_continue,
              (dialog, which) -> {
                player.seekTo(position);
                player.setPlayWhenReady(true);
              })
          .setNegativeButton(
              R.string.player_start_over,
              (dialog, which) -> {
                player.seekTo(0);
                player.setPlayWhenReady(true);
              })
          .show();
    } else if (position > 0) {
      player.seekTo(position);
    }
    scheduleHideControls();
  }

  private MediaSource createMediaSource(File file, @Nullable Uri subtitleUri) {
    Uri videoUri = Uri.fromFile(file);
    MediaSource videoSource =
        new ProgressiveMediaSource.Factory(dataSourceFactory, new DefaultExtractorsFactory())
            .createMediaSource(videoUri);
    if (subtitleUri == null) return videoSource;

    Format subtitleFormat =
        Format.createTextSampleFormat(
            null, subtitleMimeType(subtitleUri.toString()), null, Format.NO_VALUE, 0, null, null);
    MediaSource subtitleSource =
        new SingleSampleMediaSource.Factory(dataSourceFactory)
            .createMediaSource(subtitleUri, subtitleFormat, C.TIME_UNSET);
    return new MergingMediaSource(videoSource, subtitleSource);
  }

  private Uri findSubtitle(File video) {
    String base = video.getName();
    int dot = base.lastIndexOf('.');
    if (dot > 0) base = base.substring(0, dot);
    String[] extensions = {".srt", ".ass", ".ssa", ".vtt", ".ttml", ".sub"};
    for (String extension : extensions) {
      File subtitle = new File(video.getParentFile(), base + extension);
      if (subtitle.isFile()) return Uri.fromFile(subtitle);
    }
    return null;
  }

  private String subtitleMimeType(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".ass") || lower.endsWith(".ssa")) return MimeTypes.TEXT_SSA;
    if (lower.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
    if (lower.endsWith(".ttml")) return MimeTypes.APPLICATION_TTML;
    return MimeTypes.APPLICATION_SUBRIP;
  }

  private void buildVideoQueue() {
    ArrayList<String> orderedPaths = getIntent().getStringArrayListExtra(EXTRA_QUEUE);
    if (orderedPaths != null && !orderedPaths.isEmpty()) {
      for (String path : orderedPaths) {
        File file = new File(path);
        if (file.isFile()
            && file.getParentFile() != null
            && currentFile.getParentFile() != null
            && file.getParentFile().equals(currentFile.getParentFile())
            && Icons.getTypeOfFile(file.getPath(), false) == Icons.VIDEO) {
          videoFiles.add(file);
        }
      }
      if (!videoFiles.isEmpty()) return;
    }
    File parent = currentFile.getParentFile();
    if (parent == null) {
      videoFiles.add(currentFile);
      return;
    }
    File[] files = parent.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && Icons.getTypeOfFile(file.getPath(), false) == Icons.VIDEO) {
          videoFiles.add(file);
        }
      }
    }
    Collections.sort(videoFiles, Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
    if (videoFiles.isEmpty()) videoFiles.add(currentFile);
  }

  private void playNeighbour(int direction) {
    int next = currentIndex + direction;
    if (next < 0 || next >= videoFiles.size()) return;
    savePosition();
    currentIndex = next;
    currentFile = videoFiles.get(currentIndex);
    prepareCurrentVideo(false);
  }

  private void togglePlayback() {
    if (player == null) return;
    if (player.isPlaying()) player.setPlayWhenReady(false);
    else player.setPlayWhenReady(true);
    updatePlayButton();
    showControls();
  }

  private void seekBy(long amount) {
    if (player == null || player.getDuration() <= 0) return;
    long target = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + amount));
    player.seekTo(target);
    showGestureMessage((amount > 0 ? "+" : "-") + getSeekStepSeconds() + "s");
  }

  private void changeVolume(float delta) {
    int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    int volume = Math.max(0, Math.min(max, current + Math.round(delta * max)));
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    showGestureMessage("Volume " + Math.round(volume * 100f / max) + "%");
  }

  private void changeBrightness(float delta) {
    WindowManager.LayoutParams attributes = getWindow().getAttributes();
    float brightness = attributes.screenBrightness;
    if (brightness < 0) brightness = initialBrightness;
    attributes.screenBrightness = Math.max(0.05f, Math.min(1f, brightness + delta));
    getWindow().setAttributes(attributes);
    showGestureMessage("Brightness " + Math.round(attributes.screenBrightness * 100) + "%");
  }

  private void showSpeedMenu() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.player_speed)
        .setItems(
            speedValues,
            (dialog, which) -> {
              player.setPlaybackParameters(new PlaybackParameters(speedNumbers[which]));
              speedButton.setText(speedValues[which]);
            })
        .show();
  }

  private void showAspectMenu() {
    String[] labels = {"Fit", "Fill", "Crop", "16:9", "Original"};
    new AlertDialog.Builder(this)
        .setTitle(R.string.player_aspect)
        .setItems(
            labels,
            (dialog, which) -> {
              aspectMode = which;
              int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
              if (which == 1) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
              if (which == 2) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
              playerView.setResizeMode(resizeMode);
              aspectButton.setText(labels[which]);
            })
        .show();
  }

  private void showAudioTracks() {
    MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
    if (info == null) return;
    ArrayList<String> labels = new ArrayList<>();
    ArrayList<String> languages = new ArrayList<>();
    for (int renderer = 0; renderer < info.getRendererCount(); renderer++) {
      if (info.getRendererType(renderer) != C.TRACK_TYPE_AUDIO) continue;
      TrackGroupArray groups = info.getTrackGroups(renderer);
      for (int group = 0; group < groups.length; group++) {
        for (int track = 0; track < groups.get(group).length; track++) {
          Format format = groups.get(group).getFormat(track);
          String language = format.language == null ? "unknown" : format.language;
          String label = format.label == null ? language : format.label + " (" + language + ")";
          labels.add(label);
          languages.add(format.language);
        }
      }
    }
    if (labels.isEmpty()) {
      Toast.makeText(this, R.string.player_no_audio_tracks, Toast.LENGTH_SHORT).show();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.player_audio_track)
        .setItems(
            labels.toArray(new String[0]),
            (dialog, which) ->
                trackSelector.setParameters(
                    trackSelector.buildUponParameters().setPreferredAudioLanguage(languages.get(which))))
        .show();
  }

  private void showSubtitleMenu() {
    String[] items = {getString(R.string.player_subtitle_off), getString(R.string.player_choose_subtitle)};
    new AlertDialog.Builder(this)
        .setTitle(R.string.player_subtitle_track)
        .setItems(
            items,
            (dialog, which) -> {
              if (which == 0) {
                selectedSubtitleUri = null;
                prepareAtCurrentPosition();
              } else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("text/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, PICK_SUBTITLE);
              }
            })
        .setNeutralButton(R.string.player_subtitle_size, (dialog, which) -> showSubtitleSizeMenu())
        .show();
  }

  private void showSubtitleSizeMenu() {
    String[] sizes = {"Small", "Medium", "Large"};
    new AlertDialog.Builder(this)
        .setTitle(R.string.player_subtitle_size)
        .setItems(
            sizes,
            (dialog, which) -> {
              if (playerView.getSubtitleView() != null) {
                playerView.getSubtitleView().setFractionalTextSize(0.045f + which * 0.018f);
              }
            })
        .show();
  }

  private void showMoreMenu() {
    String[] items = {
      getString(R.string.player_video_info),
      getString(R.string.player_auto_next),
      orientationLocked ? "Unlock orientation" : "Lock orientation"
    };
    new AlertDialog.Builder(this)
        .setItems(
            items,
            (dialog, which) -> {
              if (which == 0) showVideoInfo();
              else if (which == 1) autoNext = !autoNext;
              else toggleOrientationLock();
            })
        .show();
  }

  private void showVideoInfo() {
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(currentFile.getAbsolutePath());
      String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
      String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
      String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
      String fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
      String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      String mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
      String text =
          currentFile.getName()
              + "\n"
              + (width == null ? "?" : width)
              + " x "
              + (height == null ? "?" : height)
              + "\nFPS: "
              + (fps == null ? "?" : fps)
              + "\nBitrate: "
              + (bitrate == null ? "?" : bitrate)
              + "\nCodec: "
              + (mimeType == null ? "?" : mimeType)
              + "\nDuration: "
              + (duration == null ? "?" : formatTime(Long.parseLong(duration)))
              + "\nSize: "
              + currentFile.length()
              + " bytes\n"
              + currentFile.getAbsolutePath();
      new AlertDialog.Builder(this).setTitle(R.string.player_video_info).setMessage(text).show();
    } finally {
      retriever.release();
    }
  }

  private void prepareAtCurrentPosition() {
    long position = player == null ? 0 : player.getCurrentPosition();
    boolean playing = player != null && player.getPlayWhenReady();
    prepareCurrentVideo(false);
    if (player != null) {
      player.seekTo(position);
      player.setPlayWhenReady(playing);
    }
  }

  private void showGestureMessage(String message) {
    gestureOverlay.setText(message);
    gestureOverlay.setVisibility(View.VISIBLE);
    handler.removeCallbacks(hideOverlayRunnable);
    handler.postDelayed(hideOverlayRunnable, OVERLAY_TIMEOUT_MS);
  }

  private void toggleControls() {
    if (controls.getVisibility() == View.VISIBLE) setControlsVisible(false);
    else showControls();
  }

  private void showControls() {
    if (controlsLocked) return;
    setControlsVisible(true);
    scheduleHideControls();
  }

  private void setControlsVisible(boolean visible) {
    controls.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (visible) updateProgress();
  }

  private void scheduleHideControls() {
    handler.removeCallbacks(hideControlsRunnable);
    if (!controlsLocked) handler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS);
  }

  private void setControlsLocked(boolean locked) {
    controlsLocked = locked;
    controls.setVisibility(locked ? View.GONE : View.VISIBLE);
    findViewById(R.id.player_unlock).setVisibility(locked ? View.VISIBLE : View.GONE);
    if (!locked) scheduleHideControls();
    else showGestureMessage(getString(R.string.player_locked));
  }

  private void setFullscreen(boolean value) {
    fullscreen = value;
    int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
    if (value) flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    getWindow().getDecorView().setSystemUiVisibility(value ? flags : View.SYSTEM_UI_FLAG_VISIBLE);
  }

  private void toggleOrientationLock() {
    orientationLocked = !orientationLocked;
    setRequestedOrientation(
        orientationLocked
            ? getResources().getConfiguration().orientation == 2
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
  }

  private void updateProgress() {
    if (player == null || userSeeking) return;
    long duration = Math.max(0, player.getDuration());
    long position = Math.max(0, player.getCurrentPosition());
    seekBar.setMax((int) Math.min(Integer.MAX_VALUE, duration));
    seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, position));
    positionView.setText(formatTime(position));
    durationView.setText(formatTime(duration));
    handler.postDelayed(this::updateProgress, 500);
  }

  private void updatePlayButton() {
    playButton.setImageResource(
        player != null && player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
  }

  private int getSeekStepSeconds() {
    try {
      int value =
          Integer.parseInt(
              getPrefs().getString(PreferencesConstants.PREFERENCE_VIDEO_SEEK_SECONDS, "10"));
      return value == 5 || value == 10 || value == 15 || value == 30 ? value : 10;
    } catch (NumberFormatException exception) {
      return 10;
    }
  }

  private String formatTime(long millis) {
    long seconds = Math.abs(millis) / 1000;
    return String.format(
        Locale.getDefault(),
        seconds >= 3600 ? "%s:%02d:%02d" : "%02d:%02d",
        seconds >= 3600 ? seconds / 3600 : seconds / 60,
        seconds >= 3600 ? (seconds / 60) % 60 : seconds % 60,
        seconds >= 3600 ? seconds % 60 : 0);
  }

  private long getSavedPosition(File file) {
    return getPreferences(MODE_PRIVATE).getLong(POSITION_PREFIX + file.getAbsolutePath(), 0);
  }

  private void savePosition() {
    if (player == null || currentFile == null) return;
    getPreferences(MODE_PRIVATE)
        .edit()
        .putLong(POSITION_PREFIX + currentFile.getAbsolutePath(), player.getCurrentPosition())
        .apply();
  }

  private void registerStorageReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
    filter.addAction(Intent.ACTION_MEDIA_EJECT);
    filter.addAction(Intent.ACTION_MEDIA_REMOVED);
    filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
    filter.addDataScheme("file");
    registerReceiver(storageReceiver, filter);
  }

    private final Player.Listener playerListener =
      new Player.Listener() {
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
          updatePlayButton();
          updateProgress();
          if (playbackState == Player.STATE_ENDED && autoNext) playNeighbour(1);
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
          savePosition();
          showGestureMessage(getString(R.string.player_source_unavailable));
        }
      };

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PICK_SUBTITLE && resultCode == RESULT_OK && data != null && data.getData() != null) {
      selectedSubtitleUri = data.getData();
      try {
        getContentResolver().takePersistableUriPermission(
            selectedSubtitleUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (SecurityException ignored) {
        // Some document providers grant a temporary read permission only.
      }
      prepareAtCurrentPosition();
    }
  }

  @Override
  protected void onPause() {
    savePosition();
    if (player != null) player.setPlayWhenReady(false);
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (currentFile != null && !currentFile.exists()) {
      showGestureMessage(getString(R.string.player_source_unavailable));
    } else if (currentFile != null
        && player != null
        && player.getPlaybackState() == Player.STATE_IDLE) {
      prepareCurrentVideo(false);
    }
  }

  @Override
  protected void onDestroy() {
    savePosition();
    handler.removeCallbacksAndMessages(null);
    try {
      unregisterReceiver(storageReceiver);
    } catch (IllegalArgumentException ignored) {
      // Receiver was not registered when setup failed early.
    }
    if (player != null) {
      player.removeListener(playerListener);
      player.release();
    }
    super.onDestroy();
  }
}