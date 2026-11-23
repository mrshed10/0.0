// تشويش طفيف وثابت داخل الجلسة لقراءات Canvas (toDataURL / toBlob / getImageData)
// لتقليل التتبع بالبصمة الرقمية. يعمل في سياق Gecko المعزول عبر wrappedJSObject + exportFunction.
(function () {
  const host = location.hostname.replace(/^www\./, "");
  let enabled = true; // افتراضياً مفعّل حتى تصل الإعدادات (أكثر أماناً)
  let seed = 1;

  browser.storage.local.get(["cfg", "seed"]).then(({ cfg, seed: s }) => {
    if (s !== undefined) seed = s;
    if (cfg) {
      const ex = cfg.exceptions || [];
      const excepted = ex.some((d) => host === d || host.endsWith("." + d));
      enabled = !!cfg.blockCanvas && !excepted;
    }
  });

  const page = window.wrappedJSObject;
  if (!page || !page.HTMLCanvasElement) return;

  function prng(n) {
    // xorshift بسيط مبني على البذرة + المضيف
    let x = (seed ^ n) >>> 0 || 1;
    x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
    return x >>> 0;
  }
  let hostHash = 0;
  for (let i = 0; i < host.length; i++) hostHash = (hostHash * 31 + host.charCodeAt(i)) | 0;

  function noise(data) {
    // نغيّر البت الأدنى لعدد محدود من البكسلات: غير مرئي بصرياً لكنه يغيّر البصمة
    const len = data.length;
    const step = Math.max(4, Math.floor(len / 512) * 4);
    for (let i = 0; i < len; i += step) {
      const r = prng(i ^ hostHash);
      data[i + (r % 3)] ^= 1;
    }
  }

  function noisyCopy(canvas) {
    const w = canvas.width, h = canvas.height;
    const copy = document.createElement("canvas");
    copy.width = w; copy.height = h;
    if (!w || !h) return copy;
    const ctx = copy.getContext("2d");
    ctx.drawImage(canvas, 0, 0);
    const img = ctx.getImageData(0, 0, w, h);
    noise(img.data);
    ctx.putImageData(img, 0, 0);
    return copy;
  }

  const canvasProto = page.HTMLCanvasElement.prototype;
  const origToDataURL = canvasProto.toDataURL;
  const origToBlob = canvasProto.toBlob;
  const ctxProto = page.CanvasRenderingContext2D.prototype;
  const origGetImageData = ctxProto.getImageData;

  canvasProto.toDataURL = exportFunction(function (...args) {
    if (!enabled) return origToDataURL.apply(this, args);
    try { return noisyCopy(this).toDataURL(...args); } catch (e) { return origToDataURL.apply(this, args); }
  }, page);

  canvasProto.toBlob = exportFunction(function (cb, ...args) {
    if (!enabled) return origToBlob.call(this, cb, ...args);
    try { return noisyCopy(this).toBlob(exportFunction((b) => cb(b), page), ...args); }
    catch (e) { return origToBlob.call(this, cb, ...args); }
  }, page);

  ctxProto.getImageData = exportFunction(function (...args) {
    const img = origGetImageData.apply(this, args);
    if (enabled) { try { noise(img.data); } catch (e) {} }
    return img;
  }, page);
})();
