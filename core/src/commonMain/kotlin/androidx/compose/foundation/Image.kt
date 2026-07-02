package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale

// ==================
// MARK: Image
// ==================

/* Draws a bundled image resource. The painter (from painterResource / a
   generated Res.drawable.* accessor) is stored on the LayoutNode as a leaf;
   the active renderer decodes + caches + paints it during draw, applying
   contentScale and alpha.

   contentDescription is accepted for source-compatibility with Compose but
   is currently unused (no accessibility layer). */
@Composable
fun Image(
	painter: Painter,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit,
	alpha: Float = 1f,
) {
	// Phase 9 B5: build an upstream LayoutNode via the vendored Layout, sized to the
	// painter's intrinsic size, drawn by a PainterDrawNode (DrawModifierNode) that
	// bridges to the renderer's image cache.
	androidx.compose.ui.layout.Layout(
		modifier = modifier.then(
			com.compose.desktop.native.graphics.PainterDrawElement(painter, contentScale, alpha)
		),
	) { _, constraints ->
		val vIntrinsic = painter.intrinsicSize
		val vW = vIntrinsic.width.coerceAtLeast(0)
		val vH = vIntrinsic.height.coerceAtLeast(0)
		layout(
			vW.coerceIn(constraints.minWidth, constraints.maxWidth),
			vH.coerceIn(constraints.minHeight, constraints.maxHeight),
		) {}
	}
}

