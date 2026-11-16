# القائمة العائمة (FloatingMouseTV)

تطبيق Android TV / TV Box للتحكم الكامل عبر الماوس من خلال أيقونة عائمة تظهر فوق جميع التطبيقات.

- **أيقونة دائرية** صغيرة شبه شفافة، تُسحب بالماوس وتلتصق بحافة الشاشة.
- **لوحة تحكم زجاجية داكنة** عند النقر: رفع/خفض/كتم الصوت، تشغيل/إيقاف، رجوع، الرئيسية، التطبيقات الأخيرة.
- النقر خارج اللوحة يعيدها لأيقونة.
- **الزر الأوسط للماوس = رجوع** (على الأيقونة واللوحة دائماً، وفي كل النظام إذا كان الجهاز يرسله كزر — يكتشفه زر «اختبار» في التطبيق).
- خدمة أمامية بإشعار دائم صامت + START_STICKY + إعادة تشغيل بعد الإقلاع.

## هيكل المشروع
```
FloatingMouseTV/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/tvremote/floating/
        │   ├── MainActivity.kt                (الإعدادات)
        │   ├── FloatingService.kt             (الخدمة الأمامية + الأيقونة + اللوحة)
        │   ├── ControlAccessibilityService.kt (الرجوع/الرئيسية/الأخيرة + الزر الأوسط)
        │   ├── AccessibilityGuard.kt          (إعادة تفعيل إمكانية الوصول تلقائياً)
        │   ├── FloatAction.kt                 (الأزرار وتنفيذها)
        │   ├── Prefs.kt                       (SharedPreferences)
        │   └── BootReceiver.kt                (التشغيل بعد الإقلاع)
        └── res/
            ├── layout/ (activity_main, floating_panel, floating_item, item_action_check)
            ├── xml/accessibility_service_config.xml
            ├── values/ (strings, colors, styles)
            └── drawable/ (الأيقونات والخلفيات الزجاجية)
```

## البناء
- **GitHub Actions:** كل push يبني الـ APK وينشره على:
  https://github.com/mrshed10/0.0/releases/download/floating-mouse-tv-latest/FloatingMouseTV.apk
- **Android Studio:** افتح مجلد `FloatingMouseTV` ← Build ← Build APK(s).
- **سطر الأوامر:** `./gradlew assembleRelease` ← `app/build/outputs/apk/release/app-release.apk`

## أوامر ADB (مرة واحدة، إن لزم)
```
adb shell appops set com.tvremote.floating SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.tvremote.floating android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services com.tvremote.floating/com.tvremote.floating.ControlAccessibilityService
adb shell settings put secure accessibility_enabled 1
```
