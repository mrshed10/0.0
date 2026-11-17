# DeepSearch & Bypass Hub

تطبيق Android خفيف (Kotlin + XML Views، بدون Compose) يجمع أربع أدوات في واجهة واحدة نظيفة:

| التبويب | الوظيفة |
|---|---|
| **البحث العكسي** | اختيار/سحب/مشاركة صورة أو فيديو → اختيار إطار من الفيديو → تعديل → رفع متوازٍ إلى Google Lens وYandex وBing وTinEye وSauceNAO، ثم استخراج روابط الصور/الفيديو الأصلية من النتائج وتحميلها |
| **المحرر** | قص حر بالسحب، تدوير ±90°، قلب أفقي، سطوع/تباين/تشبع بمعاينة حيّة |
| **المتصفح** | WebView مع DNS-over-HTTPS، حجب إعلانات بقوائم hosts، بروكسي (SOCKS/HTTP مثل Orbot)، وضع سطح المكتب، مسح البيانات، تنزيل الوسائط، ضغطة مطوّلة على أي صورة ← بحث عكسي |
| **DNS الآمن** | 5 مزوّدين + مخصص، اختبار زمن الاستجابة، تطبيق على الجهاز كله عبر VPN محلي يمرر DNS فقط، أو على المتصفح فقط، أو عبر Private DNS النظامي |

## البناء

```bash
./gradlew :deepsearch:assembleDebug        # APK: deepsearch/build/outputs/apk/debug/
./gradlew :deepsearch:testDebugUnitTest
```
يبني GitHub Actions الـ APK تلقائياً مع كل push (Artifact باسم `deepsearch-hub-debug-apk`).

## الهيكلة

```
com.deepsearch.hub/
├── HubApp.kt, Prefs.kt          # تهيئة التطبيق والإعدادات
├── ui/
│   ├── MainActivity.kt          # BottomNavigation + 3 Fragments (show/hide للحفاظ على حالة المتصفح)
│   ├── SearchFragment.kt        # الاختيار، السحب والإفلات، إطار الفيديو، اختيار المحركات
│   ├── BrowserFragment.kt       # المتصفح الداخلي
│   ├── DnsFragment.kt           # إعدادات DNS وحجب الإعلانات
│   ├── LinkSheet.kt / LinkActions.kt  # عرض الروابط المستخرجة وإجراءاتها
├── search/
│   ├── SearchEngines.kt         # تعريف المحركات + صفحة الرفع التلقائي (multipart)
│   ├── SearchResultsActivity.kt # تبويب WebView لكل محرك
│   ├── SearchViewModel.kt, QuickSearch.kt
├── editor/  EditorActivity, CropImageView, ColorAdjust
├── browser/ AdBlocker, DohFetcher, HubWebViewClient, ProxyHelper, Downloader, MediaLinkExtractor
├── dns/     DohProvider(s), DohClient, DnsVpnService, IpPacket
└── util/    MediaUtils (تحميل الصور، إطارات الفيديو، EXIF)
```

## كيف يعمل كل جزء

**البحث العكسي بدون مفاتيح API:** لكل محرك نُنشئ صفحة HTML محلية فيها نموذج `multipart/form-data`
يطابق نموذج الرفع الرسمي للمحرك، نعبّئ حقل الملف ببيانات الصورة عبر `DataTransfer` ثم نرسله.
النتيجة هي صفحة النتائج الحقيقية للمحرك داخل تبويب. إذا غيّر محرك ما واجهة الرفع، استخدم
"رفع يدوي" من القائمة: يفتح صفحة المحرك وأي زر "رفع صورة" يستلم صورتك تلقائياً (`onShowFileChooser`).
زر 🔗 يشغّل `extract_links.js` الذي يجمع الفيديو/الصور/الصفحات، بما فيها الروابط الأصلية المغلّفة
في نتائج المحركات (`img_url`، `imgurl`، `mediaurl`...).

**VPN-DNS:** `DnsVpnService` يعلن خادم DNS وهمياً `10.111.222.2` ويوجّه هذا العنوان فقط إلى واجهة TUN.
كل استعلام UDP/53 يُرسل كما هو (wire format) إلى خادم DoH عبر `POST application/dns-message` (RFC 8484)
ويُعاد الرد. خادم DoH نفسه يُحل من عناوين bootstrap ثابتة فلا توجد حلقة. لا تمر أي بيانات أخرى عبر الـ VPN.

**DoH داخل المتصفح:** عند إيقاف VPN-DNS، `HubWebViewClient.shouldInterceptRequest` يجلب طلبات GET عبر
OkHttp مع `DnsOverHttps` ويزامن الكوكيز مع `CookieManager`. طلبات POST تبقى على مسار WebView الأصلي.

## حدود يجب معرفتها

- DoH يتجاوز الحجب **القائم على DNS** فقط. إذا كان الحجب على مستوى IP أو SNI فاستخدم خيار
  **البروكسي** في المتصفح (مثلاً `socks5://127.0.0.1:9050` مع Orbot).
- الـ VPN المحلي يلتقط IPv4 فقط؛ على شبكات IPv6 فقط يُنصح باستخدام Private DNS النظامي.
- المحركات قد تطلب CAPTCHA أو تغيّر نماذج الرفع؛ "رفع يدوي" هو الاحتياطي.
- تنزيل روابط `blob:` و`m3u8` (البث المقطّع) غير مدعوم بـ DownloadManager.
