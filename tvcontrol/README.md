# لوحة تحكم الشاشة (TV Control Panel)

تطبيق Android TV / AOSP 13 بواجهة مناسبة للريموت (D-pad) للوصول إلى إعدادات النظام المخفية.

## الأزرار

| الزر | ماذا يفعل |
|---|---|
| **مشغّل الأنشطة** | مستعرض مدمج يعرض كل التطبيقات التي تحتوي أنشطة (Activities) قابلة للفتح — حزم الإعدادات أولًا — ثم يفتح أي نشاط بزر OK. |
| **إعدادات أندرويد** | يفتح `com.android.settings/.Settings` مباشرة، مع بدائل (`ACTION_SETTINGS`، ثم إعدادات التلفاز). |
| **خيارات المطور** | `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` مع بدائل صريحة. إن كانت معطّلة يشرح كيف تُفعَّل ويفتح "حول". |
| **تصحيح أخطاء USB** | يعرض الحالة الحالية. بدون صلاحية: يفتح خيارات المطور عند خيار `enable_adb`. مع صلاحية `WRITE_SECURE_SETTINGS`: يشغّل/يوقف تصحيح USB والتصحيح اللاسلكي بضغطة واحدة. |
| **حول الجهاز** | لتفعيل وضع المطور (7 ضغطات على "الإصدار"). |
| **اختصارات النظام** | ~20 شاشة: الواي فاي، كل التطبيقات، المصادر غير المعروفة، تطبيق الشاشة الرئيسية، اللغة، التخزين، قائمة الاختبار، System UI Tuner… |

## البناء

**من Android Studio:** افتح مجلد المستودع، اختر الإعداد `tvcontrol`، ثم *Build → Build APK(s)*.
الناتج: `tvcontrol/build/outputs/apk/debug/tvcontrol-debug.apk`

**من سطر الأوامر:**
```bash
./gradlew :tvcontrol:assembleDebug
```

**من GitHub Actions:** كل push يبني الـ APK ويرفعه كـ Artifact باسم `tv-control-panel-debug-apk`.

## التثبيت على الشاشة

1. انسخ الـ APK إلى فلاشة وثبّته بمدير ملفات، أو عبر الشبكة:
   ```bash
   adb connect <IP-الشاشة>:5555
   adb install -r tvcontrol-debug.apk
   ```
2. يظهر التطبيق في قائمة التطبيقات (Leanback banner) وفي مشغّلات AOSP العادية.

## (اختياري) التبديل الفوري لتصحيح USB

أندرويد لا يسمح لأي تطبيق عادي بتغيير `adb_enabled`. بعد تفعيل التصحيح مرة واحدة يدويًا، نفّذ من الكمبيوتر:
```bash
adb shell pm grant com.tvtools.controlpanel android.permission.WRITE_SECURE_SETTINGS
```
بعدها يصبح زر "تصحيح أخطاء USB" مفتاح تشغيل/إيقاف فوري (مفيد إن كانت الشركة تُطفئه بعد كل إعادة تشغيل).

## ملاحظات

- إذا حذفت الشركة المصنّعة شاشة ما من النظام، يظهر تنبيه "غير متوفرة" بدل أن يتعطل التطبيق.
- `QUERY_ALL_PACKAGES` مطلوبة ليرى مستعرض الأنشطة كل التطبيقات على Android 11+.
- لجعله مشغّل الشاشة الرئيسي (Home) أضف `<category android:name="android.intent.category.HOME" />` و`DEFAULT` إلى `MainActivity` — غير مفعّل افتراضيًا لتجنّب استبدال واجهة الشاشة عن طريق الخطأ.
