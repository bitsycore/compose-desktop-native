package com.compose.sdl.graphics

import androidx.compose.ui.graphics.GraphicsContext

// ==================
// MARK: createGraphicsContext — SDL3 renderer actual
// ==================

/* The SDL leg uses the project record/replay GraphicsContext (backed by the SDL
   NativeRenderNode via createProjectGraphicsLayer). This is the permanent SDL home. */
internal actual fun createGraphicsContext(): GraphicsContext = ProjectGraphicsContext()
