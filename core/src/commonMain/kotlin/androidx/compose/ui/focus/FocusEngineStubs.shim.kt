package androidx.compose.ui.focus

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

// Phase 9 stubs — the real focus engine is unvendored. NodeKind / BackwardsCompatNode
// reference these types for kind-set computation + `is` checks; marker shapes suffice.
interface FocusProperties { var canFocus: Boolean }
interface FocusTargetNode : DelegatableNode
interface FocusEventModifierNode : DelegatableNode
interface FocusPropertiesModifierNode : DelegatableNode {
	fun applyFocusProperties(focusProperties: FocusProperties)
}
interface FocusEventModifier : Modifier.Element
interface FocusOrderModifier : Modifier.Element
fun FocusEventModifierNode.invalidateFocusEvent() {}
fun FocusPropertiesModifierNode.invalidateFocusProperties() {}
