package com.compose.desktop.native

import com.compose.desktop.native.renderer.sdl.Sdl3RenderBackend

// ==================
// MARK: createRenderBackend (SDL3 renderer module entry)
// ==================

/* Public factory the compose-desktop-native module calls (its per-target
   makeRenderBackend actual delegates here when this module is the selected
   renderer). Both renderer modules expose createRenderBackend /
   rendererPreferredGpuMode with identical signatures in this package, and the
   build includes exactly one of them per target. Rejects Skia.* since this
   module has no Skiko. */
fun createRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend? {
	val vResolved = if (inGpu is GpuMode.Auto) rendererPreferredGpuMode() else inGpu
	if (vResolved is GpuMode.Skia) {
		error("$vResolved isn't available in this build — Skiko isn't linked. " +
			"Rerun without -Prenderer=sdl3 (on macOS / Linux) to use Skia, " +
			"or pick a GpuMode.Sdl3.* / GpuMode.None.")
	}
	return try {
		Sdl3RenderBackend(inSdl)
	} catch (t: Throwable) {
		println("makeRenderBackend (sdl3) failed: ${t.message}")
		null
	}
}
