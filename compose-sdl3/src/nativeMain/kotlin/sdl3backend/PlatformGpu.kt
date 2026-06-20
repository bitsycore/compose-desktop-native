package sdl3backend

// ==================
// MARK: Platform GPU defaults
// ==================

/* The default GPU backend for this Kotlin/Native target — what GpuMode.AUTO
   resolves to. macOS picks METAL, Linux picks OPENGL, mingwX64 picks NONE
   (it has no Skia and thus no GPU acceleration). */
internal expect fun preferredGpuMode(): GpuMode
