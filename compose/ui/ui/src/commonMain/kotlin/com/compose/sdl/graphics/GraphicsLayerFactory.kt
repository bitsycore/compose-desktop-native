package com.compose.sdl.graphics

import androidx.compose.ui.graphics.layer.GraphicsLayer

// ==================
// MARK: GraphicsLayer factory (project bridge)
// ==================

/* The vendored `expect class GraphicsLayer` exposes neither a constructor nor
   the isReleased setter to common code — upstream owners create layers from
   platform source sets. ComposeOwner lives in commonMain, so these two hops
   let its GraphicsContext create and release the project record/replay
   actual (GraphicsLayer.native.kt). */

internal expect fun createProjectGraphicsLayer(): GraphicsLayer

internal expect fun releaseProjectGraphicsLayer(inLayer: GraphicsLayer)
