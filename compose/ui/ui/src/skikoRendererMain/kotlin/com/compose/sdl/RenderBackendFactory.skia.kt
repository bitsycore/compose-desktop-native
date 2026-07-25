package com.compose.sdl

import com.compose.sdl.renderer.skia.SkiaRenderBackend

// ==================
// MARK: createRenderBackend (Skia renderer module entry)
// ==================

/** Public factory the :window module calls (its per-target makeRenderBackend
   actual delegates here when Skia is the selected renderer). Mirrors the
   SDL3 module's createRenderBackend / rendererPreferredGpuMode in the same
   package; exactly one renderer module is included per target. */
actual fun createRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend? {
	val vResolved = if (inGpu is GpuMode.Auto) rendererPreferredGpuMode() else inGpu
	return try {
		SkiaRenderBackend(inSdl, vResolved)
	} catch (t: Throwable) {
		println("makeRenderBackend failed: ${t.message}")
		null
	}
}
