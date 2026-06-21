package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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

// ==================
// MARK: LinearProgressIndicator
// ==================

/* Determinate horizontal bar. `progress` is clamped to 0..1. The
   indeterminate overload (no progress arg) shows a static three-quarter
   bar — there is no animation runtime in this subset, so it can't sweep.
   Use the determinate form whenever you have a real fraction. */
@Composable
fun LinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    backgroundColor: Color = color.copy(alpha = 0.24f),
) {
    var vWidth by remember { mutableStateOf(0) }
    val vFraction = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ProgressIndicatorDefaults.LinearHeight)
            .background(backgroundColor, RoundedCornerShape(50))
            .onSizeChanged { vWidth = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        val vFilledPx = (vFraction * vWidth.toFloat()).toInt().coerceAtLeast(0)
        if (vFilledPx > 0) {
            Box(
                modifier = Modifier
                    .size(width = vFilledPx.dp, height = ProgressIndicatorDefaults.LinearHeight)
                    .background(color, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    backgroundColor: Color = color.copy(alpha = 0.24f),
) = LinearProgressIndicator(0.75f, modifier, color, backgroundColor)

// ==================
// MARK: CircularProgressIndicator
// ==================

/* Determinate circular indicator approximated as a square with a thick
   ring (border) — Material's true arc requires a path stroker we don't
   have. Reads acceptably at small sizes (~28-40dp). The indeterminate
   overload also renders a static ring. */
@Composable
fun CircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    // The progress fraction is reflected as a (partial) arc by combining the
    // active color with a dimmed remainder using two stacked rings: the dim
    // background ring under, the partial-coverage primary on top via opacity.
    val vFraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .size(ProgressIndicatorDefaults.CircularSize)
            .background(color.copy(alpha = 0.24f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ProgressIndicatorDefaults.CircularSize)
                .background(color.copy(alpha = 0.4f + 0.6f * vFraction), CircleShape)
        )
        // Punch-out: a slightly-smaller surface-coloured disc to leave a
        // ring of `strokeWidth`. This is how the existing renderer can fake
        // a stroked circle without path support.
        val vInner = ProgressIndicatorDefaults.CircularSize - (strokeWidth * 2)
        Box(
            modifier = Modifier
                .size(vInner)
                .background(MaterialTheme.colors.surface, CircleShape)
        )
    }
}

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) = CircularProgressIndicator(0.75f, modifier, color, strokeWidth)

object ProgressIndicatorDefaults {
    val LinearHeight: Dp = 4.dp
    val CircularSize: Dp = 36.dp
    val CircularStrokeWidth: Dp = 4.dp
}
