package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.blend
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: FloatingActionButton
// ==================

/* Filled rounded square / circle (depending on shape) with a content slot.
   No shadow — the renderer has no shadow primitive — but the bg/hover/press
   states match the standard Button. Use ExtendedFloatingActionButton for the
   pill variant that hugs a label. */
@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.Shape,
    backgroundColor: Color = MaterialTheme.colors.secondary,
    contentColor: Color = MaterialTheme.colors.onSecondary,
    content: @Composable () -> Unit,
) {
    var vHover by remember { mutableStateOf(false) }
    var vPress by remember { mutableStateOf(false) }
    val vBg = when {
        vPress -> backgroundColor.blend(contentColor, 0.12f)
        vHover -> backgroundColor.blend(contentColor, 0.08f)
        else   -> backgroundColor
    }
    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = FloatingActionButtonDefaults.Size,
                minHeight = FloatingActionButtonDefaults.Size,
            )
            .background(vBg, shape)
            .hoverable { vHover = it }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.secondary,
    contentColor: Color = MaterialTheme.colors.onSecondary,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        backgroundColor = backgroundColor,
        contentColor = contentColor,
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Box(modifier = Modifier.padding(start = 12.dp)) { text() }
            }
        }
    }
}

object FloatingActionButtonDefaults {
    val Size: Dp = 56.dp
    val Shape = RoundedCornerShape(16.dp)
}
