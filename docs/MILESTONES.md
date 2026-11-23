# خطة المراحل التفصيلية

كل مرحلة تنتهي بنسخة تعمل ويمكن اختبارها. الملفات المذكورة موجودة في المستودع.

## M1 — MVP قابل للتشغيل
**الهدف:** متصفح يعمل بتبويبات وشريط عنوان وإعداد أولي.
- `engine/GeckoProvider.kt`: إنشاء GeckoRuntime واحد.
- `engine/TabManager.kt` و`BrowserTab.kt`: إنشاء التبويبات وإغلاقها والتبديل بينها، والتبويبات المتخفية، وفتح النوافذ الجديدة (`onNewSession`)، وتمرير الروابط الخارجية (`intent:` و`tel:` و`mailto:`) إلى تطبيقاتها.
- `engine/BasicPromptDelegate.kt`: alert وconfirm وprompt و`<select>`.
- `ui/onboarding`: محرك البحث، وحظر الإعلانات، ومكان الشريط.
- `ui/browser/AddressBar.kt`: البحث أو الرابط (`UrlUtils`).
- `data/SessionStore`: استعادة التبويبات باستخدام `SessionState`.

**القبول:** تصفح عادي، وتبويبات متعددة، واستعادة الجلسة بعد إغلاق التطبيق، وعمل قوائم `<select>` وتنبيهات المواقع.

## M2 — الخصوصية والأمان
- إعدادات Gecko الأصلية: HTTPS-Only، وETP Strict، وحظر AD وCRYPTOMINING وFINGERPRINTING، والكوكيز (`ACCEPT_FIRST_PARTY`، أو `…_AND_ISOLATE_OTHERS` عند السماح)، وFPP.
- إضافة `privacyshield`:
  - `privacy.network.webRTCIPHandlingPolicy = default_public_interface_only` لإخفاء IP المحلي.
  - `canvas.js`: ضجيج ثابت داخل الجلسة لـ `toDataURL` و`toBlob` و`getImageData`، مع احترام الاستثناءات.
- استثناء المواقع: `PERMISSION_TRACKING` عبر `StorageController.setPermission`، مع تعطيل تشويش Canvas للنطاق نفسه.

**القبول:** اجتياز اختبارات EFF Cover Your Tracks وBrowserLeaks، وأن يعمل الاستثناء فوراً بعد إعادة التحميل.

## M3 — تخصيص الواجهة والإيماءات
- `ToolbarCustomizeScreen`: اختيار حتى 7 أزرار وترتيبها (القائمة إلزامية).
- `VerticalTabsPanel`: تبويبات عمودية جانبية.
- `Gestures.kt`: السحب الأفقي لتبديل التبويبات أو للرجوع والتقدم، والسحب للأعلى لفتح المبدّل.
- ثيم بنفسجي للتصفح المتخفي، وألوان ديناميكية.

## M4 — نظام الإضافات
- `ExtensionManager`: `install(xpi)`، و`list`، و`enable` و`disable`، و`uninstall`، و`ActionDelegate` لأيقونة الزر وعنوانه، و`onOpenPopup` لعرض النافذة في لوح سفلي.
- نافذة تأكيد الأذونات قبل التثبيت (`onInstallPromptRequest`).
- حفظ مصدر كل إضافة في الإعدادات لإعادة تثبيتها عند الاستعادة.

**المرحلة التالية لهذا الجزء:** مزامنة الإضافات مع تحديثات AMO عبر `WebExtensionController.update`، وصفحة إعدادات كل إضافة (`optionsPageUrl`).

## M5 — الترجمة الحية: النصوص
- `content.js` (وضع TEXT): `IntersectionObserver` و`MutationObserver`، وإرسال الدفعات، واستعادة الأصل عند الإيقاف.
- `ProviderRouter` + 5 مزودين + `TranslationCache`.
- `TranslatePanel`: التشغيل والإيقاف، وطريقة العرض، ونوع المحتوى، واللغتان، والمصدر.

**قياسات الأداء المستهدفة:** ظهور أول ترجمة في أقل من 800ms مع Google أو على الجهاز لصفحة نموذجية، وألا تُرسل طلبات للنص غير المرئي.

## M6 — الترجمة الحية: الصور والمانغا والفيديو
- `ImagePreprocessor` ← `OcrRouter` (ML Kit أو Tesseract) ← `OcrPostProcessor`.
- MANGA: لقطة بعد توقف التمرير، وإخفاء الترجمات السابقة قبل الالتقاط، ومربعات تتحرك مع الصفحة.
- VIDEO: ترجمة مسارات VTT، أو OCR للترجمة المحروقة كل 1.2 ثانية مع تجاهل الإطارات المكررة وتمرير سياق الحوار.
- `FloatingGlassBox`: ترجمة لمرة واحدة أو مباشرة لمنطقة محددة.

**تحسينات مقترحة لاحقاً:** كشف الفقاعات بنموذج TFLite صغير قبل OCR، وإعادة رسم النص داخل الفقاعة بعد تبييض خلفيتها (inpainting)، وتجميع عدة صفحات مانغا في طلب واحد للنموذج لتحسين اتساق السياق.

## M7 — الوسائط والتحميل والنسخ الاحتياطي
- `MediaPlaybackService` بنوع `mediaPlayback` مع `MediaStyle`.
- `DownloadHandler`: DownloadManager مع التحكم بالإشعارات، أو مدير تحميل خارجي.
- `BackupCrypto` و`BackupManager` عبر Storage Access Framework.
- `RoleManager.ROLE_BROWSER` لتعيين المتصفح كافتراضي.

## M8 — الصقل (مخطط)
- منتقي الملفات والتاريخ والألوان في `PromptDelegate`، وأذونات الكاميرا والميكروفون والموقع (`PermissionDelegate`).
- البحث داخل الصفحة (`SessionFinder`)، ووضع سطح المكتب، واقتراحات البحث، والسجل.
- تنزيل روابط `blob:` و`data:` عبر قراءة `WebResponse.body` مباشرة.
- اختبارات واجهة (Compose UI Test) واختبارات أداء للإقلاع (Macrobenchmark + Baseline Profile).
