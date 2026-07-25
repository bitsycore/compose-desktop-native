package com.compose.sdl

// ==================
// MARK: Renderer entry points (expect)
// ==================

/* The renderer seam :desktop-native-window builds against. The skikoRendererMain source set
   (attached to every native target) supplies the actual.

   Declared as an expect in nativeMain — not just a plain fun in
   skikoRendererMain — so shared nativeMain METADATA (what :desktop-native-window's
   KotlinMultiplatform publication compiles against) can see the symbol.
   Without the expect the actual would sit below nativeMain and be invisible to
   nativeMain-level consumers on the WINDOWS publish job — the only host that
   declares every target, and so the one that produces the root modules. */

/** Create the render backend for the selected [GpuMode]; null when the
   backend can't initialise (caller falls back / reports). */
expect fun createRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend?

/** The renderer module's per-OS default GpuMode (used for GpuMode.Auto). */
expect fun rendererPreferredGpuMode(): GpuMode
