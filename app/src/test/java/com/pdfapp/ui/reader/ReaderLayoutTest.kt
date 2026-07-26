package com.pdfapp.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Adaptive-layout breakpoints (mobile-ui-plan Phase E): compact phone portrait
 * keeps the thumb-reachable bottom bar, short or wide windows move those actions
 * to a side rail, and only expanded widths dock the navigation pane.
 */
class ReaderLayoutTest {
    @Test
    fun compact_portrait_phone_keeps_the_bottom_bar() {
        val spec = ReaderLayout.spec(widthDp = 411, heightDp = 891)
        assertThat(spec.navStyle).isEqualTo(ReaderNavStyle.BOTTOM_BAR)
        assertThat(spec.docksThumbnails).isFalse()
    }

    @Test
    fun landscape_phone_moves_the_actions_to_the_rail() {
        // Short axis is scarce, so the bottom bar must not eat it — even though a
        // 640 dp landscape phone is only "medium" wide.
        val spec = ReaderLayout.spec(widthDp = 640, heightDp = 360)
        assertThat(spec.navStyle).isEqualTo(ReaderNavStyle.SIDE_RAIL)
        assertThat(spec.docksThumbnails).isFalse()
    }

    @Test
    fun a_short_but_narrow_window_still_gets_the_rail() {
        // Split-screen: narrower than the medium breakpoint but too short for
        // vertical chrome.
        val spec = ReaderLayout.spec(widthDp = 411, heightDp = 400)
        assertThat(spec.navStyle).isEqualTo(ReaderNavStyle.SIDE_RAIL)
    }

    @Test
    fun medium_width_gets_the_rail_but_no_docked_pane() {
        val spec = ReaderLayout.spec(widthDp = 700, heightDp = 900)
        assertThat(spec.navStyle).isEqualTo(ReaderNavStyle.SIDE_RAIL)
        assertThat(spec.docksThumbnails).isFalse()
    }

    @Test
    fun expanded_width_docks_the_navigation_pane_beside_the_page() {
        val spec = ReaderLayout.spec(widthDp = 1280, heightDp = 800)
        assertThat(spec.navStyle).isEqualTo(ReaderNavStyle.SIDE_RAIL)
        assertThat(spec.docksThumbnails).isTrue()
    }

    @Test
    fun breakpoints_are_inclusive_at_the_lower_bound() {
        val tall = 900
        assertThat(ReaderLayout.spec(ReaderLayout.MEDIUM_WIDTH_DP - 1, tall).navStyle)
            .isEqualTo(ReaderNavStyle.BOTTOM_BAR)
        assertThat(ReaderLayout.spec(ReaderLayout.MEDIUM_WIDTH_DP, tall).navStyle)
            .isEqualTo(ReaderNavStyle.SIDE_RAIL)

        assertThat(ReaderLayout.spec(ReaderLayout.EXPANDED_WIDTH_DP - 1, tall).docksThumbnails)
            .isFalse()
        assertThat(ReaderLayout.spec(ReaderLayout.EXPANDED_WIDTH_DP, tall).docksThumbnails)
            .isTrue()
    }

    @Test
    fun the_compact_height_breakpoint_is_exclusive_at_its_upper_bound() {
        val narrow = 411
        assertThat(ReaderLayout.spec(narrow, ReaderLayout.COMPACT_HEIGHT_DP - 1).navStyle)
            .isEqualTo(ReaderNavStyle.SIDE_RAIL)
        assertThat(ReaderLayout.spec(narrow, ReaderLayout.COMPACT_HEIGHT_DP).navStyle)
            .isEqualTo(ReaderNavStyle.BOTTOM_BAR)
    }
}
