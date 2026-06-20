package com.compose.desktop.native

// macOS links compose-renderer-skia by default, compose-renderer-sdl3 under
// -Prenderer=sdl3 (the build picks which project this source set depends on).

internal actual fun makeRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend? =
	createRenderBackend(inSdl, inGpu)

internal actual fun preferredGpuMode(): GpuMode = rendererPreferredGpuMode()
