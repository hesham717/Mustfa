# 04 — الأداء والقياسات

## 1. الميزانيات (Budgets)

الأرقام أهداف قابلة للقياس، وتُستخدم كمعايير قبول في `05-test-plan.md`.

| المقياس | فئة منخفضة | فئة متوسطة | فئة عليا | كيفية القياس |
| --- | --- | --- | --- | --- |
| زمن أول إطار TTFF (محلي) | ≤ 350 ms p50 | ≤ 200 ms p50 | ≤ 150 ms p50 | `PlaybackMetrics.timeToFirstFrameMs` |
| TTFF (شبكة، CDN دافئ) | ≤ 1200 ms p95 | ≤ 800 ms p95 | ≤ 600 ms p95 | نفسه + سمة `source=network` |
| نسبة التقطيع | ≤ 1% | ≤ 0.5% | ≤ 0.3% | `snapshot.rebufferRatio()` |
| الإطارات الساقطة | ≤ 1% | ≤ 0.1% | ≤ 0.1% | `snapshot.droppedFrameRatio()` |
| كمون القفز | ≤ 250 ms p95 | ≤ 150 ms p95 | ≤ 100 ms p95 | `snapshot.seekLatencyMsAverage/Max` |
| ذاكرة Java فوق الأساس | ≤ 30 MB | ≤ 55 MB | ≤ 90 MB | `Debug.getMemoryInfo` قبل/بعد |
| طاقة أثناء تشغيل 720p | ≤ 350 mW | ≤ 500 mW | ≤ 700 mW | `batterystats` / `PowerStats HAL` |
| إقلاع بارد للنشاط | ≤ 450 ms | ≤ 300 ms | ≤ 250 ms | `adb shell am start -W` |
| حجم الزيادة في APK | ≤ 6 MB (بدون FFmpeg ext) | — | — | `apkanalyzer` |

**ملاحظة عن «24/30/60 fps»:** المشغل لا يملك معدل إطارات مستقلًا؛ هو يقدّم إطارات الوسائط
في مواعيدها. المقياس الصحيح لذلك هو `droppedFrameRatio` و`framesRendered/framesScheduled`،
وهذا ما نُثبت به السلاسة بدل الادعاء.

## 2. مخطط أحداث QoE

كل الأحداث من `PlaybackEvent(name, timestampMs, attributes)`، والمفاتيح ثابتة بين المنصتين.

| الحدث | السمات | متى |
| --- | --- | --- |
| `player_open` | `tier`, `max_video`, `media` | فتح المشغل |
| `media_ready` | `duration_ms`, `tier` | أول `STATE_READY` |
| `first_frame` | `ttff_ms`, `state` | `onRenderedFirstFrame` |
| `rebuffer_start` / `rebuffer_end` | `duration_ms` | تجويع المخزن |
| `seek` | `latency_ms`, `from`, `to` | كل قفز |
| `speed_changed` | `speed`, `pitch_correction` | تغيير السرعة |
| `ab_repeat` | `state`, `position` | تحديد/إلغاء A-B |
| `frame_step` | `direction`, `fps` | خطوة إطار |
| `repeat_mode` | `mode` | تغيير التكرار |
| `queue_advance` | `direction`, `size` | التالي/السابق |
| `pip_enter` | `aspect` | دخول PiP |
| `subtitle_style` | `size`, `edge` | تغيير نمط الترجمة |
| `playback_ended` | `position` | نهاية العنصر |
| `playback_error` | `error_code`, `engine_code` | أي خطأ |
| `state_transition_rejected` | `from`, `to` | تناقض بين المحرك والنموذج |
| `decoder_selected` *(مطلوب)* | `codec`, `mode`, `reason` | من `DecoderDecision` |
| `bitrate_changed` *(مطلوب)* | `kbps`, `track_id` | تبديل rendition في HLS/DASH |

**قواعد الخصوصية:** لا مسارات مطلقة، لا أسماء مستخدمين؛ اسم الملف فقط، ويُجزَّأ (hash)
قبل الرفع في الإنتاج. الأحداث تُدفَّع (20 حدثًا افتراضيًا) لتقليل الاستيقاظات.

## 3. نقاط تجميع القياسات في الكود

| النقطة | الملف/الدالة |
| --- | --- |
| TTFF | `VideoPlayerActivity.onRenderedFirstFrame` → `metrics.onFirstFrame` |
| التقطيع | `onPlaybackStateChanged(STATE_BUFFERING/STATE_READY)` |
| كمون القفز | `pendingSeekAtMs` في `seekBy`/`applyGesture` → `metrics.onSeek` |
| الإطارات الساقطة | `onDroppedVideoFrames` |
| زمن المشاهدة | `updateProgress` → `metrics.onPosition` (يتجاهل القفزات للخلف) |
| الأخطاء | `handlePlaybackError` + `mapErrorCode` |
| الملخّص | `onDestroy` → `Log.i(TAG, "qoe " + metrics.snapshot())` |

سطر `qoe …` هو ما يقرأه ملف الأداء في CI (انظر §6).

## 4. ملفات البطارية والذاكرة

### 4.1 ملف البطارية
```bash
# تفريغ إحصاءات البطارية قبل/بعد جلسة 10 دقائق 720p
adb shell dumpsys batterystats --reset
adb shell am start -W -n com.droidtechlab.filemanager.debug/.ui.activities.VideoPlayerActivity
# ... 10 دقائق ...
adb shell dumpsys batterystats > batterystats-after.txt
adb shell cat /sys/class/power_supply/battery/current_now
```
المُبلَّغ: متوسط `current_now` × الجهد = مللي واط، لكل فئة جهاز ولكل ترميز
(H.264 720p30، HEVC 1080p30، VP9 720p60).

### 4.2 ملف الذاكرة
```bash
adb shell am start -W ... && sleep 5 && \
adb shell dumpsys meminfo com.droidtechlab.filemanager.debug > meminfo-playing.txt
```
المُبلَّغ: `Java Heap`، `Native Heap` (هنا يعيش مخزن `MediaCodec`)، `Graphics`، `TOTAL PSS`
قبل التحضير وأثناء التشغيل وبعد 5 دقائق.

### 4.3 حالات الاختبار القياسية
| الحالة | المحتوى | الجهاز | المدة |
| --- | --- | --- | --- |
| P1 | MP4 H.264 720p30 محلي | فئة منخفضة | 10 دقائق |
| P2 | MKV HEVC 1080p30 + ترجمة ASS | فئة متوسطة | 10 دقائق |
| P3 | HLS 4 renditions 1080p | فئة عليا | 15 دقيقة |
| P4 | VP9 60fps WebM | فئة عليا | 10 دقائق |
| P5 | ملف تالف/مقطوع | كل الفئات | حتى الخطأ |
| P6 | تبديل شبكة Wi-Fi ↔ 4G | فئة متوسطة | 10 دقائق |

## 5. مصفوفة الأجهزة المرجعية

| الفئة | مثال | RAM | أنوية | Android |
| --- | --- | --- | --- | --- |
| منخفضة | Android Go / Snapdragon 425 | 2 GB | 4 | 8.1–10 |
| متوسطة | Snapdragon 665 / Helio G80 | 3–4 GB | 8 | 10–12 |
| عليا | Snapdragon 8 Gen 2 | 8–12 GB | 8 | 13–15 |
| حدية | Android 5.1 API 22 | 1 GB | 4 | 5.1 (اختبار تراجع) |

## 6. أتمتة القياس

1. `:player-core:test` يثبت صحة القواعد (زمن/حالات/دلتات) بلا جهاز.
2. **مطلوب (المرحلة 3):** اختبار أداء على جهاز في Firebase Test Lab أو جهاز موصول:
   يشغّل P1–P4، يقرأ سطر `qoe …` من logcat، ويقارنه بالميزانيات أعلاه، ويفشل البناء عند
   تجاوزها. الصيغة المقترحة:
   ```
   qoe ttff=168ms rebuffer=1x/1420ms ratio=0.02 dropped=3/5412 seeks=6 avgSeek=84ms bitrate=2841kbps errors=0
   ```
   (`PlaybackMetrics.Snapshot.toString()` تُنتج هذا السطر فعلًا — انظر `PlaybackMetricsTest`).
3. **مطلوب:** `macrobenchmark` (AndroidX) لقياس TTFF وإقلاع النشاط على جهاز حقيقي.

## 7. ما الذي يجعل TTFF قصيرًا — القرارات الفعّالة

| القرار | التأثير |
| --- | --- |
| `bufferForPlaybackMs` صغير (800/1000/1250 ms) | يبدأ العرض بدل انتظار مخزن عميق |
| `ProgressiveMediaSource` مع `DefaultExtractorsFactory` | لا مفاوضات manifest للملفات المحلية |
| سقف دقة حسب الفئة | لا تحميل rendition 4K على شاشة 720p |
| فك تشفير عتادي | لا إحماء مفكك برمجي |
| `FLAG_KEEP_SCREEN_ON` بلا wakelock إضافي | طاقة أقل |
| إلغاء `progressRunnable` قبل إعادة جدولته | يمنع تكاثر حلقات التحديث (أُصلح) |
