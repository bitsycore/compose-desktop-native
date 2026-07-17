package com.compose.sdl.graphics

import androidx.compose.ui.graphics.layer.GraphicsLayer

// ==================
// MARK: GraphicsLayer factory — native actuals
// ==================

internal fun createProjectGraphicsLayer(): GraphicsLayer =
	GraphicsLayer(createNativeRenderNode(NativeRenderNodeContext()))

internal fun releaseProjectGraphicsLayer(inLayer: GraphicsLayer) {
	inLayer.release()
}
