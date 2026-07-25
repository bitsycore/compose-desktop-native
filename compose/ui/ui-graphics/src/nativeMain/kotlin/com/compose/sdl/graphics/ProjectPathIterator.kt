package com.compose.sdl.graphics

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment

// ==================
// MARK: ProjectPathIterator
// ==================

/**
 * Walks the [PathCommand] list of a [ProjectPath], emitting [PathSegment]s
 * in the upstream-expected shape. This is the bridge between our custom
 * command-list representation and the vendored [PathIterator] interface.
 *
 * We don't support conic-evaluation (no conic verbs in our PathCommand
 * sealed type) and ignore the tolerance parameter — there are no conics
 * to expand. The `actual fun PathIterator(...)` factory lives in
 * `core/src/nativeMain/.../androidx/compose/ui/graphics/PathActuals.native.kt`
 * (must stay in the same package as the expect).
 */
class ProjectPathIterator(
	private val fPath: ProjectPath,
	private val fConicEvaluation: PathIterator.ConicEvaluation,
) : PathIterator {

	private var fIndex = 0
	private var fLastMoveX = 0f
	private var fLastMoveY = 0f
	private var fCurrentX = 0f
	private var fCurrentY = 0f

	override val path: Path = fPath
	override val conicEvaluation: PathIterator.ConicEvaluation = fConicEvaluation
	override val tolerance: Float = 0.25f

	override fun hasNext(): Boolean = fIndex < fPath.commands.size

	override fun next(): PathSegment {
		val vC = fPath.commands[fIndex++]
		return when (vC) {
			is PathCommand.MoveTo -> {
				fLastMoveX = vC.x; fLastMoveY = vC.y
				fCurrentX = vC.x; fCurrentY = vC.y
				PathSegment(PathSegment.Type.Move, floatArrayOf(vC.x, vC.y), 0f)
			}
			is PathCommand.LineTo -> {
				val pts = floatArrayOf(fCurrentX, fCurrentY, vC.x, vC.y)
				fCurrentX = vC.x; fCurrentY = vC.y
				PathSegment(PathSegment.Type.Line, pts, 0f)
			}
			is PathCommand.QuadTo -> {
				val pts = floatArrayOf(fCurrentX, fCurrentY, vC.cx, vC.cy, vC.x, vC.y)
				fCurrentX = vC.x; fCurrentY = vC.y
				PathSegment(PathSegment.Type.Quadratic, pts, 0f)
			}
			is PathCommand.CubicTo -> {
				val pts = floatArrayOf(fCurrentX, fCurrentY, vC.c1x, vC.c1y, vC.c2x, vC.c2y, vC.x, vC.y)
				fCurrentX = vC.x; fCurrentY = vC.y
				PathSegment(PathSegment.Type.Cubic, pts, 0f)
			}
			PathCommand.Close -> {
				fCurrentX = fLastMoveX; fCurrentY = fLastMoveY
				PathSegment(PathSegment.Type.Close, floatArrayOf(), 0f)
			}
		}
	}

	override fun next(outPoints: FloatArray, offset: Int): PathSegment.Type {
		val seg = next()
		val src = seg.points
		val n = minOf(src.size, outPoints.size - offset)
		for (i in 0 until n) outPoints[offset + i] = src[i]
		return seg.type
	}

	override fun calculateSize(includeConvertedConics: Boolean): Int = fPath.commands.size
}
