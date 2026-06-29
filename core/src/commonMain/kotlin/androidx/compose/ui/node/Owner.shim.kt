package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import kotlin.coroutines.CoroutineContext

// ==================
// MARK: Owner shim
// ==================

/**
 * Project shim for upstream `androidx.compose.ui.node.Owner`. Exposes only
 * the four members vendored DelegatableNode + Modifier.Node access:
 * `graphicsContext`, `coroutineContext`, `registerOnEndApplyChangesListener`,
 * `dispatchOnScrollChanged`. Everything else upstream Owner ships
 * (focus, autofill, drag-and-drop, insets, accessibility, …) is omitted.
 *
 * `LayoutNode.owner` stays `null` in Phase 1 — nothing creates an Owner —
 * so the lifecycle hooks that read these properties never fire. Delete
 * once the real Owner.kt is vendored.
 */
interface Owner {

	/** Used by `DelegatableNode.requireGraphicsContext()`. */
	val graphicsContext: GraphicsContext

	/** Used by `Modifier.Node.coroutineScope` lazy init. */
	val coroutineContext: CoroutineContext

	/** Used by `Modifier.Node.sideEffect()`. */
	fun registerOnEndApplyChangesListener(inEffect: () -> Unit)

	/** Used by `DelegatableNode.dispatchOnScrollChanged()`. */
	fun dispatchOnScrollChanged(inDelta: Offset)

	/** Used by `ObserverModifierNode.observeReads()` (Phase 3). */
	val snapshotObserver: OwnerSnapshotObserver

	/** Used by `Modifier.keepScreenOn()`'s Node lifecycle; no-op default. */
	fun incrementKeepScreenOnCount() { /* no-op — we don't track screen-on requests */ }
	fun decrementKeepScreenOnCount() { /* no-op */ }

	/** Used by `Modifier.sensitiveContent()`'s Node lifecycle; no-op default. */
	fun incrementSensitiveComponentCount() { /* no-op — screen-share masking not wired */ }
	fun decrementSensitiveComponentCount() { /* no-op */ }
}
