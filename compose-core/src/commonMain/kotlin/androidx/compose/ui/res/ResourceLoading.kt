package androidx.compose.ui.res

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize

// ==================
// MARK: ResourceKind
// ==================

/* Classifies a bundled resource so the active RenderBackend knows how to
   decode it. Resources under drawable/ are classified by file extension;
   those under files/ are always Raw (read-only bytes, not drawable). */
enum class ResourceKind {
	Raster,         // png / jpg / bmp / gif / webp … — decode to a bitmap
	Svg,            // .svg — rasterise the vector
	AndroidVector,  // android <vector> xml — converted to svg, then rasterised
	Raw,            // arbitrary bytes — not drawable, only Res.readBytes
}

/* Picks a ResourceKind from a file extension. */
fun resourceKindForPath(inPath: String): ResourceKind {
	val vExt = inPath.substringAfterLast('.', "").lowercase()
	return when (vExt) {
		"svg"                                              -> ResourceKind.Svg
		"xml"                                              -> ResourceKind.AndroidVector
		"png", "jpg", "jpeg", "bmp", "gif", "webp",
		"tga", "qoi", "tif", "tiff"                        -> ResourceKind.Raster
		else                                               -> ResourceKind.Raw
	}
}

// ==================
// MARK: ImageLoader (backend-provided)
// ==================

/* Shared bridge the commonMain layout pass uses to size images and read raw
   bytes. The active RenderBackend installs an implementation at startup
   (parallel to currentTextMeasurer). Actual drawing is done by the renderer
   directly off the LayoutNode painter leaf, reusing the backend's decode
   cache — so a decoded texture is created once and shared by measure + draw. */
interface ImageLoader {
	/* Intrinsic pixel size of the decoded resource, treated as logical points
	   by the layout pass. IntSize(-1, -1) when the resource is missing or can't
	   be decoded. */
	fun intrinsicSize(inPath: String, inKind: ResourceKind): IntSize

	/* Raw bytes of a bundled resource of any kind, or null if missing. */
	fun readBytes(inPath: String): ByteArray?
}

private val kFallbackImageLoader = object : ImageLoader {
	override fun intrinsicSize(inPath: String, inKind: ResourceKind) = IntSize(-1, -1)
	override fun readBytes(inPath: String): ByteArray? = null
}

/* Installed by ComposeWindow from renderBackend.imageLoader. */
var currentImageLoader: ImageLoader = kFallbackImageLoader

// ==================
// MARK: painterResource
// ==================

/* Builds a Painter for a bundled drawable by its path relative to
   composeResources/ (e.g. "drawable/logo.png"). The generated Res.drawable.*
   accessors call this; it's also fine to call directly. Kind is inferred from
   the extension. */
fun painterResource(inPath: String): Painter =
	Painter(inPath, resourceKindForPath(inPath))

/* Same, with an explicit kind when the extension is ambiguous. */
fun painterResource(inPath: String, inKind: ResourceKind): Painter =
	Painter(inPath, inKind)
