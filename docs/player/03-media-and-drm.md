# 03 — الوسائط، الترميزات، وإدارة الحقوق الرقمية

## 1. مصفوفة الترميزات

| الترميز | MIME | عتادي (MediaCodec) | برمجي احتياطي | ملاحظات |
| --- | --- | --- | --- | --- |
| H.264/AVC | `video/avc` | ✅ على كل الأجهزة المدعومة | ✅ (FFmpeg ext) | الافتراضي الآمن؛ الاحتياطي البرمجي مسموح حتى 1080p |
| H.265/HEVC | `video/hevc` | ✅ API 24+ عادةً، وبعض أجهزة API 21 | ❌ (مكلف جدًا) | يُرفض بلا عتاد بدل حرق البطارية |
| VP9 | `video/x-vnd.on2.vp9` | ✅ على معظم الأجهزة | ❌ | شائع في WebM/YouTube |
| VP8 | `video/x-vnd.on2.vp8` | متغيّر | ✅ | نادر اليوم |
| AV1 | `video/av01` | ✅ أجهزة 2020+ فقط | ❌ (dav1d مكلف على الهاتف) | يُبلَّغ كغير مدعوم مع سبب واضح |
| MPEG-4 Part 2 | `video/mp4v-es` | ✅ | ✅ | ملفات قادمة من كاميرات قديمة |

المنطق في `CodecId.isSoftwareFallbackViable()`: الاحتياطي البرمجي مسموح لـ H.264/VP8/MPEG-4
فقط، لأنه على الهاتف فك HEVC/VP9/AV1 برمجيًا يعني 2–4 أنوية مشغولة وإطارات ساقطة — أي
«يعمل» على الورق وتجربة سيئة في الواقع.

## 2. الحاويات والتدفق

| الحاوية | المحلّل | الحالة |
| --- | --- | --- |
| MP4/MOV | `ProgressiveMediaSource` + `DefaultExtractorsFactory` | منفَّذ |
| MKV/WebM | نفسه (Matroska extractor) | منفَّذ |
| HLS (`.m3u8`) | `HlsMediaSource.Factory` | منفَّذ |
| DASH (`.mpd`) | `DashMediaSource.Factory` | منفَّذ |
| SmoothStreaming | `SsMediaSource` | غير موصول (الحزمة متاحة) |
| بث مباشر | نفسه | يحتاج اختبارًا على تدفق حي |

**اختيار المحلّل** يتم من امتداد المسار في `createMediaSourceForUri()`؛ في المرحلة 2 يُستبدل
بفحص `Content-Type`/`sniffing` لأن الامتداد غير موثوق في روابط CDN.

**HTTP Range:** مدعوم ضمنًا عبر `DefaultDataSource`/`DefaultHttpDataSource` في المسار التقدمي
والبحث في MP4/MKV. عند رفض الخادم للرأس (`416`/`400`) يُتوقّع `ERROR_CODE_IO_*` →
`NETWORK_RANGE_UNSUPPORTED` → تعطيل البحث والعودة إلى القراءة التسلسلية (المرحلة 2).

## 3. سياسة فك التشفير

`DecoderPolicy.decide(codec, width, height, requiresSecure, capabilities, tuning)` —
الترتيب ثابت ومُختبَر:

```
1) codec == UNKNOWN أو capabilities غير متاحة      → UNSUPPORTED("codec_unknown")
2) requiresSecure:
     لا عتاد للترميز                              → UNSUPPORTED("drm_no_hardware_decoder")
     عتاد بلا مسار آمن                            → UNSUPPORTED("drm_no_secure_path")
     وإلا                                         → HARDWARE_SECURE("drm_secure_path")
3) عتاد متاح:
     الدقة فوق سقف الفئة + احتياطي برمجي ممكن     → SOFTWARE("hardware_above_resolution_ceiling")
     الدقة فوق سقف الفئة بلا احتياطي              → UNSUPPORTED("resolution_above_device_ceiling")
     وإلا                                         → HARDWARE("hardware_supported")
4) احتياطي برمجي ممكن ومسموح للترميز:
     فوق سقف الفئة                                → UNSUPPORTED("software_above_resolution_ceiling")
     وإلا                                         → SOFTWARE("software_fallback")
5) غير ذلك                                        → UNSUPPORTED("no_decoder_for_codec")
```

**القاعدة التي لا تُكسر:** محتوى DRM لا ينزل أبدًا إلى فك تشفير برمجي. الرخصة مرتبطة
بمسار العتاد الآمن؛ الرجوع البرمجي يعني فشل ترخيص لا «جودة أقل».

**مصدر `DeviceCapabilities`:**
- أندرويد: عدّ `MediaCodecList` عند الإقلاع (مرة واحدة، مخزَّن)، مع `isSecure` من
  `MediaCodecInfo.CodecCapabilities.isFeatureSupported(FEATURE_SecurePlayback)`.
- iOS: جدول ثابت حسب الشريحة + `VTIsHardwareDecodeSupported`.

## 4. إدارة الحقوق الرقمية

### 4.1 أندرويد — Widevine
- **المسار:** `MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(url)`، وهو
  المسار المدعوم في ExoPlayer 2.18 ويُبقي `DefaultDrmSessionManagerProvider` مسؤولًا عن
  الجلسة (منفَّذ في `createMediaSourceForUri` عند وجود `license_url` في الرابط).
- **مستوى الحماية:** `HW_SECURE_ALL` متى كان `HARDWARE_SECURE` متاحًا؛ وإلا رفض مع
  `DRM_DEVICE_NOT_SECURE` بدل تخفيض صامت قد يُبطل العقد مع صاحب المحتوى.
- **Offline DRM:** تخزين الرخص (License) يتطلب `OfflineLicenseHelper` + قاعدة بيانات رخص —
  **غير منفَّذ**، مُجدول في المرحلة 4 مع التنزيلات.
- **L3 vs L1:** L1 إلزامي لمحتوى HD+؛ L3 يُقبل فقط لـ SD ويُبلَّغ في القياسات.

### 4.2 iOS — FairPlay Streaming
- `AVContentKeySession` + `AVAssetResourceLoaderDelegate` لمعالجة طلبات `skd://`.
- الإخراج عبر `AVSampleBufferDisplayLayer` مع `enablesSecureDecoding`/مسار آمن؛
  العرض عبر `UIScreen` فقط (لا AirPlay لمحتوى محمي إلا بتصريح).
- التفصيل في [`07-ios-parity.md`](07-ios-parity.md).

### 4.3 الحماية المعزولة
- مفاتيح DRM لا تعبر أبدًا إلى كود التطبيق؛ تبقى داخل `MediaDrm`/`MediaCrypto` (أندرويد)
  أو `FPSSession` (iOS).
- لا تسجيل شاشة: عند `FLAG_SECURE` يُعرض أسود في الالتقاط — **مطلوب تنفيذه** عند تفعيل
  `HARDWARE_SECURE` (`getWindow().addFlags(FLAG_SECURE)`).
- لقطات المعاينة (thumbnails) لمحتوى محمي ممنوعة.

## 5. نموذج التهديد (مختصر)

| التهديد | التأثير | التخفيف |
| --- | --- | --- |
| تسجيل شاشة لمحتوى L1 | تسريب | `FLAG_SECURE` + رفض المسار غير الآمن |
| فك تشفير برمجي لمحتوى DRM | إبطال التزام الترخيص | ممنوع بنيةً في `DecoderPolicy` (مُختبَر) |
| تخزين مفاتيح في SharedPreferences | استخراج من جهاز rooted | المفاتيح لا تغادر `MediaDrm`؛ لا تخزين |
| تخزين مؤقت لمقاطع محمية على القرص | نسخ | **مطلوب:** منع `SimpleCache` لمسارات DRM |
| روابط ترخيص مُضمَّنة في APK | انتحال | الترخيص يأتي من خادم التطبيق بجلسة موقّتة |
| تحليل القياسات لكشف العادات | خصوصية | لا مسارات ملفات كاملة في الأحداث؛ أسماء فقط (راجع 04) |

## 6. الصوت

- `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MOVIE)` + `setHandleAudioBecomingNoisy(true)`
  (فصل السماعة يوقف التشغيل).
- `setWakeMode(C.WAKE_MODE_LOCAL)` للتشغيل من التخزين المحلي.
- **مفقود:** `MediaSession` للتحكم من سماعات الرأس/شاشة القفل — يحتاج القطعة الإضافية
  `exoplayer-mediasession` (المرحلة 2).
