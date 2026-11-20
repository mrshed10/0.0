# كاميرا المراقبة — تخزين محلي فقط

تطبيق أندرويد (Kotlin + CameraX) يحوّل الجوال إلى كاميرا مراقبة تسجّل بشكل متواصل
على ذاكرة الجهاز فقط: **لا حسابات، لا إنترنت (لا يوجد إذن INTERNET أصلاً)، لا سحابة.**

## فتح المشروع
Android Studio ← **File ▸ Open** ← اختر مجلد `SurveillanceCam` (وليس جذر المستودع) ← انتظر Gradle Sync ← Run.
الحد الأدنى: Android 8.0 (API 26).

## خطة الملفات

```
SurveillanceCam/
├── settings.gradle.kts            إعداد المشروع والمستودعات
├── build.gradle.kts               إصدارات الإضافات (AGP + Kotlin)
├── gradle.properties
└── app/
    ├── build.gradle.kts           المكتبات: CameraX, Material, Lifecycle
    └── src/main/
        ├── AndroidManifest.xml    الأذونات + الشاشات + الخدمة الأمامية (camera|microphone)
        ├── java/com/localcam/surveillance/
        │   ├── MainActivity.kt       الشاشة الرئيسية: المعاينة، زر التسجيل، الشاشة السوداء
        │   ├── RecordingService.kt   الخدمة الأمامية: تسجيل مقسّم + WakeLock + إشعار ثابت
        │   ├── ClipStorage.kt        المجلد والتسمية والحذف التلقائي للأقدم غير المقفل
        │   ├── AppSettings.kt        الإعدادات + قائمة المقاطع المقفلة (SharedPreferences)
        │   ├── RecordingState.kt     حالة التسجيل المشتركة (StateFlow)
        │   ├── ThermalMonitor.kt     مراقبة الحرارة (تنبيه فقط بلا إيقاف)
        │   ├── BatteryHelper.kt      إرشاد ضبط البطارية على «غير مقيدة»
        │   ├── ClipsActivity.kt      قائمة المقاطع (الأحدث أولاً)
        │   ├── ClipsAdapter.kt       عنصر القائمة: تشغيل / قفل / حذف
        │   ├── PlayerActivity.kt     مشغل داخلي (VideoView)
        │   └── SettingsActivity.kt   الإعدادات
        └── res/
            ├── layout/  activity_main, activity_clips, item_clip, activity_settings, activity_player
            ├── values/  strings (عربي), colors, themes
            └── drawable/ أيقونات Vector
```

## مكان حفظ المقاطع
`Android/data/com.localcam.surveillance/files/Movies/Recordings/2026-11-20_16-05-00.mp4`

مجلد خاص بالتطبيق: لا يحتاج إذن تخزين، **ويُحذف عند إلغاء تثبيت التطبيق** — انسخ المقاطع المهمة قبل الإلغاء
(عبر USB إلى الكمبيوتر أو مدير الملفات).

## ملاحظات تشغيلية
- اضبط البطارية على «غير مقيدة» (زر في الإعدادات)، وأبقِ الجوال على الشاحن للمراقبة الطويلة.
- عند ارتفاع الحرارة يظهر تنبيه فقط ويستمر التسجيل؛ لكن لو وصل الجهاز لحد الإغلاق الحراري
  فالنظام نفسه قد يغلق الكاميرا — حينها يعيد التطبيق المحاولة تلقائياً كل بضع ثوانٍ.
- إن أخذ تطبيق آخر الكاميرا (مكالمة فيديو مثلاً) يتوقف المقطع ويُعاد ربط الكاميرا تلقائياً بعد تحريرها.
