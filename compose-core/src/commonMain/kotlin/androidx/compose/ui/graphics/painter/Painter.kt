package androidx.compose.ui.graphics.painter

import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.res.currentImageLoader
import androidx.compose.ui.unit.IntSize

// ==================
// MARK: Painter
// ==================

/* A handle to a bundled image resource that the active RenderBackend decodes,
   caches and draws. This mirrors the role of Compose's Painter, but is a thin
   reference (path + kind) rather than a draw primitive: the renderer owns
   decoding so commonMain stays free of any SDL / Skia dependency.

   Construct via painterResource(...). The renderer reads resourcePath / kind
   off the LayoutNode painter leaf to resolve the decoded texture. */
class Painter internal constructor(
	val resourcePath: String,    // relative to composeResources/, e.g. "drawable/logo.png"
	val kind: ResourceKind,
) {
	/* Intrinsic pixel size reported by the active loader (decodes + caches on
	   first query). IntSize(-1, -1) if the resource is missing or no loader is
	   installed yet. */
	val intrinsicSize: IntSize get() = currentImageLoader.intrinsicSize(resourcePath, kind)

	override fun equals(other: Any?): Boolean =
		other is Painter && other.resourcePath == resourcePath && other.kind == kind

	override fun hashCode(): Int = resourcePath.hashCode() * 31 + kind.hashCode()

	override fun toString(): String = "Painter($resourcePath, $kind)"
}
