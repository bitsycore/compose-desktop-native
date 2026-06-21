package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.onDrag
import androidx.compose.foundation.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ==================
// MARK: Slider
// ==================

/* Single-thumb continuous slider with optional integer steps. The thumb
   tracks pointer position during drag (onDrag fires while the pointer is
   captured even outside the track), and the visible track splits into a
   filled "active" half and a dimmed "inactive" half at the thumb position.

   `steps` = number of intermediate stops between the range endpoints; the
   value snaps to (valueRange.endpoints + steps) discrete positions when
   non-zero. Pass `onValueChangeFinished` to act on drag release (e.g. push
   the final value into a persistent store while the intermediate values
   only update visual state). */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
) {
    var vTrackWidth by remember { mutableStateOf(0) }

    val vMin = valueRange.start
    val vMax = valueRange.endInclusive
    val vSpan = (vMax - vMin).coerceAtLeast(1e-6f)
    val vClamped = value.coerceIn(vMin, vMax)
    val vFraction = ((vClamped - vMin) / vSpan).coerceIn(0f, 1f)

    fun fractionToValue(inF: Float): Float {
        var v = vMin + inF.coerceIn(0f, 1f) * vSpan
        if (steps > 0) {
            val vStepSize = vSpan / (steps + 1)
            v = vMin + (((v - vMin) / vStepSize).roundToInt() * vStepSize)
        }
        return v.coerceIn(vMin, vMax)
    }

    fun positionToValue(inXPx: Int) {
        if (vTrackWidth <= 0) return
        val vF = inXPx.toFloat() / vTrackWidth.toFloat()
        val vNew = fractionToValue(vF)
        if (vNew != value) onValueChange(vNew)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderDefaults.TouchHeight)
            .onSizeChanged { vTrackWidth = max(0, it.width) }
            .onDrag(
                onStart = { x, _ -> if (enabled) positionToValue(x) },
                onDrag  = { x, _ -> if (enabled) positionToValue(x) },
                onEnd   = { if (enabled) onValueChangeFinished?.invoke() },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Inactive track (the full width).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SliderDefaults.TrackHeight)
                .background(
                    if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor,
                    RoundedCornerShape(50)
                )
        )
        // Active track (from start to thumb).
        val vActiveWidthPx = (vFraction * vTrackWidth.toFloat()).toInt().coerceAtLeast(0)
        if (vActiveWidthPx > 0) {
            Box(
                modifier = Modifier
                    .size(width = vActiveWidthPx.dp, height = SliderDefaults.TrackHeight)
                    .background(
                        if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor,
                        RoundedCornerShape(50)
                    )
            )
        }
        // Thumb. Centred on the value position, so we offset by (fraction*trackW - thumbRadius).
        val vThumbDp: Dp = SliderDefaults.ThumbSize
        val vThumbX = (vFraction * vTrackWidth.toFloat() - SliderDefaults.ThumbSize.value / 2f)
            .roundToInt()
            .coerceAtLeast(0)
        Box(
            modifier = Modifier
                .offset(x = min(vThumbX, max(0, vTrackWidth - vThumbDp.value.toInt())).dp)
                .size(vThumbDp)
                .background(
                    if (enabled) colors.thumbColor else colors.disabledThumbColor,
                    CircleShape,
                )
        )
    }
}

// ==================
// MARK: SliderColors / Defaults
// ==================

data class SliderColors(
    val thumbColor: Color,
    val activeTrackColor: Color,
    val inactiveTrackColor: Color,
    val disabledThumbColor: Color,
    val disabledActiveTrackColor: Color,
    val disabledInactiveTrackColor: Color,
)

object SliderDefaults {
    val TouchHeight: Dp = 32.dp
    val TrackHeight: Dp = 4.dp
    val ThumbSize: Dp = 16.dp

    @Composable
    fun colors(
        thumbColor: Color = MaterialTheme.colors.primary,
        activeTrackColor: Color = MaterialTheme.colors.primary,
        inactiveTrackColor: Color = Color(0x40FFFFFFL),
        disabledThumbColor: Color = Color(0x66FFFFFFL),
        disabledActiveTrackColor: Color = Color(0x40FFFFFFL),
        disabledInactiveTrackColor: Color = Color(0x20FFFFFFL),
    ) = SliderColors(
        thumbColor, activeTrackColor, inactiveTrackColor,
        disabledThumbColor, disabledActiveTrackColor, disabledInactiveTrackColor,
    )
}
