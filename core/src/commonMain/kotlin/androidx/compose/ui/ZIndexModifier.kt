package androidx.compose.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement

// ==================
// MARK: zIndex
// ==================

/**
 * Creates a modifier that controls the drawing order for the children of the
 * same layout parent. A child with larger [zIndex] will be drawn on top of all
 * the children with smaller [zIndex]. When children have the same [zIndex] the
 * original order in which the parent placed the children is used.
 *
 * Note that if there would be multiple [zIndex] modifiers applied for the same
 * layout the sum of their values will be used as the final zIndex. If no
 * [zIndex] were applied for the layout then the default zIndex is 0.
 */
@Stable
fun Modifier.zIndex(zIndex: Float): Modifier =
	if (zIndex == 0f) this else this.then(ZIndexElement(zIndex = zIndex))

/**
 * `ModifierNodeElement` factory for the project's z-ordering modifier. Mirrors
 * upstream's `ZIndexElement` (also `internal`) at
 * `compose/ui/ui/.../androidx/compose/ui/ZIndexModifier.kt:42`.
 *
 * The project's renderer reads the value by walking `Modifier.foldIn` and
 * summing every `ZIndexElement.zIndex` — see
 * `com.compose.desktop.native.node.LayoutNode.zIndex`. The paired
 * [ZIndexNode] lifecycle is dormant until the renderer rewrite drives it via
 * the upstream `NodeCoordinator` layout pipeline.
 */
internal data class ZIndexElement(val zIndex: Float) : ModifierNodeElement<ZIndexNode>() {
	override fun create() = ZIndexNode(zIndex)
	override fun update(node: ZIndexNode) { node.zIndex = zIndex }
}

/**
 * Paired `Modifier.Node` for [ZIndexElement]. Marks itself as a
 * [LayoutModifierNode] to match upstream's shape — the official `measure()`
 * body that calls `placeable.place(0, 0, zIndex = zIndex)` is not implemented
 * here since the project's renderer reads zIndex directly from the element via
 * foldIn rather than via the Modifier.Node measure pipeline.
 */
internal class ZIndexNode(var zIndex: Float) : Modifier.Node(), LayoutModifierNode {
	override fun toString(): String = "ZIndexNode(zIndex=$zIndex)"
}
