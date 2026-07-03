package androidx.compose.foundation.text.modifiers

import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.text.TextStyle
import kotlin.jvm.JvmInline

// ==================
// MARK: StylePhase / TextStyleProviderNode / inheritedTextStyle — project subset
// ==================

/*
 Extracted from upstream foundation.text.modifiers.TextStyleProviderNode.kt (which
 depends on foundation.style.StyleOuterNode + OuterNodeKey — 1000+L style module
 we don't vendor). The vendored TextStringSimpleNode / TextAnnotatedStringNode
 reference StylePhase.Layout / Draw / All and call inheritedTextStyle to resolve
 style inheritance up the tree.

 Since foundation.style is unvendored, no `StyleOuterNode` ever mounts and
 `inheritedTextStyle` short-circuits to `fallback`. Text styles are set inline
 on each Text() call — no ambient style inheritance. Byte-identical value class
 shape matches upstream so vendored files link.
*/
@JvmInline
internal value class StylePhase private constructor(internal val value: Int) {
	companion object {
		val Layout: StylePhase = StylePhase(1)
		val Draw: StylePhase = StylePhase(2)
		val All: StylePhase = StylePhase(0.inv())
	}
}

internal interface TextStyleProviderNode : TraversableNode {
	fun computeInheritedTextStyle(phase: StylePhase, fallback: TextStyle): TextStyle
}

// Short-circuit: no StyleOuterNode ancestors exist because foundation.style isn't
// vendored. Vendored TextStringSimple/TextAnnotatedString nodes call this to
// resolve inherited style; we always return `fallback` (their explicit style).
internal fun DelegatableNode.inheritedTextStyle(
	@Suppress("UNUSED_PARAMETER") phase: StylePhase,
	fallback: TextStyle,
): TextStyle = fallback
