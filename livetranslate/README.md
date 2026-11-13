# الترجمة العائمة (Live Translate Overlay)

تطبيق Android أصلي (Kotlin + Jetpack Compose) يطبّق فكرة ميزة «مرّر وترجم»:
ترجمة النص الظاهر على الشاشة **في مكانه** بدل النص الأصلي، وتحديثها تلقائياً كلما مرّرت، في أي تطبيق،
عبر طبقة شفافة تماماً بلا فقاعات ولا حواف ولا إطارات.

## طريقة العمل

- **بلا أي عناصر عائمة:** لا فقاعة ولا شريط ولا أيقونة ولا حواف ملوّنة. الشيء الوحيد المرسوم فوق الشاشة هو النص المترجم نفسه.
- **التحكم:** زر «ترجمة الشاشة» في الإعدادات السريعة (تشغيل/إيقاف)، أو زر «إيقاف» في الإشعار، أو شاشة التطبيق.
- **استبدال مباشر:** كل كتلة نص تُغطّى بصندوق بلون خلفيتها الأصلية، وتُكتب الترجمة فوقها بنفس الموضع والزاوية.
  مع تفعيل خدمة إمكانية الوصول تكون التغطية ‎100%‎؛ وبدونها 80% (قيد في Android 12+، انظر أدناه).
- **سرعة أثناء التمرير:** الترجمة تبقى ظاهرة حتى يتحرك المحتوى فعلاً، وتختفي خلال إطار واحد عند التمرير،
  ثم تعود بعد ‎180ms‎ فقط من توقف الشاشة. كل كتلة تظهر فور ترجمتها، والشاشات المترجمة سابقاً تظهر فوراً من الذاكرة.

## الهيكلة

```
com.livetranslate.overlay/
├── MainActivity.kt               # الإعداد: الأذونات، اللغات، تنزيل الحزم مسبقاً، بدء/إيقاف
├── OverlayTrampolineActivity.kt  # نشاط شفاف لطلب موافقة التقاط الشاشة
├── a11y/TranslateAccessibilityService.kt # مضيف اختياري لطبقة بعتامة كاملة (لا يقرأ أي محتوى)
├── service/
│   ├── OverlayService.kt         # الخدمة الأمامية: الطبقة الشفافة + خط الترجمة
│   └── NotificationHelper.kt
├── overlay/
│   ├── StageView.kt              # الطبقة الشفافة: ترسم النصوص المترجمة فقط
│   ├── EngineStatus.kt
│   └── ScreenMetrics.kt
├── capture/
│   ├── ScreenCapturer.kt         # MediaProjection → VirtualDisplay → ImageReader
│   ├── FrameSignature.kt         # بصمة سطوع لكل إطار + قناع لتجاهل مناطق ترجمتنا
│   └── LiveTranslateController.kt# انتظار ← معالجة (عرض تدريجي) ← عرض
├── translate/
│   ├── TranslationEngine.kt      # OCR + إعادة قراءة محسّنة + كشف اللغة + ترجمة على الجهاز
│   ├── TextNormalizer.kt         # تنظيف نص الكوميكس: الحروف الكبيرة، 0/O، 1/I، الكلمات المقسومة
│   ├── BlockMerger.kt            # دمج أسطر فقاعة الكلام الواحدة في جملة واحدة
│   ├── ModelStore.kt             # تنزيل حزم اللغات مسبقاً
│   ├── Languages.kt
│   └── TextPatch.kt
├── tile/TranslateTileService.kt  # زر الإعدادات السريعة (تشغيل/إيقاف)
└── data/Prefs.kt
```

## الطبقة الشفافة ولماذا لا تعيق اللمس

نافذة واحدة بملء الشاشة، `PixelFormat.TRANSLUCENT` بلا أي خلفية، والأعلام
`FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`،
فكل اللمسات تمر للتطبيق تحتها.

> **Android 12+:** يحجب النظام اللمس تحت نوافذ `TYPE_APPLICATION_OVERLAY` إذا تجاوزت عتامتها
> `InputManager.getMaximumObscuringOpacityForTouch()` (0.8). لذلك:
> - مع تفعيل خدمة إمكانية الوصول تُضاف الطبقة كـ `TYPE_ACCESSIBILITY_OVERLAY` (نافذة موثوقة) بعتامة ‎100%‎،
>   فتختفي الكلمات الأصلية تماماً. الخدمة لا تقرأ أي محتوى (`canRetrieveWindowContent=false`).
> - بدونها تُستخدم عتامة 0.8 فيبقى النص الأصلي ظاهراً قليلاً.
> - على Android 13+ للتطبيقات المثبتة من ملف APK: معلومات التطبيق ← ⋮ ← «السماح بالإعدادات المقيّدة» قبل تفعيل الخدمة.

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
- عند بدء الترجمة تُستدعى `startForeground` بالنوع `specialUse | mediaProjection`
  **قبل** `getMediaProjection()` كما يشترط Android 14.

### 4. التقاط الشاشة (MediaProjection)
ليس إذناً في الـ Manifest بل موافقة لكل جلسة: عند الضغط على «بدء الترجمة الفورية» أو زر الإعدادات السريعة يُفتح
`OverlayTrampolineActivity` الشفاف، الذي يستدعي `MediaProjectionManager.createScreenCaptureIntent()`
ويمرّر النتيجة للخدمة. على Android 14+ لا يمكن إعادة استخدام الموافقة، لذا تُطلب من جديد بعد كل إيقاف.

## تشغيل الخدمة

الترجمة تبدأ عبر النشاط الشفاف (لأن موافقة التقاط الشاشة تحتاج نشاطاً):
```kotlin
context.startActivity(OverlayTrampolineActivity.startIntent(context))
```
بعد الموافقة يشغّل النشاط `OverlayService` بالأمر `ACTION_PROJECTION_GRANTED`، والإيقاف بـ `ACTION_STOP`.
الخدمة تعمل فقط أثناء الترجمة، وتبقى عند مغادرة التطبيق والتنقل بين التطبيقات.

## حزم اللغات (بلا تنزيل أثناء الترجمة)

- تُنزَّل حزمتا اللغتين المختارتين **مسبقاً** من شاشة التطبيق فور اختيارهما، ويظهر «حزم اللغات جاهزة».
- المحرك لا ينزّل أي حزمة أثناء الترجمة إطلاقاً.
- في الوضع التلقائي، كشف اللغة على نصوص قصيرة أو بحروف كبيرة كان يخطئ (كرواتية hr، فيتنامية vi…).
  الآن تُقبل اللغة المكتشفة فقط إن كانت حزمتها منزّلة، وإلا يُعامل النص اللاتيني كإنجليزي (الإنجليزية مضمّنة).

## دقة الـ OCR مع الكوميكس والمانجا

1. **دمج الفقاعات:** الأسطر المتمركزة المتقاربة تُضم في جملة واحدة قبل الترجمة، مع إبقاء عناصر القوائم منفصلة.
2. **تنظيف النص:** `G00D`→`GOOD`، `HELL0`→`HELLO`، `|`→`I`، ضم `SOME-/THING`، وتحويل النص المكتوب كله
   بحروف كبيرة إلى حالة الجملة (المترجم يترك "HELLO THERE" دون ترجمة لظنه اختصارات).
3. **إعادة قراءة محسّنة:** الكتل ضعيفة الثقة (أقل من 0.7) تُقص وتُكبَّر ×1.5–3 وتُحوَّل لرمادي عالي التباين ثم تُقرأ مجدداً،
   وتُعتمد القراءة الأعلى ثقة.

## حدود معروفة
- قراءة **العربية** من الشاشة غير مدعومة في ML Kit؛ الترجمة **إلى** العربية مدعومة.
- لا يمكن لتطبيق عادي استبدال الضغط المطوّل على زر الرئيسية؛ البديل زر الإعدادات السريعة.
- موافقة التقاط الشاشة تُطلب في بداية كل جلسة (شرط Android 14+).
- المانجا اليابانية العمودية قد لا تُقرأ بدقة (حدّ في نموذج ML Kit الياباني).

## البناء

```bash
./gradlew :livetranslate:testDebugUnitTest
./gradlew :livetranslate:assembleDebug
```
