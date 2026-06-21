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

	fun moveTo(inX: Float, inY: Float) { fCommands.add(PathCommand.MoveTo(inX, inY)) }
	fun lineTo(inX: Float, inY: Float) { fCommands.add(PathCommand.LineTo(inX, inY)) }
	fun quadraticBezierTo(inCx: Float, inCy: Float, inX: Float, inY: Float) {
		fCommands.add(PathCommand.QuadTo(inCx, inCy, inX, inY))
	}
	fun cubicTo(
		inC1x: Float, inC1y: Float,
		inC2x: Float, inC2y: Float,
		inX: Float, inY: Float,
	) {
		fCommands.add(PathCommand.CubicTo(inC1x, inC1y, inC2x, inC2y, inX, inY))
	}
	fun close() { fCommands.add(PathCommand.Close) }
	fun reset() { fCommands.clear() }

	/* Convenience builders for common figures. */
	fun addRect(inTopLeft: Offset, inSize: Size) {
		moveTo(inTopLeft.x, inTopLeft.y)
		lineTo(inTopLeft.x + inSize.width, inTopLeft.y)
		lineTo(inTopLeft.x + inSize.width, inTopLeft.y + inSize.height)
		lineTo(inTopLeft.x, inTopLeft.y + inSize.height)
		close()
	}

	fun addOval(inTopLeft: Offset, inSize: Size) {
		val vCx = inTopLeft.x + inSize.width / 2f
		val vCy = inTopLeft.y + inSize.height / 2f
		val vRx = inSize.width / 2f
		val vRy = inSize.height / 2f
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
