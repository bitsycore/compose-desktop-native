package sdl3backend

// ==================
// MARK: Platform GPU defaults
// ==================

/* The default GPU backend for this Kotlin/Native target. macOS picks METAL,
   Linux picks OPENGL. Falls back to OPENGL if a platform has no native
   answer (so opting into useGpu always picks SOMETHING). */
internal expect fun preferredGpuMode(): GpuMode

/* Constructs the platform-specific Metal bridge, or null if Metal isn't
   supported on this target. Linux returns null. */
internal expect fun makeMetalBridge(backend: SDL3Backend): SkiaBridge?
