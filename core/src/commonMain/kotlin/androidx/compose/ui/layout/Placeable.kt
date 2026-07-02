package androidx.compose.ui.layout

import com.compose.desktop.native.node.ProjectLayoutNode

// ==================
// MARK: Placeable
// ==================

/**
 * The measured result of asking a Measurable to measure. Carries width
 * and height, plus a [placeAt] that positions the underlying ProjectLayoutNode
 * inside its parent. Placement is deferred — measure() returns the
 * Placeable, then the MeasurePolicy's `layout { }` block calls placeAt(x, y)
 * inside its placement closure.
 */
abstract class Placeable : Measured {

	/** Default `width`/`height` derive from [measuredSize]. Subclasses can
	 *  either override these directly (project style) or write to
	 *  [measuredSize] from an init block (upstream Placeable style). */
	open val width: Int get() = measuredSize.width
	open val height: Int get() = measuredSize.height

	/** Upstream Placeable's protected `measuredSize` — a mutable
	 *  `IntSize` field the subclass sets in `init { measuredSize = ... }`.
	 *  Our project's own subclasses override width/height directly and
	 *  ignore this; vendored `FixedSizeIntrinsicsPlaceable` sets it. */
	protected var measuredSize: androidx.compose.ui.unit.IntSize =
		androidx.compose.ui.unit.IntSize.Zero

	/** Phase 9: the constraints this Placeable was measured against. Upstream
	 *  NodeCoordinator reads/writes this; project subclasses ignore it. */
	open var measurementConstraints: androidx.compose.ui.unit.Constraints =
		androidx.compose.ui.unit.Constraints()

	/** Upstream Measured.measuredWidth — before constraint coercion. Our
	 *  project doesn't distinguish measured vs coerced size; both are width. */
	override val measuredWidth: Int get() = width
	override val measuredHeight: Int get() = height

	/** Upstream Measured.parentData — data set by ParentDataModifier.
	 *  Subclasses that carry weight / alignment / etc. override this. */
	override val parentData: Any? get() = null

	/**
	 * Place the underlying node at ([inX], [inY]) in its parent's coordinate
	 * space. Coordinates are in logical points (the layout pass runs at
	 * logical resolution; HiDPI scaling happens in the renderer).
	 *
	 * `open` (was abstract) — a subclass may instead override the upstream-
	 * shape [placeAt(IntOffset, Float, layerBlock)] below; the default
	 * implementation here routes through that overload.
	 */
	open fun placeAt(inX: Int, inY: Int) {
		placeAt(androidx.compose.ui.unit.IntOffset(inX, inY), 0f, null)
	}

	/**
	 * Upstream `Placeable.placeAt(IntOffset, Float, layerBlock?)` protected
	 * abstract entry point (public here since our project layout code calls
	 * `placeAt(x, y)` directly, not via PlacementScope extensions). Default
	 * routes to [placeAt(inX, inY)]; vendored `FixedSizeIntrinsicsPlaceable`
	 * / `EmptyPlaceable` override this instead of the 2-arg form.
	 */
	open fun placeAt(
		position: androidx.compose.ui.unit.IntOffset,
		@Suppress("UNUSED_PARAMETER") zIndex: Float,
		@Suppress("UNUSED_PARAMETER")
		layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)?,
	) {
		placeAt(position.x, position.y)
	}

	/**
	 * Returns the position of the given [AlignmentLine] in this Placeable,
	 * or [AlignmentLine.Unspecified] if not provided. This renderer does
	 * not currently propagate alignment-line values up the tree, so the
	 * default is always Unspecified — callers fall back to a 0 line
	 * position and paddingFrom degenerates to padding-with-coerce.
	 */
	override operator fun get(inAlignmentLine: androidx.compose.ui.layout.AlignmentLine): Int =
		androidx.compose.ui.layout.AlignmentLine.Unspecified

	/**
	 * Receiver scope for a `MeasureScope.layout { }` placement block.
	 * Placement happens by calling `placeable.placeAt(x, y)` directly
	 * (Placeable.placeAt is public). Nested inside Placeable for
	 * upstream-API parity; the single Default instance is the receiver
	 * passed into every `layout { }` block.
	 */
	/**
	 * Placement-block receiver. Phase 4d brings the public surface to
	 * upstream's shape: extends [androidx.compose.ui.unit.Density],
	 * exposes `parentWidth` + `parentLayoutDirection` for RTL mirroring,
	 * carries the `density` + `fontScale` Density members. Defaults are
	 * sensible no-ops — RTL mirroring is disabled (parentWidth = 0) and
	 * density / fontScale are 1f.
	 */
	@androidx.compose.ui.layout.PlacementScopeMarker
	abstract class PlacementScope : androidx.compose.ui.unit.Density {

		override val density: Float
			get() = 1f

		override val fontScale: Float
			get() = 1f

		/** RTL mirroring origin. 0 disables mirroring. */
		protected open val parentWidth: Int
			get() = 0

		/** Layout direction of the parent. Ltr means no mirroring. */
		protected open val parentLayoutDirection: androidx.compose.ui.unit.LayoutDirection
			get() = androidx.compose.ui.unit.LayoutDirection.Ltr

		/**
		 * Phase 4b: read the current value of [Ruler] in this PlacementScope,
		 * or [defaultValue] if not provided. Mirrors upstream
		 * `Placeable.PlacementScope.kt` line 199 (open fun with the same
		 * default). Vendored Ruler.mergeRulerValues calls this — but no
		 * Ruler instances are constructed in our pipeline, so this is
		 * never invoked at runtime.
		 */
		open fun Ruler.current(@Suppress("UNUSED_PARAMETER") defaultValue: Float): Float = defaultValue

		/**
		 * Phase 4f: upstream-shape `place(x, y)` extension on Placeable.
		 * Mirrors upstream `Placeable.kt` line 245. Our Placeable.placeAt
		 * is a public 2-arg `(x: Int, y: Int)` instead of upstream's
		 * protected 3-arg `(position: IntOffset, zIndex: Float, layerBlock)`,
		 * so this thin wrapper forwards `(x, y)` directly. `zIndex` is
		 * accepted-and-ignored — our renderer reads z-order from a
		 * separate ZIndexElement on the ProjectLayoutNode.
		 */
		fun Placeable.place(
			x: Int,
			y: Int,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
		) {
			placeAt(x, y)
		}

		/** [place(x, y, zIndex)] but takes an [IntOffset] position. */
		fun Placeable.place(
			position: androidx.compose.ui.unit.IntOffset,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
		) {
			placeAt(position.x, position.y)
		}

		/**
		 * RTL-aware placement. If `parentLayoutDirection == Rtl`, mirrors
		 * the x coordinate around `parentWidth`. With our defaults
		 * (parentWidth = 0, parentLayoutDirection = Ltr) this is identical
		 * to [place]. Vendored downstream code (Box / Column / Row) calls
		 * `placeRelative` in places where Ltr/Rtl handling matters.
		 */
		fun Placeable.placeRelative(
			x: Int,
			y: Int,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
		) {
			val vX =
				if (parentLayoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr || parentWidth == 0) x
				else parentWidth - width - x
			placeAt(vX, y)
		}

		/** [placeRelative(x, y, zIndex)] but takes an [IntOffset] position. */
		fun Placeable.placeRelative(
			position: androidx.compose.ui.unit.IntOffset,
			zIndex: Float = 0f,
		) {
			placeRelative(position.x, position.y, zIndex)
		}

		/**
		 * Like [place] but accepts a `layerBlock` for graphics-layer effects
		 * (alpha, scale, rotation, etc.). This renderer doesn't apply the
		 * layer block during placement — graphicsLayer is read from a
		 * separate `GraphicsLayerModifier` on the ProjectLayoutNode — so the block
		 * is accepted-and-ignored. Vendored modifiers (Offset, etc.) call
		 * this expecting upstream's shape.
		 */
		fun Placeable.placeWithLayer(
			x: Int,
			y: Int,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
			@Suppress("UNUSED_PARAMETER")
			layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
		) {
			placeAt(x, y)
		}

		/** RTL-aware variant of [placeWithLayer]. */
		fun Placeable.placeRelativeWithLayer(
			x: Int,
			y: Int,
			@Suppress("UNUSED_PARAMETER") zIndex: Float = 0f,
			@Suppress("UNUSED_PARAMETER")
			layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
		) {
			val vX =
				if (parentLayoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr || parentWidth == 0) x
				else parentWidth - width - x
			placeAt(vX, y)
		}

		internal companion object Default : PlacementScope()
	}
}

// ==================
// MARK: PlacementScope factory
// ==================

/**
 * Upstream `internal fun PlacementScope(owner: Owner): Placeable.PlacementScope`.
 * Vendored `Owner.placementScope` calls this. Returns the default
 * PlacementScope companion for now — parentWidth = 0 disables RTL
 * mirroring which matches our project pipeline.
 */
internal fun PlacementScope(
	@Suppress("UNUSED_PARAMETER") owner: androidx.compose.ui.node.Owner,
): Placeable.PlacementScope = Placeable.PlacementScope.Default

internal class LayoutNodePlaceable(private val fNode: ProjectLayoutNode) : Placeable() {

	override val width: Int get() = fNode.width
	override val height: Int get() = fNode.height
	override val parentData: Any? get() = fNode.cachedParentData
	override fun placeAt(inX: Int, inY: Int) { fNode.place(inX, inY) }
}
