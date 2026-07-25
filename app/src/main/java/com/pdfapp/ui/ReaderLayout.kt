package com.pdfapp.ui

/** Where the reader's primary actions live at the current window size. */
enum class ReaderNavStyle {
    /** Compact phone portrait: a bottom bar, within reach of the thumb. */
    BOTTOM_BAR,

    /** Short or wide windows: a slim start-side rail that spares the short axis. */
    SIDE_RAIL,
}

/** The reader chrome arrangement resolved for one window size. */
data class ReaderLayoutSpec(
    val navStyle: ReaderNavStyle,
    /** Whether the thumbnail/outline pane docks beside the page instead of overlaying it. */
    val docksThumbnails: Boolean,
)

/**
 * Pure adaptive-layout rules for the reader (mobile-ui-plan Phase E). Kept free
 * of Compose — like [ReaderChrome] and [ReaderBack] — so the breakpoint maths is
 * unit-testable on the JVM; [com.pdfapp.ui.reader.ReaderBody] renders whatever
 * this resolves.
 *
 * The thresholds are the Material 3 window-size-class breakpoints, applied to the
 * window's own measured size (so split-screen and foldable resizes are tracked)
 * rather than to the physical screen.
 */
object ReaderLayout {
    /** At or above this width the window is at least "medium" (small tablet, phone landscape). */
    const val MEDIUM_WIDTH_DP: Int = 600

    /** At or above this width the window is "expanded" (tablet, unfolded foldable). */
    const val EXPANDED_WIDTH_DP: Int = 840

    /** Below this height the short axis is scarce and vertical chrome must go. */
    const val COMPACT_HEIGHT_DP: Int = 480

    /** Width of the docked thumbnail/outline pane on expanded windows. */
    const val PANE_WIDTH_DP: Int = 240

    /**
     * A window's reader layout: compact portrait keeps the thumb-reachable bottom
     * bar; anything wide enough to spare the width — or too short to spare the
     * height — moves those actions to a side rail; expanded widths additionally
     * dock the thumbnail/outline pane beside the page.
     */
    fun spec(
        widthDp: Int,
        heightDp: Int,
    ): ReaderLayoutSpec {
        val wide = widthDp >= MEDIUM_WIDTH_DP
        val short = heightDp < COMPACT_HEIGHT_DP
        return ReaderLayoutSpec(
            navStyle = if (wide || short) ReaderNavStyle.SIDE_RAIL else ReaderNavStyle.BOTTOM_BAR,
            docksThumbnails = widthDp >= EXPANDED_WIDTH_DP,
        )
    }
}
