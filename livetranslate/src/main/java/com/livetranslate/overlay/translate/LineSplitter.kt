package com.livetranslate.overlay.translate

/**
 * يوزّع الترجمة على أسطر النص الأصلي، بحيث يُرسم كل جزء داخل صندوق سطره الأصلي تماماً
 * (نفس العرض والارتفاع والموضع) بدلاً من صندوق واحد كبير للفقرة.
 *
 * التوزيع يتناسب مع عرض كل سطر أصلي: السطر العريض يأخذ كلمات أكثر.
 * الكلمات لا تُقسم؛ في اللغات بلا مسافات (الصينية/اليابانية/التايلاندية) يُوزَّع حرفاً حرفاً.
 */
object LineSplitter {

    fun distribute(text: String, lineWidths: List<Float>, spaced: Boolean): List<String> {
        val n = lineWidths.size
        if (n == 0) return emptyList()
        val clean = text.trim()
        if (n == 1) return listOf(clean)

        val tokens: List<String> = if (spaced) {
            clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        } else {
            clean.codePoints().toArray().map { String(Character.toChars(it)) }.filter { it.isNotBlank() }
        }
        val separator = if (spaced) " " else ""
        if (tokens.isEmpty()) return List(n) { "" }

        val totalWidth = lineWidths.sum().takeIf { it > 0f } ?: n.toFloat()
        val weights = lineWidths.map { if (lineWidths.sum() > 0f) it / totalWidth else 1f / n }
        val totalChars = tokens.sumOf { it.length }.toFloat() + (tokens.size - 1) * separator.length

        val result = MutableList(n) { mutableListOf<String>() }
        var line = 0
        var consumed = 0f
        var boundary = weights[0] * totalChars
        for ((i, token) in tokens.withIndex()) {
            val remainingTokens = tokens.size - i
            val remainingLines = n - line
            // انتقل لسطر جديد إن تجاوزنا حصة السطر الحالي (مع ترك كلمة على الأقل لكل سطر متبقٍّ إن أمكن)
            val midpoint = consumed + token.length / 2f
            val mustAdvance = remainingTokens <= remainingLines - 1 && result[line].isNotEmpty()
            if (line < n - 1 && result[line].isNotEmpty() && (midpoint > boundary || mustAdvance)) {
                line++
                boundary += weights[line] * totalChars
            }
            result[line] += token
            consumed += token.length + separator.length
        }
        return result.map { it.joinToString(separator) }
    }
}
