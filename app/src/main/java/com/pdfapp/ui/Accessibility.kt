package com.pdfapp.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.defaultMinSize
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
 * Raises a control to the 48 dp touch floor without enlarging what it draws.
 * Needed because Material 3's text and filled buttons stop at 40 dp, which is
 * below the accessibility minimum the rest of the chrome already meets.
 */
fun Modifier.touchTargetFloor(): Modifier =
    defaultMinSize(
        minWidth = DynamicType.MIN_TOUCH_TARGET_DP.dp,
        minHeight = DynamicType.MIN_TOUCH_TARGET_DP.dp,
    )

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
