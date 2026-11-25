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
  let port = null;

  /*
   * كل طلبات الصفحة للتطبيق تمر عبر المنفذ (Port) كطلب/رد مرقّم (RPC) مع مهلة،
   * بدلاً من runtime.sendNativeMessage الذي لا يصل على بعض الأجهزة/الإصدارات فتتوقف الترجمة المستمرة بصمت.
   */
  const pendingRpc = new Map();
  let rpcSeq = 1;
  function send(msg, timeoutMs = 45000) {
    return new Promise((resolve, reject) => {
      if (!port) { reject(new Error("no-port")); return; }
      const id = rpcSeq++;
      const timer = setTimeout(() => { pendingRpc.delete(id); reject(new Error("timeout")); }, timeoutMs);
      pendingRpc.set(id, { resolve, timer });
      try { port.postMessage(Object.assign({ rpc: id }, msg)); }
      catch (e) { clearTimeout(timer); pendingRpc.delete(id); reject(e); }
    });
  }

  function connect() {
    try { port = browser.runtime.connectNative(APP); } catch (e) { port = null; return false; }
    port.onMessage.addListener(onPortMessage);
    port.onDisconnect.addListener(() => {
      port = null;
      for (const [, p] of pendingRpc) clearTimeout(p.timer);
      pendingRpc.clear();
      setTimeout(connect, 1000); // إعادة الاتصال (مثلاً بعد العودة من bfcache)
    });
    reportViewport();
    return true;
  }
  const continuous = () => cfg.enabled && cfg.mode === "CONTINUOUS";
  const raf2 = () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));

  function onPortMessage(msg) {
    if (msg.cmd === "rpc") {
      const p = pendingRpc.get(msg.rpc);
      if (p) { clearTimeout(p.timer); pendingRpc.delete(msg.rpc); p.resolve(msg.data); }
      return;
    }
    if (msg.cmd === "config") { applyConfig(msg); if (isTop && typeof msg.zoom === "number") applyZoom(msg.zoom); }
    else if (msg.cmd === "zoom" && isTop) applyZoom(msg.value);
    else if (msg.cmd === "pageText" && isTop) replyPageText(msg.id);
    else if (msg.cmd === "rectText" && isTop) replyRectText(msg);
    else if (msg.cmd === "overlays" && isTop) placeBoxes(msg.boxes || [], msg.scope || "float");
    else if (msg.cmd === "clearOverlays" && isTop) clearOverlays(msg.scope);
    else if (msg.cmd === "hide" && isTop) hideForCapture(msg.id);
    else if (msg.cmd === "show" && isTop) showAfterCapture();
  }

  // منطقة العرض المرئية (تراعي التكبير بإصبعين): اللقطة تطابقها، لا الـ layout viewport
  const VV = window.visualViewport;
  const vvW = () => (VV ? VV.width : innerWidth);
  const vvH = () => (VV ? VV.height : innerHeight);
  const vvPageLeft = () => (VV ? VV.pageLeft : scrollX);
  const vvPageTop = () => (VV ? VV.pageTop : scrollY);
  const vvOffLeft = () => (VV ? VV.offsetLeft : 0);
  const vvOffTop = () => (VV ? VV.offsetTop : 0);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  function reportViewport() { if (isTop && port) { try { port.postMessage({ type: "viewport", innerWidth, vw: vvW() }); } catch (e) {} } }
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
   * التثبيت بالمرساة (Anchoring):
   *   كل ترجمة تُربط بالعنصر الذي تقع فوقه (صورة/Canvas غالباً) وتُخزَّن إحداثياتها كنِسَب من مستطيله.
   *   في كل إطار تمرير يُعاد حساب موضعها من موقع ذلك العنصر الفعلي على الشاشة ⇒ تبقى ملتصقة بالفقاعة
   *   مهما كان من يمرّر (النافذة، حاوية داخلية مثل Manga Plus، تحويلات CSS، تقليب الصفحات).
   *   إن تغيّرت صورة المرساة (صفحة جديدة) أو أُزيلت ⇒ تُحذف ترجماتها فوراً (لا بقايا في غير موضعها).
   *
   * دورة الترجمة المستمرة: إخفاء الطبقة ← التقاط ← OCR وتجميع الفقاعات ← ترجمة ← مسح القديم داخل الشاشة ← رسم الجديد.
   * المشغّلات: فتح الصفحة، توقف أي تمرير (حتى الحاويات الداخلية) 500ms، تحميل صور، تغيّر الصور الظاهرة، تكبير، تنقل.
   */
  let layer = null, scrollTimer = null, tickTimer = null, watchTimer = null, mangaOn = false, capturing = false;
  let lastHref = location.href, lastScale = VV ? VV.scale : 1, failStreak = 0, forceNext = true, syncQueued = false;
  let lastMediaSig = "", pendingSig = "";
  const items = []; // {data, scope, anchor, anchorSrc, frac, docRect, nodes, cleanNode, textNode, span, ...}

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
  function showAfterCapture() { if (layer) { syncPositions(); layer.style.visibility = "visible"; } }

  function schedule(delay = 500) {
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(captureViewport, delay);
  }

  /** أي تمرير في الصفحة (مرحلة الالتقاط ⇒ يشمل الحاويات الداخلية): مزامنة فورية للمواضع + التقاط بعد التوقف. */
  function onAnyScroll() {
    requestSync();
    if (location.href !== lastHref) { lastHref = location.href; clearOverlays("cont"); forceNext = true; }
    if (mangaOn) schedule(500);
  }

  function onZoom() {
    requestSync();
    if (!VV || Math.abs(VV.scale - lastScale) < 0.01) { onAnyScroll(); return; }
    lastScale = VV.scale; forceNext = true; if (mangaOn) schedule(500);
  }

  function onImageLoad(e) {
    if (e.target && (e.target.tagName === "IMG" || e.target.tagName === "VIDEO")) {
      requestSync();
      if (mangaOn) { forceNext = true; schedule(500); }
    }
  }

  // --- الهندسة: إحداثيات الالتقاط (visual viewport) ⇄ layout viewport ⇄ نِسَب من المرساة
  const toLayout = (r) => ({ x: r.x + vvOffLeft(), y: r.y + vvOffTop(), w: r.w, h: r.h });
  const inside = (a, b) => a.x >= b.x - 1 && a.y >= b.y - 1 && a.x + a.w <= b.x + b.w + 1 && a.y + a.h <= b.y + b.h + 1;
  const overlaps = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
  function overlapRatio(a, b) {
    const w = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x), h = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
    if (w <= 0 || h <= 0) return 0;
    return (w * h) / Math.min(a.w * a.h, b.w * b.h);
  }
  function layoutViewport() { return { x: 0, y: 0, w: innerWidth, h: innerHeight }; }

  const MEDIA = new Set(["IMG", "CANVAS", "VIDEO", "PICTURE", "SVG"]);
  /** العنصر الذي يحمل الفقاعة عند نقطة (بإحداثيات layout): يفضّل الصورة/Canvas، ويتجاهل طبقتنا. */
  function anchorAt(x, y) {
    let list = [];
    try { list = document.elementsFromPoint(x, y); } catch (e) { return null; }
    list = list.filter((el) => !(layer && layer.contains(el)) && el !== document.documentElement && el !== document.body);
    const media = list.find((el) => MEDIA.has(el.tagName.toUpperCase()) || (el.style && el.style.backgroundImage));
    const el = media || list[0] || null;
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return r.width > 20 && r.height > 20 ? el : null;
  }
  const srcOf = (el) => (el && (el.currentSrc || el.src || (el.style && el.style.backgroundImage))) || "";

  function fracOf(r, A) { return { x: (r.x - A.left) / A.width, y: (r.y - A.top) / A.height, w: r.w / A.width, h: r.h / A.height }; }
  function fromFrac(f, A) { return { x: A.left + f.x * A.width, y: A.top + f.y * A.height, w: f.w * A.width, h: f.h * A.height }; }

  /** الهندسة الحالية لعنصر (بإحداثيات layout viewport)، أو null إن اختفت مرساته. */
  function geom(item) {
    const b = item.data;
    if (item.anchor) {
      if (!item.anchor.isConnected || srcOf(item.anchor) !== item.anchorSrc) return null; // صفحة جديدة/أزيلت
      const A = item.anchor.getBoundingClientRect();
      if (A.width < 2 || A.height < 2) return { hidden: true };
      const k = A.width / item.anchorW0;
      return {
        box: fromFrac(item.frac.box, A), area: fromFrac(item.frac.area, A),
        clean: item.frac.clean ? fromFrac(item.frac.clean, A) : null, k,
      };
    }
    // بلا مرساة: إحداثيات المستند (تتبع تمرير النافذة فقط)
    const dx = item.docX - scrollX, dy = item.docY - scrollY;
    const sh = (r) => r && { x: r.x + dx, y: r.y + dy, w: r.w, h: r.h };
    return { box: sh(item.lay.box), area: sh(item.lay.area), clean: sh(item.lay.clean), k: 1 };
  }

  function requestSync() {
    if (syncQueued || !items.length) return;
    syncQueued = true;
    requestAnimationFrame(() => { syncQueued = false; syncPositions(); });
  }

  /** تحريك كل الترجمات مع محتواها؛ حذف ما فقد مرساته؛ إعادة الملاءمة إن تغيّر الحجم. */
  function syncPositions() {
    for (let i = items.length - 1; i >= 0; i--) {
      const it = items[i];
      const g = geom(it);
      if (!g) { it.nodes.forEach((n) => n.remove()); items.splice(i, 1); continue; }
      if (g.hidden) { it.nodes.forEach((n) => (n.style.display = "none")); continue; }
      it.nodes.forEach((n) => (n.style.display = ""));
      if (Math.abs(g.k - it.lastK) > 0.03) {
        // تغيّر حجم الصورة (تكبير/ملاءمة) ⇒ إعادة رسم بنفس النِّسَب
        it.lastK = g.k; it.nodes.forEach((n) => n.remove()); it.nodes = []; renderBubble(layer, it, g);
      } else {
        applyGeom(it, g);
      }
      it.rect = g.box;
    }
  }

  function applyGeom(it, g) {
    if (it.cleanNode) {
      const c = it.cleanKind === "soft" ? pad(g.box, 3) : pad(g.clean || g.box, 1);
      setRect(it.cleanNode, c);
    }
    if (it.textNode) setRect(it.textNode, textArea(it, g));
  }
  const pad = (r, p) => ({ x: r.x - p, y: r.y - p, w: r.w + 2 * p, h: r.h + 2 * p });
  function setRect(n, r) {
    n.style.transform = `translate(${r.x}px, ${r.y}px)`;
    n.style.width = r.w + "px"; n.style.height = r.h + "px";
  }
  function textArea(it, g) {
    const base = it.useArea ? g.area : g.box;
    const inset = Math.max(2, Math.min(10, Math.min(base.w, base.h) * 0.08));
    return { x: base.x + inset, y: base.y + inset, w: Math.max(24, base.w - 2 * inset), h: Math.max(14, base.h - 2 * inset) };
  }

  /** بصمة الصور الظاهرة (مصدرها وموقعها): تكشف تقليب الصفحات والتحميل الكسول دون التقاط شاشة. */
  function mediaSignature() {
    const out = [];
    const vh = innerHeight, vw = innerWidth;
    for (const el of document.querySelectorAll("img, canvas, video")) {
      const r = el.getBoundingClientRect();
      if (r.width < 80 || r.height < 80 || r.bottom < 0 || r.top > vh || r.right < 0 || r.left > vw) continue;
      out.push(srcOf(el).slice(-60) + "@" + Math.round(r.left / 16) + "," + Math.round(r.top / 16) + "," + Math.round(r.width / 16));
      if (out.length > 24) break;
    }
    return out.join("|");
  }

  function watchTick() {
    if (!mangaOn || capturing || document.visibilityState !== "visible") return;
    syncPositions();
    const sig = mediaSignature();
    if (sig === lastMediaSig) { pendingSig = ""; return; }
    // ننتظر ثبات التغيير دورة واحدة (انتهاء الحركة/التحميل) ثم نلتقط
    if (sig === pendingSig) { pendingSig = ""; schedule(80); } else pendingSig = sig;
  }

  async function captureViewport() {
    if (!mangaOn || document.visibilityState !== "visible") return;
    if (capturing) { schedule(400); return; }
    capturing = true;
    ensureLayer();
    syncPositions();
    await hideLayer();
    const lv = layoutViewport();
    // ترجمات تتجاوز حافة الشاشة: نبقيها ونطلب عدم إعادة ترجمة ما تحتها (فقاعة مقصوصة بالحافة)
    const covered = items.filter((it) => it.scope === "cont" && it.rect && overlaps(it.rect, lv) && !inside(it.rect, lv))
      .map((it) => ({ x: it.rect.x - vvOffLeft(), y: it.rect.y - vvOffTop(), w: it.rect.w, h: it.rect.h }));
    const sig = mediaSignature();
    let res = null;
    try { res = await send({ type: "captureViewport", vw: vvW(), innerWidth, covered, force: forceNext }); } catch (e) { res = null; }
    capturing = false;
    if (!mangaOn) { showAfterCapture(); return; }
    if (!res || res.error) {
      showAfterCapture();
      // فشل مؤقت (شبكة، نموذج قيد التنزيل...) ⇒ إعادة محاولة بدل التعليق بلا نتيجة
      if (++failStreak <= 3) schedule(3000);
      return;
    }
    failStreak = 0; forceNext = false; lastMediaSig = sig;
    if (res.unchanged) { showAfterCapture(); return; } // نفس المحتوى والموقع: لا إعادة ترجمة
    // مسح القديم داخل الشاشة ثم رسم الجديد
    for (let i = items.length - 1; i >= 0; i--) {
      const it = items[i];
      if (it.scope === "cont" && it.rect && inside(it.rect, lv)) { it.nodes.forEach((n) => n.remove()); items.splice(i, 1); }
    }
    placeBoxes(res.boxes || [], "cont");
    showAfterCapture();
  }

  /** رسم فقاعات بإحداثيات الشاشة المرئية لحظة الالتقاط، مع ربط كل منها بمرساتها. */
  function placeBoxes(boxes, scope) {
    const l = ensureLayer();
    if (scope === "float") clearOverlays("float"); // ترجمات المربع السابقة تُمسح قبل الجديدة
    const batch = [];
    for (const b of boxes) {
      const lay = { box: toLayout(b.box), area: toLayout(b.area || b.box), clean: b.clean ? toLayout(b.clean) : null };
      // لا تراكب: أي ترجمة قديمة تغطي نفس الفقاعة تُزال
      for (let i = items.length - 1; i >= 0; i--) {
        if (items[i].rect && overlapRatio(items[i].rect, lay.box) > 0.3) { items[i].nodes.forEach((n) => n.remove()); items.splice(i, 1); }
      }
      const item = { data: b, scope, nodes: [], lay, docX: scrollX, docY: scrollY, rect: lay.box, lastK: 1 };
      const anchor = anchorAt(lay.box.x + lay.box.w / 2, lay.box.y + lay.box.h / 2);
      if (anchor) {
        const A = anchor.getBoundingClientRect();
        item.anchor = anchor; item.anchorSrc = srcOf(anchor); item.anchorW0 = A.width;
        item.frac = { box: fracOf(lay.box, A), area: fracOf(lay.area, A), clean: lay.clean ? fracOf(lay.clean, A) : null };
      }
      renderBubble(l, item, geom(item) || { box: lay.box, area: lay.area, clean: lay.clean, k: 1 });
      items.push(item);
      batch.push(item);
    }
    harmonize(batch);
  }

  function restyleAll() {
    applyLayerVisibility();
    if (!layer) return;
    for (const item of items) {
      const g = geom(item); if (!g || g.hidden) continue;
      item.nodes.forEach((n) => n.remove()); item.nodes = []; renderBubble(layer, item, g);
    }
    harmonize(items);
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
   * رسم فقاعة واحدة بأسلوب ترجمة Google: خط خفيف وصغير ومتناسق، داخل الفقاعة دائماً.
   *  CLEAN       : الفقاعة الموحّدة تُطلى بلونها داخل حدودها، والترجمة داخلها بهامش داخلي آمن.
   *               خلفية فاتحة غير موحّدة (screentone): رقعة ناعمة الحواف فوق النص الأصلي فقط.
   *               خلفية داكنة/رسم متوسط الإضاءة: نص فقط بتوهج خفيف (لا رقع رمادية فوق الرسم).
   *  TRANSLUCENT : خلفية بلون الفقاعة بعتامة 0..35% (بدون ضبابية).
   *  TEXT_ONLY   : نص فقط مع حافة/توهج خفيف.
   */
  function renderBubble(l, item, g) {
    const b = item.data;
    const [r, gr, bl] = hexToRgb(b.bg);
    const bgLum = (r * 299 + gr * 587 + bl * 114) / 1000;
    const style = cfg.style || "CLEAN";
    const opacity = Math.max(0, Math.min(35, cfg.opacity == null ? 20 : cfg.opacity)) / 100;
    const darkText = b.dark !== false;
    const solidClean = style === "CLEAN" && b.uniform && !!b.clean;
    const softClean = style === "CLEAN" && !solidClean && bgLum >= 170;
    item.useArea = solidClean;
    item.cleanNode = null; item.cleanKind = null;

    if (solidClean || softClean) {
      const c = document.createElement("div");
      c.className = "safar-lt-clean" + (softClean ? " safar-lt-soft" : "");
      c.style.background = softClean ? `rgba(${r},${gr},${bl},0.93)` : `rgb(${r},${gr},${bl})`;
      if (softClean) c.style.boxShadow = `0 0 6px 4px rgba(${r},${gr},${bl},0.93)`;
      l.appendChild(c); item.nodes.push(c);
      item.cleanNode = c; item.cleanKind = softClean ? "soft" : "solid";
    }

    const t = document.createElement("div");
    t.className = "safar-lt-text";
    t.dir = cfg.rtl ? "rtl" : "ltr";
    t.style.color = darkText ? "#1a1a1a" : "#fff";
    const span = document.createElement("span");
    span.textContent = b.text;
    t.appendChild(span);
    if (!solidClean && !softClean) {
      t.classList.add(darkText ? "safar-glow-light" : "safar-glow-dark");
      if (style === "TRANSLUCENT" && opacity > 0) t.style.background = `rgba(${r},${gr},${bl},${opacity})`;
    }
    // لمسة: إظهار النص الأصلي / العودة للترجمة
    t.addEventListener("click", (e) => {
      e.stopPropagation(); e.preventDefault();
      const showOrig = !t.classList.contains("safar-show-orig");
      t.classList.toggle("safar-show-orig", showOrig);
      span.textContent = showOrig ? b.orig : b.text;
      if (item.cleanNode) item.cleanNode.style.visibility = showOrig ? "hidden" : "";
      fitText(t, span, item.maxFont);
    });
    l.appendChild(t); item.nodes.push(t);
    item.textNode = t; item.span = span;
    applyGeom(item, g);
    // الحد الأعلى للخط: قريب من حجم النص الأصلي (لا يكبر عنه) ويتبع تكبير الصورة — مثل Google
    item.maxFont = Math.max(9, Math.min(17 * (g.k || 1), (b.lh || 16) * 0.82 * (g.k || 1)));
    item.fitted = fitText(t, span, item.maxFont);
  }

  const MIN_FONT = 7;

  /** أكبر حجم خط يجعل النص كله داخل المساحة (بحث ثنائي)، أسطر متوازنة، لا خروج عن الحدود. */
  function fitText(box, span, maxFont) {
    const maxW = box.clientWidth, maxH = box.clientHeight;
    const fits = () => span.offsetHeight <= maxH && span.scrollWidth <= maxW + 0.5;
    let hi = Math.max(MIN_FONT, Math.min(maxFont || 17, maxH));
    span.style.fontSize = hi + "px";
    if (fits()) return hi;
    let lo = MIN_FONT;
    for (let i = 0; i < 9; i++) {
      const mid = (lo + hi) / 2;
      span.style.fontSize = mid + "px";
      if (fits()) lo = mid; else hi = mid;
    }
    span.style.fontSize = lo + "px";
    return lo;
  }

  /** تناسق الأحجام في الصفحة: لا تكبر فقاعة عن الوسيط بأكثر من 15% (مظهر مرتب كترجمة Google). */
  function harmonize(batch) {
    const sizes = batch.map((it) => it.fitted).filter(Boolean).sort((a, b) => a - b);
    if (sizes.length < 3) return;
    const cap = sizes[Math.floor(sizes.length / 2)] * 1.15;
    for (const it of batch) if (it.fitted > cap) { it.span.style.fontSize = cap + "px"; it.fitted = cap; }
  }

  // مستمعو المزامنة يعملون دائماً (للمربع العائم أيضاً)، أما الالتقاط التلقائي فللوضع المستمر فقط
  if (isTop) {
    document.addEventListener("scroll", onAnyScroll, { capture: true, passive: true });
    window.addEventListener("resize", onZoom, { passive: true });
    if (VV) { VV.addEventListener("resize", onZoom, { passive: true }); VV.addEventListener("scroll", requestSync, { passive: true }); }
    document.addEventListener("load", onImageLoad, true);
    window.addEventListener("popstate", onAnyScroll);
    setInterval(() => { if (items.length && !mangaOn) syncPositions(); }, 700);
  }

  function startManga() {
    mangaOn = true; forceNext = true; failStreak = 0; lastMediaSig = "";
    ensureLayer();
    schedule(300); // فور فتح الصفحة
    // محاولات إضافية لصور تتأخر في التحميل + مراقبة تغيّر الصور الظاهرة (تقليب الصفحات)
    tickTimer = [1800, 4500].map((d) => setTimeout(() => { if (mangaOn && !capturing) captureViewport(); }, d));
    watchTimer = setInterval(watchTick, 700);
  }

  function stopManga() {
    mangaOn = false;
    clearTimeout(scrollTimer); (tickTimer || []).forEach(clearTimeout); tickTimer = null;
    clearInterval(watchTimer); watchTimer = null;
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
  /* ============================== أدوات الصفحة ============================== */
  /** تكبير/تصغير محتوى الصفحة (مستوى محفوظ لكل موقع). يُطبق على body فلا تتأثر طبقة الترجمة. */
  function applyZoom(pct) {
    const v = Math.max(30, Math.min(300, pct || 100));
    if (!document.body) { document.addEventListener("DOMContentLoaded", () => applyZoom(v), { once: true }); return; }
    document.body.style.zoom = v === 100 ? "" : String(v / 100);
  }

  /** نص الصفحة للاستماع إليها (القراءة الصوتية). يفضّل المحتوى الرئيسي إن وُجد. */
  function replyPageText(id) {
    const main = document.querySelector("article, main, [role=main]") || document.body;
    const text = (originals.size ? main.innerText : main.innerText || "").replace(/\n{3,}/g, "\n\n").slice(0, 40000);
    const lang = document.documentElement.lang || "";
    send({ type: "pageTextResult", id, text, lang }).catch(() => {});
  }

  connect();
})();
