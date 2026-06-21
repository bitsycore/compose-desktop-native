package androidx.compose.ui.graphics.drawscope

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor

// ==================
// MARK: DrawScope
// ==================

/* Receiver scope handed to Canvas { ... } and Modifier.drawBehind { ... }
   blocks. Mirrors a subset of upstream Compose's DrawScope: the shape
   primitives every widget actually reaches for, plus Brush-based variants
   so gradients work everywhere flat colours do. Coordinates are in
   logical points relative to the node's top-left; size reports the node's
   bounds so callers can centre / scale to it.

   Implementations live in the renderer modules — SkiaDrawScope wraps an
   org.jetbrains.skia.Canvas; Sdl3DrawScope tessellates each primitive into
   triangles and submits via SDL_RenderGeometry. Brush is converted to the
   backend-native equivalent (Skia Shader / SDL per-vertex colour
   gradient). */
interface DrawScope {

	/* Logical-point bounds of the node this scope is drawing into. */
	val size: Size

	/* Centre point of [size]. */
	val center: Offset get() = Offset(size.width / 2f, size.height / 2f)

	// ============
	//  Brush-flavoured primitives — gradients work here, flat colour is
	//  driven through SolidColor.

	fun drawRect(
		brush: Brush,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	)

	fun drawCircle(
		brush: Brush,
		radius: Float = size.minDimension / 2f,
		center: Offset = this.center,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	)

	fun drawArc(
		brush: Brush,
		startAngle: Float,
		sweepAngle: Float,
		useCenter: Boolean = false,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	)

	fun drawLine(
		brush: Brush,
		start: Offset,
		end: Offset,
		strokeWidth: Float = 1f,
		cap: StrokeCap = StrokeCap.Butt,
		alpha: Float = 1f,
	)

	/* Fill or stroke an arbitrary Path. Sub-paths separated by moveTo
	   form discontiguous figures; the renderer fills each one. */
	fun drawPath(
		path: Path,
		brush: Brush,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	)

	/* Fill or stroke an ellipse inscribed in the given rect. */
	fun drawOval(
		brush: Brush,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	)

	/* Fill or stroke a rectangle with rounded corners. cornerRadius is
	   uniform; pass 0 for a sharp-cornered rect. */
	fun drawRoundRect(
		brush: Brush,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		cornerRadius: Float,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	)

	// ============
	//  Color overloads — convenience wrappers that boxing the colour into
	//  a SolidColor brush. Cheap and keep the call-site short.

	fun drawRect(
		color: Color,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	) = drawRect(SolidColor(color), topLeft, size, alpha, style)

	fun drawCircle(
		color: Color,
		radius: Float = size.minDimension / 2f,
		center: Offset = this.center,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	) = drawCircle(SolidColor(color), radius, center, alpha, style)

	fun drawArc(
		color: Color,
		startAngle: Float,
		sweepAngle: Float,
		useCenter: Boolean = false,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	) = drawArc(SolidColor(color), startAngle, sweepAngle, useCenter, topLeft, size, alpha, style)

	fun drawLine(
		color: Color,
		start: Offset,
		end: Offset,
		strokeWidth: Float = 1f,
		cap: StrokeCap = StrokeCap.Butt,
		alpha: Float = 1f,
	) = drawLine(SolidColor(color), start, end, strokeWidth, cap, alpha)

	fun drawPath(
		path: Path,
		color: Color,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	) = drawPath(path, SolidColor(color), alpha, style)

	fun drawOval(
		color: Color,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	) = drawOval(SolidColor(color), topLeft, size, alpha, style)

	fun drawRoundRect(
		color: Color,
		topLeft: Offset = Offset.Zero,
		size: Size = this.size,
		cornerRadius: Float,
		alpha: Float = 1f,
		style: DrawStyle = Fill,
	) = drawRoundRect(SolidColor(color), topLeft, size, cornerRadius, alpha, style)
}
