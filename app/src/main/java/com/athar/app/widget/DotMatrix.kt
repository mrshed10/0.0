package com.athar.app.widget

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 5×7 dot-matrix lettering (Nothing-style). Every lit dot is drawn as a
 * circle; unlit dots can be shown faintly for the "matrix" look.
 */
object DotMatrix {
    private val raw: Map<Char, List<String>> = mapOf(
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        ':' to listOf(".", "#", "#", ".", "#", "#", "."),
        '.' to listOf(".", ".", ".", ".", ".", ".", "#"),
        '-' to listOf("...", "...", "...", "###", "...", "...", "..."),
        '/' to listOf("....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...."),
        '%' to listOf("##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"),
        '+' to listOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
        ' ' to listOf("..", "..", "..", "..", "..", "..", ".."),
        'A' to listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to listOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to listOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to listOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
        'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to listOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to listOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to listOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        'J' to listOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to listOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to listOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to listOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to listOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to listOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to listOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to listOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to listOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
    )

    const val ROWS = 7

    /** Arabic-Indic digits are drawn with the same shapes as 0–9. */
    private fun normalize(c: Char): Char = when (c) {
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c.uppercaseChar()
    }

    fun supports(text: String) = text.isNotEmpty() && text.all { normalize(it) in raw }

    private fun glyph(c: Char) = raw[normalize(c)] ?: raw.getValue(' ')

    /** Width in dot columns (1 empty column between characters). */
    fun columns(text: String): Int =
        text.sumOf { glyph(it)[0].length } + (text.length - 1).coerceAtLeast(0)

    /**
     * Draws [text] with its top-left at ([x], [y]); [cell] = distance between
     * dot centres. Characters whose index is in [accentIndices] use [accent].
     */
    fun draw(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        cell: Float,
        color: Int,
        accent: Int = color,
        accentIndices: Set<Int> = emptySet(),
        ghost: Int? = null,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = cell * 0.38f
        var cx = x
        text.forEachIndexed { index, c ->
            val g = glyph(c)
            for (row in 0 until ROWS) {
                val line = g[row]
                for (col in line.indices) {
                    val px = cx + col * cell + cell / 2
                    val py = y + row * cell + cell / 2
                    if (line[col] == '#') {
                        paint.color = if (index in accentIndices) accent else color
                        canvas.drawCircle(px, py, r, paint)
                    } else if (ghost != null) {
                        paint.color = ghost
                        canvas.drawCircle(px, py, r * 0.8f, paint)
                    }
                }
            }
            cx += (g[0].length + 1) * cell
        }
    }
}
