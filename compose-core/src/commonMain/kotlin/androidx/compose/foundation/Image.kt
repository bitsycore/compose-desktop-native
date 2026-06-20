package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.MeasurePolicy
import androidx.compose.ui.node.NodeApplier
import androidx.compose.ui.unit.IntSize

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
	ComposeNode<LayoutNode, NodeApplier>(
		factory = { LayoutNode() },
		update = {
			set(painter) { this.painter = it }
			set(contentScale) { this.contentScale = it }
			set(alpha) { this.imageAlpha = it }
			set(modifier) { this.modifier = it }
			set(Unit) { this.measurePolicy = ImageMeasurePolicy }
		}
	)
}

/* Image layout: a Size modifier (Modifier.size(...) etc.) pins the bounds via
   the constraints already applied upstream; otherwise the node takes the
   painter's intrinsic pixel size (treated as logical points), clamped into the
   incoming constraints. Aspect ratio on an unpinned axis is not enforced at
   layout time — ContentScale handles fit/crop when the box differs from the
   intrinsic ratio. */
internal val ImageMeasurePolicy = MeasurePolicy { node, constraints ->
	val vIntrinsic = node.painter?.intrinsicSize ?: IntSize(0, 0)
	val vW = vIntrinsic.width.coerceAtLeast(0)
	val vH = vIntrinsic.height.coerceAtLeast(0)
	// maxWidth / maxHeight are always >= their min (Infinity for unbounded
	// axes), so coerceIn is safe; an unbounded axis just keeps the intrinsic.
	val w = vW.coerceIn(constraints.minWidth, constraints.maxWidth)
	val h = vH.coerceIn(constraints.minHeight, constraints.maxHeight)
	IntSize(w, h)
}
