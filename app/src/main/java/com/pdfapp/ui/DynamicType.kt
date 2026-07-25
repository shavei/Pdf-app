package com.pdfapp.ui

import kotlin.math.roundToInt

/**
 * Sizing rules for large fonts and display scale (mobile-ui-plan Phase F.1/F.3).
 *
 * Compose scales *text* by the system font scale automatically, but a dp box
 * around that text does not move — which is exactly how a fixed-height cell
 * clips its label at 2× font scale. These are the two rules the layouts need,
 * kept pure so the arithmetic is unit-testable on the JVM.
 */
object DynamicType {
    /** The accessibility floor for anything touchable, per Material guidance. */
    const val MIN_TOUCH_TARGET_DP: Int = 48

    /**
     * Ceiling on how far a *layout* box grows with the font scale. Text keeps
     * scaling past this; the box does not, because a phone-width grid that
     * tracked a 2× scale one-for-one would drop to a single column.
     */
    const val MAX_LAYOUT_SCALE: Float = 1.5f

    /**
     * [baseDp] grown by the user's font scale, clamped to [maxScale]. Scales
     * with the user but never below the design size, so a shrunk font setting
     * can't crush a thumbnail.
     */
    fun scaledDp(
        baseDp: Int,
        fontScale: Float,
        maxScale: Float = MAX_LAYOUT_SCALE,
    ): Int = (baseDp * fontScale.coerceIn(1f, maxScale.coerceAtLeast(1f))).roundToInt()
}
