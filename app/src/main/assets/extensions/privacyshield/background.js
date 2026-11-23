// يستقبل الإعدادات من التطبيق (Kotlin) ويطبقها: WebRTC + تخزين إعدادات Canvas للمحتوى.
const port = browser.runtime.connectNative("safar_privacy");

async function ensureSeed() {
  const { seed } = await browser.storage.local.get("seed");
  if (seed === undefined) {
    // بذرة جديدة لكل تشغيل: الضجيج ثابت داخل الجلسة (لا يمكن كشفه بالتكرار) ومختلف بين الجلسات
    await browser.storage.local.set({ seed: Math.floor(Math.random() * 2 ** 31) });
  }
}

port.onMessage.addListener(async (cfg) => {
  await ensureSeed();
  await browser.storage.local.set({ cfg });
  try {
    await browser.privacy.network.webRTCIPHandlingPolicy.set({
      value: cfg.hideLocalIp ? "default_public_interface_only" : "default",
    });
  } catch (e) {
    port.postMessage({ type: "warn", message: "webRTCIPHandlingPolicy unsupported: " + e });
  }
});

browser.storage.local.remove("seed").then(ensureSeed);
