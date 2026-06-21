package androidx.compose.material

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: SingleChoiceSegmentedButtonRow
// ==================

/* Container that holds a horizontal strip of mutually-exclusive segmented
   buttons. Just a horizontally-arranged Row with a bordered surface —
   children share borders with their neighbours so the strip reads as one
   pill instead of separate buttons. The active selection is controlled by
   the caller via SegmentedButton(selected = ..., onClick = ...). */
@Composable
fun SingleChoiceSegmentedButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = SegmentedButtonDefaults.Height),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

// ==================
// MARK: SegmentedButton
// ==================

/* One segment of a SingleChoiceSegmentedButtonRow. Renders as a flat pill
   when selected (background = primary, content = onPrimary) and a hollow
   outlined segment otherwise. The caller is responsible for snapping the
   appropriate corner radius via `shape` so the strip reads as a single
   pill (use SegmentedButtonDefaults.itemShape(index, count)). */
@Composable
fun SegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = SegmentedButtonDefaults.itemShape(0, 1),
    label: @Composable () -> Unit,
) {
    var vHover by remember { mutableStateOf(false) }

    val vBg: Color = when {
        !enabled -> Color.Transparent
        selected -> MaterialTheme.colors.primary.copy(alpha = if (vHover) 0.24f else 0.16f)
        vHover   -> Color(0x14FFFFFFL)
        else     -> Color.Transparent
    }
    val vBorderColor =
        if (selected) MaterialTheme.colors.primary else Color(0x33FFFFFFL)
    val vContentColor =
        if (selected) MaterialTheme.colors.primary
        else MaterialTheme.colors.onSurface

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = SegmentedButtonDefaults.Height)
            .background(vBg, shape)
            .border(BorderStroke(1.dp, vBorderColor), shape)
            .hoverable { vHover = it }
            .clickable { if (enabled) onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Provide a sensible default content color via a label slot — the
        // caller's Text/Icon picks vContentColor if it wants to track the
        // selected state, but it's exposed here for inspection in custom
        // contents. (No CompositionLocal LocalContentColor in this subset.)
        label()
    }
}

object SegmentedButtonDefaults {
    val Height: Dp = 36.dp

    /* Picks a shape with the appropriate rounded corners for the segment's
       position in the strip: left segment has rounded leading corners,
       middle has none, right has rounded trailing corners. count = 1 →
       fully rounded standalone segment. */
    fun itemShape(inIndex: Int, inCount: Int): androidx.compose.ui.graphics.Shape {
        val vR = 50  // percent — pill
        // RoundedCornerShape with per-corner control isn't in this subset,
        // so for inner positions we use a near-rectangle. This means the
        // strip reads as a continuous pill at the ends and a rectangle in
        // the middle, which is the same visual idiom as Material 3.
        return when {
            inCount <= 1            -> RoundedCornerShape(vR)
            inIndex == 0            -> RoundedCornerShape(vR)
            inIndex == inCount - 1  -> RoundedCornerShape(vR)
            else                    -> androidx.compose.ui.graphics.RectangleShape
        }
    }
}
