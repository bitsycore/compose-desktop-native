package com.compose.sdl.renderer.sdl

// ==================
// MARK: SdlDisplayList — captured tessellated geometry for retained replay
// ==================

/*
 Phase 4 foundation (RENDERER_REFACTOR.md §3/§13). A display list is the tessellated
 output of a layer's drawing, captured ONCE in layer-local space and replayed under
 the layer transform — instead of re-tessellating every frame (DeferredRenderNode) or
 round-tripping through an offscreen texture (SdlRenderNode, which had fixed-resolution
 softness + timing-dependent nondeterminism). Replaying re-transforms cached vertices:
 crisp under any transform, bit-exact (no texture round-trip), no per-frame GPU/texture
 state → no timing dependence.

 A [GeometrySink] receives the untextured triangle batches Sdl3DrawScope produces at
 flush(); a canvas recorded with an IDENTITY base CTM yields layer-local vertices.
 Text / image / clip commands come in later increments — until then a recording that
 hits one marks itself unsupported and the node falls back to a crisp block-replay
 (so no un-captured op ever leaks to the GPU).

 NOTE: geometry-only so far. This is the recording seam; the node + replay + the
 canvas capture-mode build on top of it.
*/

/* One captured untextured triangle batch, in layer-local coords (8 floats/vertex:
   pos.xy, color.rgba, tex.xy — same packing as Sdl3DrawScope.fVertexData). */
internal class GeometryBatch(
	val vertexData: FloatArray,
	val vertexCount: Int,
)

/* Receives geometry batches during a recording. Sdl3DrawScope.flush() routes to this
   instead of SDL_RenderGeometry when a recording is active. */
internal interface GeometrySink {
	fun captureGeometry(vertexData: FloatArray, vertexCount: Int)
}

/* The captured commands of one layer. Geometry now; text/image/clip/child later. */
internal class SdlDisplayList : GeometrySink {
	val batches = ArrayList<GeometryBatch>()

	// Set true when a recording hits an op it can't capture yet (text/image/clip).
	// The node then discards this list and replays the block instead.
	var unsupported = false

	override fun captureGeometry(vertexData: FloatArray, vertexCount: Int) {
		if (vertexCount <= 0) return
		batches.add(GeometryBatch(vertexData.copyOf(vertexCount * kFloatsPerVertex), vertexCount))
	}

	fun clear() {
		batches.clear()
		unsupported = false
	}
}
