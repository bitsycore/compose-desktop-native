package androidx.compose.ui.semantics

import androidx.compose.ui.unit.IntRect

// ==================
// MARK: SemanticsRegion native actual
// ==================

/**
 * Stub actual — no semantics pipeline today. Tracks the last `set` rect
 * so [bounds] reads work, but [intersect] / [difference] are always
 * no-ops.
 */
private class StubSemanticsRegion : SemanticsRegion {
	private var fBounds: IntRect = IntRect.Zero
	override fun set(rect: IntRect) { fBounds = rect }
	override fun intersect(region: SemanticsRegion): Boolean = false
	override fun difference(rect: IntRect): Boolean = false
	override val bounds: IntRect get() = fBounds
	override val isEmpty: Boolean get() = fBounds == IntRect.Zero
}

internal actual fun SemanticsRegion(): SemanticsRegion = StubSemanticsRegion()
