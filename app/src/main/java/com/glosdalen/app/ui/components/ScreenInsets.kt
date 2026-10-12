package com.glosdalen.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Vertically scrolling screen body for the edge-to-edge window (enforced from targetSdk 35).
 *
 * Keeps content clear of display cutouts and side navigation bars, and shrinks the viewport
 * above the keyboard so focused text fields can be scrolled into view. The bottom navigation
 * bar inset is applied inside the scrolled content: content scrolls behind the translucent bar,
 * and the last item can still be scrolled above it. While the keyboard is open, the IME inset
 * already covers the navigation bar, so that padding drops to zero.
 *
 * The top inset is not handled here: a Material 3 `TopAppBar` above the body pads it, and
 * screens without one must pad [WindowInsetsSides.Top] themselves.
 */
@Composable
fun Modifier.screenBodyVerticalScroll(state: ScrollState): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    .imePadding()
    .verticalScroll(state)
    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
