package androidx.compose.ui.graphics.shadow

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

// ==================
// MARK: ShadowContext / PlatformShadowContext — shim
// ==================
//
// Marker for upstream `androidx.compose.ui.graphics.shadow.ShadowContext`.
// The real interface returns cached [DropShadowPainter] / [InnerShadowPainter]
// instances backed by the platform blur pipeline (Blur.kt + ShadowRenderer.kt).
// The full shadow engine isn't vendored, so both painters are no-op subclasses
// of [Painter] and the factory methods on ShadowContext build them directly.
//
// PlatformShadowContext is the platform base. Vendored GraphicsContext declares
//   val shadowContext: ShadowContext get() = object : PlatformShadowContext {}
// so the default no-op ShadowContext is what any node sees at runtime.

interface ShadowContext {
	fun createInnerShadowPainter(shape: Shape, shadow: Shadow): InnerShadowPainter =
		InnerShadowPainter(shape, shadow)
	fun createDropShadowPainter(shape: Shape, shadow: Shadow): DropShadowPainter =
		DropShadowPainter(shape, shadow)
	fun clearCache() {}
}

interface PlatformShadowContext : ShadowContext

class DropShadowPainter internal constructor(
	@Suppress("unused") private val shape: Shape,
	@Suppress("unused") private val shadow: Shadow,
) : Painter() {
	override val intrinsicSize: Size get() = Size.Unspecified
	override fun DrawScope.onDraw() { /* no-op until the shadow engine lands */ }
}

class InnerShadowPainter internal constructor(
	@Suppress("unused") private val shape: Shape,
	@Suppress("unused") private val shadow: Shadow,
) : Painter() {
	override val intrinsicSize: Size get() = Size.Unspecified
	override fun DrawScope.onDraw() { /* no-op until the shadow engine lands */ }
}
