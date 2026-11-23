/*
 * Safar Live Translate — سكربت المحتوى.
 * يتواصل مع التطبيق عبر Native Messaging (اسم التطبيق: "safar").
 * أوضاع: TEXT (استبدال نصوص DOM المرئية فقط) / MANGA (OCR للقطة الشاشة) / VIDEO (VTT أو OCR).
 */
(() => {
  if (window.__safarLT) return;
  window.__safarLT = true;

  const APP = "safar";
  const isTop = window === window.top;
  let cfg = { enabled: false, mode: "CONTINUOUS", kind: "TEXT" };
  let port;
  try { port = browser.runtime.connectNative(APP); } catch (e) { return; }

  const send = (msg) => browser.runtime.sendNativeMessage(APP, msg);
  const continuous = () => cfg.enabled && cfg.mode === "CONTINUOUS";
  const raf2 = () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));

  port.onMessage.addListener((msg) => {
    if (msg.cmd === "config") applyConfig(msg);
    else if (msg.cmd === "rectText" && isTop) replyRectText(msg);
    else if (msg.cmd === "overlays" && isTop) placeBoxes(msg.boxes || [], scrollX, scrollY, false);
  });

  function reportViewport() { if (isTop) port.postMessage({ type: "viewport", innerWidth: window.innerWidth }); }
  reportViewport();
  window.addEventListener("resize", reportViewport, { passive: true });

  function applyConfig(next) {
    const prev = cfg;
    cfg = next;
    const restartKey = (c) => [c.enabled, c.mode, c.kind, c.target, c.source].join("|");
    if (restartKey(prev) !== restartKey(cfg)) {
      stopAll();
      if (cfg.enabled && cfg.mode === "CONTINUOUS") {
        if (cfg.kind === "TEXT") startText();
        else if (cfg.kind === "MANGA" && isTop) startManga();
        else if (cfg.kind === "VIDEO") startVideo();
      }
    }
    // تغيّر النمط/الشفافية/الإظهار: إعادة رسم فورية بدون إعادة OCR
    if (prev.style !== cfg.style || prev.opacity !== cfg.opacity || prev.visible !== cfg.visible || prev.rtl !== cfg.rtl) restyleAll();
  }

  function stopAll() { stopText(); stopManga(); stopVideo(); }

  /* ============================== نصوص ============================== */
  const SKIP = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "CODE", "PRE", "TEXTAREA", "INPUT", "SELECT",
    "OPTION", "SVG", "MATH", "KBD", "SAMP", "VAR", "CANVAS", "IFRAME"]);
  const originals = new Map();     // textNode -> النص الأصلي
  const written = new WeakMap();   // textNode -> النص الذي كتبناه (لتجاهل طفراتنا)
  const queued = new WeakSet();
  const attempts = new WeakMap(); // عدد محاولات الترجمة الفاشلة لكل عقدة
  let retryTimer = null;
  const observedEls = new WeakSet();
  let pending = [];
  let io = null, mo = null, flushTimer = null, inFlight = 0;
  const MAX_INFLIGHT = 2, MAX_SEGMENTS = 40, MAX_CHARS = 4500;

  function eligibleText(node) {
    const t = node.nodeValue;
    if (!t || t.trim().length < 2) return false;
    if (/^[\s\d\p{P}\p{S}]+$/u.test(t)) return false;
    for (let el = node.parentElement; el; el = el.parentElement) {
      if (SKIP.has(el.tagName) || el.isContentEditable) return false;
      if (el.getAttribute("translate") === "no" || el.classList.contains("notranslate")) return false;
      if (el.classList.contains("safar-lt-layer") || el.classList.contains("safar-lt-sub")) return false;
    }
    return true;
  }

  function observeTextParents(root) {
    if (!root) return;
    if (root.nodeType === Node.TEXT_NODE) {
      if (eligibleText(root) && root.parentElement) observeEl(root.parentElement);
      return;
    }
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = walker.nextNode())) {
      if (n.parentElement && !observedEls.has(n.parentElement) && eligibleText(n)) observeEl(n.parentElement);
    }
  }

  function observeEl(el) {
    if (observedEls.has(el)) return;
    observedEls.add(el);
    io.observe(el);
  }

  // نترجم فقط ما يقترب من الشاشة => أسرع استجابة وأقل استهلاكاً
  function onIntersect(entries) {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      io.unobserve(e.target);
      observedEls.delete(e.target);
      for (const child of e.target.childNodes) {
        if (child.nodeType === Node.TEXT_NODE && !queued.has(child) && !written.has(child) && eligibleText(child)) {
          queued.add(child);
          pending.push(child);
        }
      }
    }
    scheduleFlush();
  }

  function scheduleFlush(delay = 120) {
    if (flushTimer) return;
    flushTimer = setTimeout(() => { flushTimer = null; flush(); }, delay);
  }

  async function flush() {
    if (!continuous() || cfg.kind !== "TEXT") return;
    while (pending.length && inFlight < MAX_INFLIGHT) {
      const batch = []; let chars = 0;
      while (pending.length && batch.length < MAX_SEGMENTS && chars < MAX_CHARS) {
        const n = pending.shift();
        if (!n.isConnected) { queued.delete(n); continue; }
        batch.push(n); chars += n.nodeValue.length;
      }
      if (!batch.length) break;
      inFlight++;
      translateBatch(batch).finally(() => { inFlight--; if (pending.length) scheduleFlush(0); });
    }
  }

  // عند الفشل لا نُسقط العقد: نعيد المحاولة بعد مهلة متزايدة (حتى 3 مرات)
  function retryLater(nodes) {
    for (const n of nodes) {
      queued.delete(n);
      const a = (attempts.get(n) || 0) + 1;
      attempts.set(n, a);
      if (a <= 3 && n.isConnected) { queued.add(n); pending.push(n); }
    }
    if (!retryTimer) retryTimer = setTimeout(() => { retryTimer = null; scheduleFlush(0); }, 2500);
  }

  async function translateBatch(nodes) {
    const texts = nodes.map((n) => n.nodeValue.trim());
    let res;
    try { res = await send({ type: "translate", kind: "TEXT", texts, title: document.title }); }
    catch (e) { retryLater(nodes); return; }
    if (!res || res.error || !res.translations) { retryLater(nodes); return; }
    if (!continuous() || cfg.kind !== "TEXT") return;
    nodes.forEach((n, i) => {
      queued.delete(n);
      const tr = res.translations[i];
      const cur = n.nodeValue;
      if (!tr || cur.trim() !== texts[i]) return; // تغيّر النص أثناء الترجمة
      const lead = cur.match(/^\s*/)[0], trail = cur.match(/\s*$/)[0];
      if (!originals.has(n)) originals.set(n, cur);
      const out = lead + tr + trail;
      written.set(n, out);
      n.nodeValue = out;
      const p = n.parentElement;
      if (p && !p.hasAttribute("dir")) p.setAttribute("dir", "auto");
    });
  }

  function onMutations(muts) {
    for (const m of muts) {
      if (m.type === "characterData") {
        const n = m.target;
        if (written.get(n) === n.nodeValue) continue; // تعديلنا نحن
        written.delete(n); originals.delete(n);
        if (n.parentElement) observeEl(n.parentElement);
      } else {
        m.addedNodes.forEach((n) => {
          if (n.nodeType === Node.ELEMENT_NODE && n.classList && n.classList.contains("safar-lt-layer")) return;
          observeTextParents(n);
        });
      }
    }
  }

  function startText() {
    io = new IntersectionObserver(onIntersect, { rootMargin: "300px 0px" });
    mo = new MutationObserver(onMutations);
    observeTextParents(document.body);
    mo.observe(document.body, { childList: true, subtree: true, characterData: true });
  }

  function stopText() {
    if (io) io.disconnect(); if (mo) mo.disconnect();
    io = mo = null; pending = [];
    if (flushTimer) { clearTimeout(flushTimer); flushTimer = null; }
    if (retryTimer) { clearTimeout(retryTimer); retryTimer = null; }
    for (const [n, orig] of originals) { if (n.isConnected && written.get(n) === n.nodeValue) n.nodeValue = orig; }
    originals.clear();
  }

  /* ============================== مانغا / صور ============================== */
  let layer = null, scrollTimer = null, mangaOn = false, capturing = false, lastHref = location.href;
  const doneBands = new Map(); // band -> عدد المحاولات/النجاح
  const rendered = [];         // بيانات الفقاعات المرسومة لإعادة الرسم عند تغيير النمط

  function ensureLayer() {
    if (layer && layer.isConnected) return layer;
    layer = document.createElement("div");
    layer.className = "safar-lt-layer";
    document.documentElement.appendChild(layer);
    applyLayerVisibility();
    return layer;
  }

  function applyLayerVisibility() { if (layer) layer.style.display = cfg.visible === false ? "none" : ""; }

  function hasImagesInView() {
    const vh = innerHeight, vw = innerWidth;
    for (const el of document.querySelectorAll("img, canvas, picture, svg image, [style*='background-image']")) {
      const r = el.getBoundingClientRect();
      if (r.width > 100 && r.height > 100 && r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw) return true;
    }
    return false;
  }

  function onScroll() {
    if (location.href !== lastHref) { lastHref = location.href; clearOverlays(); } // تنقل داخل SPA
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(captureViewport, 450); // بعد توقف التمرير
  }

  function onImageLoad(e) {
    if (mangaOn && e.target && e.target.tagName === "IMG") { doneBands.clear(); onScroll(); }
  }

  async function captureViewport() {
    if (!mangaOn || document.visibilityState !== "visible") return;
    if (capturing) { onScroll(); return; } // لقطة جارية: أعد الجدولة بدلاً من التجاهل
    if (!hasImagesInView()) return;
    const band = Math.round(scrollY / 40) + ":" + Math.round(scrollX / 40) + ":" + innerWidth;
    const st = doneBands.get(band);
    if (st === "ok" || (typeof st === "number" && st >= 3)) return;
    capturing = true;
    const l = ensureLayer();
    l.style.visibility = "hidden"; // لا نريد أن يقرأ OCR ترجماتنا السابقة
    await raf2();
    const sy = scrollY, sx = scrollX;
    let res = null;
    try { res = await send({ type: "captureViewport", innerWidth, innerHeight }); } catch (e) { res = null; }
    l.style.visibility = "visible";
    capturing = false;
    if (!mangaOn) return;
    if (!res || res.error) {
      // فشل (شبكة، نموذج قيد التنزيل...) ⇒ إعادة محاولة لاحقاً بدل تعليق الصفحة بلا نتيجة
      doneBands.set(band, (typeof st === "number" ? st : 0) + 1);
      setTimeout(onScroll, 3000);
      return;
    }
    doneBands.set(band, "ok");
    placeBoxes(res.boxes || [], sx, sy, true);
  }

  /** يرسم مجموعة فقاعات بإحداثيات نسبة للشاشة (CSS px) مع إزاحة التمرير. */
  function placeBoxes(boxes, sx, sy, replaceViewport) {
    const l = ensureLayer();
    if (replaceViewport) {
      for (let i = rendered.length - 1; i >= 0; i--) {
        const r = rendered[i];
        if (r.top >= sy - 10 && r.top <= sy + innerHeight) { r.nodes.forEach((n) => n.remove()); rendered.splice(i, 1); }
      }
    }
    for (const b of boxes) {
      const item = { data: b, sx, sy, top: b.box.y + sy, nodes: [] };
      renderBubble(l, item);
      rendered.push(item);
    }
  }

  function restyleAll() {
    applyLayerVisibility();
    if (!layer) return;
    for (const item of rendered) { item.nodes.forEach((n) => n.remove()); item.nodes = []; renderBubble(layer, item); }
  }

  function clearOverlays() {
    rendered.splice(0).forEach((r) => r.nodes.forEach((n) => n.remove()));
    doneBands.clear();
  }

  function hexToRgb(hex) {
    const v = parseInt((hex || "#ffffff").slice(1), 16);
    return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
  }

  /**
   * رسم فقاعة واحدة حسب النمط:
   *  CLEAN       : طلاء مكان النص الأصلي بلون الفقاعة (داخل حدودها فقط) + الترجمة داخل الفقاعة.
   *               إن لم تكن الخلفية موحّدة (رسم/screentone) نتحول تلقائياً إلى "نص فقط" حتى لا نشوّه الرسم.
   *  TRANSLUCENT : خلفية بلون الفقاعة بعتامة 0..35% (بدون أي ضبابية).
   *  TEXT_ONLY   : نص فقط مع حافة/توهج خفيف للوضوح.
   */
  function renderBubble(l, item) {
    const b = item.data, sx = item.sx, sy = item.sy;
    const [r, g, bl] = hexToRgb(b.bg);
    const style = cfg.style || "CLEAN";
    const opacity = Math.max(0, Math.min(35, cfg.opacity == null ? 20 : cfg.opacity)) / 100;
    const canClean = style === "CLEAN" && b.uniform && b.clean;
    const darkText = b.dark !== false;

    if (canClean) {
      const c = document.createElement("div");
      c.className = "safar-lt-clean";
      const pad = 1;
      c.style.cssText = `left:${b.clean.x + sx - pad}px;top:${b.clean.y + sy - pad}px;width:${b.clean.w + 2 * pad}px;height:${b.clean.h + 2 * pad}px;background:rgb(${r},${g},${bl});`;
      l.appendChild(c); item.nodes.push(c);
    }

    const area = (style === "CLEAN" && b.uniform) ? b.area : b.box;
    const t = document.createElement("div");
    t.className = "safar-lt-text";
    t.dir = cfg.rtl ? "rtl" : "ltr";
    t.style.left = (area.x + sx) + "px";
    t.style.top = (area.y + sy) + "px";
    t.style.width = Math.max(area.w, 36) + "px";
    t.style.height = Math.max(area.h, 18) + "px";
    const span = document.createElement("span");
    span.textContent = b.text;
    t.appendChild(span);

    if (canClean) {
      t.style.color = darkText ? "#111" : "#fff";
    } else if (style === "TRANSLUCENT") {
      t.style.background = `rgba(${r},${g},${bl},${opacity})`;
      t.style.color = darkText ? "#111" : "#fff";
      t.classList.add(darkText ? "safar-glow-light" : "safar-glow-dark");
    } else {
      // TEXT_ONLY (أو CLEAN على خلفية غير موحّدة): نص فقط + توهج خفيف يضمن القراءة فوق الرسم
      t.style.color = darkText ? "#111" : "#fff";
      t.classList.add(darkText ? "safar-glow-light" : "safar-glow-dark");
      if (opacity > 0 && style !== "TEXT_ONLY") t.style.background = `rgba(${r},${g},${bl},${opacity})`;
    }

    // لمسة: إظهار النص الأصلي / العودة للترجمة
    t.addEventListener("click", (e) => {
      e.stopPropagation(); e.preventDefault();
      const showOrig = !t.classList.contains("safar-show-orig");
      t.classList.toggle("safar-show-orig", showOrig);
      span.textContent = showOrig ? b.orig : b.text;
      item.nodes.forEach((n) => { if (n !== t) n.style.visibility = showOrig ? "hidden" : ""; });
      fitText(t, span);
    });
    l.appendChild(t); item.nodes.push(t);
    fitText(t, span);
  }

  /** ملاءمة حجم الخط لمساحة الفقاعة (بحث ثنائي) حتى لا تتكدس الكلمات أو تخرج عن الحدود. */
  function fitText(box, span) {
    const maxW = box.clientWidth - 4, maxH = box.clientHeight - 2;
    let lo = 7, hi = Math.max(8, Math.min(30, maxH));
    span.style.fontSize = hi + "px";
    if (span.offsetHeight <= maxH && span.scrollWidth <= maxW + 1) return;
    for (let i = 0; i < 8; i++) {
      const mid = (lo + hi) / 2;
      span.style.fontSize = mid + "px";
      if (span.offsetHeight <= maxH && span.scrollWidth <= maxW + 1) lo = mid; else hi = mid;
    }
    span.style.fontSize = lo + "px";
  }

  function startManga() {
    mangaOn = true;
    ensureLayer();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize, { passive: true });
    document.addEventListener("load", onImageLoad, true); // صور كسولة التحميل
    onScroll();
  }

  function onResize() { clearOverlays(); onScroll(); }

  function stopManga() {
    mangaOn = false;
    window.removeEventListener("scroll", onScroll);
    window.removeEventListener("resize", onResize);
    document.removeEventListener("load", onImageLoad, true);
    clearOverlays();
    if (layer) { layer.remove(); layer = null; }
  }

  /* ============================== فيديو ============================== */
  const videos = new Map(); // video -> {sub, timer, cleanup}
  let videoMo = null;

  function subPosition(v, sub, atTop) {
    const r = v.getBoundingClientRect();
    const host = document.fullscreenElement && document.fullscreenElement !== v ? document.fullscreenElement : document.documentElement;
    if (sub.parentNode !== host) host.appendChild(sub);
    const fsOffset = host === document.documentElement ? { x: scrollX, y: scrollY } : (() => { const h = host.getBoundingClientRect(); return { x: -h.left, y: -h.top }; })();
    sub.style.left = (r.left + fsOffset.x + r.width * 0.05) + "px";
    sub.style.width = (r.width * 0.9) + "px";
    sub.style.top = atTop ? (r.top + fsOffset.y + r.height * 0.06) + "px" : (r.top + fsOffset.y + r.height * 0.78) + "px";
  }

  function attachVideo(v) {
    if (videos.has(v)) return;
    const sub = document.createElement("div");
    sub.className = "safar-lt-sub";
    sub.style.display = "none";
    const state = { sub, timer: null, listeners: [] };
    videos.set(v, state);

    const tracks = [...(v.textTracks || [])].filter((t) => t.kind === "subtitles" || t.kind === "captions");
    if (tracks.length) {
      // 1) مسارات نصية: الأدق والأخف (بدون OCR)
      const track = tracks.find((t) => t.mode === "showing") || tracks[0];
      track.mode = "hidden";
      const onCue = async () => {
        const cues = [...(track.activeCues || [])].map((c) => c.text.replace(/<[^>]+>/g, "")).join("\n").trim();
        if (!cues) { sub.style.display = "none"; return; }
        try {
          const res = await send({ type: "translate", kind: "VIDEO", texts: [cues], title: document.title });
          if (res && res.translations) { sub.textContent = res.translations[0]; sub.dir = cfg.rtl ? "rtl" : "ltr"; sub.style.display = ""; subPosition(v, sub, false); }
        } catch (e) {}
      };
      track.addEventListener("cuechange", onCue);
      state.listeners.push(() => track.removeEventListener("cuechange", onCue));
    } else if (isTop) {
      // 2) ترجمة محروقة في الصورة: OCR دوري للثلث السفلي، والعرض أعلى الفيديو لتجنب التداخل
      state.timer = setInterval(async () => {
        if (v.paused || v.ended || document.visibilityState !== "visible") return;
        const r = v.getBoundingClientRect();
        if (r.width < 200 || r.bottom < 0 || r.top > innerHeight) return;
        const rect = { x: r.left, y: r.top + r.height * 0.62, w: r.width, h: r.height * 0.38 };
        try {
          const res = await send({ type: "captureRegion", rect, innerWidth });
          if (!res || res.unchanged) return;
          sub.textContent = res.text || "";
          sub.dir = cfg.rtl ? "rtl" : "ltr";
          sub.style.display = res.text ? "" : "none";
          subPosition(v, sub, true);
        } catch (e) {}
      }, 1200);
    }
  }

  function startVideo() {
    document.querySelectorAll("video").forEach(attachVideo);
    videoMo = new MutationObserver(() => document.querySelectorAll("video").forEach(attachVideo));
    videoMo.observe(document.body, { childList: true, subtree: true });
  }

  function stopVideo() {
    if (videoMo) { videoMo.disconnect(); videoMo = null; }
    for (const [, s] of videos) { if (s.timer) clearInterval(s.timer); s.listeners.forEach((f) => f()); s.sub.remove(); }
    videos.clear();
  }

  /* ============================== المربع العائم (نص داخل مستطيل) ============================== */
  function replyRectText(msg) {
    const { x, y, w, h } = msg.rect;
    const parts = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    const range = document.createRange();
    let n, count = 0;
    while ((n = walker.nextNode()) && count < 400) {
      if (!n.nodeValue.trim()) continue;
      range.selectNodeContents(n);
      const r = range.getBoundingClientRect();
      if (r.right < x || r.left > x + w || r.bottom < y || r.top > y + h || !r.width) continue;
      const t = (originals.get(n) || n.nodeValue).trim();
      if (t) { parts.push(t); count++; }
    }
    send({ type: "rectTextResult", id: msg.id, text: parts.join(" ").slice(0, 4000) }).catch(() => {});
  }
})();
