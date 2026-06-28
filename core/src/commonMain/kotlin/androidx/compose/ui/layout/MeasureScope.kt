package androidx.compose.ui.layout

// ==================
// MARK: MeasureScope / MeasureResult
// ==================

/**
 * Receiver scope for a MeasurePolicy's measure() body. Provides the
 * `layout()` builders that bundle the resolved parent size with a
 * placement block to be invoked after measurement returns.
 *
 * Phase 4c: now extends [IntrinsicMeasureScope] so vendored consumers
 * that expect a MeasureScope to also be an IntrinsicMeasureScope (the
 * Density + layoutDirection + isLookingAhead carrier) compile. Adds
 * upstream-shape `layout()` overloads with `alignmentLines` + `rulers`
 * defaults. Param names mirror upstream (width / height / placementBlock)
 * so call sites that use named args resolve correctly.
 */
@MeasureScopeMarker
interface MeasureScope : IntrinsicMeasureScope {

	/**
	 * Three-arg layout: width + height + placement block. Backwards-
	 * compatible with our pre-Phase-4c callers.
	 */
	fun layout(
		width: Int,
		height: Int,
		placementBlock: Placeable.PlacementScope.() -> Unit,
	): MeasureResult = layout(width, height, emptyMap(), null, placementBlock)

	/**
	 * Four-arg layout: width + height + alignmentLines + placement block.
	 */
	fun layout(
		width: Int,
		height: Int,
		alignmentLines: Map<AlignmentLine, Int>,
		placementBlock: Placeable.PlacementScope.() -> Unit,
	): MeasureResult = layout(width, height, alignmentLines, null, placementBlock)

	/**
	 * Full upstream-shape layout: width + height + alignmentLines + rulers
	 * + placement block.
	 */
	fun layout(
		width: Int,
		height: Int,
		alignmentLines: Map<AlignmentLine, Int>,
		rulers: (RulerScope.() -> Unit)?,
		placementBlock: Placeable.PlacementScope.() -> Unit,
	): MeasureResult
}

/** DSL marker matching upstream's MeasureScopeMarker. */
@DslMarker
annotation class MeasureScopeMarker

/* MeasureResult is vendored from upstream (MeasureResult.kt) — this
   block intentionally left blank so the type lives at upstream's
   canonical path. */

internal class MeasureScopeImpl : MeasureScope {

	override val density: Float = 1f
	override val fontScale: Float = 1f
	override val layoutDirection: androidx.compose.ui.unit.LayoutDirection =
		androidx.compose.ui.unit.LayoutDirection.Ltr

	override fun layout(
		width: Int,
		height: Int,
		alignmentLines: Map<AlignmentLine, Int>,
		rulers: (RulerScope.() -> Unit)?,
		placementBlock: Placeable.PlacementScope.() -> Unit,
	): MeasureResult {
		val vWidth = width
		val vHeight = height
		val vAlignmentLines = alignmentLines
		val vRulers = rulers
		return object : MeasureResult {
			override val width: Int = vWidth
			override val height: Int = vHeight
			override val alignmentLines: Map<AlignmentLine, Int> = vAlignmentLines
			override val rulers: (RulerScope.() -> Unit)? = vRulers
			override fun placeChildren() { Placeable.PlacementScope.placementBlock() }
		}
	}
}
