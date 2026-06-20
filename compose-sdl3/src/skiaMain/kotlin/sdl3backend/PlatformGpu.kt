package sdl3backend

// ==================
// MARK: Metal bridge factory (skia-only)
// ==================

/* Constructs the platform-specific Metal bridge, or null if Metal isn't
   supported on this target. Linux returns null; macOS returns the real
   thing. Only used by SkiaRenderBackend. */
internal expect fun makeMetalBridge(backend: SDL3Backend): SkiaBridge?
