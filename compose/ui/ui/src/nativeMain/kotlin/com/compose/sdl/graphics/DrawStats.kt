package com.compose.sdl.graphics

// ==================
// MARK: DrawStats — per-frame renderer work counters (diagnostic)
// ==================

/**
 * Cheap per-frame counters the SDL draw path bumps and the frame profiler
 * reads, to answer "what inside `draw` costs the time?" — geometry submissions
 * (SDL_RenderGeometry calls), total vertices tessellated, rounded-clip mask
 * realizations (offscreen render-target passes — the expensive ones), text
 * blits and image blits. Reset each frame by the profiler after reading.
 *
 * Single-threaded (all draw is on the main thread), so plain vars are fine.
 * Zero overhead when the profiler isn't reading — just integer increments.
 */
object DrawStats {
	var geometrySubmits: Int = 0
	var vertices: Int = 0
	var maskRealizations: Int = 0
	var textDraws: Int = 0
	var imageBlits: Int = 0

	fun reset() {
		geometrySubmits = 0
		vertices = 0
		maskRealizations = 0
		textDraws = 0
		imageBlits = 0
	}

	fun summary(): String =
		"geo=$geometrySubmits verts=$vertices masks=$maskRealizations text=$textDraws img=$imageBlits"
}
