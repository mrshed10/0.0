# TiltSensorApp — قارئ الميلان

تطبيق أندرويد (Kotlin) يبدأ تلقائياً عند الفتح بقراءة ميلان الجهاز (يمين/يسار، أعلى/أسفل)
باستخدام حساس التسارع + المغناطيس، مع تنعيم، ويستمر في الخلفية عبر Foreground Service
بدون أي نوافذ عائمة أو مؤشرات على الشاشة.

افتح مجلد `TiltSensorApp` في Android Studio عبر File > Open.

```
TiltSensorApp/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/…
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/tiltsensor/
        │   ├── MainActivity.kt
        │   ├── TiltSensorService.kt
        │   ├── TiltProcessor.kt
        │   └── TiltState.kt
        └── res/
            ├── layout/activity_main.xml
            ├── values/strings.xml
            ├── values/themes.xml
            └── drawable/ic_app.xml, ic_notification.xml
```
