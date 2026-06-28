package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset

// ==================
// MARK: Brush
// ==================

/* Mirrors upstream Compose's Brush sealed hierarchy: a paint source that
   shape primitives use instead of (or alongside) a flat Color. The four
   subtypes cover the common cases — SolidColor wraps a single colour
   (used when a caller passes a Color directly), and the three gradients
   sample positions along the shape's bounds.

   Stop semantics match upstream: each colour is anchored at a fraction
   of the gradient's primary axis (0..1). If no stops are supplied the
   colours are spread evenly. */
sealed class Brush {
	companion object {

		/* Linear gradient between [start] and [end] in the shape's local
		   coordinate space. Companion member (not extension) to match
		   upstream's ABI placement. */
		fun linearGradient(
			colors: List<Color>,
			start: Offset = Offset.Zero,
			end: Offset = Offset(Float.POSITIVE_INFINITY, 0f),
			tileMode: TileMode = TileMode.Clamp,
		): Brush = LinearGradient(colors, stops = null, start = start, end = end, tileMode = tileMode)

		fun verticalGradient(
			colors: List<Color>,
			startY: Float = 0f,
			endY: Float = Float.POSITIVE_INFINITY,
			tileMode: TileMode = TileMode.Clamp,
		): Brush = LinearGradient(
			colors = colors,
			stops = null,
			start = Offset(0f, startY),
			end = Offset(0f, endY),
			tileMode = tileMode,
		)

		fun horizontalGradient(
			colors: List<Color>,
			startX: Float = 0f,
			endX: Float = Float.POSITIVE_INFINITY,
			tileMode: TileMode = TileMode.Clamp,
		): Brush = LinearGradient(
			colors = colors,
			stops = null,
			start = Offset(startX, 0f),
			end = Offset(endX, 0f),
			tileMode = tileMode,
		)

		fun radialGradient(
			colors: List<Color>,
			center: Offset = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			radius: Float = Float.POSITIVE_INFINITY,
			tileMode: TileMode = TileMode.Clamp,
		): Brush = RadialGradient(colors, stops = null, center = center, radius = radius, tileMode = tileMode)

		fun sweepGradient(
			colors: List<Color>,
			center: Offset = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
		): Brush = SweepGradient(colors, stops = null, center = center)
	}
}

/* The Brush for a single flat colour — created implicitly when a draw
   call takes a Color. Field name `value` (not `color`) matches upstream
   `class SolidColor(val value: Color) : Brush()`. Plain class with manual
   equals/hashCode — drops auto-generated component1/copy that upstream
   doesn't expose. */
class SolidColor(val value: Color) : Brush() {
	override fun equals(other: Any?): Boolean = other is SolidColor && other.value == value
	override fun hashCode(): Int = value.hashCode()
	override fun toString(): String = "SolidColor(value=$value)"
}

/* Linear gradient. start and end are anchor points in the *shape's* local
   coordinate space (DrawScope's coordinates). When start.x = -1 or end.x
   = Float.POSITIVE_INFINITY etc. the renderer interprets it as "across
   the full bounds" — but most callers will pass concrete points.

   Fields are `internal` matching upstream's visibility — renderers reach
   them through the `gradientColors` / `gradientStops` / `gradientStart` /
   `gradientEnd` / `gradientTileMode` extension properties in
   com.compose.desktop.native.graphics.GradientBridge. */
class LinearGradient internal constructor(
	internal val colors: List<Color>,
	internal val stops: List<Float>?,
	internal val start: Offset,
	internal val end: Offset,
	internal val tileMode: TileMode = TileMode.Clamp,
) : Brush() {
	override fun equals(other: Any?): Boolean = other is LinearGradient &&
		other.colors == colors && other.stops == stops &&
		other.start == start && other.end == end && other.tileMode == tileMode
	override fun hashCode(): Int {
		var h = colors.hashCode()
		h = 31 * h + (stops?.hashCode() ?: 0)
		h = 31 * h + start.hashCode()
		h = 31 * h + end.hashCode()
		h = 31 * h + tileMode.hashCode()
		return h
	}
}

/* Radial gradient centred at [center] with the given radius. Colours
   sample from centre (stop=0) to edge (stop=1). Fields `internal`; see
   GradientBridge for renderer accessors. */
class RadialGradient internal constructor(
	internal val colors: List<Color>,
	internal val stops: List<Float>?,
	internal val center: Offset,
	internal val radius: Float,
	internal val tileMode: TileMode = TileMode.Clamp,
) : Brush() {
	override fun equals(other: Any?): Boolean = other is RadialGradient &&
		other.colors == colors && other.stops == stops &&
		other.center == center && other.radius == radius && other.tileMode == tileMode
	override fun hashCode(): Int {
		var h = colors.hashCode()
		h = 31 * h + (stops?.hashCode() ?: 0)
		h = 31 * h + center.hashCode()
		h = 31 * h + radius.hashCode()
		h = 31 * h + tileMode.hashCode()
		return h
	}
}

/* Sweep gradient: colours rotate around [center], 0 degrees pointing
   right (3 o'clock), clockwise. Useful for hue wheels and material 3
   spinner tracks. Fields `internal`; see GradientBridge for renderer
   accessors. */
class SweepGradient internal constructor(
	internal val colors: List<Color>,
	internal val stops: List<Float>?,
	internal val center: Offset,
) : Brush() {
	override fun equals(other: Any?): Boolean = other is SweepGradient &&
		other.colors == colors && other.stops == stops && other.center == center
	override fun hashCode(): Int {
		var h = colors.hashCode()
		h = 31 * h + (stops?.hashCode() ?: 0)
		h = 31 * h + center.hashCode()
		return h
	}
}

// TileMode lives in its own vendored file (androidx.compose.ui.graphics.TileMode).
