package com.compose.desktop.native.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.TileMode

// ==================
// MARK: Gradient renderer bridge
// ==================

/* Public extension properties that re-expose the `internal`-visibility
   gradient fields to the renderer modules. Upstream Compose marks
   LinearGradient/RadialGradient/SweepGradient field accessors `internal`,
   which keeps `androidx.compose.ui.graphics.LinearGradient.colors` etc. out
   of the public ABI — fidelity rule. Sealed-class scope forces the concrete
   gradient classes to live in `androidx.compose.ui.graphics`, but our
   renderer-skia / renderer-sdl3 modules can't read the resulting `internal`
   fields directly.

   These extensions sit in com.compose.desktop.native.graphics so the
   public surface of `androidx.compose.ui.graphics` matches upstream's
   field-less view, while still giving the renderer a typed accessor for
   each piece of gradient state. Defined in :core (same module as the
   internal fields), so the extension bodies CAN read them; the extension
   properties themselves are public, so :renderer-skia / :renderer-sdl3
   can import + use them. */

// ============
//  LinearGradient

val LinearGradient.gradientColors: List<Color> get() = colors
val LinearGradient.gradientStops: List<Float>? get() = stops
val LinearGradient.gradientStart: Offset get() = start
val LinearGradient.gradientEnd: Offset get() = end
val LinearGradient.gradientTileMode: TileMode get() = tileMode

// ============
//  RadialGradient

val RadialGradient.gradientColors: List<Color> get() = colors
val RadialGradient.gradientStops: List<Float>? get() = stops
val RadialGradient.gradientCenter: Offset get() = center
val RadialGradient.gradientRadius: Float get() = radius
val RadialGradient.gradientTileMode: TileMode get() = tileMode

// ============
//  SweepGradient

val SweepGradient.gradientColors: List<Color> get() = colors
val SweepGradient.gradientStops: List<Float>? get() = stops
val SweepGradient.gradientCenter: Offset get() = center
