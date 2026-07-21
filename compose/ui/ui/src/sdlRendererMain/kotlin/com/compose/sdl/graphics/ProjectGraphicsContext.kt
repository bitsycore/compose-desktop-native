package com.compose.sdl.graphics

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer

// ==================
// MARK: ProjectGraphicsContext — SDL leg (record/replay GraphicsLayer)
// ==================

/** The SDL leg's GraphicsContext: creates/releases the project record/replay
   GraphicsLayer (backed by the SDL NativeRenderNode). GraphicsLayer's `expect class`
   hides its constructor/release from common code, so layer creation/release hop
   through the [createProjectGraphicsLayer] / [releaseProjectGraphicsLayer] factories
   (co-located here). `shadowContext` keeps the GraphicsContext interface default. */
internal class ProjectGraphicsContext : GraphicsContext {
	override fun createGraphicsLayer(): GraphicsLayer = createProjectGraphicsLayer()
	override fun releaseGraphicsLayer(layer: GraphicsLayer) = releaseProjectGraphicsLayer(layer)
}
