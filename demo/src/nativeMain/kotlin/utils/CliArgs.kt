package utils

import com.compose.sdl.GpuMode

// ==================
// MARK: CLI args
// ==================

/* Parsed view of the demo's command line.

   --gpu=auto | none | skia.metal | skia.opengl |
         sdl3 | sdl3.auto | sdl3.software |
         sdl3.opengl | sdl3.metal | sdl3.vulkan |
         sdl3.d3d11 | sdl3.d3d12                 (default: auto)
   --screen=Buttons | TextField | ...            (default: full app w/ sidebar)
   --screenshot=path.bmp                         capture at quiescence and quit
   --width=W  --height=H                         (default 1000 / 700)
   --frames=N                                    quiescence cap: capture at frame N even if
                                                 the screen never settles (default 300)

   Names match the Screen registry entries (case-insensitive). */
internal data class CliArgs(
    val gpu: GpuMode = GpuMode.Auto,
    val screen: String? = null,
    val screenshot: String? = null,
    val width: Int = 1000,
    val height: Int = 700,
    val maxFrames: Int = 300,
)

/* Translates the --gpu= string to the right GpuMode sealed instance.
   Accepts both dotted (`skia.metal`) and dashed (`skia-metal`) forms,
   plus the bare driver names (`metal`, `opengl`, …) as Skia aliases
   for backwards compatibility. */
private fun parseGpu(inValue: String): GpuMode = when (inValue.lowercase().replace('-', '.')) {
    "auto"           -> GpuMode.Auto
    "none", "cpu", "software"    -> GpuMode.Software
    "metal", "skia.metal"   -> GpuMode.Skia.Metal
    "opengl", "gl", "skia.opengl" -> GpuMode.Skia.OpenGL
    "sdl3", "sdl3.auto"     -> GpuMode.Sdl3.Auto
    "sdl3.software", "sdl3.sw" -> GpuMode.Sdl3.Software
    "sdl3.opengl"    -> GpuMode.Sdl3.OpenGL
    "sdl3.metal"     -> GpuMode.Sdl3.Metal
    "sdl3.vulkan"    -> GpuMode.Sdl3.Vulkan
    "sdl3.d3d11"     -> GpuMode.Sdl3.D3D11
    "sdl3.d3d12"     -> GpuMode.Sdl3.D3D12
    else -> {
        println("Unknown --gpu=$inValue, using auto")
        GpuMode.Auto
    }
}

internal fun parseArgs(argv: Array<String>): CliArgs {
    var vArgs = CliArgs()
    for (arg in argv) {
        val eq = arg.indexOf('=')
        if (!arg.startsWith("--") || eq < 0) continue
        val key = arg.substring(2, eq)
        val value = arg.substring(eq + 1)
        vArgs = when (key) {
            "gpu"        -> vArgs.copy(gpu = parseGpu(value))
            "screen"     -> vArgs.copy(screen = value)
            "screenshot" -> vArgs.copy(screenshot = value)
            "width"      -> vArgs.copy(width = value.toIntOrNull() ?: vArgs.width)
            "height"     -> vArgs.copy(height = value.toIntOrNull() ?: vArgs.height)
            "frames"     -> vArgs.copy(maxFrames = value.toIntOrNull() ?: vArgs.maxFrames)
            else -> vArgs
        }
    }
    return vArgs
}
