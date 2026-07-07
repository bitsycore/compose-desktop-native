package screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ==================
// MARK: PathScreen
// ==================

/* Demonstrates the new Path API and the new DrawScope primitives:
   drawPath (filled + stroked), drawOval, drawRoundRect. Skia renders
   each natively; SDL3 tessellates paths into triangle fans via the
   Sdl3DrawScope.linearisePath / fanFill pipeline. */
@Composable
internal fun PathScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary

	// Build paths once and reuse — Path is mutable but compose-stable
	// while the commands don't change.
	val vTriangle = remember {
		Path().apply {
			moveTo(40f, 8f)
			lineTo(72f, 56f)
			lineTo(8f, 56f)
			close()
		}
	}
	val vStar = remember {
		Path().apply {
			moveTo(40f, 4f)
			lineTo(50f, 28f)
			lineTo(76f, 30f)
			lineTo(56f, 48f)
			lineTo(62f, 72f)
			lineTo(40f, 60f)
			lineTo(18f, 72f)
			lineTo(24f, 48f)
			lineTo(4f, 30f)
			lineTo(30f, 28f)
			close()
		}
	}
	val vWave = remember {
		Path().apply {
			moveTo(8f, 32f)
			quadraticBezierTo(20f,  8f, 32f, 32f)
			quadraticBezierTo(44f, 56f, 56f, 32f)
			quadraticBezierTo(68f,  8f, 72f, 32f)
		}
	}
	val vSpline = remember {
		Path().apply {
			moveTo(8f, 56f)
			cubicTo(24f,  8f, 56f, 56f, 72f,  8f)
		}
	}

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Path + drawPath / drawOval / drawRoundRect",
			"Path with moveTo / lineTo / quadraticBezierTo / cubicTo / close. " +
				"DrawScope.drawPath fills or strokes it. drawOval and drawRoundRect are " +
				"direct primitives on both backends; SDL3 tessellates paths into triangle " +
				"fans (works for convex shapes).",
		)

		Section("drawPath — fill", "Triangle, star, and oval-via-Path.addOval (5 fills).") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Canvas(modifier = Modifier.size(80.dp)) { drawPath(vTriangle, vPrimary) }
				Canvas(modifier = Modifier.size(80.dp)) { drawPath(vStar, vSecondary) }
				Canvas(modifier = Modifier.size(80.dp)) {
					val vP = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(8f, 16f), Size(64f, 48f))) }
					drawPath(vP, vPrimary)
				}
				Canvas(modifier = Modifier.size(80.dp)) {
					val vP = Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset(16f, 16f), Size(48f, 48f))) }
					drawPath(vP, vSecondary)
				}
			}
		}

		Section("drawPath — stroke", "Same paths, stroked. Quad/Cubic bezier curves at the end.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Canvas(modifier = Modifier.size(80.dp)) {
					drawPath(vTriangle, vPrimary, style = Stroke(width = 3f))
				}
				Canvas(modifier = Modifier.size(80.dp)) {
					drawPath(vStar, vSecondary, style = Stroke(width = 3f))
				}
				Canvas(modifier = Modifier.size(80.dp)) {
					drawPath(vWave, vPrimary, style = Stroke(width = 4f))
				}
				Canvas(modifier = Modifier.size(80.dp)) {
					drawPath(vSpline, vSecondary, style = Stroke(width = 4f))
				}
			}
		}

		Section("drawOval / drawRoundRect", "Filled and stroked variants of each.") {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Canvas(modifier = Modifier.size(80.dp)) {
					drawOval(vPrimary, Offset(4f, 16f), Size(72f, 48f))
				}
				Canvas(modifier = Modifier.size(80.dp)) {
					drawOval(vSecondary, Offset(4f, 16f), Size(72f, 48f), style = Stroke(width = 4f))
				}
				Canvas(modifier = Modifier.size(80.dp)) {
					drawRoundRect(vPrimary, Offset(4f, 4f), Size(72f, 72f), cornerRadius = CornerRadius(18f))
				}
				Canvas(modifier = Modifier.size(80.dp)) {
					drawRoundRect(vSecondary, Offset(4f, 4f), Size(72f, 72f), cornerRadius = CornerRadius(18f), style = Stroke(width = 4f))
				}
			}
		}
	}
}
