package com.compose.sdl

// ==================
// MARK: GpuMode
// ==================

/** Which GPU path the Skia renderer uses.
 *
 *  ┌─ Auto      — pick the best for this platform (Metal on macOS, OpenGL on
 *  │              Linux, OpenGL on Windows). If the GPU context/bridge can't be
 *  │              created, ComposeWindow falls back to Software.
 *  ├─ Software  — Skia CPU raster: paints a host pixel buffer that SDL_Renderer
 *  │              uploads as a texture each frame. No GPU context needed.
 *  └─ Skia.*    — Skia GPU bridges
 *     ├─ OpenGL — Skia GL backend on an SDL OpenGL (WGL/GLX) context
 *     └─ Metal  — Skia Metal on a CAMetalLayer via SDL_Metal_CreateView (macOS)
 *
 *  AUTO resolves via rendererPreferredGpuMode() per target, at composeWindow entry. */
sealed class GpuMode {
    /** Let the platform pick: Skia.Metal on macOS, Skia.OpenGL on Linux/Windows. */
    object Auto : GpuMode() { override fun toString() = "Auto" }

    /** Skia CPU raster — a host pixel buffer uploaded via SDL_Renderer each frame. */
    object Software : GpuMode() { override fun toString() = "Software" }

    /** Skia GPU bridges. */
    sealed class Skia : GpuMode() {
        object OpenGL : Skia() { override fun toString() = "Skia.OpenGL" }
        object Metal  : Skia() { override fun toString() = "Skia.Metal" }
    }
}
