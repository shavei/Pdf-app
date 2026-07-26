package com.pdfapp.ui.common

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Compose-side accessibility helpers (mobile-ui-plan Phase F). The rules
// themselves live in the pure DynamicType / ReaderSemantics objects; this file
// only binds them to composition state.

/**
 * [baseDp] grown by the window's font scale (capped by
 * [DynamicType.MAX_LAYOUT_SCALE]), for boxes that must keep holding scaled text
 * — thumbnail cells, grid columns.
 */
@Composable
fun scaledDp(baseDp: Int): Dp = DynamicType.scaledDp(baseDp, LocalDensity.current.fontScale).dp

/**
 * Raises a control to the 48 dp touch floor without enlarging what it draws,
 * leaving it free to grow past that with its content. For text and filled
 * buttons, which Material 3 stops at 40 dp.
 */
fun Modifier.touchTargetFloor(): Modifier =
    defaultMinSize(
        minWidth = DynamicType.MIN_TOUCH_TARGET_DP.dp,
        minHeight = DynamicType.MIN_TOUCH_TARGET_DP.dp,
    )

/**
 * Sizes an icon button to the 48 dp floor.
 *
 * Material 3's `IconButton` draws a 40 dp state layer and expands only its
 * *touch* bounds, so the node it reports — what an accessibility scanner, a
 * layout inspector and a UI test all measure — stays 40 dp. An explicit size is
 * the only way to raise both: [touchTargetFloor]'s `defaultMinSize` cannot do
 * it, because `IconButton`'s own `.size()` overrides a minimum.
 */
fun Modifier.iconTouchTarget(): Modifier = size(DynamicType.MIN_TOUCH_TARGET_DP.dp)

/**
 * Whether a screen reader is currently exploring by touch.
 *
 * Extracting a page's text costs a PdfBox parse; it is only worth that IO when
 * something is going to read the result out loud, so the reader gates page-text
 * descriptions on this (Phase F.2). Tracks the setting live, so turning
 * TalkBack on mid-session starts populating descriptions without a restart.
 */
@Composable
fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager =
        remember(context) {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener =
            AccessibilityManager.TouchExplorationStateChangeListener { active -> enabled = active }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
