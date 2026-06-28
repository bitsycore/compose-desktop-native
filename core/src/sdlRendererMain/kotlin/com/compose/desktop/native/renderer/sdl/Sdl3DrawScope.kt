package com.compose.desktop.native.renderer.sdl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import com.compose.desktop.native.graphics.r8
import com.compose.desktop.native.graphics.g8
import com.compose.desktop.native.graphics.b8
import com.compose.desktop.native.graphics.a8
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.RadialGradient
import com.compose.desktop.native.graphics.PathCommand
import com.compose.desktop.native.graphics.gradientCenter
import com.compose.desktop.native.graphics.gradientColors
import com.compose.desktop.native.graphics.gradientEnd
import com.compose.desktop.native.graphics.gradientRadius
import com.compose.desktop.native.graphics.gradientStart
import com.compose.desktop.native.graphics.gradientStops
import com.compose.desktop.native.graphics.gradientTileMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.cinterop.*
import sdl3.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ==================
// MARK: Sdl3DrawScope
// ==================

/* SDL3 implementation of DrawScope. SDL3's only public shape-rendering API
   is SDL_RenderGeometry (triangle list with per-vertex colour) — there is
   no arc / stroke primitive. We tessellate every primitive into triangles
   here:

   - drawRect: 2 triangles (or 8 for a stroked rect — 4 thin quads, one
     per side, with mitred corners).
   - drawCircle (filled): triangle fan of (segments + 1) verts around the
     centre.
   - drawCircle (stroked) / drawArc (stroked): a strip of `segments` quads
     forming the ring/arc between innerR (r - w/2) and outerR (r + w/2).
     Round caps add a semi-fan of N segments at each end of the arc.
   - drawArc (filled, useCenter): triangle fan from the centre.
   - drawLine (thick): a single quad with optional round caps at the ends.

   segment count is proportional to arc length so wide arcs stay smooth:
   ~64 segments per full circle (5.6° per step). At smaller sizes that's
   sub-pixel work; at larger sizes the human eye stops seeing facets.

   Gradient brushes are sampled per vertex (Sampler in this file); SDL
   linearly interpolates between vertex colours across each triangle,
   which approximates a shader at the tessellation density we use.

   Batching: every call to a draw* primitive pushes triangles into a
   shared native-heap vertex buffer; we submit ONE SDL_RenderGeometry per
   flush() (called at the end of each Canvas{} or drawBehind invocation).
   This collapses what used to be ~64 GPU calls per spinner frame into
   one, and matters even more for screens with many drawn shapes. */
@OptIn(ExperimentalForeignApi::class)
internal class Sdl3DrawScope(
	private val fRenderer: COpaquePointer,
	private val fOriginX: Float,
	private val fOriginY: Float,
	override val size: Size,
) : DrawScope {

	// Exposed for the sampler extension functions — they map gradient
	// anchor points (in node-local coords) into the SDL renderer's
	// absolute pixel space.
	internal val originX: Float get() = fOriginX
	internal val originY: Float get() = fOriginY

	// ============
	//  Vertex batch — heap-allocated buffer of SDL_Vertex shared across
	//  every draw call in this scope. Submitted in one SDL_RenderGeometry
	//  on flush() (called from the renderer after the user's drawer
	//  lambda returns). Auto-flushes if the buffer is about to overflow.

	private val fBatch: CPointer<SDL_Vertex> = nativeHeap.allocArray(kBatchCapacity)
	private var fBatchCount: Int = 0

	/* Pushes a single vertex into the batch buffer. If the buffer is full
	   the batch is auto-flushed before writing. Returns the slot the
	   vertex was written to so emitters can configure position / colour. */
	private fun pushVertex(): SDL_Vertex {
		if (fBatchCount >= kBatchCapacity) flush()
		val vSlot = fBatch[fBatchCount]
		fBatchCount++
		return vSlot
	}

	/* Submit the accumulated triangles to SDL. Called once per drawer
	   invocation by the renderer; safe to call mid-scope when the buffer
	   fills up (state across draw calls is purely positional, no
	   between-tri state). */
	fun flush() {
		if (fBatchCount == 0) return
		SDL_RenderGeometry(
			fRenderer.reinterpret(),
			null,
			fBatch,
			fBatchCount,
			null,
			0,
		)
		fBatchCount = 0
	}

	/* Releases the native vertex pool. Called by the renderer after the
	   drawer block + flush() complete. */
	fun release() {
		flush()
		nativeHeap.free(fBatch)
	}

	// ============
	//  Public primitives. Each one resolves the Brush into a "sampler" —
	//  a function (x, y) → ComposeColor in this scope's pixel space — and
	//  hands that sampler to the triangle emitters so per-vertex colour
	//  interpolation handles gradients on the GPU. SolidColor's sampler
	//  is constant, so flat-coloured calls cost no per-vertex work.

	override fun drawRect(
		brush: Brush,
		topLeft: Offset,
		size: Size,
		alpha: Float,
		style: DrawStyle,
	) {
		val vSampler = samplerFor(brush, size, alpha)
		val vL = fOriginX + topLeft.x
		val vT = fOriginY + topLeft.y
		val vR = vL + size.width
		val vB = vT + size.height
		when (style) {
			Fill -> emitQuad(vL, vT, vR, vT, vR, vB, vL, vB, vSampler)
			is Stroke -> {
				val vW = style.width
				val vIL = vL + vW; val vIT = vT + vW; val vIR = vR - vW; val vIB = vB - vW
				if (vIR <= vIL || vIB <= vIT) {
					emitQuad(vL, vT, vR, vT, vR, vB, vL, vB, vSampler)
				} else {
					emitQuad(vL, vT, vR, vT, vIR, vIT, vIL, vIT, vSampler) // top
					emitQuad(vIR, vIT, vR, vT, vR, vB, vIR, vIB, vSampler) // right
					emitQuad(vIL, vIB, vIR, vIB, vR, vB, vL, vB, vSampler) // bottom
					emitQuad(vL, vT, vIL, vIT, vIL, vIB, vL, vB, vSampler) // left
				}
			}
		}
	}

	override fun drawCircle(
		brush: Brush,
		radius: Float,
		center: Offset,
		alpha: Float,
		style: DrawStyle,
	) {
		drawArc(
			brush = brush,
			startAngle = 0f,
			sweepAngle = 360f,
			useCenter = (style === Fill),
			topLeft = Offset(center.x - radius, center.y - radius),
			size = Size(radius * 2f, radius * 2f),
			alpha = alpha,
			style = style,
		)
	}

	override fun drawArc(
		brush: Brush,
		startAngle: Float,
		sweepAngle: Float,
		useCenter: Boolean,
		topLeft: Offset,
		size: Size,
		alpha: Float,
		style: DrawStyle,
	) {
		val vSampler = samplerFor(brush, size, alpha)
		val vCx = fOriginX + topLeft.x + size.width / 2f
		val vCy = fOriginY + topLeft.y + size.height / 2f
		val vRx = size.width / 2f
		val vRy = size.height / 2f
		val vSeg = arcSegments(sweepAngle)

		when (style) {
			Fill -> emitFilledArc(vCx, vCy, vRx, vRy, startAngle, sweepAngle, useCenter, vSeg, vSampler)
			is Stroke -> {
				val vR = (vRx + vRy) / 2f
				val vOuter = vR + style.width / 2f
				val vInner = (vR - style.width / 2f).coerceAtLeast(0f)
				emitStrokedArc(vCx, vCy, vInner, vOuter, startAngle, sweepAngle, vSeg, vSampler)
				if (style.cap == StrokeCap.Round && sweepAngle < 360f) {
					emitRoundCap(vCx, vCy, vR, style.width / 2f, startAngle, vSampler)
					emitRoundCap(vCx, vCy, vR, style.width / 2f, startAngle + sweepAngle, vSampler)
				}
			}
		}
	}

	override fun drawPath(
		path: ComposePath,
		brush: Brush,
		alpha: Float,
		style: DrawStyle,
	) {
		// Linearise the path into polyline sub-paths, then fan-triangulate
		// each. Stroking is approximated as a per-segment thick quad.
		val vSampler = samplerFor(brush, size, alpha)
		val vSubpaths = linearisePath(path)
		when (style) {
			Fill -> for (vSub in vSubpaths) fanFill(vSub, vSampler)
			is Stroke -> for (vSub in vSubpaths) strokePolyline(vSub, style.width, vSampler)
		}
	}

	override fun drawOval(
		brush: Brush,
		topLeft: Offset,
		size: Size,
		alpha: Float,
		style: DrawStyle,
	) {
		val vSampler = samplerFor(brush, size, alpha)
		val vCx = fOriginX + topLeft.x + size.width / 2f
		val vCy = fOriginY + topLeft.y + size.height / 2f
		val vRx = size.width / 2f
		val vRy = size.height / 2f
		when (style) {
			Fill -> emitFilledArc(vCx, vCy, vRx, vRy, 0f, 360f, true, 64, vSampler)
			is Stroke -> {
				// Approximate oval stroke as ring between r - w/2 and r + w/2
				// using the smaller axis as the radius reference.
				val vR = kotlin.math.min(vRx, vRy)
				emitStrokedArc(vCx, vCy, vR - style.width / 2f, vR + style.width / 2f, 0f, 360f, 64, vSampler)
			}
		}
	}

	override fun drawRoundRect(
		brush: Brush,
		topLeft: Offset,
		size: Size,
		cornerRadius: Float,
		alpha: Float,
		style: DrawStyle,
	) {
		val vSampler = samplerFor(brush, size, alpha)
		val vR = cornerRadius.coerceIn(0f, kotlin.math.min(size.width, size.height) / 2f)
		val vX = fOriginX + topLeft.x
		val vY = fOriginY + topLeft.y
		val vW = size.width
		val vH = size.height
		if (vR <= 0f) {
			// Trivial: just two triangles.
			emitQuad(vX, vY, vX + vW, vY, vX + vW, vY + vH, vX, vY + vH, vSampler)
			return
		}
		// Body in 3 strips: middle (full width × inner height), top edge,
		// bottom edge — plus the 4 corner arcs.
		if (style == Fill) {
			// Middle strip
			emitQuad(vX, vY + vR, vX + vW, vY + vR, vX + vW, vY + vH - vR, vX, vY + vH - vR, vSampler)
			// Top edge (between left+right corners)
			emitQuad(vX + vR, vY, vX + vW - vR, vY, vX + vW - vR, vY + vR, vX + vR, vY + vR, vSampler)
			// Bottom edge
			emitQuad(vX + vR, vY + vH - vR, vX + vW - vR, vY + vH - vR, vX + vW - vR, vY + vH, vX + vR, vY + vH, vSampler)
			// 4 corner fills
			emitFilledArc(vX + vR,          vY + vR,         vR, vR, 180f,  90f, false, 16, vSampler)
			emitFilledArc(vX + vW - vR,     vY + vR,         vR, vR, 270f,  90f, false, 16, vSampler)
			emitFilledArc(vX + vW - vR,     vY + vH - vR,    vR, vR,   0f,  90f, false, 16, vSampler)
			emitFilledArc(vX + vR,          vY + vH - vR,    vR, vR,  90f,  90f, false, 16, vSampler)
		} else if (style is Stroke) {
			// Stroked rounded rect: 4 straight edges + 4 quarter arcs.
			val vSw = style.width
			drawLine(brush, Offset(topLeft.x + cornerRadius, topLeft.y), Offset(topLeft.x + vW - cornerRadius, topLeft.y), vSw, StrokeCap.Butt, alpha)
			drawLine(brush, Offset(topLeft.x + vW, topLeft.y + cornerRadius), Offset(topLeft.x + vW, topLeft.y + vH - cornerRadius), vSw, StrokeCap.Butt, alpha)
			drawLine(brush, Offset(topLeft.x + vW - cornerRadius, topLeft.y + vH), Offset(topLeft.x + cornerRadius, topLeft.y + vH), vSw, StrokeCap.Butt, alpha)
			drawLine(brush, Offset(topLeft.x, topLeft.y + vH - cornerRadius), Offset(topLeft.x, topLeft.y + cornerRadius), vSw, StrokeCap.Butt, alpha)
			emitStrokedArc(vX + vR,      vY + vR,      vR - vSw / 2f, vR + vSw / 2f, 180f, 90f, 16, vSampler)
			emitStrokedArc(vX + vW - vR, vY + vR,      vR - vSw / 2f, vR + vSw / 2f, 270f, 90f, 16, vSampler)
			emitStrokedArc(vX + vW - vR, vY + vH - vR, vR - vSw / 2f, vR + vSw / 2f,   0f, 90f, 16, vSampler)
			emitStrokedArc(vX + vR,      vY + vH - vR, vR - vSw / 2f, vR + vSw / 2f,  90f, 90f, 16, vSampler)
		}
	}

	override fun drawLine(
		brush: Brush,
		start: Offset,
		end: Offset,
		strokeWidth: Float,
		cap: StrokeCap,
		alpha: Float,
	) {
		val vSampler = samplerFor(brush, size, alpha)
		val vX1 = fOriginX + start.x
		val vY1 = fOriginY + start.y
		val vX2 = fOriginX + end.x
		val vY2 = fOriginY + end.y
		val vDx = vX2 - vX1
		val vDy = vY2 - vY1
		val vLen = sqrt(vDx * vDx + vDy * vDy)
		if (vLen < 1e-4f) return
		val vNx = -vDy / vLen * strokeWidth / 2f
		val vNy = vDx / vLen * strokeWidth / 2f
		emitQuad(
			vX1 + vNx, vY1 + vNy,
			vX2 + vNx, vY2 + vNy,
			vX2 - vNx, vY2 - vNy,
			vX1 - vNx, vY1 - vNy,
			vSampler,
		)
		if (cap == StrokeCap.Round) {
			emitFilledArc(vX1, vY1, strokeWidth / 2f, strokeWidth / 2f, 0f, 360f, true, 12, vSampler)
			emitFilledArc(vX2, vY2, strokeWidth / 2f, strokeWidth / 2f, 0f, 360f, true, 12, vSampler)
		}
	}

	// ============
	//  Path tessellation: linearise curves into polylines, then
	//  fan-triangulate each sub-path. Fan triangulation only works
	//  correctly for convex polygons; concave shapes (rare in UI) will
	//  show overlap artifacts at the concave vertices. Cubics are
	//  subdivided into a fixed 16 segments (good enough for typical
	//  UI sizes); refine if needed.

	private fun linearisePath(inPath: ComposePath): List<List<Pair<Float, Float>>> {
		val vSubs = mutableListOf<MutableList<Pair<Float, Float>>>()
		var vCurrent: MutableList<Pair<Float, Float>>? = null
		var vCx = 0f; var vCy = 0f
		// Path is now an interface; our concrete impl is ProjectPath in
		// commonMain. Cast to read the PathCommand list directly — falls
		// back to an empty path if a foreign Path implementation slips in.
		val vCommands = (inPath as? com.compose.desktop.native.graphics.ProjectPath)?.commands ?: emptyList()
		for (vCmd in vCommands) when (vCmd) {
			is PathCommand.MoveTo -> {
				vCx = fOriginX + vCmd.x; vCy = fOriginY + vCmd.y
				vCurrent = mutableListOf(vCx to vCy)
				vSubs.add(vCurrent)
			}
			is PathCommand.LineTo -> {
				vCx = fOriginX + vCmd.x; vCy = fOriginY + vCmd.y
				vCurrent?.add(vCx to vCy)
			}
			is PathCommand.QuadTo -> {
				val vEx = fOriginX + vCmd.x; val vEy = fOriginY + vCmd.y
				val vCx1 = fOriginX + vCmd.cx; val vCy1 = fOriginY + vCmd.cy
				val vN = 12
				for (vI in 1..vN) {
					val vT = vI.toFloat() / vN
					val vOne = 1f - vT
					val vXx = vOne * vOne * vCx + 2f * vOne * vT * vCx1 + vT * vT * vEx
					val vYy = vOne * vOne * vCy + 2f * vOne * vT * vCy1 + vT * vT * vEy
					vCurrent?.add(vXx to vYy)
				}
				vCx = vEx; vCy = vEy
			}
			is PathCommand.CubicTo -> {
				val vC1x = fOriginX + vCmd.c1x; val vC1y = fOriginY + vCmd.c1y
				val vC2x = fOriginX + vCmd.c2x; val vC2y = fOriginY + vCmd.c2y
				val vEx = fOriginX + vCmd.x;     val vEy = fOriginY + vCmd.y
				val vN = 16
				for (vI in 1..vN) {
					val vT = vI.toFloat() / vN
					val vOne = 1f - vT
					val vXx = vOne * vOne * vOne * vCx + 3f * vOne * vOne * vT * vC1x +
					          3f * vOne * vT * vT * vC2x + vT * vT * vT * vEx
					val vYy = vOne * vOne * vOne * vCy + 3f * vOne * vOne * vT * vC1y +
					          3f * vOne * vT * vT * vC2y + vT * vT * vT * vEy
					vCurrent?.add(vXx to vYy)
				}
				vCx = vEx; vCy = vEy
			}
			PathCommand.Close -> {
				val vList = vCurrent ?: continue
				val vFirst = vList.firstOrNull() ?: continue
				if (vList.last() != vFirst) vList.add(vFirst)
			}
		}
		return vSubs
	}

	private fun fanFill(inPolyline: List<Pair<Float, Float>>, inSampler: Sampler) {
		if (inPolyline.size < 3) return
		val (vAx, vAy) = inPolyline[0]
		for (vI in 1 until inPolyline.size - 1) {
			val (vBx, vBy) = inPolyline[vI]
			val (vCx, vCy) = inPolyline[vI + 1]
			emitTri(vAx, vAy, vBx, vBy, vCx, vCy, inSampler)
		}
	}

	private fun strokePolyline(inPolyline: List<Pair<Float, Float>>, inWidth: Float, inSampler: Sampler) {
		for (vI in 0 until inPolyline.size - 1) {
			val (vAx, vAy) = inPolyline[vI]
			val (vBx, vBy) = inPolyline[vI + 1]
			val vDx = vBx - vAx; val vDy = vBy - vAy
			val vLen = sqrt(vDx * vDx + vDy * vDy)
			if (vLen < 1e-4f) continue
			val vNx = -vDy / vLen * inWidth / 2f
			val vNy =  vDx / vLen * inWidth / 2f
			emitQuad(vAx + vNx, vAy + vNy, vBx + vNx, vBy + vNy, vBx - vNx, vBy - vNy, vAx - vNx, vAy - vNy, inSampler)
		}
	}

	// ============
	//  Tessellation primitives

	/* Number of arc segments proportional to sweep — 64 around a full
	   circle, never less than 8 for very short arcs (avoids visible
	   facets at the head when sweep is small). */
	private fun arcSegments(inSweepDeg: Float): Int {
		val vAbs = if (inSweepDeg < 0) -inSweepDeg else inSweepDeg
		return max(8, ((vAbs / 360f) * 64f).toInt() + 1)
	}

	private fun emitFilledArc(
		inCx: Float, inCy: Float, inRx: Float, inRy: Float,
		inStartDeg: Float, inSweepDeg: Float, inUseCenter: Boolean,
		inSegments: Int, inSampler: Sampler,
	) {
		val vStartRad = inStartDeg * (PI / 180.0).toFloat()
		val vSweepRad = inSweepDeg * (PI / 180.0).toFloat()
		val vStep = vSweepRad / inSegments
		for (i in 0 until inSegments) {
			val vA = vStartRad + i * vStep
			val vB = vStartRad + (i + 1) * vStep
			val vAx = inCx + inRx * cos(vA)
			val vAy = inCy + inRy * sin(vA)
			val vBx = inCx + inRx * cos(vB)
			val vBy = inCy + inRy * sin(vB)
			emitTri(inCx, inCy, vAx, vAy, vBx, vBy, inSampler)
		}
	}

	private fun emitStrokedArc(
		inCx: Float, inCy: Float, inInnerR: Float, inOuterR: Float,
		inStartDeg: Float, inSweepDeg: Float, inSegments: Int,
		inSampler: Sampler,
	) {
		val vStartRad = inStartDeg * (PI / 180.0).toFloat()
		val vSweepRad = inSweepDeg * (PI / 180.0).toFloat()
		val vStep = vSweepRad / inSegments
		for (i in 0 until inSegments) {
			val vA = vStartRad + i * vStep
			val vB = vStartRad + (i + 1) * vStep
			val vCosA = cos(vA); val vSinA = sin(vA)
			val vCosB = cos(vB); val vSinB = sin(vB)
			val vOAX = inCx + inOuterR * vCosA; val vOAY = inCy + inOuterR * vSinA
			val vOBX = inCx + inOuterR * vCosB; val vOBY = inCy + inOuterR * vSinB
			val vIAX = inCx + inInnerR * vCosA; val vIAY = inCy + inInnerR * vSinA
			val vIBX = inCx + inInnerR * vCosB; val vIBY = inCy + inInnerR * vSinB
			emitQuad(vOAX, vOAY, vOBX, vOBY, vIBX, vIBY, vIAX, vIAY, inSampler)
		}
	}

	private fun emitRoundCap(
		inCx: Float, inCy: Float, inR: Float, inCapRadius: Float,
		inAtAngleDeg: Float, inSampler: Sampler,
	) {
		val vRad = inAtAngleDeg * (PI / 180.0).toFloat()
		val vPx = inCx + inR * cos(vRad)
		val vPy = inCy + inR * sin(vRad)
		emitFilledArc(vPx, vPy, inCapRadius, inCapRadius, 0f, 360f, true, 12, inSampler)
	}

	private fun emitQuad(
		ax: Float, ay: Float, bx: Float, by: Float,
		cx: Float, cy: Float, dx: Float, dy: Float,
		inSampler: Sampler,
	) {
		emitTri(ax, ay, bx, by, cx, cy, inSampler)
		emitTri(ax, ay, cx, cy, dx, dy, inSampler)
	}

	private fun emitTri(
		ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float,
		inSampler: Sampler,
	) {
		writeVertex(pushVertex(), ax, ay, inSampler(ax, ay))
		writeVertex(pushVertex(), bx, by, inSampler(bx, by))
		writeVertex(pushVertex(), cx, cy, inSampler(cx, cy))
	}

	private fun writeVertex(inV: SDL_Vertex, inX: Float, inY: Float, inColor: ComposeColor) {
		inV.position.x = inX
		inV.position.y = inY
		inV.color.r = inColor.r8 / 255f
		inV.color.g = inColor.g8 / 255f
		inV.color.b = inColor.b8 / 255f
		inV.color.a = inColor.a8 / 255f
		inV.tex_coord.x = 0f
		inV.tex_coord.y = 0f
	}
}

// ============
//  Batch capacity — 8192 vertices = ~2730 triangles per submission. At
//  64 segments per full circle that's room for ~21 full-circle filled
//  shapes per Canvas{} before any flush. Bigger gives fewer GPU
//  submissions; smaller saves RAM. ~128 KB at 16 bytes per SDL_Vertex.
private const val kBatchCapacity: Int = 8192

// ==================
// MARK: Brush → per-vertex colour sampler
// ==================

/* A Sampler is a (x, y) → ComposeColor function in this scope's pixel
   coordinate space. The triangle emitters call it once per vertex; the GPU
   linearly interpolates between vertex colours across each triangle, which
   approximates the gradient shading you'd get from a real shader. The
   approximation is good when the tessellation is fine enough — circles use
   64 segments by default, so the inter-vertex gap is < 6° which keeps the
   linear-interp colour close to the analytic gradient value. */
private typealias Sampler = (x: Float, y: Float) -> ComposeColor

@OptIn(ExperimentalForeignApi::class)
private fun Sdl3DrawScope.samplerFor(
	inBrush: Brush,
	inShapeSize: Size,
	inAlpha: Float,
): Sampler = when (inBrush) {
	is SolidColor -> {
		val vC = inBrush.value.withAlphaScaled(inAlpha)
		val vS: Sampler = { _, _ -> vC }
		vS
	}
	is LinearGradient -> linearSampler(inBrush, inShapeSize, inAlpha)
	is RadialGradient -> radialSampler(inBrush, inShapeSize, inAlpha)
	is SweepGradient -> sweepSampler(inBrush, inShapeSize, inAlpha)
}

@OptIn(ExperimentalForeignApi::class)
private fun Sdl3DrawScope.linearSampler(inB: LinearGradient, inSize: Size, inAlpha: Float): Sampler {
	val vSx = originX + resolveX(inB.gradientStart.x, inSize.width)
	val vSy = originY + resolveY(inB.gradientStart.y, inSize.height)
	val vEx = originX + resolveX(inB.gradientEnd.x, inSize.width)
	val vEy = originY + resolveY(inB.gradientEnd.y, inSize.height)
	val vDx = vEx - vSx
	val vDy = vEy - vSy
	val vLen2 = vDx * vDx + vDy * vDy
	val vColors = inB.gradientColors
	val vStops = inB.gradientStops
	return { x, y ->
		val vT = if (vLen2 < 1e-6f) 0f
		         else (((x - vSx) * vDx + (y - vSy) * vDy) / vLen2).coerceIn(0f, 1f)
		sampleColors(vColors, vStops, vT).withAlphaScaled(inAlpha)
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun Sdl3DrawScope.radialSampler(inB: RadialGradient, inSize: Size, inAlpha: Float): Sampler {
	val vCx = originX + resolveX(inB.gradientCenter.x, inSize.width)
	val vCy = originY + resolveY(inB.gradientCenter.y, inSize.height)
	val vR = if (inB.gradientRadius.isFinite()) inB.gradientRadius else (inSize.minDimension / 2f)
	val vColors = inB.gradientColors
	val vStops = inB.gradientStops
	return { x, y ->
		val vDx = x - vCx; val vDy = y - vCy
		val vT = if (vR < 1e-6f) 0f
		         else (sqrt(vDx * vDx + vDy * vDy) / vR).coerceIn(0f, 1f)
		sampleColors(vColors, vStops, vT).withAlphaScaled(inAlpha)
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun Sdl3DrawScope.sweepSampler(inB: SweepGradient, inSize: Size, inAlpha: Float): Sampler {
	val vCx = originX + resolveX(inB.gradientCenter.x, inSize.width)
	val vCy = originY + resolveY(inB.gradientCenter.y, inSize.height)
	val vColors = inB.gradientColors
	val vStops = inB.gradientStops
	return { x, y ->
		// atan2 returns radians in [-π, π]. Map to [0, 1] going clockwise from
		// 3 o'clock to match Skia's sweep gradient convention.
		val vAng = kotlin.math.atan2(y - vCy, x - vCx)
		val vT = ((vAng / (2.0 * PI) + 1.0) % 1.0).toFloat()
		sampleColors(vColors, vStops, vT).withAlphaScaled(inAlpha)
	}
}

/* Sample the colour at position [0..1] along the gradient. Uses uniform
   distribution when stops is null, otherwise the user-supplied stops. */
private fun sampleColors(
	inColors: List<ComposeColor>,
	inStops: List<Float>?,
	inT: Float,
): ComposeColor {
	if (inColors.isEmpty()) return ComposeColor.Transparent
	if (inColors.size == 1) return inColors[0]
	val vStops = inStops ?: (0 until inColors.size).map { it / (inColors.size - 1f) }
	// Walk to the bracketing stop pair.
	if (inT <= vStops.first()) return inColors.first()
	if (inT >= vStops.last()) return inColors.last()
	var vIdx = 0
	while (vIdx < vStops.size - 1 && vStops[vIdx + 1] < inT) vIdx++
	val vS1 = vStops[vIdx]
	val vS2 = vStops[vIdx + 1]
	val vSpan = (vS2 - vS1).coerceAtLeast(1e-6f)
	val vLocal = ((inT - vS1) / vSpan).coerceIn(0f, 1f)
	return lerpColor(inColors[vIdx], inColors[vIdx + 1], vLocal)
}

private fun lerpColor(inA: ComposeColor, inB: ComposeColor, inT: Float): ComposeColor =
	ComposeColor(
		red   = inA.red   + (inB.red   - inA.red)   * inT,
		green = inA.green + (inB.green - inA.green) * inT,
		blue  = inA.blue  + (inB.blue  - inA.blue)  * inT,
		alpha = inA.alpha + (inB.alpha - inA.alpha) * inT,
	)

private fun ComposeColor.withAlphaScaled(inAlpha: Float): ComposeColor =
	if (inAlpha >= 1f) this else copy(alpha = alpha * inAlpha)

private fun resolveX(inV: Float, inW: Float): Float = if (inV.isFinite()) inV else inW
private fun resolveY(inV: Float, inH: Float): Float = if (inV.isFinite()) inV else inH
