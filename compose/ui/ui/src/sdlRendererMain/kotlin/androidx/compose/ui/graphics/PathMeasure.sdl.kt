package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import com.compose.sdl.graphics.PathCommand
import com.compose.sdl.graphics.ProjectPath
import kotlin.math.sqrt

// ==================
// MARK: PathMeasure — SDL3 renderer actual
// ==================

/*
 * SDL3 counterpart of upstream `SkiaBackedPathMeasure.skiko.kt`, implemented
 * over ProjectPath's command list: the FIRST contour is flattened to a
 * polyline (same curve sampling density as the tessellator) with cumulative
 * arc lengths, then length / getSegment / getPosition / getTangent interpolate
 * on it. Material3 draws the Checkbox checkmark (and other progress-along-path
 * effects) through getSegment — a no-op here means no checkmark glyph.
 *
 * Skia semantics notes: setPath measures one contour at a time (we take the
 * first); getSegment APPENDS to the destination path (m3 resets it first).
 */
private class ProjectPathMeasure : PathMeasure {

	// Flattened first contour: interleaved x,y pairs + cumulative length per point.
	private var fPts: FloatArray = FloatArray(0)
	private var fCum: FloatArray = FloatArray(0)
	private var fTotal: Float = 0f

	override val length: Float get() = fTotal

	override fun setPath(path: Path?, forceClosed: Boolean) {
		fPts = FloatArray(0); fCum = FloatArray(0); fTotal = 0f
		val vCommands = (path as? ProjectPath)?.commands ?: return
		val vPts = ArrayList<Float>(32)
		var vCx = 0f; var vCy = 0f
		var vStartX = 0f; var vStartY = 0f
		var vStarted = false
		var vClosed = false

		fun add(inX: Float, inY: Float) { vPts.add(inX); vPts.add(inY) }

		run {
			for (vCmd in vCommands) when (vCmd) {
				is PathCommand.MoveTo -> {
					if (vStarted) return@run  // first contour only (Skia semantics)
					vStarted = true
					vCx = vCmd.x; vCy = vCmd.y
					vStartX = vCx; vStartY = vCy
					add(vCx, vCy)
				}
				is PathCommand.LineTo -> {
					vCx = vCmd.x; vCy = vCmd.y
					add(vCx, vCy)
				}
				is PathCommand.QuadTo -> {
					val vN = 12
					for (vI in 1..vN) {
						val vT = vI.toFloat() / vN
						val vOne = 1f - vT
						add(
							vOne * vOne * vCx + 2f * vOne * vT * vCmd.cx + vT * vT * vCmd.x,
							vOne * vOne * vCy + 2f * vOne * vT * vCmd.cy + vT * vT * vCmd.y,
						)
					}
					vCx = vCmd.x; vCy = vCmd.y
				}
				is PathCommand.CubicTo -> {
					val vN = 16
					for (vI in 1..vN) {
						val vT = vI.toFloat() / vN
						val vOne = 1f - vT
						add(
							vOne * vOne * vOne * vCx + 3f * vOne * vOne * vT * vCmd.c1x +
								3f * vOne * vT * vT * vCmd.c2x + vT * vT * vT * vCmd.x,
							vOne * vOne * vOne * vCy + 3f * vOne * vOne * vT * vCmd.c1y +
								3f * vOne * vT * vT * vCmd.c2y + vT * vT * vT * vCmd.y,
						)
					}
					vCx = vCmd.x; vCy = vCmd.y
				}
				PathCommand.Close -> {
					add(vStartX, vStartY)
					vClosed = true
					return@run
				}
			}
		}
		if (forceClosed && vStarted && !vClosed && vPts.size >= 4) add(vStartX, vStartY)

		val vCount = vPts.size / 2
		if (vCount < 2) return
		fPts = FloatArray(vPts.size)
		for (vI in vPts.indices) fPts[vI] = vPts[vI]
		fCum = FloatArray(vCount)
		var vAcc = 0f
		for (vI in 1 until vCount) {
			val vDx = fPts[vI * 2] - fPts[(vI - 1) * 2]
			val vDy = fPts[vI * 2 + 1] - fPts[(vI - 1) * 2 + 1]
			vAcc += sqrt(vDx * vDx + vDy * vDy)
			fCum[vI] = vAcc
		}
		fTotal = vAcc
	}

	// Interpolated point at arc distance inD (clamped). Requires ≥2 points.
	private fun pointAt(inD: Float): Offset {
		val vD = inD.coerceIn(0f, fTotal)
		var vI = 1
		while (vI < fCum.size && fCum[vI] < vD) vI++
		if (vI >= fCum.size) return Offset(fPts[fPts.size - 2], fPts[fPts.size - 1])
		val vSeg = (fCum[vI] - fCum[vI - 1]).coerceAtLeast(1e-6f)
		val vT = (vD - fCum[vI - 1]) / vSeg
		return Offset(
			fPts[(vI - 1) * 2] + (fPts[vI * 2] - fPts[(vI - 1) * 2]) * vT,
			fPts[(vI - 1) * 2 + 1] + (fPts[vI * 2 + 1] - fPts[(vI - 1) * 2 + 1]) * vT,
		)
	}

	override fun getSegment(
		startDistance: Float,
		stopDistance: Float,
		destination: Path,
		startWithMoveTo: Boolean,
	): Boolean {
		if (fPts.size < 4 || fTotal <= 0f) return false
		val vStart = startDistance.coerceIn(0f, fTotal)
		val vStop = stopDistance.coerceIn(0f, fTotal)
		if (vStop <= vStart) return false

		val vFirst = pointAt(vStart)
		if (startWithMoveTo) destination.moveTo(vFirst.x, vFirst.y)
		else destination.lineTo(vFirst.x, vFirst.y)
		// Interior polyline points strictly inside (start, stop).
		for (vI in 1 until fCum.size) {
			if (fCum[vI] <= vStart) continue
			if (fCum[vI] >= vStop) break
			destination.lineTo(fPts[vI * 2], fPts[vI * 2 + 1])
		}
		val vLast = pointAt(vStop)
		destination.lineTo(vLast.x, vLast.y)
		return true
	}

	override fun getPosition(distance: Float): Offset =
		if (fPts.size < 4) Offset.Unspecified else pointAt(distance)

	override fun getTangent(distance: Float): Offset {
		if (fPts.size < 4 || fTotal <= 0f) return Offset.Unspecified
		val vD = distance.coerceIn(0f, fTotal)
		var vI = 1
		while (vI < fCum.size && fCum[vI] < vD) vI++
		if (vI >= fCum.size) vI = fCum.size - 1
		val vDx = fPts[vI * 2] - fPts[(vI - 1) * 2]
		val vDy = fPts[vI * 2 + 1] - fPts[(vI - 1) * 2 + 1]
		val vLen = sqrt(vDx * vDx + vDy * vDy)
		return if (vLen < 1e-6f) Offset.Unspecified else Offset(vDx / vLen, vDy / vLen)
	}
}

actual fun PathMeasure(): PathMeasure = ProjectPathMeasure()
