// VENDOR-REIMPL: compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/semantics/SemanticsRegion.skiko.kt @ v1.12.0
// Project REIMPLEMENTATION, not a derived copy: same package + signatures,
// different body (the port replaces upstream's skiko scene/platform layer).
// Upstream edits to the counterpart do NOT need reconciling here - re-check
// only if the expect/actual SIGNATURES change.

package androidx.compose.ui.semantics

import androidx.compose.ui.unit.IntRect

// ==================
// MARK: SemanticsRegion native actual
// ==================

/**
 * Stub actual - no semantics pipeline today. Tracks the last `set` rect
 * so [bounds] reads work, but [intersect] / [difference] are always
 * no-ops.
 */
private class StubSemanticsRegion : SemanticsRegion {
	private var mBounds: IntRect = IntRect.Zero
	override fun set(rect: IntRect) { mBounds = rect }
	override fun intersect(region: SemanticsRegion): Boolean = false
	override fun difference(rect: IntRect): Boolean = false
	override val bounds: IntRect get() = mBounds
	override val isEmpty: Boolean get() = mBounds == IntRect.Zero
}

internal actual fun SemanticsRegion(): SemanticsRegion = StubSemanticsRegion()
