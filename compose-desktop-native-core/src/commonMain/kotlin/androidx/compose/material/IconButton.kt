package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: IconButton
// ==================

/* Round clickable wrapper around an Icon. 40dp hit target with hover/press
   state-layer overlay (Material's "icon button" affordance). The content
   slot is just whatever Icon / Image / Text you put inside. */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var vHovered by remember { mutableStateOf(false) }

    val vBg: Color = when {
        !enabled -> Color.Transparent
        vHovered -> Color(0x14FFFFFFL)
        else     -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(IconButtonDefaults.Size)
            .background(vBg, CircleShape)
            .hoverable { vHovered = it }
            .clickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

object IconButtonDefaults {
    val Size: Dp = 40.dp
}
