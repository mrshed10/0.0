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
    else if (msg.cmd === "overlays" && isTop) placeBoxes(msg.boxes || [], vvPageLeft(), vvPageTop(), msg.scope || "float");
    else if (msg.cmd === "clearOverlays" && isTop) clearOverlays(msg.scope);
    else if (msg.cmd === "hide" && isTop) hideForCapture(msg.id);
    else if (msg.cmd === "show" && isTop) showAfterCapture();
  });

  // منطقة العرض المرئية (تراعي التكبير بإصبعين): اللقطة تطابقها، لا الـ layout viewport
  const VV = window.visualViewport;
  const vvW = () => (VV ? VV.width : innerWidth);
  const vvH = () => (VV ? VV.height : innerHeight);
  const vvPageLeft = () => (VV ? VV.pageLeft : scrollX);
  const vvPageTop = () => (VV ? VV.pageTop : scrollY);
  const vvOffLeft = () => (VV ? VV.offsetLeft : 0);
  const vvOffTop = () => (VV ? VV.offsetTop : 0);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  function reportViewport() { if (isTop) port.postMessage({ type: "viewport", innerWidth, vw: vvW() }); }
  reportViewport();
  window.addEventListener("resize", reportViewport, { passive: true });
  if (VV) VV.addEventListener("resize", reportViewport, { passive: true });

  function applyConfig(next) {
    const prev = cfg;
    cfg = next;
    const restartKey = (c) => [c.enabled, c.mode, c.kind, c.target, c.source].join("|");
    if (restartKey(prev) !== restartKey(cfg)) {
      stopAll();
      if (cfg.enabled && cfg.mode === "CONTINUOUS") {
        if (cfg.kind === "TEXT") { startText(); if (isTop) setTimeout(checkImagePage, 1500); }
        else if (cfg.kind === "MANGA" && isTop) startManga();
        else if (cfg.kind === "VIDEO") startVideo();
      }
    }
    // تغيّر النمط/الشفافية/الإظهار: إعادة رسم فورية بدون إعادة OCR
    if (prev.style !== cfg.style || prev.opacity !== cfg.opacity || prev.visible !== cfg.visible || prev.rtl !== cfg.rtl) restyleAll();
  }

  /** صفحة مصوّرة (قارئ مانغا) في وضع النصوص ⇒ نقترح على التطبيق التبديل لوضع الصور تلقائياً. */
  function checkImagePage() {
    if (!(cfg.enabled && cfg.mode === "CONTINUOUS" && cfg.kind === "TEXT")) return;
    const vw = innerWidth, vh = innerHeight;
    let imgArea = 0;
    for (const el of document.querySelectorAll("img, canvas, picture")) {
      const r = el.getBoundingClientRect();
      const w = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0)), h = Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
      imgArea += w * h;
    }
    let textChars = 0;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    const range = document.createRange();
    let n;
    while ((n = walker.nextNode()) && textChars < 400) {
      if (!n.nodeValue.trim()) continue;
      range.selectNodeContents(n);
      const r = range.getBoundingClientRect();
      if (r.bottom > 0 && r.top < vh && r.width) textChars += n.nodeValue.trim().length;
    }
    if (imgArea / (vw * vh) > 0.45 && textChars < 400) send({ type: "suggestManga" }).catch(() => {});
  }

  function stopAll() { stopText(); stopManga(); stopVideo(); clearOverlays(); }

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
  /*
   * دورة الترجمة المستمرة (مستقلة تماماً عن المربع العائم، على كامل الشاشة المرئية):
   *   إخفاء طبقة الترجمة ← انتظار إعادة الرسم ← التقاط الصفحة الأصلية ← OCR وتجميع الفقاعات ← ترجمة
   *   ← مسح الترجمات القديمة في الشاشة ← رسم الجديدة.
   * المشغّلات: فتح الصفحة، توقف التمرير (500ms)، تحميل صور، تكبير/تصغير، تغيّر الصفحة.
   * التكرار ممنوع: التطبيق يقارن بصمة اللقطة ويرد "unchanged" إن لم يتغير شيء.
   */
  let layer = null, scrollTimer = null, tickTimer = null, mangaOn = false, capturing = false;
  let lastHref = location.href, lastScale = VV ? VV.scale : 1, failStreak = 0, forceNext = true;
  const items = []; // {data, ox, oy, rect:{x,y,w,h} بإحداثيات الصفحة, scope:"cont"|"float", nodes}

  function ensureLayer() {
    if (layer && layer.isConnected) return layer;
    layer = document.createElement("div");
    layer.className = "safar-lt-layer";
    document.documentElement.appendChild(layer);
    applyLayerVisibility();
    return layer;
  }

  function applyLayerVisibility() { if (layer) layer.style.display = cfg.visible === false ? "none" : ""; }

  // --- إخفاء/إظهار حول لحظة الالتقاط (يستخدمه التطبيق للمربع العائم أيضاً)
  async function hideLayer() {
    if (layer) layer.style.visibility = "hidden";
    await raf2();
    await sleep(90); // مهلة ليصل الإطار الجديد (بدون الطبقة) إلى المركّب قبل الالتقاط
  }
  async function hideForCapture(id) {
    await hideLayer();
    send({ type: "hiddenAck", id }).catch(() => {});
  }
  function showAfterCapture() { if (layer) layer.style.visibility = "visible"; }

  function schedule(delay = 500) {
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(captureViewport, delay);
  }

  function onScroll() {
    if (location.href !== lastHref) { lastHref = location.href; clearOverlays("cont"); forceNext = true; } // صفحة جديدة (SPA)
    schedule(500); // بعد توقف التمرير
  }

  function onZoom() {
    if (!VV || Math.abs(VV.scale - lastScale) < 0.01) { onScroll(); return; }
    lastScale = VV.scale; forceNext = true; schedule(500);
  }

  function onImageLoad(e) {
    if (mangaOn && e.target && e.target.tagName === "IMG") { forceNext = true; schedule(500); }
  }

  function viewportRect() { return { x: vvPageLeft(), y: vvPageTop(), w: vvW(), h: vvH() }; }
  const inside = (a, b) => a.x >= b.x - 1 && a.y >= b.y - 1 && a.x + a.w <= b.x + b.w + 1 && a.y + a.h <= b.y + b.h + 1;
  const overlaps = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
  function overlapRatio(a, b) {
    const w = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x), h = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
    if (w <= 0 || h <= 0) return 0;
    return (w * h) / Math.min(a.w * a.h, b.w * b.h);
  }

  async function captureViewport() {
    if (!mangaOn || document.visibilityState !== "visible") return;
    if (capturing) { schedule(400); return; }
    capturing = true;
    ensureLayer();
    await hideLayer();
    const vr = viewportRect();
    const ox = vr.x, oy = vr.y;
    // ترجمات تتجاوز حافة الشاشة: نبقيها ونطلب عدم إعادة ترجمة ما تحتها (فقاعة مقصوصة بالحافة)
    const covered = items.filter((it) => it.scope === "cont" && overlaps(it.rect, vr) && !inside(it.rect, vr))
      .map((it) => ({ x: it.rect.x - ox, y: it.rect.y - oy, w: it.rect.w, h: it.rect.h }));
    let res = null;
    try { res = await send({ type: "captureViewport", vw: vr.w, innerWidth, covered, force: forceNext }); } catch (e) { res = null; }
    showAfterCapture();
    capturing = false;
    if (!mangaOn) return;
    if (!res || res.error) {
      // فشل مؤقت (شبكة، نموذج قيد التنزيل...) ⇒ إعادة محاولة بدل التعليق بلا نتيجة
      if (++failStreak <= 3) schedule(3000);
      return;
    }
    failStreak = 0;
    forceNext = false;
    if (res.unchanged) return; // نفس المحتوى والموقع: لا إعادة ترجمة
    // مسح القديم داخل الشاشة ثم رسم الجديد
    for (let i = items.length - 1; i >= 0; i--) {
      const it = items[i];
      if (it.scope === "cont" && inside(it.rect, vr)) { it.nodes.forEach((n) => n.remove()); items.splice(i, 1); }
    }
    placeBoxes(res.boxes || [], ox, oy, "cont");
  }

  /** رسم فقاعات بإحداثيات نسبة للشاشة المرئية (CSS px) + إزاحة الصفحة. */
  function placeBoxes(boxes, ox, oy, scope) {
    const l = ensureLayer();
    if (scope === "float") clearOverlays("float"); // ترجمات المربع السابقة تُمسح قبل الجديدة
    for (const b of boxes) {
      const rect = { x: b.box.x + ox, y: b.box.y + oy, w: b.box.w, h: b.box.h };
      // لا تراكب: أي ترجمة قديمة تغطي نفس الفقاعة تُزال
      for (let i = items.length - 1; i >= 0; i--) {
        if (overlapRatio(items[i].rect, rect) > 0.3) { items[i].nodes.forEach((n) => n.remove()); items.splice(i, 1); }
      }
      const item = { data: b, ox, oy, rect, scope, nodes: [] };
      renderBubble(l, item);
      items.push(item);
    }
  }

  function restyleAll() {
    applyLayerVisibility();
    if (!layer) return;
    for (const item of items) { item.nodes.forEach((n) => n.remove()); item.nodes = []; renderBubble(layer, item); }
  }

  function clearOverlays(scope) {
    for (let i = items.length - 1; i >= 0; i--) {
      if (!scope || items[i].scope === scope) { items[i].nodes.forEach((n) => n.remove()); items.splice(i, 1); }
    }
  }

  function hexToRgb(hex) {
    const v = parseInt((hex || "#ffffff").slice(1), 16);
    return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
  }

  /**
   * رسم فقاعة واحدة حسب النمط:
   *  CLEAN       : طلاء مكان النص الأصلي بلون الفقاعة (أبيض للفقاعات البيضاء) داخل حدودها فقط + الترجمة داخلها.
   *               على الرسم/الخلفيات المنقطة: نص فقط بتوهج خفيف (لا صناديق رمادية، لا تشويه).
   *  TRANSLUCENT : خلفية بلون الفقاعة بعتامة 0..35% (بدون ضبابية).
   *  TEXT_ONLY   : نص فقط مع حافة/توهج خفيف.
   */
  function renderBubble(l, item) {
    const b = item.data, ox = item.ox, oy = item.oy;
    const [r, g, bl] = hexToRgb(b.bg);
    const style = cfg.style || "CLEAN";
    const opacity = Math.max(0, Math.min(35, cfg.opacity == null ? 20 : cfg.opacity)) / 100;
    const canClean = style === "CLEAN" && b.uniform && b.clean;
    const darkText = b.dark !== false;

    if (canClean) {
      const c = document.createElement("div");
      c.className = "safar-lt-clean";
      c.style.cssText = `left:${b.clean.x + ox - 1}px;top:${b.clean.y + oy - 1}px;width:${b.clean.w + 2}px;height:${b.clean.h + 2}px;background:rgb(${r},${g},${bl});`;
      l.appendChild(c); item.nodes.push(c);
    }

    const area = (style === "CLEAN" && b.uniform) ? b.area : b.box;
    const t = document.createElement("div");
    t.className = "safar-lt-text";
    t.dir = cfg.rtl ? "rtl" : "ltr";
    t.style.left = (area.x + ox) + "px";
    t.style.top = (area.y + oy) + "px";
    t.style.width = Math.max(area.w, 36) + "px";
    t.style.height = Math.max(area.h, 18) + "px";
    t.style.color = darkText ? "#111" : "#fff";
    const span = document.createElement("span");
    span.textContent = b.text;
    t.appendChild(span);

    if (!canClean) {
      t.classList.add(darkText ? "safar-glow-light" : "safar-glow-dark");
      if (style === "TRANSLUCENT" && opacity > 0) t.style.background = `rgba(${r},${g},${bl},${opacity})`;
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
    mangaOn = true; forceNext = true; failStreak = 0;
    ensureLayer();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onZoom, { passive: true });
    if (VV) { VV.addEventListener("resize", onZoom, { passive: true }); VV.addEventListener("scroll", onScroll, { passive: true }); }
    document.addEventListener("load", onImageLoad, true); // صور كسولة التحميل
    window.addEventListener("popstate", onScroll);
    schedule(300); // فور فتح الصفحة
    // محاولات إضافية قليلة لصور تتأخر في التحميل (بدون فحص دوري دائم حتى لا تومض الترجمات)
    tickTimer = [1800, 4500].map((d) => setTimeout(() => { if (mangaOn && !capturing) captureViewport(); }, d));
  }

  function stopManga() {
    mangaOn = false;
    clearTimeout(scrollTimer); (tickTimer || []).forEach(clearTimeout); tickTimer = null;
    window.removeEventListener("scroll", onScroll);
    window.removeEventListener("resize", onZoom);
    if (VV) { VV.removeEventListener("resize", onZoom); VV.removeEventListener("scroll", onScroll); }
    document.removeEventListener("load", onImageLoad, true);
    window.removeEventListener("popstate", onScroll);
    clearOverlays("cont");
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
        const rect = { x: r.left - vvOffLeft(), y: r.top - vvOffTop() + r.height * 0.62, w: r.width, h: r.height * 0.38 };
        try {
          const res = await send({ type: "captureRegion", rect, innerWidth: vvW() });
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
    const { w, h } = msg.rect;
    const x = msg.rect.x + vvOffLeft(), y = msg.rect.y + vvOffTop();
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
