package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.compose.desktop.native.node.LayoutNode
import com.compose.desktop.native.node.NodeApplier

// ==================
// MARK: Canvas
// ==================

/* Bare-bones drawing surface. Emits a LayoutNode whose `drawer` field is
   set to [onDraw]; the active renderer invokes it during the draw pass
   with a backend-specific DrawScope sized to the node's bounds. Useful for
   custom shapes, charts, and Material widgets whose look isn't expressible
   via modifiers alone (progress rings, gauges, etc.).

   For decoration that sits behind an existing node's content, use
   Modifier.drawBehind { ... } instead — same DrawScope, but composed onto
   another node rather than as a standalone leaf.

   Sizing: by default the canvas wraps to zero (no intrinsic content), so
   callers pin a size with Modifier.size / fillMaxSize / etc. */
@Composable
fun Canvas(
	modifier: Modifier,
	onDraw: DrawScope.() -> Unit,
) {
	ComposeNode<LayoutNode, NodeApplier>(
		factory = { LayoutNode() },
		update = {
			set(modifier) { this.modifier = it }
			set(onDraw) { this.drawer = it }
		},
	)
}
