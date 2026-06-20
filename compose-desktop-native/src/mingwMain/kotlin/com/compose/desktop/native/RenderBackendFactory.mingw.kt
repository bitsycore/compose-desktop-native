package com.compose.desktop.native

// mingwX64 always links compose-renderer-sdl3.

internal actual fun makeRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend? =
	createRenderBackend(inSdl, inGpu)

internal actual fun preferredGpuMode(): GpuMode = rendererPreferredGpuMode()
