package com.pdfapp.persistence

import java.text.Bidi
import java.text.BreakIterator

/**
 * Reorder a single line of logical-order text into visual order for PDF text
 * showing. PDF content streams place glyphs strictly left-to-right with no bidi
 * algorithm of their own, so RTL runs (Hebrew, …) must be reordered before
 * [com.tom_roush.pdfbox.pdmodel.PDPageContentStream.showText] or they render
 * backwards. Pure-LTR text is returned unchanged.
 */
internal fun String.toVisualOrder(): String {
    if (!Bidi.requiresBidi(toCharArray(), 0, length)) return this
    val bidi = Bidi(this, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
    val levels = ByteArray(bidi.runCount) { bidi.getRunLevel(it).toByte() }
    val runs: Array<Any> =
        Array(bidi.runCount) { i ->
            val run = substring(bidi.getRunStart(i), bidi.getRunLimit(i))
            if (bidi.getRunLevel(i) % 2 == 1) run.reversedByGrapheme() else run
        }
    Bidi.reorderVisually(levels, 0, runs, 0, bidi.runCount)
    return runs.joinToString(separator = "")
}

/**
 * Reverse by grapheme cluster rather than by char, so combining marks (e.g.
 * Hebrew nikud) stay attached to their base letter and surrogate pairs are not
 * split.
 */
private fun String.reversedByGrapheme(): String {
    val boundaries = BreakIterator.getCharacterInstance().also { it.setText(this) }
    val out = StringBuilder(length)
    var end = boundaries.last()
    var start = boundaries.previous()
    while (start != BreakIterator.DONE) {
        out.append(this, start, end)
        end = start
        start = boundaries.previous()
    }
    return out.toString()
}
