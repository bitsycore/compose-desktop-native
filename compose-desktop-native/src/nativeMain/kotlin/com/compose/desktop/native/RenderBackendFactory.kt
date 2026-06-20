package com.compose.desktop.native

// ==================
// MARK: Per-target renderer selection
// ==================
// composeWindow() calls these two expects. Each target's actual forwards to the
// renderer module this module depends on for that target — compose-renderer-sdl3
// on mingwX64 (always), compose-renderer-skia on macOS/Linux (or sdl3 under
// -Prenderer=sdl3). The renderer modules expose createRenderBackend /
// rendererPreferredGpuMode with identical signatures, so the actuals are a
// one-line forward and the build links exactly one renderer per target.

internal expect fun makeRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend?

internal expect fun preferredGpuMode(): GpuMode
