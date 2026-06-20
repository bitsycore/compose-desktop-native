package sdl3backend

// ==================
// MARK: GpuMode
// ==================

/* Which Skia backend the SDL3Backend should set up its window for.

   NONE    — SDL_Renderer + CPU raster Skia surface (SkiaSurfaceBridge)
   OPENGL  — SDL_WINDOW_OPENGL + SDL_GL_CreateContext (SkiaGLBridge),
             default on Linux / fallback elsewhere
   METAL   — SDL_WINDOW_METAL + SDL_Metal_CreateView (SkiaMetalBridge),
             preferred on macOS

   preferredGpuMode() picks per-target via expect/actual. */
enum class GpuMode {
    /* Skia CPU raster — paints into a pixel buffer that SDL_Renderer
       uploads as a texture each frame. */
    NONE,
    /* Skia GPU on an SDL3 OpenGL context. Linux default. */
    OPENGL,
    /* Skia GPU on a CAMetalLayer via SDL_Metal_CreateView. macOS only. */
    METAL,
    /* No Skia at all — render with SDL3 primitives + SDL3_ttf. Limited
       (no antialiased shape edges, no proper rounded-corner fills) but
       the only mode that works on mingwX64 since Skiko ships no klib
       there. Available on every target as a comparison renderer. */
    SDL3,
    /* Let the platform pick: METAL on macOS, OPENGL on Linux, SDL3 on
       mingwX64. */
    AUTO,
}
