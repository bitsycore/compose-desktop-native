package com.compose.sdl.graphics

// ==================
// MARK: PathCommand
// ==================

/* Project-only render-bridge sealed type — the building blocks of our reduced
   Path implementation. Upstream's Path is an `expect class` backed by Skia /
   Android Canvas internals; our `androidx.compose.ui.graphics.Path` instead
   carries a `commands: List<PathCommand>` that the SDL3 / Skia renderers walk
   directly. No official equivalent, so lives in com.compose.sdl
   per FIDELITY.md's relocate rule. */
sealed interface PathCommand {
	class MoveTo(val x: Float, val y: Float) : PathCommand
	class LineTo(val x: Float, val y: Float) : PathCommand
	class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCommand
	class CubicTo(
		val c1x: Float, val c1y: Float,
		val c2x: Float, val c2y: Float,
		val x:   Float, val y:   Float,
	) : PathCommand
	data object Close : PathCommand
}
