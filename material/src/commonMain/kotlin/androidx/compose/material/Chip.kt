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
import androidx.compose.ui.unit.sp

// ==================
// MARK: Chip
// ==================

/* Flat clickable chip (no selected state). Use it for actions: "Add filter",
   "Show all", quick navigation tags. Inside the chip you can put a label, an
   Icon, or both — Chip just provides the rounded surface and hover/click. */
@Composable
fun Chip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke? = ChipDefaults.outlinedBorder,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colors.onSurface,
    content: @Composable () -> Unit,
) {
    var vHover by remember { mutableStateOf(false) }
    val vBg = when {
        !enabled -> Color.Transparent
        vHover   -> contentColor.copy(alpha = 0.08f)
        else     -> backgroundColor
    }

    var m: Modifier = modifier
        .defaultMinSize(minHeight = ChipDefaults.MinHeight)
        .background(vBg, ChipDefaults.Shape)
    if (border != null) m = m.border(border, ChipDefaults.Shape)
    m = m
        .hoverable { vHover = it }
        .clickable { if (enabled) onClick() }
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Box(modifier = m, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

// ==================
// MARK: FilterChip
// ==================

/* Toggleable chip — leading checkmark glyph when selected. Used in filter
   strips, multi-select rails, etc. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    var vHover by remember { mutableStateOf(false) }

    val vBg = when {
        !enabled -> Color.Transparent
        selected -> MaterialTheme.colors.primary.copy(alpha = if (vHover) 0.24f else 0.16f)
        vHover   -> Color(0x14FFFFFFL)
        else     -> Color.Transparent
    }
    val vBorder = if (selected) BorderStroke(1.dp, MaterialTheme.colors.primary)
                  else ChipDefaults.outlinedBorder

    var m: Modifier = modifier
        .defaultMinSize(minHeight = ChipDefaults.MinHeight)
        .background(vBg, ChipDefaults.Shape)
        .border(vBorder, ChipDefaults.Shape)
        .hoverable { vHover = it }
        .clickable { if (enabled) onClick() }
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Box(modifier = m, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Text(
                    text = "✓ ",
                    color = MaterialTheme.colors.primary,
                    fontSize = 14.sp,
                )
            }
            label()
        }
    }
}

object ChipDefaults {
    val MinHeight: Dp = 28.dp
    val Shape = RoundedCornerShape(50)
    val outlinedBorder: BorderStroke = BorderStroke(1.dp, Color(0x33FFFFFFL))
}
