package androidx.compose.ui.layout

import androidx.compose.ui.unit.LayoutDirection

// ==================
// MARK: MeasureScopeImpl
// ==================

/**
 * Concrete [MeasureScope] used by our project's `Layout` composable to
 * invoke a `MeasurePolicy`'s `measure()` body. The MeasureScope
 * interface itself is vendored from upstream `MeasureScope.kt`. This
 * impl provides the IntrinsicMeasureScope members (Density / fontScale
 * / layoutDirection / isLookingAhead) and delegates `layout()` to
 * upstream's default impl (which creates the MeasureResult object).
 *
 * No need to override `layout()` ourselves — upstream's default impl is
 * fine for our purposes (we don't have a custom NodeCoordinator-based
 * layout pipeline yet).
 */
internal open class MeasureScopeImpl : MeasureScope {
	override val density: Float = 1f
	override val fontScale: Float = 1f
	override val layoutDirection: LayoutDirection = LayoutDirection.Ltr
}
