# 01 — المعمارية

## 1. المبدأ الحاكم

> **المنطق في مكان واحد، والمنصة محوِّل.**

كل ما يشعر به المستخدم (الحالات، الطابور، الإيماءات، الاستئناف، السرعة، A-B، سياسة فك
التشفير، القياسات) يعيش في `player-core` بلغة جافا نقية بلا أي استيراد من `android.*`.
طبقة أندرويد مسؤولة عن ثلاثة أشياء فقط: ترجمة أحداث المحرك إلى مفردات النواة، تنفيذ
أوامرها على `ExoPlayer`/الـ`View`s، وتوفير قدرات الجهاز للنواة.

هذا هو الفرق بين «مشغل يعمل» و«مشغل يمكن إثبات أنه يعمل»: الأولى تُختبر باليد على جهاز،
والثانية تُختبر في CI خلال ثوانٍ على أي منصة.

## 2. مخطط الطبقات

```
┌───────────────────────────────────────────────────────────────────────────┐
│  Presentation (Android)                                                   │
│  VideoPlayerActivity · layouts · PlayerView · dialogs · overlays          │
│  ─ مسؤول عن: العرض، الإيماءات الخام، دورة الحياة، PiP، الصلاحيات           │
└───────────────▲──────────────────────────────────────────────┬────────────┘
                │  intents / results                           │ render
┌───────────────┴──────────────────────────────────────────────▼────────────┐
│  player-core  (pure JVM, no android.*)                                    │
│                                                                           │
│   PlaybackStateMachine ── MediaQueue ── AbRepeatController ── SpeedCtrl   │
│   GestureEngine ── ResumePolicy ── FrameStepPlanner ── SubtitleStyle      │
│   DeviceTier ── PlaybackTuning ── DecoderPolicy ── CacheIndex             │
│   PlaybackMetrics ── PlaybackEvent ── TelemetrySink ── PlayerErrorCode    │
└───────────────▲──────────────────────────────────────────────┬────────────┘
                │  engine events                               │ commands
┌───────────────┴──────────────────────────────────────────────▼────────────┐
│  Engine adapters (platform)                                               │
│  Android: ExoPlayer 2.18.7 · MediaCodec · DefaultLoadControl · HLS/DASH   │
│  iOS:     AVPlayer / AVSampleBufferDisplayLayer · VideoToolbox (مخطط)      │
└───────────────────────────────────────────────────────────────────────────┘
                │
┌───────────────▼───────────────────────────────────────────────────────────┐
│  Platform services: storage, network, DRM (Widevine/FairPlay),            │
│  MediaSession/Now Playing, analytics uploader                             │
└───────────────────────────────────────────────────────────────────────────┘
```

## 3. حدود الوحدات ومسؤولياتها

| الوحدة | المسؤول عن | ممنوع عنها |
| --- | --- | --- |
| `player-core` | القواعد والقرارات والقياسات | أي استيراد `android.*`، أي I/O، أي خيوط |
| `ui.activities.VideoPlayerActivity` | العرض ودورة الحياة وتحويل الأحداث | اتخاذ قرارات سياسة (تُفوّض للنواة) |
| ExoPlayer adapter (داخل النشاط حاليًا، يُفصل في المرحلة 2) | بناء `MediaSource`، ضبط `LoadControl`/`TrackSelector`، إدارة `MediaCodec` | معرفة قواعد الطابور أو الاستئناف |
| خدمات المنصة | التخزين المؤقت على القرص، الشبكة، DRM، التحليلات | معرفة واجهة المستخدم |

**قاعدة التبعيات:** `app → player-core` فقط. النواة لا تعرف شيئًا عن `app`، وهذا ما يجعل
إعادة استخدامها في تطبيق آخر (أو في iOS عبر Kotlin Multiplatform لاحقًا) ممكنة دون تعديل.

## 4. نموذج الخيوط

| الخيط | ما يعمل عليه | القاعدة |
| --- | --- | --- |
| Main/UI | كل الـ`View`s، `GestureEngine`، `PlaybackStateMachine` (قراءة) | لا عمل مُكلف، لا I/O |
| ExoPlayer playback thread | فك التشفير، تقديم الإطارات، أحداث `Player.Listener` | الأحداث تُسلَّم إلى Main تلقائيًا في 2.18 |
| Loader thread(s) | الشبكة/القرص | عددها من `PlaybackTuning` (2/3/4 حسب الفئة) |
| Telemetry worker (المرحلة 2) | رفع الأحداث | `TelemetrySink` غير حاجز؛ الدفعات تُخزَّن ثم تُرفع |

`PlaybackStateMachine` و`InMemoryResumeStore` و`InMemoryTelemetrySink` و`CacheIndex`
مُزامَنة (`synchronized`) لأن القراء والكتّاب قد يكونون على خيوط مختلفة. `MediaQueue`
و`GestureEngine` غير مُزامَنة عن قصد: ملكية واحدة على خيط Main، والمزامنة هناك كلفة بلا فائدة.

## 5. دورة حياة جلسة تشغيل

```
IDLE ──▶ PREPARING ──▶ READY ──▶ PLAYING ⇄ BUFFERING ──▶ ENDED ──▶ IDLE
                 │        │          │                       │
                 └────────┴──────────┴───────────────────────┴──▶ ERROR ──▶ IDLE
```

الانتقالات غير المسموحة (مثل `ENDED → PLAYING` دون إعادة تحضير) ترمي `IllegalStateException`
في الاختبارات، وتُسجَّل كحدث `state_transition_rejected` في الإنتاج مع محاولة تعافٍ عبر
`IDLE`. الفكرة: التناقض بين المحرك والنموذج يجب أن يظهر في القياسات، لا أن يختفي.

`BUFFERING` يحفظ الحالة السابقة (`onBufferingStarted/onBufferingEnded`) حتى لا يؤدي
انتهاء التحميل المؤقت أثناء الإيقاف إلى استئناف التشغيل تلقائيًا — وهو سلوك مزعج يظهر
عند القفز أثناء الإيقاف.

## 6. ميزانية الذاكرة

الأرقام أدناه *ميزانية*، أي حدود تصميم تُقاس وتُرفض إن تجاوزها البناء، لا توصيفًا للواقع.

| البند | فئة منخفضة | فئة متوسطة | فئة عليا |
| --- | --- | --- | --- |
| مخزن فك التشفير (ExoPlayer `min/maxBuffer`) | 15s / 30s | 30s / 60s | 50s / 120s |
| سقف الدقة | 1280×720 | 1920×1080 | 3840×2160 |
| التخزين المؤقت على القرص | 64 ميغابايت | 128 ميغابايت | 256 ميغابايت |
| خيوط التحميل/فك التشفير | 2 | 3 | 4 |
| موسّعات الترميز (FFmpeg ext) | معطّلة | عند الحاجة | مُفضَّلة |
| هدف ذاكرة Java heap للمشغل | ≤ 30 ميغابايت | ≤ 55 | ≤ 90 |

**المنطق:** كل ثانية إضافية من فيديو 8 ميغابت/ث ≈ 1 ميغابايت كومة. على جهاز 2 غيغابايت
يتنافس هذا مع مخارج `MediaCodec` نفسها (التي تحتاج 4–8 إطارات مرجعية)، لذا سقف الفئة
المنخفضة متعمَّد الانخفاض مقابل زمن أول إطار قصير (`bufferForPlaybackMs = 800`).

## 7. سجل القرارات المعمارية (ADR)

### ADR-001 — نواة JVM نقية بدل الاعتماد على ExoPlayer مباشرة في كل مكان
- **السياق:** المشغل يحتاج نفس القواعد على أندرويد وiOS، والاختبارات الآلية في CI لا تملك جهازًا.
- **القرار:** عزل القواعد في `player-core`.
- **المقايضة:** طبقة تحويل إضافية (≈200 سطر) وازدواج مفاهيمي محتمل مع `Player.REPEAT_MODE_*`.
- **البديل المرفوض:** بناء SDK فوق `media3` مباشرة — يربط المشروع بمسار جوجل ويجعل iOS مشروعًا منفصلًا بالكامل.
- **النتيجة:** 128 اختبار وحدة يعمل في ثوانٍ، وقواعد قابلة للمقارنة بين المنصتين.

### ADR-002 — البقاء على ExoPlayer 2.18.7 بدل الترحيل إلى media3 الآن
- **السياق:** المستودع على `compileSdk 31` وAGP 3.5.0 وJava 8؛ `media3` يتطلب AndroidX و`compileSdk 33+`.
- **القرار:** الترحيل مؤجل إلى المرحلة 3؛ تُبنى الواجهات بحيث لا تتسرب مفاهيم ExoPlayer إلى النواة.
- **المقايضة:** تأخير الوصول إلى `Media3` API الأنظف وإلى تحسينات `MediaCodec` الأحدث.
- **معيار الخروج من القرار:** عند رفع `compileSdk` إلى 33+، يُستبدل المحوِّل دون تغيير `player-core`.

### ADR-003 — سياسة فك التشفير بيانات، لا تعليمات `if` متناثرة
- **القرار:** `DecoderPolicy.decide(codec, w, h, requiresSecure, capabilities, tuning)` تعيد
  `DecoderDecision` بسبب مقروء آليًا (`hardware_supported`, `drm_no_secure_path`, …).
- **الفائدة:** الأسباب نفسها تُستخدم في واجهة المستخدم وفي القياسات، وقرار «لا رجوع برمجي
  لمحتوى DRM» مُختبَر لا مُتذكَّر.

### ADR-004 — القياسات مخطَّطة في النواة، والرفع في المنصة
- **القرار:** `PlaybackEvent(name, ts, attributes)` + `TelemetrySink`، مع `InMemoryTelemetrySink`
  يدفّع الأحداث (افتراضيًا 20 حدثًا) للحد من الاستيقاظات.
- **المقايضة:** تأخير في وصول البيانات مقابل كلفة CPU أقل على الأجهزة الضعيفة.

## 8. واجهة البرمجة العامة (Android)

الواجهة المقترحة للمرحلة 2 — الطبقة التي سيستهلكها أي نشاط/شاشة مستقبلًا:

```java
public interface PlayerController {
  void open(MediaDescriptor media, OpenOptions options);   // ملف محلي، content://، http(s)
  void play(); void pause(); void stop();
  void seekTo(long positionMs);
  void setSpeed(float speed);                              // يُمرَّر عبر SpeedController
  void setAbRepeat(@Nullable Long aMs, @Nullable Long bMs);
  void stepFrame(int direction);                           // +1/-1
  void selectTrack(TrackType type, @Nullable String trackId);
  void setSubtitleStyle(SubtitleStyle style);
  void setRepeatMode(RepeatMode mode);
  void attachOutput(PlayerSurface surface);                // SurfaceView/TextureView/PiP
  void addListener(PlayerListener listener);
  PlaybackMetrics.Snapshot metrics();
  void release();
}

public interface PlayerListener {
  void onStateChanged(PlaybackState state);
  void onTracksAvailable(List<TrackInfo> tracks);
  void onError(PlayerErrorCode code, String detail);
  void onMetrics(PlaybackMetrics.Snapshot snapshot);
}
```

كل الأنواع أعلاه موجودة اليوم في `player-core` باستثناء `PlayerController`/`PlayerListener`/
`MediaDescriptor`/`PlayerSurface`، وهي واجهات التغليف المقررة في المرحلة 2 (راجع
`06-roadmap-and-rollout.md`).

## 9. ما هو منفَّذ فعليًا في `VideoPlayerActivity`

| الميزة | التنفيذ |
| --- | --- |
| ضبط المخازن وسقف الدقة ووضع الموسّعات | `PlaybackTuning.forTier(currentDeviceTier())` → `DefaultLoadControl` + `DefaultTrackSelector` + `DefaultRenderersFactory` |
| الإيماءات | `GestureEngine` + `applyGesture(GestureResult)`، مع إعادة بناء المحرك عند تغيّر التخطيط (تدوير/PiP) |
| الطابور والتكرار | `MediaQueue` + `RepeatMode` (OFF/ALL/ONE) |
| A-B | `AbRepeatController` + `tick()` داخل `updateProgress()` + تنفيذ عند `STATE_ENDED` |
| السرعة | `SpeedController` (قوالب إضافية على الفئة العليا) |
| خطوة الإطار | بحث إلى حد الإطار في الاتجاهين عبر `FrameStepPlanner` (لا `stepForward()`؛ غير موجود في الواجهة العامة لـ ExoPlayer 2.18)، ومعدل الإطار يُقرأ بـ`MediaMetadataRetriever` |
| الاستئناف | `ResumePolicy.decide()` مرتين: قبل التحضير (بلا مدة) وبعد `READY` (بالمدة الحقيقية) |
| HLS/DASH/DRM | `createMediaSourceForUri()` يختار المحلّل حسب الامتداد، وWidevine عبر `MediaItem.DrmConfiguration` |
| القياسات | `PlaybackMetrics` + `InMemoryTelemetrySink` + ملخّص `Log.i(TAG, "qoe …")` عند التدمير |
| PiP | `enterPip()` + `onUserLeaveHint()` + `onPictureInPictureModeChanged()`، بحارس API 26 |
| أخطاء | `mapErrorCode()` ← `PlayerErrorCode` + إعادة محاولة تلقائية واحدة للأخطاء القابلة لذلك |

## 10. ديون معمارية معروفة (بصراحة)

1. **المحوِّل ما زال داخل النشاط.** `VideoPlayerActivity` (1413 سطرًا) يحمل كود ExoPlayer؛
   فصله إلى `ExoPlayerController` هو أول مهمة في المرحلة 2.
2. **`SharedPreferences` للاستئناف.** المفتاح `video_position_<path>` ينمو بلا حد؛ المرحلة 2
   تنقله إلى Room خلف `ResumeStore` مع `InMemoryResumeStore` كطبقة أولى.
3. **لا `MediaSession`.** التحكم من سماعات الرأس/الإشعارات يحتاج `exoplayer-mediasession`
   (قطعة إضافية غير موجودة في حزمة `exoplayer`)؛ مجدول في المرحلة 2.
4. **التخزين المؤقت الحسابي فقط.** `CacheIndex` يحاسب البايتات؛ ربطه بـ`SimpleCache` على القرص
   لم يتم بعد.
5. **DRM غير مُختبَر على جهاز حقيقي.** المسار مُرمَّز لكنه يحتاج ترخيصًا فعليًا للتحقق.
6. **الإطارات الساقطة غير موصولة.** `PlaybackMetrics.onFrames` منفَّذ ومُختبَر، لكن
   `onDroppedVideoFrames` ليس من دوال `Player.Listener` في النسخة المحلولة هنا؛ التوصيل يحتاج
   `AnalyticsListener` (المرحلة 1).
7. **معدل الإطار يُقرأ من الحاوية لا من المحرك.** `MediaMetadataRetriever` ينجح على الملفات
   المحلية، لا على تدفقات HLS/DASH؛ خطوة الإطار تُعلن «غير متاحة» في تلك الحالة.
