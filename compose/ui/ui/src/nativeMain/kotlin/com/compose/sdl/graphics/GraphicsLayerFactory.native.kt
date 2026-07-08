package com.compose.sdl.graphics

import androidx.compose.ui.graphics.layer.GraphicsLayer

// ==================
// MARK: GraphicsLayer factory — native actuals
// ==================

internal actual fun createProjectGraphicsLayer(): GraphicsLayer = GraphicsLayer()

internal actual fun releaseProjectGraphicsLayer(inLayer: GraphicsLayer) {
	inLayer.isReleased = true
}
