package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

// ==================
// MARK: Path
// ==================

/* Vector path made of move / line / quadratic / cubic / close commands.
   Cheap to build, immutable-by-convention once handed to drawPath. The
   Skia renderer translates each command 1:1 onto org.jetbrains.skia.Path;
   the SDL3 renderer linearises curves into line segments and tessellates
   the resulting polygon into a triangle fan. */
class Path {

	private val fCommands = mutableListOf<PathCommand>()

	/* Read-only view for renderers. */
	val commands: List<PathCommand> get() = fCommands

	fun moveTo(x: Float, y: Float) { fCommands.add(PathCommand.MoveTo(x, y)) }
	fun lineTo(x: Float, y: Float) { fCommands.add(PathCommand.LineTo(x, y)) }
	fun quadraticBezierTo(x1: Float, y1: Float, x2: Float, y2: Float) {
		fCommands.add(PathCommand.QuadTo(x1, y1, x2, y2))
	}
	fun cubicTo(
		x1: Float, y1: Float,
		x2: Float, y2: Float,
		x3: Float, y3: Float,
	) {
		fCommands.add(PathCommand.CubicTo(x1, y1, x2, y2, x3, y3))
	}
	fun close() { fCommands.add(PathCommand.Close) }
	fun reset() { fCommands.clear() }

	/* Convenience builders for common figures. NOTE: official addRect/addOval
	   take a Rect; these (Offset, Size) forms are a reduced project shape. */
	fun addRect(topLeft: Offset, size: Size) {
		moveTo(topLeft.x, topLeft.y)
		lineTo(topLeft.x + size.width, topLeft.y)
		lineTo(topLeft.x + size.width, topLeft.y + size.height)
		lineTo(topLeft.x, topLeft.y + size.height)
		close()
	}

	fun addOval(topLeft: Offset, size: Size) {
		val vCx = topLeft.x + size.width / 2f
		val vCy = topLeft.y + size.height / 2f
		val vRx = size.width / 2f
		val vRy = size.height / 2f
		// 4-segment Bézier circle approximation: kappa ≈ 0.5522847498f
		val vK = 0.5522847498f
		moveTo(vCx + vRx, vCy)
		cubicTo(vCx + vRx, vCy + vRy * vK, vCx + vRx * vK, vCy + vRy, vCx, vCy + vRy)
		cubicTo(vCx - vRx * vK, vCy + vRy, vCx - vRx, vCy + vRy * vK, vCx - vRx, vCy)
		cubicTo(vCx - vRx, vCy - vRy * vK, vCx - vRx * vK, vCy - vRy, vCx, vCy - vRy)
		cubicTo(vCx + vRx * vK, vCy - vRy, vCx + vRx, vCy - vRy * vK, vCx + vRx, vCy)
		close()
	}
}

// ==================
// MARK: PathCommand
// ==================

sealed interface PathCommand {
	class MoveTo(val x: Float, val y: Float) : PathCommand
	class LineTo(val x: Float, val y: Float) : PathCommand
	class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCommand
	class CubicTo(
		val c1x: Float, val c1y: Float,
		val c2x: Float, val c2y: Float,
		val x: Float, val y: Float,
	) : PathCommand
	data object Close : PathCommand
}
