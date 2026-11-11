package com.livetranslate.screen.core

/**
 * ترتيب القراءة للفقاعات/الكتل:
 * تُجمّع الكتل في "صفوف" حسب التداخل الرأسي، ثم تُرتّب داخل كل صف
 * من اليمين لليسار (مانغا يابانية) أو من اليسار لليمين (كوميكس/ويبتون).
 */
object ReadingOrder {

    fun <T> sort(items: List<T>, rtl: Boolean, boxOf: (T) -> Box): List<T> {
        if (items.size < 2) return items
        val byTop = items.sortedBy { boxOf(it).top }
        val rows = mutableListOf<MutableList<T>>()
        var rowBox: Box? = null
        for (item in byTop) {
            val b = boxOf(item)
            val current = rowBox
            val sameRow = current != null &&
                current.verticalOverlap(b) >= 0.5f * minOf(b.height, current.height).coerceAtLeast(1)
            if (sameRow) {
                rows.last().add(item)
                rowBox = current!!.union(b)
            } else {
                rows.add(mutableListOf(item))
                rowBox = b
            }
        }
        return rows.flatMap { row ->
            if (rtl) row.sortedByDescending { boxOf(it).right } else row.sortedBy { boxOf(it).left }
        }
    }
}

/**
 * يدمج كتل OCR المتقاربة في فقاعة واحدة (مانغا) أو سطر ترجمة واحد (فيديو).
 */
object BlockGrouper {

    fun group(blocks: List<SourceBlock>, rtl: Boolean, cjk: Boolean, gapFactor: Float = 0.8f): List<SourceBlock> {
        if (blocks.size < 2) return blocks
        val lineH = medianLineHeight(blocks)
        val gap = (lineH * gapFactor).toInt().coerceAtLeast(2)

        val parent = IntArray(blocks.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        for (i in blocks.indices) for (j in i + 1 until blocks.size) {
            val a = blocks[i].box
            val b = blocks[j].box
            val near = a.inflate(gap).intersects(b)
            val aligned = a.horizontalOverlap(b) > 0 || a.verticalOverlap(b) > 0
            if (near && aligned) parent[find(i)] = find(j)
        }
        val groups = blocks.indices.groupBy { find(it) }.values
        return groups.map { idx ->
            val members = idx.map { blocks[it] }
            if (members.size == 1) return@map members[0]
            val union = members.map { it.box }.reduce { acc, b -> acc.union(b) }
            val vertical = cjk && union.height > union.width * 1.3f
            val ordered = if (vertical) {
                // أعمدة رأسية: من اليمين لليسار في اليابانية/الصينية التقليدية
                members.sortedWith(compareByDescending<SourceBlock> { it.box.right }.thenBy { it.box.top })
            } else {
                ReadingOrder.sort(members, rtl) { it.box }
            }
            SourceBlock(
                text = TextTools.joinLines(ordered.map { it.text }, cjk),
                box = union,
                lines = ordered.flatMap { it.lines.ifEmpty { listOf(it.box) } },
                backgroundColor = members.firstNotNullOfOrNull { it.backgroundColor },
            )
        }
    }

    fun medianLineHeight(blocks: List<SourceBlock>): Int {
        val hs = blocks.flatMap { b -> b.lines.ifEmpty { listOf(b.box) } }
            .map { minOf(it.height, it.width) }
            .filter { it > 0 }
            .sorted()
        return if (hs.isEmpty()) 16 else hs[hs.size / 2]
    }
}
