package com.compose.sdl

// ==================
// MARK: SDL3 renderer per-OS default
// ==================

/* What GpuMode.Auto resolves to when this (SDL3) renderer is the active one:
   mingwX64 → Sdl3.Auto (SDL picks the best Windows driver), macOS → Sdl3.Metal,
   Linux → Sdl3.Auto. Public — :window's preferredGpuMode actual delegates
   here. */
expect fun rendererPreferredGpuMode(): GpuMode
