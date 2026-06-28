package androidx.compose.ui.node

// ==================
// MARK: OwnerSnapshotObserver shim
// ==================

/**
 * Phase 3 shim for upstream `androidx.compose.ui.node.OwnerSnapshotObserver`
 * (157 lines in upstream — wraps `SnapshotStateObserver` and exposes a
 * pile of inline helpers like `observeLayoutSnapshotReads`,
 * `observeLayoutModifierSnapshotReads`, etc.).
 *
 * Vendored ObserverModifierNode.observeReads only reaches in via
 * `requireOwner().snapshotObserver.observeReads(target, onChanged, block)`
 * — a single 3-arg method. We stub it to invoke `block` once with no
 * observation (Phase 3 has no live Modifier.Node attached so nothing
 * subscribes to snapshot reads anyway). Delete when the real
 * OwnerSnapshotObserver is vendored alongside upstream LayoutNode in
 * Phase 4.
 */
class OwnerSnapshotObserver {

	/** Single 3-arg overload ObserverModifierNode.observeReads calls. */
	fun <T : Any> observeReads(
		target: T,
		@Suppress("UNUSED_PARAMETER") onChanged: (T) -> Unit,
		block: () -> Unit,
	) {
		// Phase 3: no snapshot tracking — just run the block.
		block()
	}
}
