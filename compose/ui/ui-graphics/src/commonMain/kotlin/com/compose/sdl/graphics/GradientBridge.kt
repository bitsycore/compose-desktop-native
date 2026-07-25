package com.compose.sdl.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.TileMode

// ==================
// MARK: Gradient renderer bridge
// ==================

// Public extension properties that re-expose the `internal`-visibility
// gradient fields to the renderer modules. Upstream Compose marks
// LinearGradient / RadialGradient / SweepGradient field accessors
// `internal`, which keeps `androidx.compose.ui.graphics.LinearGradient.colors`
// etc. out of the public ABI. Sealed-class scope forces the concrete
// gradient classes to live in `androidx.compose.ui.graphics`, but our
// renderer-skia / renderer-sdl3 modules can't read the resulting `internal`
// fields directly. The extensions below sit in :core (same module as the
// internal fields, so the bodies CAN read them) and are themselves public,
// so :renderer-skia / :renderer-sdl3 import them and reach gradient state
// through them.

// ============
//  LinearGradient

/** Color stops of the gradient. Bridge accessor for renderer modules. */
val LinearGradient.gradientColors: List<Color> get() = colors

/** Optional positional stops in [0..1]; null spreads evenly. */
val LinearGradient.gradientStops: List<Float>? get() = stops

/** Start anchor of the linear gradient in the shape's local space. */
val LinearGradient.gradientStart: Offset get() = start

/** End anchor of the linear gradient in the shape's local space. */
val LinearGradient.gradientEnd: Offset get() = end

/** Tile mode for samples outside [[gradientStart], [gradientEnd]]. */
val LinearGradient.gradientTileMode: TileMode get() = tileMode

// ============
//  RadialGradient

/** Color stops of the gradient. Bridge accessor for renderer modules. */
val RadialGradient.gradientColors: List<Color> get() = colors

/** Optional positional stops in [0..1]; null spreads evenly. */
val RadialGradient.gradientStops: List<Float>? get() = stops

/** Centre point of the radial gradient in the shape's local space. */
val RadialGradient.gradientCenter: Offset get() = center

/** Outer radius of the radial gradient. */
val RadialGradient.gradientRadius: Float get() = radius

/** Tile mode for samples outside [gradientRadius]. */
val RadialGradient.gradientTileMode: TileMode get() = tileMode

// ============
//  SweepGradient

/** Color stops of the gradient. Bridge accessor for renderer modules. */
val SweepGradient.gradientColors: List<Color> get() = colors

/** Optional positional stops in [0..1]; null spreads evenly. */
val SweepGradient.gradientStops: List<Float>? get() = stops

/** Centre point the sweep rotates around. */
val SweepGradient.gradientCenter: Offset get() = center
