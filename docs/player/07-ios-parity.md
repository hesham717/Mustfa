# 07 — تحقيق التكافؤ على iOS 13+

> **حالة صريحة:** هذا المستودع أندرويد-فقط (Gradle + Java، لا يوجد أي مشروع Xcode أو ملف
> Swift). ما يلي **تصميم** قابل للتنفيذ في مستودع iOS منفصل، وليس كودًا موجودًا هنا.
> كل ما يمكن مشاركته فعليًا اليوم هو `player-core` (منطق JVM) عبر Kotlin Multiplatform
> أو إعادة كتابة موازية تُختبر بنفس حالات الاختبار.

## 1. لماذا النواة المشتركة ممكنة

`player-core` لا يستورد `android.*` ولا يستخدم أي صنف منصة. العقد المطلوب من iOS:

| عقد النواة | التنفيذ على iOS |
| --- | --- |
| `PlaybackStateMachine` | نفسها؛ تُترجم `AVPlayerItem.status` و`timeControlStatus` إليها |
| `MediaQueue` / `RepeatMode` | نفسها فوق `AVQueuePlayer` |
| `GestureEngine` | تُغذّى من `UIPanGestureRecognizer`/`UITapGestureRecognizer` (نقاط بدل بكسل) |
| `ResumePolicy` / `ResumeStore` | نفسها؛ التخزين في `UserDefaults` أو ملف plist |
| `PlaybackTuning` | تُترجم إلى `preferredPeakBitRate`، `preferredForwardBufferDuration`، وحد التخزين المؤقت |
| `DecoderPolicy` | `VTIsHardwareDecodeSupported` + جدول الشرائح |
| `SubtitleStyle` | مفاتيح `AVPlayerItemLegibleOutput` (`kAVPlayerItemLegibleOutputText*`) |
| `PlaybackMetrics` / `PlaybackEvent` | نفسها؛ الرفع عبر نفس مخطط الأحداث |

## 2. التكديس التقني

| الطبقة | الاختيار |
| --- | --- |
| العرض | `AVPlayerLayer` (افتراضي) أو `AVSampleBufferDisplayLayer` عند الحاجة إلى تحكم كامل |
| فك التشفير | VideoToolbox عبر AVFoundation (H.264/HEVC عتادي)، AV1 عبر `libdav1d` عند الضرورة |
| الحاويات | MP4/MOV/HLS أصلية؛ MKV/WebM تحتاج `VLCKit` أو محوّل (قرار منفصل) |
| DRM | FairPlay Streaming عبر `AVContentKeySession` |
| التحكم عن بعد | `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` |
| PiP | `AVPictureInPictureController` (iOS 14+ للهاتف، 13+ لـ iPad/tvOS) |
| البث | `AVRoutePickerView` (AirPlay) |
| الخلفية | `AVAudioSession(.playback)` + Background Modes |

## 3. الترجمة الدقيقة للضبط

| `PlaybackTuning` | iOS |
| --- | --- |
| `bufferForPlaybackMs` | `automaticallyWaitsToMinimizeStalling = false` + بدء مبكر |
| `maxBufferMs` | `AVPlayerItem.preferredForwardBufferDuration = maxBufferMs/1000` |
| سقف الدقة | `AVPlayerItem.preferredPeakBitRate` + تفضيل rendition عبر `AVAssetMediaSelectionOptions` |
| `cacheBytes` | مجلد `Caches/Player` مع نفس منطق `CacheIndex` |
| `allowExtendedSpeeds` | `AVPlayerItemAudioMix`/`rate` حتى 2.0 (أعلى يحتاج معالجة صوت مخصصة) |

## 4. FairPlay

1. `AVURLAsset` مع `resourceLoader` مخصص (`setDelegate:queue:` على `resourceLoader`).
2. طلب `skd://` → `contentKeySession:contentKeyRequest:didFinish...` → CKC من خادم المفاتيح.
3. تخزين رخصة التشغيل دون اتصال عبر `AVContentKeyResponse(fairPlayStreamingKeyResponseData:...)`
   و`persistableContentKey`.
4. المحتوى المحمي: لا AirPlay، لا التقاط شاشة (يُعتَّم تلقائيًا)، لا تصغير مصغّرات.

## 5. فروق يجب احترامها في المواصفة

| الموضوع | أندرويد | iOS |
| --- | --- | --- |
| سرعة التشغيل | حتى 4× بموسّع | عمليًا 0.5×–2× بدون معالجة إضافية |
| MKV/WebM | مدعوم | يحتاج مكتبة إضافية |
| PiP | API 26+، أي جهاز | iPhone يتطلب iOS 14+ |
| التخزين المؤقت | `SimpleCache` | `Caches/` مع تنظيف عند ضغط التخزين |
| DRM | Widevine L1/L3 | FairPlay فقط |
| خطوة إطار للأمام | `stepForward()` | `AVPlayerItem.step(byCount:)` |
| الترجمة المضمّنة | كل الحاويات | قيود على MKV |

## 6. خطة التحقق المشتركة

1. **اختبارات التكافؤ:** نفس ملفات الوسائط المرجعية (P1–P6 في `04` §4.3) على المنصتين،
   ومقارنة `first_frame`, `rebufferRatio`, `droppedFrameRatio` بهامش ±20%.
2. **اختبار قواعد النواة:** إن انتقلت النواة إلى Kotlin Multiplatform، تُشغَّل نفس حالات
   الاختبار الـ127 على JVM/iOS دون تعديل.
3. **مراجعة الخصوصية:** نفس مخطط الأحداث بلا معرّفات جهاز خام.
