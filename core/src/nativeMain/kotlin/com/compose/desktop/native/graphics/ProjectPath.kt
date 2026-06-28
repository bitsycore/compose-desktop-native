package com.compose.desktop.native.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ==================
// MARK: ProjectPath — native actual for the vendored Path interface
// ==================

/* Our concrete Path implementation. Backs the upstream `interface Path`
   vendored from `compose/ui/ui-graphics/.../Path.kt`. Renderers
   (SkiaDrawScope / Sdl3DrawScope) cast to ProjectPath to access the
   `commands` list for direct drawing without going through PathIterator.

   Most upstream Path methods that are unused by our pipeline are no-op
   stubs (transform / op / getBounds returns Rect.Zero); the ones that
   ARE used (moveTo / lineTo / quadraticBezierTo / cubicTo / close /
   reset / addRect / addOval / addRoundRect / addPath) emit PathCommands
   matching the legacy hand-written impl exactly.

   When the wider Canvas/Paint port lands (separate pass), this can switch
   to delegating to org.jetbrains.skia.Path for the Skia renderer and
   keep the PathCommand list as fallback for SDL3. */
class ProjectPath : Path {

	private val fCommands = mutableListOf<PathCommand>()

	/* Read-only view for renderers — public for cross-module access. */
	val commands: List<PathCommand> get() = fCommands

	override var fillType: PathFillType = PathFillType.NonZero
	override val isConvex: Boolean get() = true
	override val isEmpty: Boolean get() = fCommands.isEmpty()

	override fun moveTo(x: Float, y: Float) { fCommands.add(PathCommand.MoveTo(x, y)) }

	override fun relativeMoveTo(dx: Float, dy: Float) {
		val (px, py) = currentPoint()
		moveTo(px + dx, py + dy)
	}

	override fun lineTo(x: Float, y: Float) { fCommands.add(PathCommand.LineTo(x, y)) }

	override fun relativeLineTo(dx: Float, dy: Float) {
		val (px, py) = currentPoint()
		lineTo(px + dx, py + dy)
	}

	override fun quadraticBezierTo(x1: Float, y1: Float, x2: Float, y2: Float) {
		fCommands.add(PathCommand.QuadTo(x1, y1, x2, y2))
	}

	override fun relativeQuadraticBezierTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
		val (px, py) = currentPoint()
		quadraticBezierTo(px + dx1, py + dy1, px + dx2, py + dy2)
	}

	override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
		fCommands.add(PathCommand.CubicTo(x1, y1, x2, y2, x3, y3))
	}

	override fun relativeCubicTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float, dx3: Float, dy3: Float) {
		val (px, py) = currentPoint()
		cubicTo(px + dx1, py + dy1, px + dx2, py + dy2, px + dx3, py + dy3)
	}

	override fun arcToRad(rect: Rect, startAngleRadians: Float, sweepAngleRadians: Float, forceMoveTo: Boolean) {
		arcTo(rect, startAngleRadians * 180f / PI.toFloat(), sweepAngleRadians * 180f / PI.toFloat(), forceMoveTo)
	}

	override fun arcTo(rect: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float, forceMoveTo: Boolean) {
		// Approximate the arc as a Bézier — minimal; renderers typically
		// don't go through ProjectPath.arcTo (they draw arcs directly).
		val vCx = rect.center.x
		val vCy = rect.center.y
		val vRx = rect.width / 2f
		val vRy = rect.height / 2f
		val vStart = startAngleDegrees * PI.toFloat() / 180f
		val vSweep = sweepAngleDegrees * PI.toFloat() / 180f
		val vStartX = vCx + vRx * cos(vStart)
		val vStartY = vCy + vRy * sin(vStart)
		if (forceMoveTo || isEmpty) moveTo(vStartX, vStartY) else lineTo(vStartX, vStartY)
		val vEnd = vStart + vSweep
		val vEndX = vCx + vRx * cos(vEnd)
		val vEndY = vCy + vRy * sin(vEnd)
		lineTo(vEndX, vEndY)
	}

	override fun addRect(rect: Rect) = addRect(rect, Path.Direction.CounterClockwise)
	override fun addOval(oval: Rect) = addOval(oval, Path.Direction.CounterClockwise)
	override fun addRoundRect(roundRect: RoundRect) = addRoundRect(roundRect, Path.Direction.CounterClockwise)

	override fun addRect(rect: Rect, direction: Path.Direction) {
		moveTo(rect.left, rect.top)
		lineTo(rect.right, rect.top)
		lineTo(rect.right, rect.bottom)
		lineTo(rect.left, rect.bottom)
		close()
	}

	override fun addOval(oval: Rect, direction: Path.Direction) {
		val vCx = oval.center.x
		val vCy = oval.center.y
		val vRx = oval.width / 2f
		val vRy = oval.height / 2f
		val vK = 0.5522847498f
		moveTo(vCx + vRx, vCy)
		cubicTo(vCx + vRx, vCy + vRy * vK, vCx + vRx * vK, vCy + vRy, vCx, vCy + vRy)
		cubicTo(vCx - vRx * vK, vCy + vRy, vCx - vRx, vCy + vRy * vK, vCx - vRx, vCy)
		cubicTo(vCx - vRx, vCy - vRy * vK, vCx - vRx * vK, vCy - vRy, vCx, vCy - vRy)
		cubicTo(vCx + vRx * vK, vCy - vRy, vCx + vRx, vCy - vRy * vK, vCx + vRx, vCy)
		close()
	}

	override fun addRoundRect(roundRect: RoundRect, direction: Path.Direction) {
		// Stripped-down: treat as plain rect (per-corner radii not used by
		// our renderer's Path consumers today).
		addRect(Rect(roundRect.left, roundRect.top, roundRect.right, roundRect.bottom), direction)
	}

	override fun addArcRad(oval: Rect, startAngleRadians: Float, sweepAngleRadians: Float) {
		arcToRad(oval, startAngleRadians, sweepAngleRadians, forceMoveTo = true)
	}

	override fun addArc(oval: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float) {
		arcTo(oval, startAngleDegrees, sweepAngleDegrees, forceMoveTo = true)
	}

	override fun addPath(path: Path, offset: Offset) {
		val vOther = path as? ProjectPath ?: return
		for (vC in vOther.fCommands) when (vC) {
			is PathCommand.MoveTo -> moveTo(vC.x + offset.x, vC.y + offset.y)
			is PathCommand.LineTo -> lineTo(vC.x + offset.x, vC.y + offset.y)
			is PathCommand.QuadTo -> quadraticBezierTo(vC.cx + offset.x, vC.cy + offset.y, vC.x + offset.x, vC.y + offset.y)
			is PathCommand.CubicTo -> cubicTo(vC.c1x + offset.x, vC.c1y + offset.y, vC.c2x + offset.x, vC.c2y + offset.y, vC.x + offset.x, vC.y + offset.y)
			PathCommand.Close -> close()
		}
	}

	override fun close() { fCommands.add(PathCommand.Close) }
	override fun reset() { fCommands.clear() }

	override fun translate(offset: Offset) {
		val vTranslated = fCommands.map { c ->
			when (c) {
				is PathCommand.MoveTo -> PathCommand.MoveTo(c.x + offset.x, c.y + offset.y)
				is PathCommand.LineTo -> PathCommand.LineTo(c.x + offset.x, c.y + offset.y)
				is PathCommand.QuadTo -> PathCommand.QuadTo(c.cx + offset.x, c.cy + offset.y, c.x + offset.x, c.y + offset.y)
				is PathCommand.CubicTo -> PathCommand.CubicTo(c.c1x + offset.x, c.c1y + offset.y, c.c2x + offset.x, c.c2y + offset.y, c.x + offset.x, c.y + offset.y)
				PathCommand.Close -> c
			}
		}
		fCommands.clear()
		fCommands.addAll(vTranslated)
	}

	override fun getBounds(): Rect {
		if (fCommands.isEmpty()) return Rect.Zero
		var vMinX = Float.POSITIVE_INFINITY; var vMaxX = Float.NEGATIVE_INFINITY
		var vMinY = Float.POSITIVE_INFINITY; var vMaxY = Float.NEGATIVE_INFINITY
		for (c in fCommands) when (c) {
			is PathCommand.MoveTo -> { vMinX = minOf(vMinX, c.x); vMaxX = maxOf(vMaxX, c.x); vMinY = minOf(vMinY, c.y); vMaxY = maxOf(vMaxY, c.y) }
			is PathCommand.LineTo -> { vMinX = minOf(vMinX, c.x); vMaxX = maxOf(vMaxX, c.x); vMinY = minOf(vMinY, c.y); vMaxY = maxOf(vMaxY, c.y) }
			is PathCommand.QuadTo -> { vMinX = minOf(vMinX, c.x); vMaxX = maxOf(vMaxX, c.x); vMinY = minOf(vMinY, c.y); vMaxY = maxOf(vMaxY, c.y) }
			is PathCommand.CubicTo -> { vMinX = minOf(vMinX, c.x); vMaxX = maxOf(vMaxX, c.x); vMinY = minOf(vMinY, c.y); vMaxY = maxOf(vMaxY, c.y) }
			PathCommand.Close -> Unit
		}
		return if (vMinX.isFinite()) Rect(vMinX, vMinY, vMaxX, vMaxY) else Rect.Zero
	}

	override fun op(path1: Path, path2: Path, operation: PathOperation): Boolean = false  // unsupported

	private fun currentPoint(): Pair<Float, Float> {
		val vLast = fCommands.lastOrNull() ?: return 0f to 0f
		return when (vLast) {
			is PathCommand.MoveTo -> vLast.x to vLast.y
			is PathCommand.LineTo -> vLast.x to vLast.y
			is PathCommand.QuadTo -> vLast.x to vLast.y
			is PathCommand.CubicTo -> vLast.x to vLast.y
			PathCommand.Close -> 0f to 0f
		}
	}
}

// actuals for `fun Path(): Path` live in
// core/src/nativeMain/kotlin/androidx/compose/ui/graphics/PathActuals.native.kt
// — the actual must stay in the same package as the expect declaration.
