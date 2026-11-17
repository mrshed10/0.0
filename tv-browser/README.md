# متصفح الشاشة — AOSP TV Browser

متصفح إنترنت لأجهزة التلفاز وشاشات AOSP المعدّلة (معالج رباعي النواة، ذاكرة 1GB)،
مبني على **GeckoView** (محرك Firefox) لأن Android WebView **لا يدعم الإضافات إطلاقاً**.

## الميزات

| المطلوب | التنفيذ |
|---|---|
| تثبيت الإضافات (uBlock Origin / Adblock Plus) | WebExtensions كاملة عبر `WebExtensionController` في GeckoView |
| متجر افتراضي في الشريط العلوي | زر 🛒 يفتح متجراً جاهزاً: تثبيت بضغطة واحدة لـ uBlock Origin وABP وAdGuard وSponsorBlock وDark Reader وPrivacy Badger |
| متاجر خارجية + تثبيت يدوي | متاجر AMO وChrome Web Store (Chrome/Brave/Kiwi) وEdge، مع إمكانية إضافة أي متجر. التثبيت من رابط xpi/crx، أو من معرّف Chrome، أو من ملف محلي |
| إضافات Chrome / Brave / Kiwi | تنزيل ملف CRX ثم تحويله إلى XPI (إزالة الترويسة + تعديل `manifest.json`) |
| وضع سطح المكتب دائماً | `USER_AGENT_MODE_DESKTOP` + `VIEWPORT_MODE_DESKTOP` + User-Agent لـ Windows، مع تزييف `navigator.platform` و`maxTouchPoints` |
| التحكم بالصوت | أزرار 🔉 🔇 🔊 في الشريط، ومؤشر صوت على الشاشة، وواجهة `window.tvDevice` لصفحات الويب |
| التحكم بالريموت (D-Pad) | مؤشر ماوس افتراضي بتسارع، ونقر، وتمرير عند حواف الشاشة بعجلة الماوس، وتمرير فوق القوائم (hover) |
| تحسين الذاكرة لـ 1GB | تبويب واحد نشط فقط وتجميد البقية، وعملية محتوى واحدة، وتعطيل Fission وSafe Browsing وbfcache، واستعادة الصفحة تلقائياً إذا قتلها النظام لنقص الذاكرة |

## هيكلة المشروع

```
tv-browser/
├── app/build.gradle.kts          # نسختا المحرك stable / unsigned + ملف APK لكل معمارية + R8
└── app/src/main/
    ├── AndroidManifest.xml       # LEANBACK_LAUNCHER، بانر، largeHeap، لا يتطلب شاشة لمس
    ├── assets/extensions/tvbridge/   # إضافة مدمجة: window.tvDevice + تزييف سطح المكتب
    └── java/com/tvbrowser/aosp/
        ├── TvBrowserApp.kt               # كائنات مفردة كسولة (GeckoRuntime واحد)
        ├── browser/
        │   ├── GeckoRuntimeFactory.kt    # إعدادات المحرك وتفضيلات الذاكرة (YAML)
        │   ├── DesktopMode.kt            # User-Agent وإعدادات الجلسة لسطح المكتب
        │   ├── TabManager.kt             # التبويبات + Navigation/Progress/Content delegates + التجميد
        │   ├── BrowserTab.kt · SessionAttacher.kt
        │   └── PromptHandler.kt          # alert/confirm/prompt/<select>/مصادقة HTTP
        ├── extensions/
        │   ├── ExtensionManager.kt       # تثبيت/تفعيل/إزالة/إعدادات/نافذة الإضافة + الجسر
        │   ├── ExtensionSources.kt       # ⇐ المقابل لـ "WebViewClient المخصص": اعتراض xpi/crx وصفحات المتاجر
        │   ├── CrxConverter.kt           # تحويل CRX2/CRX3 → XPI
        │   ├── ExtensionStores.kt        # المتاجر والكتالوج الافتراضي
        │   └── ExtensionsActivity.kt     # شاشة إدارة الإضافات
        ├── device/
        │   ├── VolumeController.kt       # AudioManager (يعمل عبر HDMI-CEC أيضاً)
        │   └── DeviceBridge.kt           # الطرف الأصلي لـ window.tvDevice
        ├── ui/
        │   ├── MainActivity.kt           # الشريط العلوي، أزرار الريموت، ملء الشاشة، onTrimMemory
        │   ├── CursorController.kt · CursorView.kt   # الماوس الافتراضي
        │   ├── BrowserDialogs.kt         # المتجر والتبويبات والإعدادات
        │   └── PopupHost.kt              # نافذة الإضافة (مثل لوحة uBlock) مع مؤشر
        └── util/Prefs.kt · DeviceProfile.kt
```

## نسختا المحرك (مهم)

| النسخة | المحرك | ما يمكن تثبيته |
|---|---|---|
| `stable` | GeckoView الرسمي | إضافات Firefox الموقّعة من Mozilla: uBlock Origin وABP وAdGuard وغيرها (أي إضافة من AMO) |
| `unsigned` | GeckoView Nightly | كل ما سبق، **بالإضافة إلى** إضافات Chrome/Brave/Kiwi/Edge المحوّلة وغير الموقّعة |

السبب: نسخ GeckoView الرسمية تشترط أن تكون الإضافة موقّعة من Mozilla، وهذا الشرط مدمج في المحرك نفسه.
إضافات Chrome ليست موقّعة من Mozilla، لذلك تحتاج نسخة Nightly التي يمكن فيها تعطيل
`xpinstall.signatures.required`.
ملاحظة: Brave وKiwi يستخدمان متجر Chrome نفسه، لذلك يغطيهما دعم Chrome Web Store.

## واجهة الصوت لصفحات الويب

```js
await tvDevice.volumeUp();      // {level, max, percent, muted, fixed}
await tvDevice.volumeDown();
await tvDevice.toggleMute();
await tvDevice.setVolume(40);   // نسبة مئوية
await tvDevice.getVolume();
// أو من iframe:
window.postMessage({ tvDevice: "volumeUp" }, "*");
```
يمكن إيقاف هذه الواجهة من الإعدادات ("السماح للمواقع بالتحكم بالصوت").

## أزرار الريموت

| الزر | داخل الصفحة | في الشريط العلوي |
|---|---|---|
| الأسهم | تحريك المؤشر (يتسارع مع الضغط المستمر)، والدفع نحو الحافة يمرّر الصفحة | التنقل بين الأزرار |
| OK | نقرة (الضغط المطوّل = سحب) | تنفيذ |
| ↑ أعلى الصفحة / MENU | الانتقال للشريط العلوي | — |
| ↓ | — | العودة للصفحة |
| CH+ / CH- | صفحة لأعلى/لأسفل | — |
| BACK | الخروج من ملء الشاشة ← رجوع في السجل ← إغلاق التبويب ← (مرتين) خروج | العودة للصفحة |

زر 🖱 يوقف المؤشر، فتُرسل الأسهم مباشرة للصفحة (مفيد لمواقع التلفاز مثل youtube.com/tv).

## البناء

```bash
cd tv-browser
./gradlew assembleStableRelease      # النسخة الموصى بها
./gradlew assembleUnsignedRelease    # مع دعم إضافات Chrome
# الناتج: app/build/outputs/apk/<flavor>/release/app-<flavor>-armeabi-v7a-release.apk
```
- معظم أجهزة 1GB تعمل بنظام **32-bit**، لذلك ثبّت ملف `armeabi-v7a`.
- يبني GitHub Actions (`.github/workflows/tv-browser.yml`) ملفات APK تلقائياً.
- نسخة release موقّعة بمفتاح debug **للتجربة فقط**، فاستبدله بمفتاحك قبل النشر.
- لبناء قابل للتكرار ثبّت `geckoviewVersion` في `gradle.properties` على رقم محدد.

## حدود يجب معرفتها

- **الذاكرة:** محرك Gecko يستهلك قرابة 150–250MB. على جهاز 1GB يبقى تبويب واحد فقط حيّاً،
  وبقية التبويبات تُجمَّد ثم تُستعاد عند فتحها (تُعاد تحميلها مع حفظ السجل والتمرير).
- **إضافات Chrome:** معظم الإضافات البسيطة تعمل بعد التحويل، لكن الإضافات التي تعتمد على
  واجهات خاصة بـ Chrome (مثل `offscreen` و`sidePanel` و`declarativeNetRequest` المتقدمة) قد لا تعمل.
  لحجب الإعلانات استخدم نسخ Firefox من uBlock/ABP، فهي الأخف والأقوى.
- **التنزيلات العامة** (غير ملفات الإضافات) غير مدعومة، تقليلاً للحجم والذاكرة.
- **GeckoView minSdk:** إذا كان الإصدار الأحدث من GeckoView يتطلب أندرويد أحدث من جهازك،
  فثبّت إصداراً أقدم عبر `geckoviewVersion`.
