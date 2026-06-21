package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onGloballyPositioned
import androidx.compose.foundation.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PositionedPopup
import kotlinx.coroutines.delay

// ==================
// MARK: TooltipBox
// ==================

/* Wraps any clickable/hoverable target so a tooltip appears below it after
   a hover delay. Anchored to the target's window-coordinate position via
   onGloballyPositioned — the popup lands directly under the target wherever
   it lives in the layout tree. The popup uses no scrim (clicks below pass
   through); it auto-hides on hover-out. */
@Composable
fun TooltipBox(
    text: String,
    modifier: Modifier = Modifier,
    delayMillis: Long = TooltipDefaults.DelayMillis,
    content: @Composable () -> Unit,
) {
    var vHover by remember { mutableStateOf(false) }
    var vVisible by remember { mutableStateOf(false) }
    var vPos by remember { mutableStateOf(IntOffset.Zero) }
    var vHeight by remember { mutableStateOf(0) }

    LaunchedEffect(vHover) {
        if (vHover) {
            delay(delayMillis)
            if (vHover) vVisible = true
        } else {
            vVisible = false
        }
    }

    Box(
        modifier = modifier
            .hoverable { vHover = it }
            .onGloballyPositioned { vPos = it }
            .onSizeChanged { vHeight = it.height }
    ) {
        content()
        if (vVisible) {
            PositionedPopup(
                x = vPos.x.dp,
                y = (vPos.y + vHeight + TooltipDefaults.GapDp.value.toInt()).dp,
                onDismissRequest = { vVisible = false },
            ) {
                Box(
                    modifier = Modifier
                        .background(TooltipDefaults.BackgroundColor, TooltipDefaults.Shape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = text,
                        color = TooltipDefaults.ContentColor,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

object TooltipDefaults {
    val DelayMillis: Long = 600L
    val BackgroundColor: Color = Color(0xE6111111L)
    val ContentColor: Color = Color.White
    val Shape = RoundedCornerShape(4.dp)
    /* Vertical gap between the target's bottom edge and the tooltip's top. */
    val GapDp: Dp = 4.dp
}
