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
import com.droidtechlab.player.core.AbRepeatController;
import com.droidtechlab.player.core.DeviceTier;
import com.droidtechlab.player.core.FrameStepPlanner;
import com.droidtechlab.player.core.GestureEngine;
import com.droidtechlab.player.core.GestureResult;
import com.droidtechlab.player.core.GestureType;
import com.droidtechlab.player.core.InMemoryTelemetrySink;
import com.droidtechlab.player.core.MediaQueue;
import com.droidtechlab.player.core.PlaybackEvent;
import com.droidtechlab.player.core.PlaybackMetrics;
import com.droidtechlab.player.core.PlaybackState;
import com.droidtechlab.player.core.PlaybackStateMachine;
import com.droidtechlab.player.core.PlaybackTuning;
import com.droidtechlab.player.core.PlayerErrorCode;
import com.droidtechlab.player.core.RepeatMode;
import com.droidtechlab.player.core.ResumePolicy;
import com.droidtechlab.player.core.SpeedController;
import com.droidtechlab.player.core.SubtitleStyle;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.Rational;
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
  private static final String TAG = "VideoPlayer";

  private final Handler handler = new Handler();
  private final ArrayList<File> videoFiles = new ArrayList<>();

  /** Platform neutral playback logic; see the {@code player-core} module and docs/player/. */
  private final PlaybackStateMachine stateMachine = new PlaybackStateMachine();
  private final AbRepeatController abRepeat = new AbRepeatController();
  private final SpeedController speedController = new SpeedController();
  private final PlaybackMetrics metrics = new PlaybackMetrics();
  private final InMemoryTelemetrySink telemetry = new InMemoryTelemetrySink();

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
  private GestureEngine gestureEngine;
  private Runnable hideControlsRunnable;
  private Runnable hideOverlayRunnable;
  private Uri selectedSubtitleUri;
  private File currentFile;
  private MediaQueue queue;
  private int currentIndex;
  private boolean seekingByGesture;
  private boolean controlsLocked;
  private boolean fullscreen;
  private boolean orientationLocked;
  private boolean autoNext = true;
  private boolean userSeeking;
  private float initialBrightness;
  private int aspectMode;

  /** Derived from the device class: buffers, cache, resolution ceiling, decoder preference. */
  private PlaybackTuning tuning;
  private SubtitleStyle subtitleStyle = SubtitleStyle.defaults();
  private FrameStepPlanner frameStepPlanner;

  private long pendingSeekAtMs = -1L;
  private long lastReportedPositionMs;
  private boolean resumeApplied;
  /** Single instance so the polling loop can be cancelled instead of stacking up. */
  private final Runnable progressRunnable = this::updateProgress;

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
  public void onCreate(Bundle savedInstanceState) {
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
    queue = MediaQueue.of(videoPaths(), currentIndex);
    tuning = PlaybackTuning.forTier(currentDeviceTier());

    dataSourceFactory =
        new DefaultDataSourceFactory(this, Util.getUserAgent(this, getString(R.string.app_name)));
    trackSelector = new DefaultTrackSelector(this);
    // Honour the resolution ceiling for this device class: picking a 4K rendition on a 720p-class
    // device only buys dropped frames and battery drain.
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setMaxVideoSize(tuning.maxVideoWidth(), tuning.maxVideoHeight()));
    DefaultLoadControl loadControl =
        new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                tuning.minBufferMs(),
                tuning.maxBufferMs(),
                tuning.bufferForPlaybackMs(),
                tuning.bufferForPlaybackAfterRebufferMs())
            .build();
    DefaultRenderersFactory renderersFactory =
        new DefaultRenderersFactory(this).setExtensionRendererMode(tuning.extensionRendererMode());

    // The renderers factory is a constructor argument of SimpleExoPlayer.Builder, not a setter.
    player =
        new SimpleExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build();
    player.setAudioAttributes(
        new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.CONTENT_TYPE_MOVIE).build(),
        true);
    player.setWakeMode(C.WAKE_MODE_LOCAL);
    // Headset unplug must pause instead of blasting audio from the speaker.
    player.setHandleAudioBecomingNoisy(true);
    playerView.setPlayer(player);
    player.addListener(playerListener);

    configureGestures();
    prepareCurrentVideo(true);
    registerStorageReceiver();
    recordEvent(
        "player_open",
        "tier",
        tuning.tier().name(),
        "max_video",
        tuning.maxVideoWidth() + "x" + tuning.maxVideoHeight());
  }

  /** Device class used to pick the tuning profile. */
  private DeviceTier currentDeviceTier() {
    ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    long totalRamMb = 0L;
    if (activityManager != null) {
      ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
      activityManager.getMemoryInfo(memoryInfo);
      totalRamMb = memoryInfo.totalMem / (1024L * 1024L);
    }
    return DeviceTier.classify(
        Runtime.getRuntime().availableProcessors(), totalRamMb, Build.VERSION.SDK_INT);
  }

  /** Media ids for the queue: the same list the list UI built, in playback order. */
  private java.util.List<String> videoPaths() {
    java.util.List<String> paths = new ArrayList<>(videoFiles.size());
    for (File file : videoFiles) {
      paths.add(file.getAbsolutePath());
    }
    return paths;
  }

  /** Appends a QoE event with up to two key/value pairs. */
  private void recordEvent(String name, String key1, String value1, String key2, String value2) {
    java.util.Map<String, String> attributes = new java.util.LinkedHashMap<>();
    if (key1 != null) attributes.put(key1, value1);
    if (key2 != null) attributes.put(key2, value2);
    attributes.put("media", currentFile == null ? "unknown" : currentFile.getName());
    telemetry.record(new PlaybackEvent(name, SystemClock.elapsedRealtime(), attributes));
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
    gestureEngine = newGestureEngine();
    // The viewport is only known after layout, and it changes on rotation and in PiP.
    playerView.addOnLayoutChangeListener(
        (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            gestureEngine = newGestureEngine());

    gestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent event) {
                applyGesture(
                    gestureEngine.onDown(event.getX(), event.getY(), currentPositionMs()));
                return true;
              }

              @Override
              public boolean onSingleTapConfirmed(MotionEvent event) {
                applyGesture(gestureEngine.onSingleTap());
                return true;
              }

              @Override
              public boolean onDoubleTap(MotionEvent event) {
                applyGesture(gestureEngine.onDoubleTap(event.getX(), event.getY()));
                return true;
              }

              @Override
              public boolean onScroll(
                  MotionEvent first, MotionEvent current, float distanceX, float distanceY) {
                applyGesture(gestureEngine.onMove(current.getX(), current.getY()));
                return true;
              }

              @Override
              public boolean onFling(
                  MotionEvent first, MotionEvent current, float velocityX, float velocityY) {
                gestureEngine.onUp();
                seekingByGesture = false;
                return true;
              }
            });
    playerView.setOnTouchListener(
        (view, event) -> {
          int action = event.getActionMasked();
          if (controlsLocked && action == MotionEvent.ACTION_UP) {
            gestureEngine.onUp();
            seekingByGesture = false;
            return true;
          }
          gestureDetector.onTouchEvent(event);
          if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            gestureEngine.onUp();
            seekingByGesture = false;
            scheduleHideControls();
          }
          return true;
        });
  }

  /** Rebuilds the gesture engine for the current viewport, keeping lock state and duration. */
  private GestureEngine newGestureEngine() {
    int width = playerView == null ? 0 : playerView.getWidth();
    int height = playerView == null ? 0 : playerView.getHeight();
    if (width <= 0 || height <= 0) {
      android.graphics.Point size = new android.graphics.Point();
      getWindowManager().getDefaultDisplay().getSize(size);
      width = size.x;
      height = size.y;
    }
    GestureEngine engine = new GestureEngine(width, height);
    engine.setDurationMs(player == null ? 0L : Math.max(0L, player.getDuration()));
    engine.setLocked(controlsLocked);
    return engine;
  }

  /** Applies an intent produced by {@link GestureEngine}. */
  private void applyGesture(GestureResult result) {
    if (result == null || result.type() == GestureType.NONE) return;
    switch (result.type()) {
      case TOGGLE_CONTROLS:
        toggleControls();
        break;
      case PLAY_PAUSE:
        togglePlayback();
        break;
      case SEEK_BACKWARD:
      case SEEK_FORWARD:
        seekBy(result.seekDeltaMs());
        break;
      case SEEK_PREVIEW:
        seekingByGesture = true;
        if (player != null) {
          pendingSeekAtMs = SystemClock.elapsedRealtime();
          player.seekTo(result.seekTargetMs());
        }
        showGestureMessage(
            formatDelta(result.seekDeltaMs()) + "  " + formatTime(result.seekTargetMs()));
        break;
      case BRIGHTNESS:
        changeBrightness(result.delta());
        break;
      case VOLUME:
        changeVolume(result.delta());
        break;
      default:
        break;
    }
  }

  private long currentPositionMs() {
    return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
  }

  private void prepareCurrentVideo(boolean offerResume) {
    if (player == null || currentFile == null || !currentFile.exists()) return;
    titleView.setText(currentFile.getName());
    selectedSubtitleUri = findSubtitle(currentFile);
    abRepeat.clear();
    resumeApplied = false;
    pendingSeekAtMs = -1L;
    frameStepPlanner = null;
    metrics.onPrepareStarted(SystemClock.elapsedRealtime());
    transitionToState(PlaybackState.PREPARING);

    long savedPosition = getSavedPosition(currentFile);
    // Duration is not known before the media is parsed, so the tail rules are applied later,
    // when the first READY state arrives (see onMediaReady).
    ResumePolicy.Decision decision = ResumePolicy.decide(savedPosition, 0L);
    MediaSource source = createMediaSource(currentFile, selectedSubtitleUri);
    player.setMediaSource(source);
    player.prepare();
    player.setPlayWhenReady(!(offerResume && decision.isResume()));
    if (offerResume && decision.isResume()) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.player_continue_title)
          .setMessage(
              getString(R.string.player_continue_message, formatTime(decision.positionMs())))
          .setPositiveButton(
              R.string.player_continue,
              (dialog, which) -> {
                player.seekTo(decision.positionMs());
                player.setPlayWhenReady(true);
              })
          .setNegativeButton(
              R.string.player_start_over,
              (dialog, which) -> {
                player.seekTo(0);
                player.setPlayWhenReady(true);
              })
          .show();
    } else if (decision.isResume()) {
      player.seekTo(decision.positionMs());
    }
    scheduleHideControls();
  }

  /**
   * Moves the shared state machine, recovering through IDLE when the engine reports a transition the
   * machine does not model. Losing playback is worse than an extra transition, but it is reported so
   * the divergence shows up in telemetry.
   */
  private void transitionToState(PlaybackState target) {
    try {
      stateMachine.transitionTo(target);
    } catch (IllegalStateException rejected) {
      recordEvent(
          "state_transition_rejected",
          "from",
          stateMachine.current().name(),
          "to",
          target.name());
      try {
        stateMachine.transitionTo(PlaybackState.IDLE);
        stateMachine.transitionTo(target);
      } catch (IllegalStateException ignored) {
        // The engine keeps playing; the state machine is only a mirror of it.
      }
    }
  }

  private MediaSource createMediaSource(File file, @Nullable Uri subtitleUri) {
    return createMediaSourceForUri(Uri.fromFile(file), subtitleUri);
  }

  /**
   * Selects the demuxer from the container: HLS for {@code .m3u8}, DASH for {@code .mpd}, progressive
   * otherwise. Widevine is configured through the {@link MediaItem}, which is the supported route in
   * ExoPlayer 2.18 and keeps the secure output path intact. See docs/player/03-media-and-drm.md.
   */
  private MediaSource createMediaSourceForUri(Uri videoUri, @Nullable Uri subtitleUri) {
    MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(videoUri);
    String licenseUrl = videoUri.getQueryParameter("license_url");
    if (licenseUrl != null && !licenseUrl.isEmpty()) {
      itemBuilder.setDrmConfiguration(
          new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(licenseUrl).build());
    }
    MediaItem mediaItem = itemBuilder.build();

    String path = videoUri.getPath() == null ? "" : videoUri.getPath().toLowerCase(Locale.ROOT);
    MediaSource videoSource;
    if (path.endsWith(".m3u8")) {
      videoSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
    } else if (path.endsWith(".mpd")) {
      videoSource = new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
    } else {
      videoSource =
          new ProgressiveMediaSource.Factory(dataSourceFactory, new DefaultExtractorsFactory())
              .createMediaSource(mediaItem);
    }
    if (subtitleUri == null) return videoSource;

    MediaItem.SubtitleConfiguration subtitleConfiguration =
      new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
        .setMimeType(subtitleMimeType(subtitleUri.toString()))
        .build();
    MediaSource subtitleSource =
        new SingleSampleMediaSource.Factory(dataSourceFactory)
        .createMediaSource(subtitleConfiguration, C.TIME_UNSET);
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
    if (queue == null) return;
    String next = direction >= 0 ? queue.next() : queue.previous();
    if (next == null) {
      showGestureMessage(getString(R.string.player_queue_end));
      return;
    }
    savePosition();
    currentIndex = queue.currentIndex();
    currentFile = videoFiles.get(currentIndex);
    recordEvent("queue_advance", "direction", direction >= 0 ? "next" : "previous", "size",
        String.valueOf(queue.size()));
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
    long from = player.getCurrentPosition();
    long target = Math.max(0, Math.min(player.getDuration(), from + amount));
    pendingSeekAtMs = SystemClock.elapsedRealtime();
    player.seekTo(target);
    showGestureMessage(formatDelta(target - from));
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
    float[] presets = SpeedController.availablePresets(tuning != null && tuning.allowExtendedSpeeds());
    String[] labels = new String[presets.length];
    for (int i = 0; i < presets.length; i++) {
      labels[i] = SpeedController.label(presets[i]);
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.player_speed)
        .setItems(
            labels,
            (dialog, which) -> {
              speedController.set(presets[which]);
              applyPlaybackSpeed();
            })
        .show();
  }

  private void applyPlaybackSpeed() {
    if (player == null) return;
    player.setPlaybackParameters(
        new PlaybackParameters(
            speedController.current(), speedController.isPitchCorrectionEnabled() ? 1f : 0f));
    speedButton.setText(speedController.label());
    recordEvent("speed_changed", "speed", speedController.label(), "pitch_correction",
        String.valueOf(speedController.isPitchCorrectionEnabled()));
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
    String[] sizes = {
      getString(R.string.player_subtitle_small),
      getString(R.string.player_subtitle_medium),
      getString(R.string.player_subtitle_large),
      getString(R.string.player_subtitle_huge)
    };
    new AlertDialog.Builder(this)
        .setTitle(R.string.player_subtitle_size)
        .setItems(
            sizes,
            (dialog, which) -> {
              subtitleStyle = SubtitleStyle.builder().stepSize(which).build();
              applySubtitleStyle();
            })
        .show();
  }

  /** Maps the shared {@link SubtitleStyle} model onto the ExoPlayer subtitle view. */
  private void applySubtitleStyle() {
    if (playerView == null || playerView.getSubtitleView() == null) return;
    playerView.getSubtitleView().setFractionalTextSize(subtitleStyle.textFraction());
    playerView
        .getSubtitleView()
        .setStyle(
            new CaptionStyleCompat(
                subtitleStyle.textColorArgb(),
                (int) (subtitleStyle.backgroundAlpha() * 255f),
                subtitleStyle.backgroundColorArgb(),
                captionEdgeType(),
                Color.TRANSPARENT,
                null));
    recordEvent(
        "subtitle_style",
        "size",
        String.valueOf(subtitleStyle.textFraction()),
        "edge",
        subtitleStyle.edgeType().name());
  }

  private int captionEdgeType() {
    switch (subtitleStyle.edgeType()) {
      case OUTLINE:
        return CaptionStyleCompat.EDGE_TYPE_OUTLINE;
      case DROP_SHADOW:
        return CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW;
      case NONE:
      default:
        return CaptionStyleCompat.EDGE_TYPE_NONE;
    }
  }

  private void showMoreMenu() {
    String[] items = {
      getString(R.string.player_video_info),
      getString(R.string.player_playback_stats),
      abRepeat.isActive()
          ? getString(R.string.player_ab_repeat_off)
          : getString(R.string.player_ab_repeat),
      getString(R.string.player_step_backward),
      getString(R.string.player_step_forward),
      getString(R.string.player_repeat_mode)
          + ": "
          + (queue == null ? RepeatMode.OFF.name() : queue.repeatMode().name()),
      getString(R.string.player_pip),
      getString(R.string.player_auto_next),
      orientationLocked
          ? getString(R.string.player_orientation_unlock)
          : getString(R.string.player_orientation_lock)
    };
    new AlertDialog.Builder(this)
        .setItems(
            items,
            (dialog, which) -> {
              switch (which) {
                case 0:
                  showVideoInfo();
                  break;
                case 1:
                  showPlaybackStats();
                  break;
                case 2:
                  toggleAbRepeat();
                  break;
                case 3:
                  stepFrame(-1);
                  break;
                case 4:
                  stepFrame(1);
                  break;
                case 5:
                  cycleRepeatMode();
                  break;
                case 6:
                  enterPip();
                  break;
                case 7:
                  autoNext = !autoNext;
                  break;
                default:
                  toggleOrientationLock();
                  break;
              }
            })
        .show();
  }

  /** A-B loop: first press marks A, second marks B, third clears. */
  private void toggleAbRepeat() {
    long position = currentPositionMs();
    if (abRepeat.isActive()) {
      abRepeat.clear();
      showGestureMessage(getString(R.string.player_ab_repeat_off));
    } else if (abRepeat.pointAMs() < 0) {
      abRepeat.setPointA(position);
      showGestureMessage(getString(R.string.player_ab_repeat_set_a));
    } else {
      abRepeat.setPointB(position);
      showGestureMessage(getString(R.string.player_ab_repeat_active));
    }
    recordEvent(
        "ab_repeat",
        "state",
        abRepeat.isActive() ? "active" : "marking",
        "position",
        String.valueOf(position));
  }

  /**
   * Reads the container frame rate with the platform retriever.
   *
   * <p>Deliberately not taken from an ExoPlayer callback: the frame rate is a property of the file,
   * and {@link MediaMetadataRetriever} is a stable framework API across every supported release.
   */
  private FrameStepPlanner probeFrameRate() {
    if (currentFile == null) return null;
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(currentFile.getAbsolutePath());
      String fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
      if (fps == null) return null;
      return new FrameStepPlanner(Double.parseDouble(fps));
    } catch (RuntimeException | NumberFormatException unreadable) {
      return null;
    } finally {
      retriever.release();
    }
  }

  /**
   * Single frame stepping.
   *
   * <p>Both directions are seeks onto frame boundaries computed by {@link FrameStepPlanner}. A seek
   * is the honest implementation on a phone: a true decoder level step needs a flush plus a
   * reference chain replay, which costs more than the frame it produces.
   */
  private void stepFrame(int direction) {
    if (player == null) return;
    if (player.isPlaying()) player.setPlayWhenReady(false);
    if (frameStepPlanner == null) frameStepPlanner = probeFrameRate();
    if (frameStepPlanner == null || !frameStepPlanner.isSupported()) {
      Toast.makeText(this, R.string.player_step_unavailable, Toast.LENGTH_SHORT).show();
      return;
    }
    long positionUs = currentPositionMs() * 1000L;
    long durationUs = player.getDuration() > 0 ? player.getDuration() * 1000L : -1L;
    long targetUs =
        direction > 0
            ? frameStepPlanner.stepForward(positionUs, durationUs)
            : frameStepPlanner.stepBackward(positionUs);
    pendingSeekAtMs = SystemClock.elapsedRealtime();
    player.seekTo(targetUs / 1000L);
    recordEvent(
        "frame_step",
        "direction",
        direction > 0 ? "forward" : "backward",
        "fps",
        String.valueOf(frameStepPlanner.framesPerSecond()));
  }

  private void cycleRepeatMode() {
    if (queue == null) return;
    queue.setRepeatMode(queue.repeatMode().next());
    showGestureMessage(getString(R.string.player_repeat_mode) + ": " + queue.repeatMode().name());
    recordEvent("repeat_mode", "mode", queue.repeatMode().name(), null, null);
  }

  /** Picture in picture, guarded because it needs API 26 and a device that declares support. */
  private void enterPip() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      Toast.makeText(this, R.string.player_pip_unavailable, Toast.LENGTH_SHORT).show();
      return;
    }
    try {
      int width = 16;
      int height = 9;
      if (player != null && player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
        width = player.getVideoSize().width;
        height = player.getVideoSize().height;
      }
      PictureInPictureParams params =
          new PictureInPictureParams.Builder().setAspectRatio(new Rational(width, height)).build();
      enterPictureInPictureMode(params);
      recordEvent("pip_enter", "aspect", width + ":" + height, null, null);
    } catch (IllegalStateException unavailable) {
      Toast.makeText(this, R.string.player_pip_unavailable, Toast.LENGTH_SHORT).show();
    }
  }

  private void showPlaybackStats() {
    PlaybackMetrics.Snapshot snapshot = metrics.snapshot();
    String text =
        "Device tier: "
            + (tuning == null ? "?" : tuning.tier().name())
            + "\nBuffer: "
            + (tuning == null ? "?" : tuning.minBufferMs() + "-" + tuning.maxBufferMs() + " ms")
            + "\nTime to first frame: "
            + snapshot.timeToFirstFrameMs()
            + " ms\nRebuffers: "
            + snapshot.rebufferCount()
            + " ("
            + snapshot.rebufferDurationMs()
            + " ms, "
            + Math.round(snapshot.rebufferRatio() * 100f)
            + "%)\nDropped frames: "
            + snapshot.droppedFrames()
            + " / "
            + (snapshot.renderedFrames() + snapshot.droppedFrames())
            + "\nSeeks: "
            + snapshot.seekCount()
            + " (avg "
            + snapshot.seekLatencyMsAverage()
            + " ms)\nAverage bit rate: "
            + snapshot.averageBitrateKbps()
            + " kbps\nErrors: "
            + snapshot.errorCount()
            + (snapshot.lastErrorCode() == null ? "" : " (last: " + snapshot.lastErrorCode() + ")");
    new AlertDialog.Builder(this).setTitle(R.string.player_playback_stats).setMessage(text).show();
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
    if (gestureEngine != null) gestureEngine.setLocked(locked);
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

    metrics.onPosition(position);
    if (pendingSeekAtMs >= 0 && position != lastReportedPositionMs) {
      metrics.onSeek(SystemClock.elapsedRealtime() - pendingSeekAtMs);
      pendingSeekAtMs = -1L;
    }
    lastReportedPositionMs = position;

    Long loopTarget = abRepeat.tick(position);
    if (loopTarget != null) player.seekTo(loopTarget);

    // Cancel before rescheduling: updateProgress is also called from player callbacks, and without
    // this every callback would start a second polling loop.
    handler.removeCallbacks(progressRunnable);
    handler.postDelayed(progressRunnable, 500);
  }

  private String formatDelta(long millis) {
    return (millis < 0 ? "-" : "+") + formatTime(Math.abs(millis));
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
    long position = player.getCurrentPosition();
    if (!ResumePolicy.shouldPersist(position)) {
      // Do not write noise for a video the user barely started.
      return;
    }
    getPreferences(MODE_PRIVATE)
        .edit()
        .putLong(POSITION_PREFIX + currentFile.getAbsolutePath(), position)
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
        public void onPlaybackStateChanged(int playbackState) {
          long elapsed = SystemClock.elapsedRealtime();
          if (playbackState == Player.STATE_BUFFERING) {
            metrics.onBufferingStarted(elapsed);
            stateMachine.onBufferingStarted();
          } else if (playbackState == Player.STATE_READY) {
            metrics.onBufferingEnded(elapsed);
            stateMachine.onBufferingEnded();
            onMediaReady();
          } else if (playbackState == Player.STATE_ENDED) {
            metrics.onBufferingEnded(elapsed);
            transitionToState(PlaybackState.ENDED);
            recordEvent("playback_ended", "position", String.valueOf(currentPositionMs()), null,
                null);
            onCurrentItemEnded();
          } else if (playbackState == Player.STATE_IDLE) {
            metrics.onBufferingEnded(elapsed);
            transitionToState(PlaybackState.IDLE);
          }
          updatePlayButton();
          updateProgress();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
          if (isPlaying) {
            transitionToState(PlaybackState.PLAYING);
          } else if (stateMachine.current() == PlaybackState.PLAYING) {
            transitionToState(PlaybackState.PAUSED);
          }
          updatePlayButton();
        }

        @Override
        public void onRenderedFirstFrame() {
          long elapsed = SystemClock.elapsedRealtime();
          metrics.onFirstFrame(elapsed);
          recordEvent(
              "first_frame",
              "ttff_ms",
              String.valueOf(metrics.snapshot().timeToFirstFrameMs()),
              "state",
              stateMachine.current().name());
        }

        @Override
        public void onPlayerError(PlaybackException error) {
          handlePlaybackError(error);
        }
      };

  // NOTE: dropped frame counting needs an AnalyticsListener (onDroppedVideoFrames is not part of
  // Player.Listener in ExoPlayer 2.18 as resolved here). PlaybackMetrics.onFrames is implemented and
  // unit tested; wiring the listener is scheduled in phase 1 (docs/player/06-roadmap-and-rollout.md).

  /** Called the first time the current media is parsed and the duration is known. */
  private void onMediaReady() {
    long duration = player == null ? 0L : Math.max(0L, player.getDuration());
    if (gestureEngine != null) gestureEngine.setDurationMs(duration);
    if (frameStepPlanner == null) frameStepPlanner = probeFrameRate();
    if (!resumeApplied) {
      resumeApplied = true;
      long saved = getSavedPosition(currentFile);
      ResumePolicy.Decision decision = ResumePolicy.decide(saved, duration);
      if (!decision.isResume() && saved > ResumePolicy.RESUME_THRESHOLD_MS) {
        // The user already watched this file: start over instead of resuming into the credits.
        player.seekTo(0);
      }
    }
    recordEvent("media_ready", "duration_ms", String.valueOf(duration), "tier",
        tuning == null ? "unknown" : tuning.tier().name());
  }

  /** End of item: honour A-B loop, then repeat mode, then auto-next. */
  private void onCurrentItemEnded() {
    if (abRepeat.isActive()) {
      player.seekTo(abRepeat.pointAMs());
      player.setPlayWhenReady(true);
      return;
    }
    if (queue != null && queue.shouldReplayCurrentOnEnd()) {
      player.seekTo(0);
      player.setPlayWhenReady(true);
      return;
    }
    if (autoNext && queue != null && queue.hasNext()) {
      playNeighbour(1);
    }
  }

  /** Maps the engine error onto the shared taxonomy and retries once when that is safe. */
  private void handlePlaybackError(PlaybackException error) {
    PlayerErrorCode code = mapErrorCode(error);
    savePosition();
    metrics.onError(code.telemetryName());
    transitionToState(PlaybackState.ERROR);
    recordEvent("playback_error", "error_code", code.telemetryName(), "engine_code",
        String.valueOf(error.errorCode));
    Log.w(TAG, "playback error " + code + " (" + error.errorCode + ")", error);
    telemetry.flush();
    showGestureMessage(getString(R.string.player_error_generic));
    if (code.retryAutomatically() && currentFile != null && currentFile.exists()) {
      handler.postDelayed(() -> prepareCurrentVideo(false), 400L);
    }
  }

  private static PlayerErrorCode mapErrorCode(PlaybackException error) {
    switch (error.errorCode) {
      case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:
        return PlayerErrorCode.SOURCE_NOT_FOUND;
      case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
      case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
        return PlayerErrorCode.NETWORK_TIMEOUT;
      case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
      case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
        return PlayerErrorCode.SOURCE_UNSUPPORTED;
      case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
      case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
        return PlayerErrorCode.DECODER_INIT_FAILED;
      case PlaybackException.ERROR_CODE_DECODING_FAILED:
      case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
      case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
        return PlayerErrorCode.DECODER_UNSUPPORTED;
      case PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED:
      case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED:
      case PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED:
      case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED:
      case PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR:
        return PlayerErrorCode.DRM_LICENSE_FAILED;
      case PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED:
        return PlayerErrorCode.DRM_DEVICE_NOT_SECURE;
      default:
        return PlayerErrorCode.UNKNOWN;
    }
  }

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
    if (!isInPip() && player != null) {
      // In picture in picture the playback keeps running; pausing here would kill it.
      player.setPlayWhenReady(false);
    }
    telemetry.flush();
    super.onPause();
  }

  @Override
  public void onUserLeaveHint() {
    // Leaving the app while playing drops into PiP instead of stopping, like a dedicated player.
    if (player != null && player.isPlaying()) {
      enterPip();
    }
    super.onUserLeaveHint();
  }

  @Override
  public void onPictureInPictureModeChanged(boolean inPictureInPictureMode) {
    super.onPictureInPictureModeChanged(inPictureInPictureMode);
    setControlsVisible(!inPictureInPictureMode && !controlsLocked);
    if (inPictureInPictureMode) {
      gestureEngine = newGestureEngine();
    }
  }

  private boolean isInPip() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
    return isInPictureInPictureMode();
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
    telemetry.flush();
    // QoE summary for this session; the CI performance harness reads these lines.
    Log.i(TAG, "qoe " + metrics.snapshot());
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