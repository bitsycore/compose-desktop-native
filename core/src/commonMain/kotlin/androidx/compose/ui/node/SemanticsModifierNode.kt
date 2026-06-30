package androidx.compose.ui.node

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

// ==================
// MARK: SemanticsModifierNode — minimal project shim (non-official)
// ==================

/*
 * Stub of upstream's `androidx.compose.ui.node.SemanticsModifierNode`. The
 * full vendored version pulls SemanticsConfiguration / SemanticsNode /
 * SemanticsOwner / SemanticsActions — all part of the unvendored
 * accessibility engine. Replace when that lands (Phase 11+).
 *
 * Vendored foundation code (Background.kt, Image.kt, etc.) declares
 * Modifier.Node subclasses implementing this interface; they invoke
 * `applySemantics` on a discard receiver, so the runtime payload never
 * reaches an accessibility consumer.
 */
interface SemanticsModifierNode : DelegatableNode {
	fun SemanticsPropertyReceiver.applySemantics()

	val shouldMergeDescendantSemantics: Boolean get() = false
	val shouldClearDescendantSemantics: Boolean get() = false
}

/** Project shim — fires `applySemantics` against a throw-away receiver. */
fun SemanticsModifierNode.invalidateSemantics() {
	with(this) {
		val vReceiver = SemanticsConfiguration()
		vReceiver.applySemantics()
	}
}
