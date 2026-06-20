package sdl3backend

// ==================
// MARK: Linux GPU defaults
// ==================

/* Linux gets OpenGL — Skia GL via SDL3's GL context. Metal isn't a thing
   here. */
internal actual fun preferredGpuMode(): GpuMode = GpuMode.Skia.OpenGL

internal actual fun makeMetalBridge(backend: SDL3Backend): SkiaBridge? = null
