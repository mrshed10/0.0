# أثر — Athar

تطبيق أندرويد (Kotlin + Jetpack Compose) يجمع الإنتاجية وأسلوب الحياة والروحانيات بتصميم زجاجي
(Glassmorphism)، ويركّز على الويدجت. موجّه لهواتف سامسونج ويعمل على Android 8.0 فأحدث (minSdk 26).

## الحالة الحالية (الخطوة 2)

- نظام التصميم الزجاجي: خلفية "أورورا" متحركة، وضبابية حقيقية عبر مكتبة Haze (Android 12+، وتلوين بديل لما قبله)،
  و`GlassStyle` قابل للتخصيص (الشفافية، الضبابية، اللون، الحواف).
- الخطوط: Tajawal للواجهة، وAmiri للآيات والتاريخ الهجري.
- اللغتان: العربية (RTL) والإنجليزية، وتتبع لغة الجهاز أو إعداد اللغة لكل تطبيق في Android 13+.
- الشاشة الرئيسية: التاريخ الميلادي والهجري (أم القرى)، الصلاة القادمة (أوقات تجريبية)، عدّاد الراتب
  (يوم 27، ويُقدَّم إلى الخميس إذا وافق الجمعة أو السبت)، السبحة، العدادات التنازلية والتصاعدية،
  مهام اليوم، العادات وسلاسل الأيام (Streak)، ملخص الديون، آية اليوم.
- البيانات حالياً تجريبية في الذاكرة (`SampleData`)، وتُستبدل بقاعدة Room في الخطوات القادمة.

## الهيكلة

```
com.athar.app
├── MainActivity.kt
├── navigation/AtharApp.kt          # الهيكل: الخلفية + التبويب الحالي + الشريط الزجاجي
├── core/
│   ├── designsystem/
│   │   ├── theme/                   # الألوان، الخطوط، الثيم
│   │   ├── glass/                   # GlassStyle، GlassCard، AuroraBackground، GlassBottomBar
│   │   └── component/               # CardHeader، GlassChip، GlassProgressBar، GlassIconButton
│   ├── calendar/HijriCalendar.kt    # تحويل أم القرى
│   ├── format/Formatters.kt         # الأرقام، التواريخ، المدد
│   └── ui/UiText.kt
└── feature/
    ├── home/                        # الشاشة الرئيسية + ViewModel + البطاقات
    ├── salary/SalarySchedule.kt     # قواعد موعد الراتب
    ├── prayer/Prayer.kt             # نماذج الصلاة والصلاة القادمة
    └── placeholder/                 # شاشات الأقسام القادمة
```

## البناء

```bash
./gradlew testDebugUnitTest   # اختبارات التقويم والرواتب والصلاة
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

يبني GitHub Actions ملف APK مع كل push (`.github/workflows/android.yml`).

## الخطوات القادمة

1. قاعدة البيانات (Room) وHilt، وشاشات العدادات والمهام والعادات والديون.
2. الويدجت (Glance): تثبيت أي عنصر، وشطب المهام من الشاشة الرئيسية، ومحرّر التخصيص.
3. مواقيت الصلاة الحقيقية (adhan2 + الموقع) والأذان والتنبيهات بالنغمات.
4. استيراد الخطوط، والنسخ الاحتياطي، وقفل الديون بالبصمة.

## التراخيص

الخطوط Tajawal وAmiri مرخّصة بـ SIL Open Font License (انظر `licenses/`).
