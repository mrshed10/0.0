package app.safar.browser.translate.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * تحليل فقاعة الحوار حول كتلة نص (دوال نقية على مصفوفة بكسلات ARGB — قابلة للاختبار):
 *  1) لون الخلفية = وسيط البكسلات في حلقة رفيعة حول النص.
 *  2) هل الخلفية موحّدة؟ (فقاعة بيضاء/ملونة)  أم رسم/screentone؟
 *  3) ملء متدفق (flood fill) من الحلقة عبر البكسلات المشابهة للخلفية حتى الحدود السوداء
 *     ⇒ المساحة الداخلية للفقاعة لنضع الترجمة داخلها لا خارجها.
 *  4) مستطيل التنظيف = مستطيل النص + هامش صغير، مقصوصاً داخل الفقاعة فلا يمس الحدود السوداء.
 */
object BubbleAnalyzer {

    data class Bubble(
        /** منطقة تُطلى بلون الخلفية لإخفاء النص الأصلي (null = لا تنظيف: الخلفية ليست موحّدة). */
        val clean: Box?,
        /** المنطقة التي تُكتب فيها الترجمة (داخل الفقاعة). */
        val textArea: Box,
        /** لون الخلفية RGB. */
        val bg: Int,
        val uniform: Boolean,
        /** هل يجب أن يكون النص داكناً (خلفية فاتحة)؟ */
        val darkText: Boolean,
        /** المساحة الداخلية للفقاعة حتى الحد الأسود (null إن لم تُحدد) — لتجميع كتل الفقاعة الواحدة. */
        val interior: Box? = null,
    )

    fun lum(c: Int): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

    private fun similar(a: Int, b: Int, tol: Int): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tol &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tol &&
            abs((a and 0xFF) - (b and 0xFF)) <= tol

    /**
     * @param exclude صناديق كتل النص الأخرى: تُستبعد بكسلاتها من حلقة تقدير الخلفية
     *                (في الفقاعات المزدحمة تقع الأسطر المجاورة داخل الحلقة فتُفسد الحكم بأن الخلفية موحّدة).
     */
    fun analyze(px: IntArray, w: Int, h: Int, text: Box, exclude: List<Box> = emptyList()): Bubble {
        val tl = text.left.toInt().coerceIn(0, w - 1)
        val tt = text.top.toInt().coerceIn(0, h - 1)
        val tr = text.right.toInt().coerceIn(tl + 1, w)
        val tb = text.bottom.toInt().coerceIn(tt + 1, h)

        // ---- 1) الحلقة المحيطة بالنص
        val ring = ArrayList<Int>()
        val inner = 2; val outer = 7
        for (y in max(0, tt - outer) until min(h, tb + outer)) {
            for (x in max(0, tl - outer) until min(w, tr + outer)) {
                val insideInner = x >= tl - inner && x < tr + inner && y >= tt - inner && y < tb + inner
                if (insideInner) continue
                if (exclude.any { e -> x >= e.left && x < e.right && y >= e.top && y < e.bottom }) continue
                ring += px[y * w + x]
            }
        }
        if (ring.isEmpty()) return Bubble(null, text, 0xFFFFFF, false, true)
        val bg = medianColor(ring)
        val bgLum = lum(bg)
        val lums = ring.map { lum(it) }
        val mean = lums.average()
        val std = sqrt(lums.sumOf { (it - mean) * (it - mean) } / lums.size)
        val closeFrac = lums.count { abs(it - bgLum) <= 40 }.toDouble() / lums.size
        // الفقاعات البيضاء تبقى "موحّدة" حتى لو لامست الحلقةُ الحدَّ الأسود أو بقايا الحروف
        val uniform = (std < 38 && closeFrac >= 0.72) || (bgLum >= 200 && closeFrac >= 0.6)
        val darkText = bgLum > 110

        if (!uniform) {
            return Bubble(null, expand(text, 0.06f, w, h), bg and 0xFFFFFF, false, darkText)
        }

        // ---- 3) ملء متدفق داخل نافذة محدودة حول النص
        val reach = (max(tr - tl, tb - tt) * 1.4f).toInt().coerceAtLeast(40)
        val wl = max(0, tl - reach); val wt = max(0, tt - reach)
        val wr = min(w, tr + reach); val wb = min(h, tb + reach)
        val ww = wr - wl; val wh = wb - wt
        val visited = BooleanArray(ww * wh)
        val queue = IntArray(ww * wh)
        var head = 0; var tail = 0
        fun push(x: Int, y: Int) {
            if (x < wl || x >= wr || y < wt || y >= wb) return
            val i = (y - wt) * ww + (x - wl)
            if (visited[i]) return
            if (!similar(px[y * w + x], bg, 40)) return
            visited[i] = true; queue[tail++] = i
        }
        // البذور: بكسلات الحلقة الداخلية المشابهة للخلفية
        for (x in tl - inner until tr + inner) { push(x, tt - inner); push(x, tb + inner - 1) }
        for (y in tt - inner until tb + inner) { push(tl - inner, y); push(tr + inner - 1, y) }
        var minX = tl; var minY = tt; var maxX = tr; var maxY = tb
        while (head < tail) {
            val i = queue[head++]
            val x = i % ww + wl; val y = i / ww + wt
            if (x < minX) minX = x; if (x + 1 > maxX) maxX = x + 1
            if (y < minY) minY = y; if (y + 1 > maxY) maxY = y + 1
            push(x + 1, y); push(x - 1, y); push(x, y + 1); push(x, y - 1)
        }
        val edges = (if (minX <= wl) 1 else 0) + (if (maxX >= wr) 1 else 0) + (if (minY <= wt) 1 else 0) + (if (maxY >= wb) 1 else 0)
        // هامش يغطي حواف الحروف المنعّمة (antialiasing) حتى لا تبقى ظلال للنص الأصلي
        val pad = max(3, ((tb - tt) * 0.12f).toInt()).coerceAtMost(8)
        val clean = Box(
            (tl - pad).toFloat(), (tt - pad).toFloat(), (tr + pad).toFloat(), (tb + pad).toFloat(),
        )
        if (edges >= 2) {
            // تسرّب: فقاعة بلا حدود أو نص على خلفية مفتوحة ⇒ نستخدم محيط النص مع هامش
            val area = expand(text, 0.15f, w, h)
            return Bubble(clamp(clean, w, h), area, bg and 0xFFFFFF, true, darkText)
        }
        val interior = Box(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
        // المستطيل المحاط داخل شكل بيضاوي ≈ 70% من أبعاده ⇒ نقلّص 15% من كل جهة، ولا يقل عن مساحة النص الأصلي
        val sx = interior.width * 0.15f; val sy = interior.height * 0.15f
        val inscribed = Box(interior.left + sx, interior.top + sy, interior.right - sx, interior.bottom - sy).union(text)
        val area = intersect(inscribed, interior) ?: text
        return Bubble(intersect(clean, interior) ?: clamp(clean, w, h), area, bg and 0xFFFFFF, true, darkText, interior)
    }

    /**
     * تجميع الكتل التي تقع في الفقاعة نفسها (مثل أعمدة النص الياباني العمودي) اعتماداً على حدود الفقاعة الفعلية،
     * لا على المسافة ⇒ فقاعتان متجاورتان لا تُدمجان أبداً لأن الحد الأسود يفصل بينهما.
     * @return مجموعات فهارس.
     */
    fun groupByBubble(blocks: List<Box>, bubbles: List<Bubble>): List<List<Int>> {
        val parent = IntArray(blocks.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; parent[x] = r; return r }
        fun inside(b: Box, r: Box): Boolean {
            val cx = (b.left + b.right) / 2; val cy = (b.top + b.bottom) / 2
            return cx >= r.left && cx <= r.right && cy >= r.top && cy <= r.bottom
        }
        for (i in blocks.indices) for (j in i + 1 until blocks.size) {
            val ii = bubbles[i].interior ?: continue
            val jj = bubbles[j].interior ?: continue
            if (inside(blocks[j], ii) && inside(blocks[i], jj) && iou(ii, jj) > 0.5f) parent[find(j)] = find(i)
        }
        return blocks.indices.groupBy { find(it) }.values.toList()
    }

    fun iou(a: Box, b: Box): Float {
        val l = max(a.left, b.left); val t = max(a.top, b.top)
        val r = min(a.right, b.right); val bt = min(a.bottom, b.bottom)
        if (r <= l || bt <= t) return 0f
        val inter = (r - l) * (bt - t)
        return inter / (a.width * a.height + b.width * b.height - inter)
    }

    fun medianColor(colors: List<Int>): Int {
        fun med(shift: Int): Int {
            val v = colors.map { (it shr shift) and 0xFF }.sorted()
            return v[v.size / 2]
        }
        return (0xFF shl 24) or (med(16) shl 16) or (med(8) shl 8) or med(0)
    }

    private fun expand(b: Box, f: Float, w: Int, h: Int): Box {
        val dx = b.width * f; val dy = b.height * f
        return clamp(Box(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy), w, h)
    }

    private fun clamp(b: Box, w: Int, h: Int) =
        Box(b.left.coerceAtLeast(0f), b.top.coerceAtLeast(0f), b.right.coerceAtMost(w.toFloat()), b.bottom.coerceAtMost(h.toFloat()))

    private fun intersect(a: Box, b: Box): Box? {
        val l = max(a.left, b.left); val t = max(a.top, b.top)
        val r = min(a.right, b.right); val bt = min(a.bottom, b.bottom)
        return if (r - l < 2 || bt - t < 2) null else Box(l, t, r, bt)
    }

    fun hex(rgb: Int) = String.format("#%06X", rgb and 0xFFFFFF)
}
