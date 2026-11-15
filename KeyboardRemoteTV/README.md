# ريموت الكيبورد (KeyboardRemoteTV)

تطبيق Android TV / TV Box يحوّل لوحة مفاتيح USB عادية إلى ريموت كنترول يعمل في الخلفية داخل جميع التطبيقات عبر `AccessibilityService`.

## المزايا
- **تخصيص كامل للأزرار:** اضغط «إضافة تخصيص جديد»، ثم اضغط أي زر في الكيبورد، واختر وظيفته
  (رفع/خفض/كتم الصوت، الرئيسية، الرجوع، التطبيقات الأخيرة، تشغيل/إيقاف، التالي/السابق، الإشعارات).
- التخصيصات محفوظة في SharedPreferences وتبقى بعد إعادة تشغيل الجهاز، ويمكن تعديلها أو حذفها من الجدول.
- **حماية من الإغلاق:** الخدمة تعمل كخدمة أمامية بإشعار صامت، وتعيد تشغيل نفسها عند إغلاق التطبيق.
- أزرار الكتابة (الحروف، Backspace، المسافة...) تعمل طبيعياً داخل مربعات النص حتى لو كانت مخصّصة.
- الأسهم و Enter وزر الرجوع محجوزة للتنقّل ولا يمكن تخصيصها.

## هيكل المشروع
```
KeyboardRemoteTV/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/tvremote/keyboard/
        │   ├── MainActivity.kt          (الواجهة + نافذة التقاط الزر + الجدول)
        │   ├── KeyboardRemoteService.kt (خدمة إمكانية الوصول + الخدمة الأمامية)
        │   ├── KeyMappingStore.kt       (الحفظ في SharedPreferences)
        │   └── RemoteAction.kt          (قائمة الوظائف)
        └── res/
            ├── layout/activity_main.xml, item_mapping.xml
            ├── xml/accessibility_service_config.xml
            ├── values/ (strings, colors, styles)
            └── drawable/ (أيقونة، بانر، خلفيات الأزرار)
```

## البناء
- **Android Studio:** افتح مجلد `KeyboardRemoteTV` ← Build ← Build APK(s).
- **سطر الأوامر:** `./gradlew assembleDebug` ← الملف: `app/build/outputs/apk/debug/app-debug.apk`
- **GitHub Actions:** كل push يبني الـ APK تلقائياً؛ حمّله من تبويب Actions ← آخر تشغيل لـ *KeyboardRemoteTV build* ← Artifacts.

## التثبيت على الـ TV Box
1. **عبر فلاشة USB:** انسخ الـ APK إلى الفلاشة، وافتحه بمدير ملفات على الجهاز (فعّل «مصادر غير معروفة» إن طُلب).
2. **عبر ADB (الشبكة):** فعّل خيارات المطور ← تصحيح USB/الشبكة، ثم:
   ```
   adb connect 192.168.1.50:5555
   adb install -r app-debug.apk
   ```
3. افتح التطبيق ← «فتح إعدادات إمكانية الوصول» ← فعّل «ريموت الكيبورد».

### إذا لم تظهر شاشة إمكانية الوصول في جهازك
```
adb shell settings put secure enabled_accessibility_services com.tvremote.keyboard/com.tvremote.keyboard.KeyboardRemoteService
adb shell settings put secure accessibility_enabled 1
```
