package androidx.compose.ui.graphics.painter

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.res.currentImageLoader
import com.compose.desktop.native.graphics.NativePainterCanvas

// ==================
// MARK: ResourcePainter
// ==================

/*
 A [Painter] that references a bundled resource by path + kind. The active
 RenderBackend decodes + caches + paints the resource; ResourcePainter carries
 no bitmap data itself. Constructed by `painterResource(...)` and the
 generated `Res.drawable.*` accessors.

 Non-upstream: upstream ships BitmapPainter / ColorPainter / VectorPainter as
 the concrete Painter subclasses. Our resource system decodes lazily inside
 the renderer, so this reduced type carries just the resource pointer.

 [onDraw] casts the current Canvas to [NativePainterCanvas] (implemented by
 Sdl3Canvas / SkiaCanvas) and delegates paint to the renderer's image cache.
 If the Canvas doesn't implement NativePainterCanvas (offscreen layer capture
 for graphics-layer effects) the paint is skipped.
*/
class ResourcePainter internal constructor(
	val resourcePath: String,
	val kind: ResourceKind,
) : Painter() {

	override val intrinsicSize: Size
		get() = currentImageLoader.intrinsicSize(resourcePath, kind)

	override fun DrawScope.onDraw() {
		drawIntoCanvas { vCanvas ->
			(vCanvas as? NativePainterCanvas)?.drawNativePainter(
				resourcePath, kind, 0f, 0f, size.width, size.height,
				androidx.compose.ui.layout.ContentScale.Fit, 1f,
			)
		}
	}

	override fun equals(other: Any?): Boolean =
		other is ResourcePainter && other.resourcePath == resourcePath && other.kind == kind

	override fun hashCode(): Int = resourcePath.hashCode() * 31 + kind.hashCode()

	override fun toString(): String = "ResourcePainter($resourcePath, $kind)"
}
