package com.compose.desktop.native.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: Toolbar
// ==================

/* Horizontal action strip. Sits across the top of a window or inside a
   panel — denser than Material's TopAppBar and without the elevated title.
   The content slot is a single Row, so put IconButtons, ToggleButtons,
   VerticalDividers etc. directly inside it. A 1-px Divider runs along the
   bottom; pass showDivider = false to suppress it.

   Project-original (no androidx mirror) — desktop-only affordance, so this
   lives under com.compose.desktop.native.widgets. */
@Composable
fun Toolbar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.surface,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolbarDefaults.Height)
                .background(backgroundColor)
                .padding(horizontal = 8.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
        if (showDivider) Divider()
    }
}

// ==================
// MARK: StatusBar
// ==================

/* Thin bar that hugs the bottom of a window. Holds short status text and
   right-aligned indicators (zoom, line:col, etc.). Uses a smaller height
   than Toolbar so it doesn't crowd the chrome. */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.surface,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(StatusBarDefaults.Height)
                .background(backgroundColor)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

object ToolbarDefaults {
    val Height: Dp = 40.dp
}

object StatusBarDefaults {
    val Height: Dp = 24.dp
}
