package com.compose.sdl

// ==================
// MARK: Renderer entry points (expect)
// ==================

/* The renderer seam :window builds against. Each renderer source set
   (skikoRendererMain / sdlRendererMain) supplies the actuals; exactly one of
   the two is attached to any given target, so resolution stays unambiguous.

   These were originally declared identically in both renderer source sets
   with NO expect — fine per-target, but shared nativeMain METADATA (=: what
   :window's KotlinMultiplatform publication compiles against) could not see
   them on a host where the attached targets span BOTH renderers (Windows:
   skikoRenderer for macos/linux + sdlRenderer for mingw). The expect makes
   nativeMain metadata self-contained, which lets the WINDOWS publish job —
   the only host that declares every target — produce the root modules. */

/** Create the render backend for the selected [GpuMode]; null when the
   backend can't initialise (caller falls back / reports). */
expect fun createRenderBackend(inSdl: SDL3Backend, inGpu: GpuMode): RenderBackend?

/** The renderer module's per-OS default GpuMode (used for GpuMode.Auto). */
expect fun rendererPreferredGpuMode(): GpuMode
