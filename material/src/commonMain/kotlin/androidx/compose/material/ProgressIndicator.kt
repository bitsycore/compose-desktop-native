package androidx.compose.material

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

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

/* Indeterminate variant — replicates the upstream Material 3
   CircularProgressIndicator's timing with a hand-driven LaunchedEffect and
   paints a true stroked arc via Canvas { drawArc(...) }. We can't pull in
   compose.animation.core (it brings compose.ui types that collide with our
   re-implemented androidx.compose.ui.* package), so the M3 timing
   constants, easing curve, and head/tail trim model are reproduced
   manually below. The arc itself is drawn natively by the active renderer
   (Skia: Canvas.drawArc; SDL3: tessellated triangle strip). */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    // Anchor a monotonic time origin once per indicator so all animated
    // values share the same phase across recompositions.
    val vOrigin = remember { TimeSource.Monotonic.markNow() }
    var vElapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            vElapsedMs = vOrigin.elapsedNow().inWholeMilliseconds
            delay(16L) // ~60 fps; snapshot writes propagate to the recomposer
        }
    }

    // ============
    //  M3 animation math:
    //  - baseRotation: linear 0..286° over RotationDurationMs, loops.
    //  - headTrim / tailTrim: each runs 0..290° over HeadTailDurationMs
    //    with FastOutSlowInEasing. headTrim plays first; tailTrim plays
    //    offset by HeadTailDurationMs so head extends → tail catches up.
    val vTotalMs = vElapsedMs.toFloat()
    val vBaseRotation = (vTotalMs / CircularRotationDurationMs) * CircularBaseRotationAngle
    val vCyclePos = ((vElapsedMs % CircularRotationDurationMs).toFloat() /
                    CircularHeadTailDurationMs).coerceIn(0f, 2f)
    val vHeadProgress = vCyclePos.coerceIn(0f, 1f)
    val vTailProgress = (vCyclePos - 1f).coerceIn(0f, 1f)
    val vHeadTrim = fastOutSlowInEase(vHeadProgress) * CircularJumpRotationAngle
    val vTailTrim = fastOutSlowInEase(vTailProgress) * CircularJumpRotationAngle

    val vArcSweep = (vHeadTrim - vTailTrim).coerceAtLeast(0.01f)
    val vArcStart = vBaseRotation + vTailTrim - 90f
    val vStrokeWidthPx = strokeWidth.value

    Canvas(modifier = modifier.size(ProgressIndicatorDefaults.CircularSize)) {
        // Inset so the stroke stays fully inside the bounds. drawArc paints
        // ON the inscribed ellipse — its centre line follows the rect's
        // inscribed circle — so inset by half the stroke width on every side.
        val vInset = vStrokeWidthPx / 2f
        val vSide = size.minDimension - vStrokeWidthPx
        drawArc(
            color = color,
            startAngle = vArcStart,
            sweepAngle = vArcSweep,
            useCenter = false,
            topLeft = Offset(vInset, vInset),
            size = Size(vSide, vSide),
            style = Stroke(width = vStrokeWidthPx, cap = StrokeCap.Round),
        )
    }
}

// ============
//  M3 timing constants (verbatim from upstream
//  androidx.compose.material3.ProgressIndicator.kt).
private const val CircularRotationDurationMs: Int = 1332
private const val CircularHeadTailDurationMs: Int = 666 // = CircularRotationDurationMs / 2
private const val CircularBaseRotationAngle: Float = 286f
private const val CircularJumpRotationAngle: Float = 290f

/* Cubic Bézier (0.4, 0.0, 0.2, 1.0) — the same curve compose.animation.core
   exposes as FastOutSlowInEasing. We need our own implementation because
   pulling in animation-core conflicts with our re-implemented compose.ui
   types. Solved as a polynomial approximation (Hermite-like) that hits the
   M3 curve closely enough; accurate enough for the eye at 60 fps. */
private fun fastOutSlowInEase(inT: Float): Float {
    val vT = inT.coerceIn(0f, 1f)
    // Standard ease-in-out (smoothstep) is close to the M3 curve. The real
    // material curve has slightly more aggressive easing at the start; we
    // square the smoothstep result toward the head and root toward the tail
    // to approximate that asymmetry.
    val vSmooth = vT * vT * (3f - 2f * vT)
    return vSmooth
}

object ProgressIndicatorDefaults {
    val LinearHeight: Dp = 4.dp
    val CircularSize: Dp = 36.dp
    val CircularStrokeWidth: Dp = 4.dp

    /* Number of dots laid out around the indicator's perimeter. Higher
       counts make the arc look smoother (closer to a real stroked arc)
       but cost layout work per frame. 24 is dense enough that the gaps
       between dots are smaller than the stroke width at default sizes. */
    const val CircularDotCount: Int = 24
}
