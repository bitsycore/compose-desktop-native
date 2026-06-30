@file:Suppress("UNUSED")

package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// ==================
// MARK: StubOwner — minimal project Owner instance
// ==================

/**
 * A no-op [Owner] singleton attached to every [androidx.compose.ui.node.LayoutNode]
 * so vendored DelegatableNode helpers (`requireOwner()`, `observeReads { … }`,
 * `requireGraphicsContext()`, `requireDensity()`, …) don't crash on an empty
 * owner.
 *
 * Doesn't track snapshots, doesn't drive lifecycle, doesn't fire scroll
 * callbacks — every Owner contract is the cheapest possible no-op. Sufficient
 * for vendored DrawModifierNode-based modifiers (Background, Border, …) whose
 * `observeReads` calls just want to execute their block once.
 *
 * Replaced when the real Owner.kt is vendored alongside upstream LayoutNode
 * (Phase 9).
 */
internal object StubOwner : Owner {
	override val graphicsContext: GraphicsContext = object : GraphicsContext {}
	override val coroutineContext: CoroutineContext = EmptyCoroutineContext
	override val snapshotObserver: OwnerSnapshotObserver = OwnerSnapshotObserver()
	override fun registerOnEndApplyChangesListener(inEffect: () -> Unit) = Unit
	override fun dispatchOnScrollChanged(inDelta: Offset) = Unit
}
