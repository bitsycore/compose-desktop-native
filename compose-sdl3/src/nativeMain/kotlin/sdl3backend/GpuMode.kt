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
    /* CPU raster — Skia paints into a pixel buffer that SDL_Renderer
       uploads as a texture each frame. Works everywhere; slowest. */
    NONE,
    /* Skia GPU on an SDL3 OpenGL context. Linux default. */
    OPENGL,
    /* Skia GPU on a CAMetalLayer via SDL_Metal_CreateView. macOS only. */
    METAL,
    /* Let the platform pick (METAL on macOS, OPENGL on Linux). */
    AUTO,
}
