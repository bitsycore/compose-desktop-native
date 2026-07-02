package androidx.compose.ui.graphics.painter

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope

// ==================
// MARK: BitmapPainter stub
// ==================

/*
 Phase 9 stub — upstream BitmapPainter needs `ImageBitmap` (expect class with
 native actual) which isn't vendored yet. Vendored upstream Image.kt references
 `BitmapPainter` in its `Image(bitmap: ImageBitmap, ...)` overload; keep this
 minimal declaration so Image.kt compiles.

 Runtime code paths reaching Image(bitmap) will paint nothing until a real
 ImageBitmap engine + BitmapPainter land.

 ColorPainter is vendored verbatim from upstream (63L, self-contained).
*/
class BitmapPainter(
	@Suppress("unused") val image: Any,
	@Suppress("unused") val srcOffset: androidx.compose.ui.unit.IntOffset =
		androidx.compose.ui.unit.IntOffset.Zero,
	@Suppress("unused") val srcSize: androidx.compose.ui.unit.IntSize =
		androidx.compose.ui.unit.IntSize.Zero,
	@Suppress("unused") val filterQuality: androidx.compose.ui.graphics.FilterQuality =
		androidx.compose.ui.graphics.drawscope.DrawScope.DefaultFilterQuality,
) : Painter() {
	override val intrinsicSize: Size get() = Size.Unspecified
	override fun DrawScope.onDraw() { /* no-op — needs real ImageBitmap engine */ }
}
