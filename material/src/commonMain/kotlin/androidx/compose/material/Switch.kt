package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: Switch
// ==================

/* Toggle pill track with a circular thumb. No animation — the thumb snaps
   between left/right on toggle. Track and thumb colour shift with the
   checked / enabled state. Material 1 sizing (track 34×14, thumb 20 with
   hit area 32×32 around it; here we use a single 36×20 pill with a 16dp
   thumb so the whole thing reads at the same density as Checkbox/Radio). */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
) {
    val vTrackColor: Color = when {
        !enabled -> colors.disabledTrackColor
        checked  -> colors.checkedTrackColor
        else     -> colors.uncheckedTrackColor
    }
    val vThumbColor: Color = when {
        !enabled -> colors.disabledThumbColor
        checked  -> colors.checkedThumbColor
        else     -> colors.uncheckedThumbColor
    }

    var m: Modifier = modifier
        .width(SwitchDefaults.TrackWidth)
        .height(SwitchDefaults.TrackHeight)
        .background(vTrackColor, RoundedCornerShape(50))
    if (onCheckedChange != null) {
        m = m.clickable { if (enabled) onCheckedChange(!checked) }
    }

    Box(modifier = m, contentAlignment = Alignment.CenterStart) {
        // Thumb travel = track width - thumb size - 2*padding.
        val vTravel: Dp = SwitchDefaults.TrackWidth -
                          SwitchDefaults.ThumbSize -
                          (SwitchDefaults.ThumbPadding * 2)
        val vXOffset: Dp = SwitchDefaults.ThumbPadding +
                           (if (checked) vTravel else 0.dp)
        Box(
            modifier = Modifier
                .offset(x = vXOffset)
                .size(SwitchDefaults.ThumbSize)
                .background(vThumbColor, CircleShape)
        )
    }
}

// ==================
// MARK: SwitchColors / Defaults
// ==================

data class SwitchColors(
    val checkedThumbColor: Color,
    val uncheckedThumbColor: Color,
    val checkedTrackColor: Color,
    val uncheckedTrackColor: Color,
    val disabledThumbColor: Color,
    val disabledTrackColor: Color,
)

object SwitchDefaults {
    val TrackWidth: Dp = 36.dp
    val TrackHeight: Dp = 20.dp
    val ThumbSize: Dp = 14.dp
    val ThumbPadding: Dp = 3.dp

    @Composable
    fun colors(
        checkedThumbColor: Color = MaterialTheme.colors.secondary,
        uncheckedThumbColor: Color = Color(0xFFECECECL),
        checkedTrackColor: Color = MaterialTheme.colors.secondary.copy(alpha = 0.5f),
        uncheckedTrackColor: Color = Color(0x66FFFFFFL),
        disabledThumbColor: Color = Color(0x66FFFFFFL),
        disabledTrackColor: Color = Color(0x22FFFFFFL),
    ) = SwitchColors(
        checkedThumbColor, uncheckedThumbColor,
        checkedTrackColor, uncheckedTrackColor,
        disabledThumbColor, disabledTrackColor,
    )
}
