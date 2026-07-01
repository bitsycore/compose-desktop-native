package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer

// ==================
// MARK: LocalGraphicsContext shim
// ==================

/**
 * Stub of upstream's `LocalGraphicsContext` (lives inside the full
 * `platform/CompositionLocals.kt`, unvendored). Vendored `GraphicsLayerScope
 * .kt` reads it via `LocalGraphicsContext.current`. Default falls back to
 * a bare GraphicsContext that throws on layer creation.
 */
val LocalGraphicsContext = staticCompositionLocalOf<GraphicsContext> {
	object : GraphicsContext {
		override fun createGraphicsLayer(): GraphicsLayer =
			throw NotImplementedError("createGraphicsLayer not wired on default LocalGraphicsContext")
		override fun releaseGraphicsLayer(layer: GraphicsLayer) = Unit
	}
}
