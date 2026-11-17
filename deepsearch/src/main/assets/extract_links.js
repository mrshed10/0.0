(function () {
  var out = new Map();
  var VID = /\.(mp4|webm|m3u8|mov|mkv|m4v|mpd)(\?|#|$)/i;
  var IMG = /\.(jpe?g|png|gif|webp|bmp|avif|heic)(\?|#|$)/i;
  function kindOf(u) { return VID.test(u) ? 'video' : (IMG.test(u) ? 'image' : 'page'); }
  function add(u, kind, label) {
    try {
      if (!u) return;
      u = new URL(u, location.href).href;
      if (!/^https?:/i.test(u)) return;
      if (!out.has(u)) out.set(u, { url: u, kind: kind || kindOf(u), label: (label || '').trim().slice(0, 140) });
    } catch (e) {}
  }

  // 1) وسائط مضمّنة في الصفحة
  document.querySelectorAll('video, video source, audio source').forEach(function (e) { add(e.currentSrc || e.src, 'video'); });
  document.querySelectorAll('meta[property^="og:video"], meta[name="twitter:player:stream"]').forEach(function (m) { add(m.content, 'video', 'og:video'); });
  document.querySelectorAll('meta[property="og:image"], meta[name="twitter:image"]').forEach(function (m) { add(m.content, 'image', 'og:image'); });
  document.querySelectorAll('img').forEach(function (i) {
    if ((i.naturalWidth || i.width) >= 120) add(i.currentSrc || i.src, 'image', i.alt);
  });

  // 2) روابط أصلية مغلّفة داخل نتائج محركات البحث (Yandex img_url، Google imgurl/url، Bing mediaurl ...)
  var KEYS = ['img_url', 'imgurl', 'mediaurl', 'imgrefurl', 'url', 'u', 'q', 'rurl', 'source'];
  document.querySelectorAll('a[href]').forEach(function (a) {
    try {
      var u = new URL(a.href);
      KEYS.forEach(function (k) {
        var v = u.searchParams.get(k);
        if (v && /^https?:\/\//i.test(v)) add(v, null, '↳ ' + u.hostname);
      });
    } catch (e) {}
  });
  // Yandex يخزن بيانات النتائج في JSON داخل data-state
  document.querySelectorAll('[data-state]').forEach(function (el) {
    var s = el.getAttribute('data-state') || '';
    var re = /"(?:url|origUrl|img_href|href)":"(https?:[^"]+)"/g, m, n = 0;
    while ((m = re.exec(s)) && n++ < 300) add(m[1].replace(/\\u002F/g, '/'), null, 'yandex');
  });

  // 3) كل الروابط العادية
  document.querySelectorAll('a[href]').forEach(function (a) { add(a.href, null, a.textContent); });

  // 4) روابط فيديو مخفية داخل السكربتات
  var html = document.documentElement.innerHTML;
  var re = /https?:\\?\/\\?\/[^"'\s<>]+?\.(?:mp4|webm|m3u8|mov)(?:\?[^"'\s<>]*)?/gi, m, n = 0;
  while ((m = re.exec(html)) && n++ < 300) add(m[0].replace(/\\\//g, '/').replace(/\\u0026/g, '&').replace(/&amp;/g, '&'), 'video', 'script');

  var rank = { video: 0, image: 1, page: 2 };
  var arr = Array.from(out.values());
  arr.sort(function (a, b) { return rank[a.kind] - rank[b.kind]; });
  return JSON.stringify(arr.slice(0, 1000));
})();
