# الترجمة العائمة (Live Translate Overlay)

تطبيق Android أصلي (Kotlin + Jetpack Compose) يطبّق فكرة ميزة «البحث الدائري + مرّر وترجم» الظاهرة في الفيديو:
إطار مضيء ملوّن حول حواف الشاشة، فقاعة عائمة بلا أي إطار أو شريط عنوان، ثم ترجمة النص الظاهر على الشاشة
**في مكانه** وتحديثها تلقائياً كلما مرّرت، في أي تطبيق.

## ما الذي يظهر في الفيديو وكيف طُبّق

| في الفيديو | في التطبيق |
|---|---|
| ضغط مطوّل على زر الرئيسية يفتح شريط Google مع زر الترجمة | الفقاعة العائمة «ترجمة الشاشة» + زر في الإعدادات السريعة (لا يمكن لتطبيق عادي اعتراض زر الرئيسية) |
| توهّج متدرّج (أزرق/بنفسجي/وردي/برتقالي/أخضر) حول حواف الشاشة | `EdgeGlowPainter`: خطوط متراكبة بتدرّج `SweepGradient` يدور، وتتبع انحناء زوايا الشاشة الفعلي |
| شريحتا «Korean → English» وزر «Scroll and translate» | بطاقة `PICKER`: شريحتا اللغتين (قائمة منسدلة داخلية) + «مرّر وترجم» + زر أزرق دائري |
| فقاعة صغيرة «Translating to English ×» أسفل الشاشة | فقاعة `TRANSLATING`: «جارٍ الترجمة / إلى العربية» مع × ومؤشّر عمل |
| النص (حتى المائل داخل الصور) يُستبدل بالترجمة بنفس الموضع | `StageView` يرسم كل كتلة مترجمة بنفس الزاوية والحجم ولون خلفية/نص مأخوذ من الصورة |
| التمرير يستمر والترجمة تتجدد | `LiveTranslateController`: تُخفى الترجمة عند اللمس، وتُعاد بعد استقرار الشاشة ‎350ms |

## الهيكلة

```
com.livetranslate.overlay/
├── MainActivity.kt               # شاشة الإعداد: الأذونات، اللغات، تشغيل/إيقاف
├── OverlayTrampolineActivity.kt  # نشاط شفاف لطلب موافقة التقاط الشاشة
├── service/
│   ├── OverlayService.kt         # الخدمة الأمامية: تملك النافذتين وتدير الحالة
│   └── NotificationHelper.kt     # إشعار الخدمة الدائم
├── overlay/
│   ├── StageView.kt              # طبقة ملء الشاشة: الإطار المضيء + النص المترجم (لا تستقبل اللمس)
│   ├── EdgeGlowPainter.kt        # رسم التوهّج حول الحواف
│   ├── DraggableFrame.kt         # حاوية قابلة للسحب + اكتشاف اللمس خارجها
│   ├── ControlPill.kt            # واجهة الفقاعة (Compose) بحالاتها الثلاث
│   ├── OverlayUiState.kt
│   └── ScreenMetrics.kt
├── capture/
│   ├── ScreenCapturer.kt         # MediaProjection → VirtualDisplay → ImageReader
│   ├── FrameSignature.kt         # بصمة سطوع رخيصة لاكتشاف التمرير/الاستقرار
│   └── LiveTranslateController.kt# آلة الحالات: انتظار ← معالجة ← عرض
├── translate/
│   ├── TranslationEngine.kt      # OCR + كشف اللغة + ترجمة ML Kit (على الجهاز)
│   ├── Languages.kt              # اللغات المدعومة وفلاتر الكتابة
│   └── TextPatch.kt
├── tile/TranslateTileService.kt  # زر الإعدادات السريعة
└── data/Prefs.kt
```

## النوافذ العائمة ولماذا هي شفافة بالكامل

يضيف `OverlayService` نافذتين عبر `WindowManager` بالنوع `TYPE_APPLICATION_OVERLAY`
و`PixelFormat.TRANSLUCENT` وبلا أي `background`، لذلك لا يرسم النظام إطاراً ولا شريط عنوان ولا ظلاً للنافذة:

1. **طبقة المسرح** (`StageView`) بحجم الشاشة الكامل:
   `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`
   → كل اللمسات تمرّ للتطبيق تحتها، فتستطيع التمرير والنقر بشكل طبيعي.
2. **الفقاعة** (`WRAP_CONTENT`):
   `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH`
   → تستقبل اللمس داخل حدودها فقط، وتُبلَّغ بـ `ACTION_OUTSIDE` عند لمس أي مكان آخر (إشارة بدء التمرير).
   السحب يتم من أي نقطة فيها باستخدام `rawX/rawY`، والفقاعة تبقى متمركزة وتكبر للأعلى عند تحولها لبطاقة.

> **ملاحظة مهمة (Android 12+):** يحجب النظام اللمس عن التطبيق السفلي إذا كانت نوافذ تطبيق آخر فوقه
> بعتامة مجمّعة أكبر من `InputManager.getMaximumObscuringOpacityForTouch()` (0.8 افتراضياً).
> لذلك دُمج الإطار المضيء والنص المترجم في **نافذة واحدة** بشفافية 0.8، فيبقى التمرير تحتها يعمل.
> الأثر الجانبي: يظهر النص الأصلي خلف الترجمة بنسبة ‎20%‎ تقريباً.

## الأذونات وكيفية منحها

### 1. `SYSTEM_ALERT_WINDOW` (الظهور فوق التطبيقات)
إذن «خاص» لا يُمنح بنافذة حوار عادية؛ يجب فتح صفحة الإعدادات:

```kotlin
if (!Settings.canDrawOverlays(context)) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    )
}
```
يفعّل المستخدم «السماح بالظهور فوق التطبيقات الأخرى» ثم يعود؛ تعيد `MainActivity` الفحص في `ON_RESUME`.
يدوياً: الإعدادات ← التطبيقات ← الترجمة العائمة ← الظهور فوق التطبيقات الأخرى.
للاختبار عبر ADB:
```bash
adb shell appops set com.livetranslate.overlay SYSTEM_ALERT_WINDOW allow
```

### 2. `POST_NOTIFICATIONS` (Android 13+)
إذن وقت تشغيل عادي يُطلب بـ `ActivityResultContracts.RequestPermission`. بدونه تعمل الخدمة لكن إشعارها لا يظهر.

### 3. الخدمة الأمامية `FOREGROUND_SERVICE` + أنواعها
Android 14 يفرض إعلان نوع الخدمة:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<service android:name=".service.OverlayService"
    android:foregroundServiceType="specialUse|mediaProjection">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Floating live-translation overlay shown above other apps" />
</service>
```
- في الوضع الخامل (الفقاعة فقط) تعمل بنوع `specialUse`.
- عند بدء الترجمة تُستدعى `startForeground` مجدداً بالنوع `specialUse | mediaProjection`
  **قبل** `getMediaProjection()` كما يشترط Android 14.

### 4. التقاط الشاشة (MediaProjection)
ليس إذناً في الـ Manifest بل موافقة لكل جلسة: عند الضغط على «مرّر وترجم» تفتح الخدمة
`OverlayTrampolineActivity` الشفاف، الذي يستدعي `MediaProjectionManager.createScreenCaptureIntent()`
ويمرّر النتيجة للخدمة. على Android 14+ لا يمكن إعادة استخدام الموافقة، لذا تُطلب من جديد بعد كل إيقاف.

## تشغيل الخدمة

```kotlin
ContextCompat.startForegroundService(
    context, OverlayService.intent(context, OverlayService.ACTION_SHOW)
)
```
الأوامر المتاحة: `ACTION_SHOW` (الفقاعة)، `ACTION_OPEN_PICKER` (بطاقة اللغات مباشرة)، `ACTION_STOP`.
الخدمة `START_STICKY`، فتبقى الفقاعة عند مغادرة التطبيق أو التنقل بين التطبيقات، ويعيدها النظام إن أوقفها.

## خط الترجمة

1. `ScreenCapturer` يعكس الشاشة إلى `ImageReader` (RGBA_8888) ويحسب لكل إطار بصمة 32×64 من السطوع
   (مع استبعاد الحواف والفقاعة).
2. عند لمس الشاشة تُخفى الترجمة، وعندما تبقى البصمة ثابتة ‎350ms تُلتقط صورة نظيفة.
3. `TranslationEngine`: ML Kit Text Recognition v2 (لاتيني/كوري/ياباني/صيني/ديفاناغاري) ← كشف لغة كل كتلة ←
   ML Kit Translation على الجهاز (تُنزَّل حزمة اللغة مرة واحدة ~30MB) مع ذاكرة مؤقتة للنصوص المترجمة.
4. لكل كتلة: الزاوية والأبعاد من `cornerPoints`، لون الخلفية من حلقة حول الكتلة، لون النص من أبعد بكسل عنها،
   وحجم خط يُضبط بالبحث الثنائي ليتسع داخل الصندوق.
5. إن عادت الشاشة لنفس المحتوى تُعرض الترجمة المحفوظة فوراً.

## حدود معروفة
- قراءة **العربية** من الشاشة غير مدعومة في ML Kit حالياً؛ الترجمة **إلى** العربية مدعومة.
- لا يمكن لتطبيق عادي استبدال إيماءة الضغط المطوّل على زر الرئيسية؛ البديل هو الفقاعة وزر الإعدادات السريعة.
- لعتامة ‎100%‎ مع مرور اللمس يلزم تحويل الطبقة إلى `AccessibilityService` بنافذة `TYPE_ACCESSIBILITY_OVERLAY`.

## البناء

```bash
./gradlew :livetranslate:testDebugUnitTest
./gradlew :livetranslate:assembleDebug
```
