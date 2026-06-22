package com.compose.desktop.native.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onDrag
import androidx.compose.foundation.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: SplitPane
// ==================

/* Two-pane resizable split. Desktop staple. `initialFirstSize` seeds the
   first pane's width (horizontal split) or height (vertical split); after
   first layout it tracks the user's drag. `minFirstSize` / `minSecondSize`
   prevent the divider from going off-screen — neither pane can shrink past
   their minimum.

   This isn't a mirror of any androidx API (Compose Multiplatform has a
   ResizablePane in the experimental panes lib but it's not in the
   stable surface), so it lives under com.compose.desktop.native.widgets. */
@Composable
fun HorizontalSplitPane(
    modifier: Modifier = Modifier,
    initialFirstSize: Dp = 200.dp,
    minFirstSize: Dp = 50.dp,
    minSecondSize: Dp = 50.dp,
    dividerColor: Color = Color(0x33FFFFFFL),
    dividerHoverColor: Color = Color(0x66FFFFFFL),
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    var vTotalWidth by remember { mutableStateOf(0) }
    var vFirstWidth by remember { mutableStateOf(initialFirstSize.value.toInt()) }
    var vDividerHover by remember { mutableStateOf(false) }

    val vDividerW = SplitPaneDefaults.DividerThickness.value.toInt()
    val vMinFirst = minFirstSize.value.toInt()
    val vMinSecond = minSecondSize.value.toInt()

    Row(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { vTotalWidth = it.width },
    ) {
        val vClampedFirst = if (vTotalWidth > 0)
            vFirstWidth.coerceIn(vMinFirst, (vTotalWidth - vDividerW - vMinSecond).coerceAtLeast(vMinFirst))
            else vFirstWidth
        val vSecondWidth = (vTotalWidth - vClampedFirst - vDividerW).coerceAtLeast(0)

        Box(modifier = Modifier.width(vClampedFirst.dp).fillMaxHeight()) { first() }

        // Fixed-width hit/drag slot; the visible line sits centred inside it and
        // only thickens on hover, so the panes never shift.
        Box(
            modifier = Modifier
                .width(SplitPaneDefaults.DividerThickness)
                .fillMaxHeight()
                .hoverable { vDividerHover = it }
                .onDrag(
                    onDrag = { x, _ ->
                        // onDrag's x is relative to the divider's *current*
                        // top-left (the dispatcher passes pointerX - node.absoluteX
                        // each move). Add it to the divider's live offset read from
                        // state — NOT the value captured when the press began, or
                        // the reference and the delta disagree every frame and the
                        // divider oscillates between the two.
                        val vMax = (vTotalWidth - vDividerW - vMinSecond).coerceAtLeast(vMinFirst)
                        val vLive = if (vTotalWidth > 0) vFirstWidth.coerceIn(vMinFirst, vMax) else vFirstWidth
                        vFirstWidth = (vLive + x).coerceIn(vMinFirst, vMax)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(if (vDividerHover) SplitPaneDefaults.DividerHoverThickness else SplitPaneDefaults.DividerLineThickness)
                    .fillMaxHeight()
                    .background(if (vDividerHover) dividerHoverColor else dividerColor),
            )
        }

        Box(modifier = Modifier.width(vSecondWidth.dp).fillMaxHeight()) { second() }
    }
}

@Composable
fun VerticalSplitPane(
    modifier: Modifier = Modifier,
    initialFirstSize: Dp = 150.dp,
    minFirstSize: Dp = 50.dp,
    minSecondSize: Dp = 50.dp,
    dividerColor: Color = Color(0x33FFFFFFL),
    dividerHoverColor: Color = Color(0x66FFFFFFL),
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    var vTotalHeight by remember { mutableStateOf(0) }
    var vFirstHeight by remember { mutableStateOf(initialFirstSize.value.toInt()) }
    var vDividerHover by remember { mutableStateOf(false) }

    val vDividerH = SplitPaneDefaults.DividerThickness.value.toInt()
    val vMinFirst = minFirstSize.value.toInt()
    val vMinSecond = minSecondSize.value.toInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { vTotalHeight = it.height },
    ) {
        val vClampedFirst = if (vTotalHeight > 0)
            vFirstHeight.coerceIn(vMinFirst, (vTotalHeight - vDividerH - vMinSecond).coerceAtLeast(vMinFirst))
            else vFirstHeight
        val vSecondHeight = (vTotalHeight - vClampedFirst - vDividerH).coerceAtLeast(0)

        Box(modifier = Modifier.height(vClampedFirst.dp).fillMaxWidth()) { first() }

        // Fixed-height hit/drag slot; centred line thickens on hover only.
        Box(
            modifier = Modifier
                .height(SplitPaneDefaults.DividerThickness)
                .fillMaxWidth()
                .hoverable { vDividerHover = it }
                .onDrag(
                    onDrag = { _, y ->
                        // y is relative to the divider's *current* top — add it to
                        // the divider's live offset read from state, not the press-
                        // time capture, otherwise the divider oscillates each frame.
                        val vMax = (vTotalHeight - vDividerH - vMinSecond).coerceAtLeast(vMinFirst)
                        val vLive = if (vTotalHeight > 0) vFirstHeight.coerceIn(vMinFirst, vMax) else vFirstHeight
                        vFirstHeight = (vLive + y).coerceIn(vMinFirst, vMax)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(if (vDividerHover) SplitPaneDefaults.DividerHoverThickness else SplitPaneDefaults.DividerLineThickness)
                    .fillMaxWidth()
                    .background(if (vDividerHover) dividerHoverColor else dividerColor),
            )
        }

        Box(modifier = Modifier.height(vSecondHeight.dp).fillMaxWidth()) { second() }
    }
}

object SplitPaneDefaults {
    val DividerThickness: Dp = 8.dp        // reserved hit/drag slot (fixed; layout never shifts)
    val DividerLineThickness: Dp = 1.dp    // visible line at rest
    val DividerHoverThickness: Dp = 3.dp   // visible line when hovered
}
