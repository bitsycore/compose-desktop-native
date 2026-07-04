package androidx.compose.foundation.text.modifiers

import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.text.TextStyle
import kotlin.jvm.JvmInline

// ==================
// MARK: StylePhase / inheritedTextStyle — project stub
// ==================
//
// Byte-identical value class extracted from upstream `TextStyleProviderNode.kt`
// (foundation/text/modifiers). We can't vendor that file because it imports
// `androidx.compose.foundation.style.OuterNodeKey` + `StyleOuterNode` from
// `foundation/style/` (7 files, ~7000L — a whole experimental style system).
// Vendored TextStringSimpleNode / TextAnnotatedStringNode call
// `inheritedTextStyle(phase, fallback)` and read `StylePhase` values, so this
// stub keeps the vendored files compiling.
//
// `inheritedTextStyle` short-circuits to `fallback` — no upstream style
// provider ancestry is traversed. A wrapping `Modifier.style { ... }` doesn't
// propagate a `TextStyle` — but nothing in our tree constructs one anyway.
//
// TODO: delete this file once `foundation.style.` (StyleOuterNode + OuterNodeKey
// + StyleScope + StyleProperties + StyleModifier + StyleAnimations + StyleState
// + ResolvedStyle) vendors cleanly.

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

internal fun DelegatableNode.inheritedTextStyle(
	@Suppress("UNUSED_PARAMETER") phase: StylePhase,
	fallback: TextStyle,
): TextStyle = fallback
