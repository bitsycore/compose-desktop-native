package androidx.compose.ui.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ==================
// MARK: Offset
// ==================

// Pixel-space 2D point used by DrawScope primitives. Components are in
// logical points (the same coordinate space as LayoutNode geometry).
// Official Compose models Offset as a value class over a packed Long; this is
// a float-pair stand-in that keeps the full public surface (see CLAUDE.md
// "Compose API Fidelity").
data class Offset(val x: Float, val y: Float) {
	operator fun plus(other: Offset) = Offset(x + other.x, y + other.y)
	operator fun minus(other: Offset) = Offset(x - other.x, y - other.y)
	operator fun times(operand: Float) = Offset(x * operand, y * operand)
	operator fun div(operand: Float) = Offset(x / operand, y / operand)
	operator fun rem(operand: Float) = Offset(x % operand, y % operand)
	operator fun unaryMinus() = Offset(-x, -y)

	fun getDistance() = sqrt(x * x + y * y)
	fun getDistanceSquared() = x * x + y * y

	// True unless either component is NaN (the Unspecified marker). Infinities
	// are valid — matches official Offset.isValid().
	fun isValid(): Boolean = !x.isNaN() && !y.isNaN()

	companion object {
		val Zero = Offset(0f, 0f)
		val Infinite = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
		val Unspecified = Offset(Float.NaN, Float.NaN)
	}
}

val Offset.isSpecified: Boolean get() = !x.isNaN() && !y.isNaN()
val Offset.isUnspecified: Boolean get() = x.isNaN() || y.isNaN()
val Offset.isFinite: Boolean get() = x.isFinite() && y.isFinite()

inline fun Offset.takeOrElse(block: () -> Offset): Offset = if (isSpecified) this else block()

fun lerp(start: Offset, stop: Offset, fraction: Float): Offset =
	Offset(
		start.x + (stop.x - start.x) * fraction,
		start.y + (stop.y - start.y) * fraction,
	)

// ==================
// MARK: Size
// ==================

// Pixel-space 2D extent — width × height in logical points. Official Compose
// models Size as a value class over a packed Long; float-pair stand-in here.
data class Size(val width: Float, val height: Float) {
	val minDimension: Float get() = min(abs(width), abs(height))
	val maxDimension: Float get() = max(abs(width), abs(height))

	operator fun times(operand: Float) = Size(width * operand, height * operand)
	operator fun div(operand: Float) = Size(width / operand, height / operand)

	fun isEmpty() = width <= 0f || height <= 0f

	companion object {
		val Zero = Size(0f, 0f)
		val Unspecified = Size(Float.NaN, Float.NaN)
	}
}

val Size.isSpecified: Boolean get() = !width.isNaN() && !height.isNaN()
val Size.isUnspecified: Boolean get() = width.isNaN() || height.isNaN()
val Size.center: Offset get() = Offset(width / 2f, height / 2f)

inline fun Size.takeOrElse(block: () -> Size): Size = if (isSpecified) this else block()

fun lerp(start: Size, stop: Size, fraction: Float): Size =
	Size(
		start.width + (stop.width - start.width) * fraction,
		start.height + (stop.height - start.height) * fraction,
	)

operator fun Int.times(size: Size) = size * this.toFloat()
operator fun Float.times(size: Size) = size * this
operator fun Double.times(size: Size) = size * this.toFloat()
