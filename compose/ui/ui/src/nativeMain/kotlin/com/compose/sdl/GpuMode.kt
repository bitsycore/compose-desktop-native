package com.compose.sdl

// ==================
// MARK: GpuMode
// ==================

/**
 Which drawing path the renderer uses.

 Every mode is Skia - the port renders through Skia on all targets. The axis
 here is only how Skia's output reaches the screen:

   Auto       resolve per platform at window creation (Metal on macOS, OpenGL
              on Linux and Windows). If the GPU context can't be created, the
              window falls back to CpuRaster rather than failing to open.
   Metal      Skia's Metal backend on a CAMetalLayer (SDL_Metal_CreateView), macOS.
   OpenGL     Skia's GL backend on an SDL OpenGL (WGL/GLX) context.
   CpuRaster  Skia paints a host pixel buffer that SDL_Renderer uploads as a
              texture each frame. No GPU context needed.

 This used to read `Software` vs a nested `Skia.*` group, which dated from when
 the alternative to Skia was SDL's own renderer. With SDL reduced to windowing
 that split was actively misleading - "Software" was always Skia too.
 */
sealed class GpuMode {
	/** Let the platform pick: [Metal] on macOS, [OpenGL] on Linux and Windows. */
	object Auto : GpuMode() { override fun toString() = "Auto" }

	/** Skia Metal on a CAMetalLayer via SDL_Metal_CreateView (macOS only). */
	object Metal : GpuMode() { override fun toString() = "Metal" }

	/** Skia GL on an SDL OpenGL context (WGL on Windows, GLX on Linux). */
	object OpenGL : GpuMode() { override fun toString() = "OpenGL" }

	/** Skia CPU raster - a host pixel buffer uploaded via SDL_Renderer each frame. */
	object CpuRaster : GpuMode() { override fun toString() = "CpuRaster" }

	/** True for the GPU-backed modes. [Auto] is false because it is unresolved:
	   ask after the window has resolved it, via ComposeNativeWindow.gpuMode. */
	val isGpuAccelerated: Boolean get() = this is Metal || this is OpenGL
}
