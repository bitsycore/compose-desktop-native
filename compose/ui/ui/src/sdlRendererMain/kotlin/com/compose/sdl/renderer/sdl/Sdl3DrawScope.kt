package com.compose.sdl.renderer.sdl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import com.compose.sdl.graphics.r8
import com.compose.sdl.graphics.g8
import com.compose.sdl.graphics.b8
import com.compose.sdl.graphics.a8
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.RadialGradient
import com.compose.sdl.graphics.PathCommand
import com.compose.sdl.graphics.gradientCenter
import com.compose.sdl.graphics.gradientColors
import com.compose.sdl.graphics.gradientEnd
import com.compose.sdl.graphics.gradientRadius
import com.compose.sdl.graphics.gradientStart
import com.compose.sdl.graphics.gradientStops
import com.compose.sdl.graphics.gradientTileMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
	inInitialOriginX: Float,
	inInitialOriginY: Float,
	override val size: Size,
) : DrawScope {

	// Draw origin in the SDL renderer's absolute pixel space. Mutable so an
	// Sdl3Canvas can retarget one scope's origin per draw call (Canvas.translate
	// accumulation) instead of allocating a scope per node. The current
	// tree-walk renderer sets it once via the ctor and never moves it.
	private var fOriginX: Float = inInitialOriginX
	private var fOriginY: Float = inInitialOriginY
	internal fun setOrigin(inX: Float, inY: Float) { fOriginX = inX; fOriginY = inY }

	// Exposed for the sampler extension functions — they map gradient
	// anchor points (in node-local coords) into the SDL renderer's
	// absolute pixel space.
	internal val originX: Float get() = fOriginX
	internal val originY: Float get() = fOriginY

	// User→screen affine (a,b,c,d,e,f): screen = (a*x+c*y+e, b*x+d*y+f). The
	// canvas sets this per draw so graphicsLayer translate / scale / rotate apply
	// to every emitted vertex. Identity by default; positions are otherwise the
	// scope's own (origin-relative) coords. Gradient samplers still run in the
	// pre-transform space (originX/Y), so a gradient transforms with its shape.
	private var fMa = 1f; private var fMb = 0f; private var fMc = 0f
	private var fMd = 1f; private var fMe = 0f; private var fMf = 0f
	internal fun setMatrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
		fMa = a; fMb = b; fMc = c; fMd = d; fMe = e; fMf = f
	}

	// ============
	//  Vertex batch — heap-allocated buffer of SDL_Vertex shared across
	//  every draw call in this scope. Submitted in one SDL_RenderGeometry
	//  on flush() (called from the renderer after the user's drawer
	//  lambda returns). Auto-flushes if the buffer is about to overflow.

	private val fBatch: CPointer<SDL_Vertex> = nativeHeap.allocArray(kBatchCapacity)
	private var fBatchCount: Int = 0
	// Vertices are staged into a plain Kotlin FloatArray (8 floats per SDL_Vertex:
	// pos.xy, color.rgba, tex.xy — tightly packed) and bulk-copied to the native
	// buffer once per flush. Writing floats to a Kotlin array is far cheaper than
	// per-field cinterop struct access, which dominated once AA tripled the vertex
	// count. kFloatsPerVertex must match SDL_Vertex's layout.
	private val fVertexData = FloatArray(kBatchCapacity * kFloatsPerVertex)

	/* Submit the accumulated triangles to SDL: copy the staged floats into the
	   native SDL_Vertex buffer in one memcpy, then one SDL_RenderGeometry. Safe to
	   call mid-scope when the buffer fills up (no cross-triangle state). */
	fun flush() {
		if (fBatchCount == 0) return
		fVertexData.usePinned { vPinned ->
			platform.posix.memcpy(
				fBatch,
				vPinned.addressOf(0),
				(fBatchCount * kFloatsPerVertex * 4).convert(),
			)
		}
		SDL_RenderGeometry(fRenderer.reinterpret(), null, fBatch, fBatchCount, null, 0)
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

	// ============
	//  DrawScope contract (upstream shape). Shapes tessellate via the private
	//  *Core helpers below; the color overloads wrap the colour in SolidColor.
	//  colorFilter / blendMode / pathEffect are accept-and-ignore (the SDL
	//  triangle pipeline has no equivalent). drawImage / drawPoints are no-ops
	//  here — images paint through the renderer's painter leaf, not DrawScope.
	//  drawContext.canvas stays EmptyCanvas (unused: primitives paint direct).

	override val density: Float get() = 1f
	override val fontScale: Float get() = 1f
	override val layoutDirection: androidx.compose.ui.unit.LayoutDirection
		get() = androidx.compose.ui.unit.LayoutDirection.Ltr

	override val drawContext: DrawContext = object : DrawContext {
		override var size: Size = this@Sdl3DrawScope.size
		override val transform: DrawTransform = object : DrawTransform {
			override val size: Size get() = this@Sdl3DrawScope.size
			override val center: Offset get() = Offset(size.width / 2f, size.height / 2f)
			override fun inset(left: Float, top: Float, right: Float, bottom: Float) {}
			override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {}
			override fun clipPath(path: ComposePath, clipOp: ClipOp) {}
			override fun translate(left: Float, top: Float) {}
			override fun rotate(degrees: Float, pivot: Offset) {}
			override fun scale(scaleX: Float, scaleY: Float, pivot: Offset) {}
			override fun transform(matrix: Matrix) {}
		}
	}

	override fun drawRect(brush: Brush, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		rectCore(brush, topLeft, size, alpha, style)
	override fun drawRect(color: ComposeColor, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		rectCore(SolidColor(color), topLeft, size, alpha, style)

	override fun drawCircle(brush: Brush, radius: Float, center: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		circleCore(brush, radius, center, alpha, style)
	override fun drawCircle(color: ComposeColor, radius: Float, center: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		circleCore(SolidColor(color), radius, center, alpha, style)

	override fun drawArc(brush: Brush, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		arcCore(brush, startAngle, sweepAngle, useCenter, topLeft, size, alpha, style)
	override fun drawArc(color: ComposeColor, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		arcCore(SolidColor(color), startAngle, sweepAngle, useCenter, topLeft, size, alpha, style)

	override fun drawOval(brush: Brush, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		ovalCore(brush, topLeft, size, alpha, style)
	override fun drawOval(color: ComposeColor, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		ovalCore(SolidColor(color), topLeft, size, alpha, style)

	override fun drawRoundRect(brush: Brush, topLeft: Offset, size: Size, cornerRadius: CornerRadius, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		roundRectCore(brush, topLeft, size, cornerRadius.x, alpha, style)
	override fun drawRoundRect(color: ComposeColor, topLeft: Offset, size: Size, cornerRadius: CornerRadius, style: DrawStyle, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) =
		roundRectCore(SolidColor(color), topLeft, size, cornerRadius.x, alpha, style)

	override fun drawPath(path: ComposePath, brush: Brush, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		pathCore(path, brush, alpha, style)
	override fun drawPath(path: ComposePath, color: ComposeColor, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) =
		pathCore(path, SolidColor(color), alpha, style)

	override fun drawLine(brush: Brush, start: Offset, end: Offset, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) =
		lineCore(brush, start, end, strokeWidth, cap, alpha)
	override fun drawLine(color: ComposeColor, start: Offset, end: Offset, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) =
		lineCore(SolidColor(color), start, end, strokeWidth, cap, alpha)

	override fun drawImage(image: ImageBitmap, topLeft: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {}
	@Deprecated("Use the overload that takes a FilterQuality", level = DeprecationLevel.HIDDEN)
	override fun drawImage(image: ImageBitmap, srcOffset: IntOffset, srcSize: IntSize, dstOffset: IntOffset, dstSize: IntSize, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {}
	override fun drawImage(image: ImageBitmap, srcOffset: IntOffset, srcSize: IntSize, dstOffset: IntOffset, dstSize: IntSize, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode, filterQuality: FilterQuality) {}

	override fun drawPoints(points: List<Offset>, pointMode: PointMode, color: ComposeColor, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {}
	override fun drawPoints(points: List<Offset>, pointMode: PointMode, brush: Brush, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {}

	internal fun rectCore(
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
			Fill -> {
				emitQuad(vL, vT, vR, vT, vR, vB, vL, vB, vSampler)
				// Axis-aligned rects are pixel-crisp and need no AA. When the affine
				// rotates/shears them, the edges become diagonal — feather each edge
				// outward (normals in local space; the matrix orients them on screen).
				if (fMb != 0f || fMc != 0f) {
					emitEdgeFringe(vL, vT, vR, vT, 0f, -kAaFeather, vSampler) // top
					emitEdgeFringe(vR, vT, vR, vB, kAaFeather, 0f, vSampler)  // right
					emitEdgeFringe(vR, vB, vL, vB, 0f, kAaFeather, vSampler)  // bottom
					emitEdgeFringe(vL, vB, vL, vT, -kAaFeather, 0f, vSampler) // left
				}
			}
			is Stroke -> {
				// Center the stroke on the rect path (Compose stroke semantics; matches
				// roundRectCore and the border-ring path). Modifier.border pre-insets the
				// rect by halfStroke, so the centered outer edges land back exactly on the
				// node bounds — pixel-crisp and 1:1 with rounded borders on the same row
				// (an inward stroke here sat half a pixel high → a 1px seam next to a
				// rounded segment in a SegmentedButtonRow).
				val vHw = style.width / 2f
				val vOL = vL - vHw; val vOT = vT - vHw; val vOR = vR + vHw; val vOB = vB + vHw
				val vIL = vL + vHw; val vIT = vT + vHw; val vIR = vR - vHw; val vIB = vB - vHw
				if (vIR <= vIL || vIB <= vIT) {
					emitQuad(vOL, vOT, vOR, vOT, vOR, vOB, vOL, vOB, vSampler)
				} else {
					emitQuad(vOL, vOT, vOR, vOT, vIR, vIT, vIL, vIT, vSampler) // top
					emitQuad(vIR, vIT, vOR, vOT, vOR, vOB, vIR, vIB, vSampler) // right
					emitQuad(vIL, vIB, vIR, vIB, vOR, vOB, vOL, vOB, vSampler) // bottom
					emitQuad(vOL, vOT, vIL, vIT, vIL, vIB, vOL, vOB, vSampler) // left
				}
			}
		}
	}

	internal fun circleCore(
		brush: Brush,
		radius: Float,
		center: Offset,
		alpha: Float,
		style: DrawStyle,
	) {
		arcCore(
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

	internal fun arcCore(
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
		val vSeg = arcSegments(sweepAngle, max(vRx, vRy))

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

	internal fun pathCore(
		path: ComposePath,
		brush: Brush,
		alpha: Float,
		style: DrawStyle,
	) {
		// Linearise the path into polyline sub-paths, then fan-triangulate
		// each. Stroking is approximated as a per-segment thick quad.
		val vSampler = samplerFor(brush, size, alpha)
		when (style) {
			Fill -> {
				// EVEN-ODD two-contour path = a ring (Modifier.border on a non-simple
				// shape: outer ∪ inset, see ProjectPath.op). Draw it as a real stroked
				// rounded rect — straight edges + AA'd corner arcs, exactly like a
				// symmetric rounded border — so it's pixel-identical to the rest.
				val vProj = path as? com.compose.sdl.graphics.ProjectPath
				if (vProj != null && vProj.fillType == androidx.compose.ui.graphics.PathFillType.EvenOdd) {
					if (strokeEvenOddRingBorder(vProj, brush, alpha, vSampler)) return
					// Fallback for a ring we can't recognise: fill the strip between
					// the two contours (still a ring, just without the arc AA).
					val vSubpaths = linearisePath(path)
					if (vSubpaths.size == 2 && vSubpaths[0].size == vSubpaths[1].size) {
						ringFill(vSubpaths[0], vSubpaths[1], vSampler)
					} else {
						for (vSub in vSubpaths) fanFill(vSub, vSampler)
					}
					return
				}
				for (vSub in linearisePath(path)) fanFill(vSub, vSampler)
			}
			is Stroke -> for (vSub in linearisePath(path)) strokePolyline(vSub, style.width, vSampler)
		}
	}

	// Detects a Modifier.border ring — the even-odd concatenation of an outer
	// addRoundRect and its inset (both emitted by ProjectPath.addRoundRect, so a
	// fixed 10-command pattern each) — and renders it as a proper stroked rounded
	// rect: straight edges via lineCore + corners via the AA'd emitStrokedArc, the
	// SAME primitives roundRectCore uses for symmetric borders. Pixel-identical to
	// those. Returns false (caller falls back) if the path isn't such a ring.
	private fun strokeEvenOddRingBorder(inPath: com.compose.sdl.graphics.ProjectPath, inBrush: Brush, inAlpha: Float, inSampler: Sampler): Boolean {
		val vCmds = inPath.commands
		if (vCmds.size != 20) return false
		val vO = parseAddRoundRect(vCmds, 0) ?: return false
		val vIn = parseAddRoundRect(vCmds, 10) ?: return false
		val vL = vO[0]; val vT = vO[1]; val vR = vO[2]; val vB = vO[3]
		val vSw = vIn[0] - vL
		if (vSw <= 0.05f) return false
		// Inset must be uniform on all four sides (a true border ring).
		if (kotlin.math.abs((vIn[1] - vT) - vSw) > 0.5f ||
			kotlin.math.abs((vR - vIn[2]) - vSw) > 0.5f ||
			kotlin.math.abs((vB - vIn[3]) - vSw) > 0.5f
		) return false
		val vTlx = vO[4]; val vTly = vO[5]; val vTrx = vO[6]; val vTry = vO[7]
		val vBrx = vO[8]; val vBry = vO[9]; val vBlx = vO[10]; val vBly = vO[11]
		// emitStrokedArc is circular — bail on elliptical corners.
		if (kotlin.math.abs(vTlx - vTly) > 0.5f || kotlin.math.abs(vTrx - vTry) > 0.5f ||
			kotlin.math.abs(vBrx - vBry) > 0.5f || kotlin.math.abs(vBlx - vBly) > 0.5f
		) return false

		val vHalf = vSw / 2f
		// Straight sides — centerline inset by half the stroke, width = stroke.
		lineCore(inBrush, Offset(vL + vTlx, vT + vHalf), Offset(vR - vTrx, vT + vHalf), vSw, StrokeCap.Butt, inAlpha) // top
		lineCore(inBrush, Offset(vR - vHalf, vT + vTry), Offset(vR - vHalf, vB - vBry), vSw, StrokeCap.Butt, inAlpha) // right
		lineCore(inBrush, Offset(vR - vBrx, vB - vHalf), Offset(vL + vBlx, vB - vHalf), vSw, StrokeCap.Butt, inAlpha) // bottom
		lineCore(inBrush, Offset(vL + vHalf, vB - vBly), Offset(vL + vHalf, vT + vTly), vSw, StrokeCap.Butt, inAlpha) // left
		// Corner arcs (concentric ring [R−sw, R] at the corner centre). Square
		// corners (R≈0) are already covered where the straight edges overlap.
		if (vTlx > 0.05f) emitStrokedArc(fOriginX + vL + vTlx, fOriginY + vT + vTly, vTlx - vSw, vTlx, 180f, 90f, arcSegments(90f, vTlx), inSampler)
		if (vTrx > 0.05f) emitStrokedArc(fOriginX + vR - vTrx, fOriginY + vT + vTry, vTrx - vSw, vTrx, 270f, 90f, arcSegments(90f, vTrx), inSampler)
		if (vBrx > 0.05f) emitStrokedArc(fOriginX + vR - vBrx, fOriginY + vB - vBry, vBrx - vSw, vBrx, 0f, 90f, arcSegments(90f, vBrx), inSampler)
		if (vBlx > 0.05f) emitStrokedArc(fOriginX + vL + vBlx, fOriginY + vB - vBly, vBlx - vSw, vBlx, 90f, 90f, arcSegments(90f, vBlx), inSampler)
		return true
	}

	// Parses a 10-command run emitted by ProjectPath.addRoundRect (Move, Line,
	// Cubic, Line, Cubic, Line, Cubic, Line, Cubic, Close) back into its rect
	// bounds + per-corner radii: [l, t, r, b, tlx, tly, trx, try, brx, bry, blx, bly].
	private fun parseAddRoundRect(inCmds: List<PathCommand>, inOff: Int): FloatArray? {
		val vM = inCmds[inOff] as? PathCommand.MoveTo ?: return null
		val vL1 = inCmds[inOff + 1] as? PathCommand.LineTo ?: return null
		val vC2 = inCmds[inOff + 2] as? PathCommand.CubicTo ?: return null
		val vL3 = inCmds[inOff + 3] as? PathCommand.LineTo ?: return null
		val vC4 = inCmds[inOff + 4] as? PathCommand.CubicTo ?: return null
		val vL5 = inCmds[inOff + 5] as? PathCommand.LineTo ?: return null
		val vC6 = inCmds[inOff + 6] as? PathCommand.CubicTo ?: return null
		val vL7 = inCmds[inOff + 7] as? PathCommand.LineTo ?: return null
		@Suppress("UNUSED_VARIABLE") val vC8 = inCmds[inOff + 8] as? PathCommand.CubicTo ?: return null
		if (inCmds[inOff + 9] !is PathCommand.Close) return null
		val vT = vM.y; val vR = vC2.x; val vB = vC4.y; val vL = vC6.x
		return floatArrayOf(
			vL, vT, vR, vB,
			vM.x - vL, vL7.y - vT,   // tl
			vR - vL1.x, vC2.y - vT,  // tr
			vR - vC4.x, vB - vL3.y,  // br
			vL5.x - vL, vB - vC6.y,  // bl
		)
	}

	// Fills the band between two same-length, point-corresponding closed contours
	// (an outer ring and its inset) as a triangle strip. The solid band is drawn at
	// FULL width on every segment so its opacity + thickness match the renderer's
	// crisp (non-AA) straight strokes on the adjacent square segments. Only the
	// diagonal corner-arc segments get an AA fringe — the axis-aligned straight
	// edges are already crisp and must stay exactly strokeWidth, or the ring reads
	// heavier (fringe added) or lighter (fringe straddled) than its neighbours.
	private fun ringFill(
		inOuter: List<Pair<Float, Float>>,
		inInner: List<Pair<Float, Float>>,
		inSampler: Sampler,
	) {
		val vN = minOf(inOuter.size, inInner.size)
		if (vN < 2) return
		val vHalf = 0.5f * kAaFeather
		// Contours are closed (last == first), so segments 0..N-2 cover the loop.
		for (vI in 0 until vN - 1) {
			val (vOx0, vOy0) = inOuter[vI]; val (vOx1, vOy1) = inOuter[vI + 1]
			val (vIx0, vIy0) = inInner[vI]; val (vIx1, vIy1) = inInner[vI + 1]
			val vEdx = vOx1 - vOx0; val vEdy = vOy1 - vOy0
			// Straight (axis-aligned) sides: crisp full-width band, matching the
			// renderer's non-AA straight strokes on the adjacent square segments.
			if (kotlin.math.abs(vEdx) < 0.35f || kotlin.math.abs(vEdy) < 0.35f) {
				emitTri(vOx0, vOy0, vOx1, vOy1, vIx1, vIy1, inSampler)
				emitTri(vOx0, vOy0, vIx1, vIy1, vIx0, vIy0, inSampler)
				continue
			}
			// Corner arcs: STRADDLE the AA on each boundary (solid core inset by half
			// a feather, fringe fading across the contour) so the curve stays exactly
			// strokeWidth — no outward bulge that would fatten or heighten it — yet
			// antialiases.
			var vN0x = vOx0 - vIx0; var vN0y = vOy0 - vIy0
			var vN1x = vOx1 - vIx1; var vN1y = vOy1 - vIy1
			val vL0 = sqrt(vN0x * vN0x + vN0y * vN0y)
			val vL1 = sqrt(vN1x * vN1x + vN1y * vN1y)
			if (vL0 < 1e-4f || vL1 < 1e-4f) {
				emitTri(vOx0, vOy0, vOx1, vOy1, vIx1, vIy1, inSampler)
				emitTri(vOx0, vOy0, vIx1, vIy1, vIx0, vIy0, inSampler)
				continue
			}
			vN0x /= vL0; vN0y /= vL0; vN1x /= vL1; vN1y /= vL1
			val vIn0 = minOf(vHalf, vL0 * 0.5f); val vIn1 = minOf(vHalf, vL1 * 0.5f)
			val vSoO0x = vOx0 - vN0x * vIn0; val vSoO0y = vOy0 - vN0y * vIn0
			val vSoO1x = vOx1 - vN1x * vIn1; val vSoO1y = vOy1 - vN1y * vIn1
			val vSoI0x = vIx0 + vN0x * vIn0; val vSoI0y = vIy0 + vN0y * vIn0
			val vSoI1x = vIx1 + vN1x * vIn1; val vSoI1y = vIy1 + vN1y * vIn1
			emitTri(vSoO0x, vSoO0y, vSoO1x, vSoO1y, vSoI1x, vSoI1y, inSampler)
			emitTri(vSoO0x, vSoO0y, vSoI1x, vSoI1y, vSoI0x, vSoI0y, inSampler)
			emitFringeQuad(
				vSoO0x, vSoO0y, vSoO1x, vSoO1y,
				vOx1 + vN1x * vHalf, vOy1 + vN1y * vHalf, vOx0 + vN0x * vHalf, vOy0 + vN0y * vHalf,
				inSampler,
			)
			emitFringeQuad(
				vSoI0x, vSoI0y, vSoI1x, vSoI1y,
				vIx1 - vN1x * vHalf, vIy1 - vN1y * vHalf, vIx0 - vN0x * vHalf, vIy0 - vN0y * vHalf,
				inSampler,
			)
		}
	}

	internal fun ovalCore(
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
			Fill -> emitFilledArc(vCx, vCy, vRx, vRy, 0f, 360f, true, arcSegments(360f, max(vRx, vRy)), vSampler)
			is Stroke -> {
				// Approximate oval stroke as ring between r - w/2 and r + w/2
				// using the smaller axis as the radius reference.
				val vR = kotlin.math.min(vRx, vRy)
				emitStrokedArc(vCx, vCy, vR - style.width / 2f, vR + style.width / 2f, 0f, 360f, arcSegments(360f, vR + style.width / 2f), vSampler)
			}
		}
	}

	internal fun roundRectCore(
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
			// 4 corner fills — segment count adapts to the corner radius.
			val vSeg = arcSegments(90f, vR)
			emitFilledArc(vX + vR,          vY + vR,         vR, vR, 180f,  90f, false, vSeg, vSampler)
			emitFilledArc(vX + vW - vR,     vY + vR,         vR, vR, 270f,  90f, false, vSeg, vSampler)
			emitFilledArc(vX + vW - vR,     vY + vH - vR,    vR, vR,   0f,  90f, false, vSeg, vSampler)
			emitFilledArc(vX + vR,          vY + vH - vR,    vR, vR,  90f,  90f, false, vSeg, vSampler)
			// Under rotation/shear the four outer straight edges become diagonal —
			// feather them (corners already AA via the arcs). Axis-aligned: skip.
			if (fMb != 0f || fMc != 0f) {
				emitEdgeFringe(vX + vR, vY, vX + vW - vR, vY, 0f, -kAaFeather, vSampler)                       // top
				emitEdgeFringe(vX + vR, vY + vH, vX + vW - vR, vY + vH, 0f, kAaFeather, vSampler)              // bottom
				emitEdgeFringe(vX, vY + vR, vX, vY + vH - vR, -kAaFeather, 0f, vSampler)                       // left
				emitEdgeFringe(vX + vW, vY + vR, vX + vW, vY + vH - vR, kAaFeather, 0f, vSampler)              // right
			}
		} else if (style is Stroke) {
			// Stroked rounded rect: 4 straight edges + 4 quarter arcs.
			val vSw = style.width
			lineCore(brush, Offset(topLeft.x + cornerRadius, topLeft.y), Offset(topLeft.x + vW - cornerRadius, topLeft.y), vSw, StrokeCap.Butt, alpha)
			lineCore(brush, Offset(topLeft.x + vW, topLeft.y + cornerRadius), Offset(topLeft.x + vW, topLeft.y + vH - cornerRadius), vSw, StrokeCap.Butt, alpha)
			lineCore(brush, Offset(topLeft.x + vW - cornerRadius, topLeft.y + vH), Offset(topLeft.x + cornerRadius, topLeft.y + vH), vSw, StrokeCap.Butt, alpha)
			lineCore(brush, Offset(topLeft.x, topLeft.y + vH - cornerRadius), Offset(topLeft.x, topLeft.y + cornerRadius), vSw, StrokeCap.Butt, alpha)
			val vSeg = arcSegments(90f, vR + vSw / 2f)
			emitStrokedArc(vX + vR,      vY + vR,      vR - vSw / 2f, vR + vSw / 2f, 180f, 90f, vSeg, vSampler)
			emitStrokedArc(vX + vW - vR, vY + vR,      vR - vSw / 2f, vR + vSw / 2f, 270f, 90f, vSeg, vSampler)
			emitStrokedArc(vX + vW - vR, vY + vH - vR, vR - vSw / 2f, vR + vSw / 2f,   0f, 90f, vSeg, vSampler)
			emitStrokedArc(vX + vR,      vY + vH - vR, vR - vSw / 2f, vR + vSw / 2f,  90f, 90f, vSeg, vSampler)
		}
	}

	internal fun lineCore(
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
			val vCapSeg = arcSegments(360f, strokeWidth / 2f)
			emitFilledArc(vX1, vY1, strokeWidth / 2f, strokeWidth / 2f, 0f, 360f, true, vCapSeg, vSampler)
			emitFilledArc(vX2, vY2, strokeWidth / 2f, strokeWidth / 2f, 0f, 360f, true, vCapSeg, vSampler)
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
		// Path is now an interface; our concrete impl is ProjectPath in
		// nativeMain. Cast to read the PathCommand list directly — falls
		// back to an empty path if a foreign Path implementation slips in.
		val vProject = inPath as? com.compose.sdl.graphics.ProjectPath

		// Unchanged paths reuse the polylines cached on the path itself —
		// re-linearising every frame allocated the full nested list per draw.
		// Only valid when the scope origin is baked to 0 (the Sdl3Canvas flow,
		// where the affine carries all positioning); legacy nonzero-origin
		// scopes recompute as before.
		val vCanCache = vProject != null && fOriginX == 0f && fOriginY == 0f
		if (vCanCache) {
			val vCached = vProject!!.linearised
			if (vCached != null && vProject.linearisedKey == vProject.contentKey) {
				@Suppress("UNCHECKED_CAST")
				return vCached as List<List<Pair<Float, Float>>>
			}
		}

		val vSubs = mutableListOf<MutableList<Pair<Float, Float>>>()
		var vCurrent: MutableList<Pair<Float, Float>>? = null
		var vCx = 0f; var vCy = 0f
		val vCommands = vProject?.commands ?: emptyList()
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
		if (vCanCache) {
			vProject!!.linearised = vSubs
			vProject.linearisedKey = vProject.contentKey
		}
		return vSubs
	}

	private fun fanFill(inPolyline: List<Pair<Float, Float>>, inSampler: Sampler) {
		val vN = inPolyline.size
		if (vN < 3) return
		val (vAx, vAy) = inPolyline[0]
		for (vI in 1 until vN - 1) {
			val (vBx, vBy) = inPolyline[vI]
			val (vCx, vCy) = inPolyline[vI + 1]
			emitTri(vAx, vAy, vBx, vBy, vCx, vCy, inSampler)
		}
		// AA the outer boundary: feather each edge outward (normal oriented away
		// from the centroid). Correct for convex fills, which is what fan fill
		// supports; concave paths already fan-overlap, so this doesn't regress them.
		var vSumX = 0f; var vSumY = 0f
		for (vP in inPolyline) { vSumX += vP.first; vSumY += vP.second }
		val vCx = vSumX / vN; val vCy = vSumY / vN
		for (vI in 0 until vN) {
			val (x0, y0) = inPolyline[vI]
			val (x1, y1) = inPolyline[(vI + 1) % vN]
			val vDx = x1 - x0; val vDy = y1 - y0
			val vLen = sqrt(vDx * vDx + vDy * vDy)
			if (vLen < 1e-4f) continue
			var vNx = -vDy / vLen; var vNy = vDx / vLen
			val vMidToCx = (x0 + x1) * 0.5f - vCx
			val vMidToCy = (y0 + y1) * 0.5f - vCy
			if (vMidToCx * vNx + vMidToCy * vNy < 0f) { vNx = -vNx; vNy = -vNy }
			emitEdgeFringe(x0, y0, x1, y1, vNx * kAaFeather, vNy * kAaFeather, inSampler)
		}
	}

	private fun strokePolyline(inPolyline: List<Pair<Float, Float>>, inWidth: Float, inSampler: Sampler) {
		val vHalfW = inWidth / 2f
		val vSolidW = (vHalfW - kAaHalf).coerceAtLeast(0f)
		val vEdgeW = vHalfW + kAaHalf
		for (vI in 0 until inPolyline.size - 1) {
			val (vAx, vAy) = inPolyline[vI]
			val (vBx, vBy) = inPolyline[vI + 1]
			val vDx = vBx - vAx; val vDy = vBy - vAy
			val vLen = sqrt(vDx * vDx + vDy * vDy)
			if (vLen < 1e-4f) continue
			val vUx = -vDy / vLen; val vUy = vDx / vLen // unit normal
			// Solid core band, then feather each long side out to alpha 0.
			emitQuad(
				vAx + vUx * vSolidW, vAy + vUy * vSolidW, vBx + vUx * vSolidW, vBy + vUy * vSolidW,
				vBx - vUx * vSolidW, vBy - vUy * vSolidW, vAx - vUx * vSolidW, vAy - vUy * vSolidW, inSampler,
			)
			emitFringeQuad(
				vAx + vUx * vSolidW, vAy + vUy * vSolidW, vBx + vUx * vSolidW, vBy + vUy * vSolidW,
				vBx + vUx * vEdgeW, vBy + vUy * vEdgeW, vAx + vUx * vEdgeW, vAy + vUy * vEdgeW, inSampler,
			)
			emitFringeQuad(
				vAx - vUx * vSolidW, vAy - vUy * vSolidW, vBx - vUx * vSolidW, vBy - vUy * vSolidW,
				vBx - vUx * vEdgeW, vBy - vUy * vEdgeW, vAx - vUx * vEdgeW, vAy - vUy * vEdgeW, inSampler,
			)
		}
	}

	// ============
	//  Tessellation primitives

	/* Number of arc segments from the arc's PIXEL length — one chord per ~4px
	   of arc, so a 4px chip corner doesn't spend as many vertices as a 200px
	   progress ring, and big shapes stop showing facets. The ~1px AA feather
	   hides residual chord flatness; bounds keep tiny arcs ≥3 chords and
	   giant ones ≤48. */
	private fun arcSegments(inSweepDeg: Float, inRadius: Float): Int {
		val vAbs = if (inSweepDeg < 0) -inSweepDeg else inSweepDeg
		val vArcLen = 2f * PI.toFloat() * inRadius * (vAbs / 360f)
		return ((vArcLen / 4f).toInt() + 1).coerceIn(3, 48)
	}

	private fun emitFilledArc(
		inCx: Float, inCy: Float, inRx: Float, inRy: Float,
		inStartDeg: Float, inSweepDeg: Float, inUseCenter: Boolean,
		inSegments: Int, inSampler: Sampler,
	) {
		val vStartRad = inStartDeg * (PI / 180.0).toFloat()
		val vSweepRad = inSweepDeg * (PI / 180.0).toFloat()
		val vStep = vSweepRad / inSegments
		// Fill to a radius inset by half the feather, then feather the curved edge
		// out to +half at alpha 0. Tiny radii (< feather) skip the inset.
		val vRxIn = (inRx - kAaHalf).coerceAtLeast(0f)
		val vRyIn = (inRy - kAaHalf).coerceAtLeast(0f)
		val vRxOut = inRx + kAaHalf
		val vRyOut = inRy + kAaHalf
		for (i in 0 until inSegments) {
			val vA = vStartRad + i * vStep
			val vB = vStartRad + (i + 1) * vStep
			val vCa = cos(vA); val vSa = sin(vA)
			val vCb = cos(vB); val vSb = sin(vB)
			// Solid fan wedge to the inset radius.
			emitTri(inCx, inCy, inCx + vRxIn * vCa, inCy + vRyIn * vSa, inCx + vRxIn * vCb, inCy + vRyIn * vSb, inSampler)
			// AA fringe along the curved outer edge.
			emitFringeQuad(
				inCx + vRxIn * vCa, inCy + vRyIn * vSa,
				inCx + vRxIn * vCb, inCy + vRyIn * vSb,
				inCx + vRxOut * vCb, inCy + vRyOut * vSb,
				inCx + vRxOut * vCa, inCy + vRyOut * vSa,
				inSampler,
			)
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
		// Solid band inset by half the feather on both edges; feather each curved
		// edge (outer + inner) out to alpha 0. If the band is thinner than the
		// feather it drops out and the two fringes meet — a thin AA'd ring.
		val vOuterSolid = (inOuterR - kAaHalf).coerceAtLeast(0f)
		val vOuterEdge = inOuterR + kAaHalf
		val vInnerSolid = inInnerR + kAaHalf
		val vInnerEdge = (inInnerR - kAaHalf).coerceAtLeast(0f)
		val vHasBand = vOuterSolid > vInnerSolid
		for (i in 0 until inSegments) {
			val vA = vStartRad + i * vStep
			val vB = vStartRad + (i + 1) * vStep
			val vCosA = cos(vA); val vSinA = sin(vA)
			val vCosB = cos(vB); val vSinB = sin(vB)
			if (vHasBand) {
				emitQuad(
					inCx + vOuterSolid * vCosA, inCy + vOuterSolid * vSinA,
					inCx + vOuterSolid * vCosB, inCy + vOuterSolid * vSinB,
					inCx + vInnerSolid * vCosB, inCy + vInnerSolid * vSinB,
					inCx + vInnerSolid * vCosA, inCy + vInnerSolid * vSinA,
					inSampler,
				)
			}
			// Outer fringe: solid edge → outside, fading out.
			emitFringeQuad(
				inCx + vOuterSolid * vCosA, inCy + vOuterSolid * vSinA,
				inCx + vOuterSolid * vCosB, inCy + vOuterSolid * vSinB,
				inCx + vOuterEdge * vCosB, inCy + vOuterEdge * vSinB,
				inCx + vOuterEdge * vCosA, inCy + vOuterEdge * vSinA,
				inSampler,
			)
			// Inner fringe: solid edge → inside, fading out.
			emitFringeQuad(
				inCx + vInnerSolid * vCosA, inCy + vInnerSolid * vSinA,
				inCx + vInnerSolid * vCosB, inCy + vInnerSolid * vSinB,
				inCx + vInnerEdge * vCosB, inCy + vInnerEdge * vSinB,
				inCx + vInnerEdge * vCosA, inCy + vInnerEdge * vSinA,
				inSampler,
			)
		}
	}

	private fun emitRoundCap(
		inCx: Float, inCy: Float, inR: Float, inCapRadius: Float,
		inAtAngleDeg: Float, inSampler: Sampler,
	) {
		val vRad = inAtAngleDeg * (PI / 180.0).toFloat()
		val vPx = inCx + inR * cos(vRad)
		val vPy = inCy + inR * sin(vRad)
		emitFilledArc(vPx, vPy, inCapRadius, inCapRadius, 0f, 360f, true, arcSegments(360f, inCapRadius), inSampler)
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
		writeVertex(ax, ay, inSampler.sample(ax, ay))
		writeVertex(bx, by, inSampler.sample(bx, by))
		writeVertex(cx, cy, inSampler.sample(cx, cy))
	}

	/* Antialiasing fringe: a quad whose SOLID edge (sA→sB) carries the shape's
	   colour at full coverage and whose FADE edge (fB→fA) carries the same colour
	   at alpha 0, so SDL interpolates a ~1px feather along an edge. */
	private fun emitFringeQuad(
		sAx: Float, sAy: Float, sBx: Float, sBy: Float,
		fBx: Float, fBy: Float, fAx: Float, fAy: Float,
		inSampler: Sampler,
	) {
		writeVertex(sAx, sAy, inSampler.sample(sAx, sAy))
		writeVertex(sBx, sBy, inSampler.sample(sBx, sBy))
		writeVertex(fBx, fBy, inSampler.sample(fBx, fBy), 0f)
		writeVertex(sAx, sAy, inSampler.sample(sAx, sAy))
		writeVertex(fBx, fBy, inSampler.sample(fBx, fBy), 0f)
		writeVertex(fAx, fAy, inSampler.sample(fAx, fAy), 0f)
	}

	/* Feathers one straight edge (x0,y0)→(x1,y1) outward along (inNx,inNy) — a
	   pre-scaled outward normal — from full alpha at the edge to 0 just outside.
	   Used to AA the edges of rotated rects and filled paths (which are otherwise
	   crisp only when axis-aligned). */
	private fun emitEdgeFringe(
		x0: Float, y0: Float, x1: Float, y1: Float, inNx: Float, inNy: Float, inSampler: Sampler,
	) {
		emitFringeQuad(x0, y0, x1, y1, x1 + inNx, y1 + inNy, x0 + inNx, y0 + inNy, inSampler)
	}

	// Stage one vertex into fVertexData (auto-flush when full). Position goes
	// through the current affine so scale/rotate reach the GPU; the colour is
	// sampled at the pre-transform point so a gradient rides its shape.
	private fun writeVertex(inX: Float, inY: Float, inColor: ComposeColor, inAlphaScale: Float = 1f) {
		if (fBatchCount >= kBatchCapacity) flush()
		val vBase = fBatchCount * kFloatsPerVertex
		fVertexData[vBase + 0] = fMa * inX + fMc * inY + fMe
		fVertexData[vBase + 1] = fMb * inX + fMd * inY + fMf
		fVertexData[vBase + 2] = inColor.r8 / 255f
		fVertexData[vBase + 3] = inColor.g8 / 255f
		fVertexData[vBase + 4] = inColor.b8 / 255f
		fVertexData[vBase + 5] = inColor.a8 / 255f * inAlphaScale
		fVertexData[vBase + 6] = 0f
		fVertexData[vBase + 7] = 0f
		fBatchCount++
	}
}

// AA fringe width in user units. Layout runs in physical pixels (render scale 1),
// so ~1 unit ≈ 1 px; the fringe straddles the true edge (±half) so the geometric
// radius stays put and the edge hits 50% coverage exactly on it.
private const val kAaFeather: Float = 1.0f
private const val kAaHalf: Float = kAaFeather * 0.5f

// ============
//  Batch capacity — 8192 vertices = ~2730 triangles per submission. At
//  64 segments per full circle that's room for ~21 full-circle filled
//  shapes per Canvas{} before any flush. Bigger gives fewer GPU
//  submissions; smaller saves RAM. ~128 KB at 16 bytes per SDL_Vertex.
private const val kBatchCapacity: Int = 8192

// Floats per SDL_Vertex: position(x,y) + color(r,g,b,a) + tex_coord(x,y), tightly
// packed. Used to stage vertices in a Kotlin FloatArray and memcpy them across.
private const val kFloatsPerVertex: Int = 8

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
// A fun interface (not a (Float,Float)->Color function type): K/N boxes the Float
// arguments of a generic function type on every call, which — multiplied by the
// per-vertex sampling and the AA fringe — dominated frame time. Primitive-param
// interface method → no per-vertex boxing.
private fun interface Sampler { fun sample(x: Float, y: Float): ComposeColor }

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
	is androidx.compose.ui.graphics.ShaderBrush -> {
		// Vendored upstream Brush.kt adds a generic `ShaderBrush` abstract
		// base. No SDL3-side bridge for arbitrary shaders; degrade to a
		// neutral white-alpha fill so the call surface compiles. The four
		// concrete brushes above bypass this branch already.
		val vC = androidx.compose.ui.graphics.Color.White.withAlphaScaled(inAlpha)
		val vS: Sampler = { _, _ -> vC }
		vS
	}
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
	val vStops = inB.gradientStops ?: uniformStops(vColors.size)
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
	val vStops = inB.gradientStops ?: uniformStops(vColors.size)
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
	val vStops = inB.gradientStops ?: uniformStops(vColors.size)
	return { x, y ->
		// atan2 returns radians in [-π, π]. Map to [0, 1] going clockwise from
		// 3 o'clock to match Skia's sweep gradient convention.
		val vAng = kotlin.math.atan2(y - vCy, x - vCx)
		val vT = ((vAng / (2.0 * PI) + 1.0) % 1.0).toFloat()
		sampleColors(vColors, vStops, vT).withAlphaScaled(inAlpha)
	}
}

/* Uniform stop positions for a gradient with no explicit stops. Built ONCE
   per sampler (not per vertex — the old per-vertex list allocation dominated
   gradient fills). Sizes < 2 return an empty list; sampleColors early-outs
   before reading stops in those cases. */
private fun uniformStops(inCount: Int): List<Float> =
	if (inCount < 2) emptyList() else List(inCount) { it / (inCount - 1f) }

/* Sample the colour at position [0..1] along the gradient. */
private fun sampleColors(
	inColors: List<ComposeColor>,
	inStops: List<Float>,
	inT: Float,
): ComposeColor {
	if (inColors.isEmpty()) return ComposeColor.Transparent
	if (inColors.size == 1) return inColors[0]
	val vStops = inStops
	if (vStops.isEmpty()) return inColors[0]
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
