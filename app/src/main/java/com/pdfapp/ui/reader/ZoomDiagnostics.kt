package com.pdfapp.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * TEMPORARY: instrumentation for verifying that a double-tap zoom lands where it
 * is supposed to, for any tap point. Debug builds only, and deleted once the
 * behaviour is trusted — nothing here is part of the reader.
 *
 * The point of it is that "looks right" is not a measurement. A double-tap is
 * supposed to bring the tapped content point to the middle of the scrolling
 * viewport; whether it did is a distance in pixels, and this reports that
 * distance for every tap rather than leaving it to the eye. Earlier rounds of
 * this bug were prolonged by reasoning about code that was, in the end, doing
 * exactly what it was told — so this measures the outcome instead.
 */

/**
 * The content point under a double-tap, pinned in terms that survive the zoom.
 *
 * [docX] is document-space — view x divided out by the zoom — and the vertical
 * pair is a page index plus a distance down that page. Neither changes when the
 * zoom does, so the same physical point can be located again afterwards and the
 * miss measured.
 */
data class ZoomProbe(
    val tap: Offset,
    val zoomBefore: Float,
    val docX: Float,
    val itemIndex: Int,
    val offsetInItem: Float,
)

/** Everything one double-tap did, measured after it settled. */
data class ZoomReading(
    val seq: Int,
    val tap: Offset,
    val constraintViewport: Size,
    val listViewport: Size,
    val density: Float,
    val zoomBefore: Float,
    val zoomAfter: Float,
    val k: Float,
    val panBefore: Float,
    val panRaw: Float,
    val panAfter: Float,
    val panMax: Float,
    val scrollAsked: Float,
    val scrollConsumed: Float,
    val itemBefore: Int,
    val offsetBefore: Int,
    val itemAfter: Int,
    val offsetAfter: Int,
    val itemSize: Int,
    val target: Offset,
    val actual: Offset?,
) {
    val error: Offset? get() = actual?.let { Offset(it.x - target.x, it.y - target.y) }

    val errorMagnitude: Float
        get() = error?.let { sqrt(it.x * it.x + it.y * it.y) } ?: Float.NaN

    /** Clamping is the usual reason a landing is legitimately unreachable. */
    val panClamped: Boolean get() = abs(panRaw - panAfter) > 1f

    val scrollClamped: Boolean get() = abs(scrollAsked - scrollConsumed) > 1f

    /**
     * A miss the clamps do not excuse. At a document edge the content genuinely
     * cannot travel far enough, so the landing is unreachable and a large error
     * is correct behaviour rather than a fault — calling those a failure would
     * train you to ignore the readout.
     */
    val verdict: String
        get() =
            when {
                actual == null -> "NO READING"
                errorMagnitude <= PASS_PX -> "OK"
                panClamped || scrollClamped -> "AT EDGE (clamped, expected)"
                else -> "FAIL"
            }

    fun summary(): String = "#$seq tap ${tap.x.toInt()},${tap.y.toInt()} -> ${errorLabel()}  $verdict"

    private fun errorLabel(): String =
        error?.let { "err ${it.x.toInt()},${it.y.toInt()} (${errorMagnitude.toInt()}px)" } ?: "-"

    fun detail(): String =
        buildString {
            appendLine("#$seq  $verdict")
            appendLine("tap        ${tap.x.toInt()}, ${tap.y.toInt()}")
            appendLine("target     ${target.x.toInt()}, ${target.y.toInt()}")
            appendLine("actual     ${actual?.let { "${it.x.toInt()}, ${it.y.toInt()}" } ?: "-"}")
            appendLine("ERROR      ${errorLabel()}")
            appendLine("zoom       ${fmt(zoomBefore)} -> ${fmt(zoomAfter)}   k ${fmt(k)}")
            appendLine(
                "panX       ${panBefore.toInt()} -> ${panAfter.toInt()}" +
                    "  raw ${panRaw.toInt()}  max ${panMax.toInt()}" +
                    if (panClamped) "  CLAMPED" else "",
            )
            appendLine(
                "scroll     asked ${scrollAsked.toInt()}  got ${scrollConsumed.toInt()}" +
                    if (scrollClamped) "  CLAMPED" else "",
            )
            appendLine("item       $itemBefore @ $offsetBefore -> $itemAfter @ $offsetAfter")
            appendLine("itemSize   $itemSize")
            appendLine(
                "viewport   constraint ${constraintViewport.width.toInt()}x${constraintViewport.height.toInt()}" +
                    "   list ${listViewport.width.toInt()}x${listViewport.height.toInt()}",
            )
            append("density    $density")
        }

    private fun fmt(value: Float) = String.format(Locale.US, "%.3f", value)

    companion object {
        /** Sub-pixel is the goal; a pixel of rounding is not a fault. */
        const val PASS_PX = 1.5f
    }
}

/**
 * Markers and numbers over the reader.
 *
 * A sibling of the list rather than a child, so the transform under
 * investigation cannot move or scale the marks describing it.
 */
@Composable
fun ZoomDiagnosticOverlay(
    current: ZoomReading?,
    history: List<ZoomReading>,
    worstPx: Float,
    constraintCentreY: Float,
    extra: String = "",
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val target = current?.target
        // Where the tapped point was supposed to land: the middle of the
        // scrolling viewport, drawn full width and height so it can be read
        // against the screen's own middle by eye as well as by number.
        if (target != null) {
            drawLine(
                color = TARGET,
                start = Offset(0f, target.y),
                end = Offset(size.width, target.y),
                strokeWidth = 2f,
            )
            drawLine(
                color = TARGET,
                start = Offset(target.x, 0f),
                end = Offset(target.x, size.height),
                strokeWidth = 2f,
            )
        }
        // The other candidate centre. The reader's incoming constraint and the
        // list's own viewport disagree on a device; drawing both shows which one
        // is the middle of what you can actually see.
        drawLine(
            color = ALTERNATE,
            start = Offset(0f, constraintCentreY),
            end = Offset(size.width, constraintCentreY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
        )
        // Where the finger went down.
        current?.let {
            drawCircle(color = TAP, radius = 14f, center = it.tap, style = Stroke(width = 3f))
        }
        // Where the tapped content point actually ended up, and the miss.
        current?.actual?.let { actual ->
            if (target != null) {
                drawLine(color = ACTUAL, start = target, end = actual, strokeWidth = 3f)
            }
            drawCircle(color = ACTUAL, radius = 10f, center = actual)
        }
    }
    Column(
        modifier =
            modifier
                .background(Color(0xE0000000))
                .padding(6.dp),
    ) {
        Text(
            text = current?.detail() ?: "double-tap anywhere",
            color = current.verdictColour(),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
        if (extra.isNotEmpty()) {
            Text(
                text = "\n$extra",
                color = Color(0xFF9FD8FF),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (history.isNotEmpty()) {
            Text(
                text =
                    "\nworst ${worstPx.toInt()}px over ${history.size} taps\n" +
                        history.takeLast(HISTORY_SHOWN).joinToString("\n") { it.summary() },
                color = Color(0xFFBBBBBB),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun ZoomReading?.verdictColour(): Color =
    when {
        this == null -> Color.White
        verdict == "OK" -> Color(0xFF7BE38B)
        verdict.startsWith("AT EDGE") -> Color(0xFFFFD37A)
        else -> Color(0xFFFF8A80)
    }

private const val HISTORY_SHOWN = 10
private val TARGET = Color(0xFF7BE38B)
private val ALTERNATE = Color(0xFFFFD37A)
private val ACTUAL = Color(0xFFFF5252)
private val TAP = Color(0xFF64B5F6)
