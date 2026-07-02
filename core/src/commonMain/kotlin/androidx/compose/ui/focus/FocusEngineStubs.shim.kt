package androidx.compose.ui.focus

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

// Phase 9 stubs — the real focus engine is unvendored. NodeKind / BackwardsCompatNode
// reference these for kind-set computation, `is` checks, and legacy-modifier bridging.
interface FocusState {
	val isFocused: Boolean
	val hasFocus: Boolean
	val isCaptured: Boolean
}
interface FocusProperties { var canFocus: Boolean }
class FocusOrder(@Suppress("unused") val focusProperties: FocusProperties? = null)

interface FocusTargetNode : DelegatableNode
interface FocusRequesterModifierNode : DelegatableNode
interface FocusEventModifierNode : DelegatableNode {
	fun onFocusEvent(focusState: FocusState)
}
interface FocusPropertiesModifierNode : DelegatableNode {
	fun applyFocusProperties(focusProperties: FocusProperties)
}
interface FocusEventModifier : Modifier.Element {
	fun onFocusEvent(focusState: FocusState)
}
interface FocusOrderModifier : Modifier.Element {
	fun populateFocusOrder(focusOrder: FocusOrder)
}
fun FocusEventModifierNode.invalidateFocusEvent() {}
fun FocusPropertiesModifierNode.invalidateFocusProperties() {}
