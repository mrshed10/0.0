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
  });

  function reportViewport() { if (isTop) port.postMessage({ type: "viewport", innerWidth: window.innerWidth }); }
  reportViewport();
  window.addEventListener("resize", reportViewport, { passive: true });

  function applyConfig(next) {
    const prevKey = cfg.enabled + cfg.mode + cfg.kind + cfg.target;
    cfg = next;
    if (prevKey === cfg.enabled + cfg.mode + cfg.kind + cfg.target) return;
    stopAll();
    if (!continuous()) return;
    if (cfg.kind === "TEXT") startText();
    else if (cfg.kind === "MANGA" && isTop) startManga();
    else if (cfg.kind === "VIDEO") startVideo();
  }

  function stopAll() { stopText(); stopManga(); stopVideo(); }

  /* ============================== نصوص ============================== */
  const SKIP = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "CODE", "PRE", "TEXTAREA", "INPUT", "SELECT",
    "OPTION", "SVG", "MATH", "KBD", "SAMP", "VAR", "CANVAS", "IFRAME"]);
  const originals = new Map();     // textNode -> النص الأصلي
  const written = new WeakMap();   // textNode -> النص الذي كتبناه (لتجاهل طفراتنا)
  const queued = new WeakSet();
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

  async function translateBatch(nodes) {
    const texts = nodes.map((n) => n.nodeValue.trim());
    let res;
    try { res = await send({ type: "translate", kind: "TEXT", texts, title: document.title }); }
    catch (e) { nodes.forEach((n) => queued.delete(n)); return; }
    if (!res || res.error || !res.translations) { nodes.forEach((n) => queued.delete(n)); return; }
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
    for (const [n, orig] of originals) { if (n.isConnected && written.get(n) === n.nodeValue) n.nodeValue = orig; }
    originals.clear();
  }

  /* ============================== مانغا / صور ============================== */
  let layer = null, scrollTimer = null, mangaOn = false, capturing = false;
  const doneBands = new Set();

  function ensureLayer() {
    if (layer && layer.isConnected) return layer;
    layer = document.createElement("div");
    layer.className = "safar-lt-layer";
    document.documentElement.appendChild(layer);
    return layer;
  }

  function hasImagesInView() {
    const vh = innerHeight, vw = innerWidth;
    for (const el of document.querySelectorAll("img, canvas, picture, [style*='background-image']")) {
      const r = el.getBoundingClientRect();
      if (r.width > 120 && r.height > 120 && r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw) return true;
    }
    return false;
  }

  function onScroll() {
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(captureViewport, 450); // بعد توقف التمرير
  }

  async function captureViewport() {
    if (!mangaOn || capturing || document.visibilityState !== "visible" || !hasImagesInView()) return;
    const band = Math.round(scrollY / 40) + ":" + Math.round(scrollX / 40) + ":" + innerWidth;
    if (doneBands.has(band)) return;
    capturing = true;
    const l = ensureLayer();
    l.style.visibility = "hidden"; // لا نريد أن يقرأ OCR ترجماتنا السابقة
    await raf2();
    const sy = scrollY, sx = scrollX;
    let res;
    try { res = await send({ type: "captureViewport", innerWidth, innerHeight }); }
    catch (e) { res = null; }
    l.style.visibility = "visible";
    capturing = false;
    if (!res || !mangaOn) return;
    doneBands.add(band);
    // احذف المربعات القديمة داخل نفس نطاق الشاشة
    for (const b of [...l.children]) {
      const top = parseFloat(b.style.top);
      if (top >= sy - 10 && top <= sy + innerHeight) b.remove();
    }
    for (const b of res.boxes || []) addBox(l, b, sx, sy);
  }

  function addBox(l, b, sx, sy) {
    const d = document.createElement("div");
    d.className = "safar-lt-box";
    d.style.left = (b.x + sx) + "px";
    d.style.top = (b.y + sy) + "px";
    d.style.width = Math.max(b.w, 40) + "px";
    d.style.minHeight = b.h + "px";
    const fs = Math.max(10, Math.min(18, Math.sqrt((b.w * b.h) / Math.max(b.text.length, 1)) * 0.8));
    d.style.fontSize = fs + "px";
    d.textContent = b.text;
    d.dataset.orig = b.orig || "";
    d.dataset.tr = b.text;
    // لمسة: إظهار النص الأصلي / العودة للترجمة
    d.addEventListener("click", (e) => {
      e.stopPropagation(); e.preventDefault();
      const showOrig = !d.classList.contains("safar-show-orig");
      d.classList.toggle("safar-show-orig", showOrig);
      d.textContent = showOrig ? d.dataset.orig : d.dataset.tr;
    });
    l.appendChild(d);
  }

  function startManga() {
    mangaOn = true;
    ensureLayer();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", () => { doneBands.clear(); onScroll(); }, { passive: true });
    onScroll();
  }

  function stopManga() {
    mangaOn = false;
    window.removeEventListener("scroll", onScroll);
    doneBands.clear();
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
          if (res && res.translations) { sub.textContent = res.translations[0]; sub.style.display = ""; subPosition(v, sub, false); }
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
