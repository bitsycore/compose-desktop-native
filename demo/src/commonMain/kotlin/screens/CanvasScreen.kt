package screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CanvasScreen() {
    // Hoist theme colours out of the @Composable scope; DrawScope lambdas
    // are plain functions and can't read CompositionLocals directly.
    val vPrimary = MaterialTheme.colorScheme.primary
    val vSecondary = MaterialTheme.colorScheme.secondary
    val vOnSurface = MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Canvas + DrawScope",
            "Custom-drawn shapes via Canvas {} and Modifier.drawBehind {} on a shared DrawScope. " +
                "Skia draws natively (Canvas.drawArc etc.); SDL3 tessellates each primitive into triangles " +
                "for SDL_RenderGeometry. Gradient brushes work on both — Skia via Shader/Gradient, SDL3 via " +
                "per-vertex colour interpolation on the tessellated geometry.",
        )

        Section(
            "Canvas { drawArc / drawCircle / drawRect / drawLine }",
            "Each shape calls one DrawScope method. The DrawScope's `size` reports the node's bounds in logical points.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Stroked arc (3/4 ring).
                Canvas(modifier = Modifier.size(64.dp)) {
                    val vInset = 6f
                    drawArc(
                        color = vPrimary,
                        startAngle = -90f, sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(vInset, vInset),
                        size = Size(size.width - vInset * 2, size.height - vInset * 2),
                        style = Stroke(width = 6f, cap = StrokeCap.Round),
                    )
                }
                // Filled circle.
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawCircle(color = vSecondary, radius = 26f)
                }
                // Stroked rect.
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawRect(color = vPrimary, style = Stroke(width = 4f))
                }
                // Diagonal thick line with round caps.
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawLine(
                        color = vPrimary,
                        start = Offset(8f, 56f),
                        end = Offset(56f, 8f),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round,
                    )
                }
                // Pie slice.
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawArc(
                        color = vSecondary,
                        startAngle = -30f, sweepAngle = 240f,
                        useCenter = true,
                    )
                }
            }
        }

        Section(
            "Modifier.drawBehind { ... }",
            "Same DrawScope, attached to an existing node — paints between its background/border and its children.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Concentric circles painted behind a text label.
                Box(
                    modifier = Modifier
                        .size(120.dp, 60.dp)
                        .drawBehind {
                            drawCircle(color = vPrimary.copy(alpha = 0.2f),
                                       radius = 28f, center = Offset(30f, size.height / 2f))
                            drawCircle(color = vPrimary.copy(alpha = 0.4f),
                                       radius = 18f, center = Offset(30f, size.height / 2f))
                            drawCircle(color = vPrimary,
                                       radius = 8f, center = Offset(30f, size.height / 2f))
                        },
                ) {
                    Text("target", color = vOnSurface, fontSize = 12.sp)
                }
                // Underlined text via drawBehind.
                Box(
                    modifier = Modifier
                        .size(140.dp, 60.dp)
                        .drawBehind {
                            drawLine(
                                color = vSecondary,
                                start = Offset(0f, size.height - 4f),
                                end = Offset(size.width, size.height - 4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round,
                            )
                        },
                ) { Text("underline", color = vOnSurface, fontSize = 14.sp) }
            }
        }

        Section(
            "Brush gradients",
            "Linear / radial / sweep brushes flow through any draw* method that accepts a Brush.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF6200EE), Color(0xFF03DAC6)),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                        ),
                    )
                }
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFF6200EE)),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = 40f,
                        ),
                    )
                }
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                                Color(0xFFFF0000),
                            ),
                        ),
                    )
                }
            }
        }

        Section(
            "Custom shape composition",
            "A bargraph drawn with a single Canvas{} that loops drawRect across N values.",
        ) {
            val vValues = listOf(0.2f, 0.55f, 0.8f, 0.35f, 0.95f, 0.6f, 0.45f, 0.7f)
            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                val vBarGap = 4f
                val vBarW = (size.width - vBarGap * (vValues.size - 1)) / vValues.size
                for ((vIdx, vFraction) in vValues.withIndex()) {
                    val vH = size.height * vFraction
                    drawRect(
                        color = vPrimary,
                        topLeft = Offset(vIdx * (vBarW + vBarGap), size.height - vH),
                        size = Size(vBarW, vH),
                    )
                }
            }
        }
    }
}
