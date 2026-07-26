package com.pdfapp.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase F.1 (mobile-ui-plan): boxes that hold scaled text grow with the font
 * scale — but only so far, because a phone-width grid that tracked a 2× scale
 * one-for-one would collapse to a single column.
 */
class DynamicTypeTest {
    @Test
    fun default_font_scale_leaves_the_design_size_untouched() {
        assertThat(DynamicType.scaledDp(104, fontScale = 1f)).isEqualTo(104)
    }

    @Test
    fun a_larger_font_scale_grows_the_box() {
        assertThat(DynamicType.scaledDp(100, fontScale = 1.3f)).isEqualTo(130)
    }

    @Test
    fun growth_stops_at_the_layout_cap() {
        assertThat(DynamicType.scaledDp(100, fontScale = 2f))
            .isEqualTo((100 * DynamicType.MAX_LAYOUT_SCALE).toInt())
        assertThat(DynamicType.scaledDp(100, fontScale = 10f))
            .isEqualTo(DynamicType.scaledDp(100, fontScale = DynamicType.MAX_LAYOUT_SCALE))
    }

    @Test
    fun a_shrunken_font_scale_never_shrinks_the_box_below_its_design_size() {
        assertThat(DynamicType.scaledDp(104, fontScale = 0.85f)).isEqualTo(104)
    }

    @Test
    fun the_touch_floor_is_the_material_accessibility_minimum() {
        assertThat(DynamicType.MIN_TOUCH_TARGET_DP).isEqualTo(48)
    }
}
