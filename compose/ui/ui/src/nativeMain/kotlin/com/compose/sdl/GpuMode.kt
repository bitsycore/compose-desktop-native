package com.compose.sdl

// ==================
// MARK: GpuMode
// ==================

/* Which renderer backend (Skia or SDL3) and which GPU driver to use.
   Sealed hierarchy so callers can pick a generic mode (Auto, None) or
   refine all the way down to a specific driver inside SDL3 / Skia.

   ┌─ Auto       — pick the best available for this platform/build
   ├─ None       — Skia CPU raster (no GPU); host buffer uploaded via SDL_Texture
   ├─ Skia.*     — Skia GPU bridges (macOS / Linux only; needs Skiko klib)
   │  ├─ OpenGL  — Skia + SDL3 OpenGL context
   │  └─ Metal   — Skia + CAMetalLayer via SDL_Metal_CreateView (macOS only)
   └─ Sdl3.*     — pure SDL3 renderer + SDL3_ttf
      ├─ Auto    — let SDL pick the best driver for this OS
      ├─ Software, OpenGL, Metal, Vulkan, D3D11, D3D12 — force a driver

   Each native target only knows about a subset:
   - macOS / Linux default build: Auto / None / Skia.*; Sdl3.* needs the
     -Prenderer=sdl3 build flag, otherwise composeWindow errors out.
   - mingwX64: Auto / None / Sdl3.*; Skia.* always errors (no Skiko klib).

   AUTO resolves via preferredGpuMode() per-target. The factory in
   each RenderBackend implementation refuses unknown-for-platform modes
   with a clear error so callers get a real failure instead of a silent
   fallback. */
sealed class GpuMode {
    /* Let the platform pick: Skia.Metal on macOS, Skia.OpenGL on Linux,
       Sdl3.Auto on mingwX64. Resolved at composeWindow entry. */
    object Auto : GpuMode() { override fun toString() = "Auto" }

    /* Skia CPU raster — paints into a host pixel buffer that
       SDL_Renderer uploads as a texture each frame. */
    object Software : GpuMode() { override fun toString() = "Software" }

    /* Skia GPU bridges. Available on macOS / Linux only. */
    sealed class Skia : GpuMode() {
        object OpenGL : Skia() { override fun toString() = "Skia.OpenGL" }
        object Metal  : Skia() { override fun toString() = "Skia.Metal" }
    }

    /* Pure-SDL3 renderer with an optional driver pin. AUTO lets SDL
       choose; the rest map to SDL_HINT_RENDER_DRIVER strings. */
    sealed class Sdl3 : GpuMode() {
        /* SDL_HINT_RENDER_DRIVER value, or null for SDL's default pick. */
        abstract val driverHint: String?

        object Auto     : Sdl3() { override val driverHint: String? = null;            override fun toString() = "Sdl3.Auto" }
        object Software : Sdl3() { override val driverHint: String  = "software";      override fun toString() = "Sdl3.Software" }
        object OpenGL   : Sdl3() { override val driverHint: String  = "opengl";        override fun toString() = "Sdl3.OpenGL" }
        object Metal    : Sdl3() { override val driverHint: String  = "metal";         override fun toString() = "Sdl3.Metal" }
        object Vulkan   : Sdl3() { override val driverHint: String  = "vulkan";        override fun toString() = "Sdl3.Vulkan" }
        object D3D11    : Sdl3() { override val driverHint: String  = "direct3d11";    override fun toString() = "Sdl3.D3D11" }
        object D3D12    : Sdl3() { override val driverHint: String  = "direct3d12";    override fun toString() = "Sdl3.D3D12" }
    }
}
