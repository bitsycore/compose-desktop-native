package androidx.compose.foundation.lazy.grid

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: GridCells
// ==================

/* Describes the cross-axis structure of a lazy grid. Public surface
   matches upstream Compose: an `interface` (not sealed class) with a
   single Density-receiver method `calculateCrossAxisCellSizes` returning
   the size of each cell. `Fixed`/`Adaptive`/`FixedSize` constructor
   params are private — callers can't read them back (matches upstream's
   "describe the grid via the method, not the data" design). */
@androidx.compose.runtime.Stable
interface GridCells {
	fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int>

	/* Fixed number of columns / rows, each taking 1/count of the parent
	   minus inter-cell spacing. */
	class Fixed(private val count: Int) : GridCells {
		init { require(count > 0) { "Provided count should be larger than zero" } }
		override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> =
			calculateCellsCrossAxisSizeImpl(availableSize, count, spacing)
		override fun hashCode(): Int = -count // different sign from Adaptive
		override fun equals(other: Any?): Boolean = other is Fixed && other.hashCode() == hashCode()
	}

	/* Pack as many columns as fit with each at least `minSize` wide;
	   extra pixels are distributed evenly among the cells. */
	class Adaptive(private val minSize: Dp) : GridCells {
		init { require(minSize > 0.dp) { "Provided min size should be larger than zero." } }
		override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
			val vCount = maxOf((availableSize + spacing) / (minSize.roundToPx() + spacing), 1)
			return calculateCellsCrossAxisSizeImpl(availableSize, vCount, spacing)
		}
		override fun hashCode(): Int = minSize.hashCode()
		override fun equals(other: Any?): Boolean = other is Adaptive && other.minSize == minSize
	}

	/* Pack as many cells of exactly `size` as fit, no stretching.
	   Remaining space is arranged via the lazy grid's arrangement. */
	class FixedSize(private val size: Dp) : GridCells {
		init { require(size > 0.dp) { "Provided size should be larger than zero." } }
		override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
			val vCell = size.roundToPx()
			return if (vCell + spacing < availableSize + spacing) {
				val vCount = (availableSize + spacing) / (vCell + spacing)
				List(vCount) { vCell }
			} else {
				List(1) { availableSize }
			}
		}
		override fun hashCode(): Int = size.hashCode()
		override fun equals(other: Any?): Boolean = other is FixedSize && other.size == size
	}
}

/* Distribute [gridSize - spacing*(slotCount-1)] across [slotCount] cells,
   handing the remainder pixels to the leading cells (matches upstream). */
private fun calculateCellsCrossAxisSizeImpl(
	gridSize: Int,
	slotCount: Int,
	spacing: Int,
): List<Int> {
	val vAvail = gridSize - spacing * (slotCount - 1)
	val vSlot = vAvail / slotCount
	val vRemainder = vAvail % slotCount
	return List(slotCount) { vSlot + if (it < vRemainder) 1 else 0 }
}

// ==================
// MARK: LazyGridScope DSL
// ==================

interface LazyGridScope {
	fun item(content: @Composable () -> Unit)
	fun items(count: Int, itemContent: @Composable (Int) -> Unit)
}

private class LazyGridScopeImpl : LazyGridScope {
	val composables: MutableList<@Composable () -> Unit> = mutableListOf()

	override fun item(content: @Composable () -> Unit) {
		composables.add(content)
	}
	override fun items(count: Int, itemContent: @Composable (Int) -> Unit) {
		for (vI in 0 until count) {
			val vIdx = vI
			composables.add { itemContent(vIdx) }
		}
	}
}

// ==================
// MARK: items / itemsIndexed extensions
// ==================

/* Top-level inline extensions matching upstream placement — LazyGridScope
   has only `item` / `items(count)` as interface members; List<T> overloads
   are extensions. */

inline fun <T> LazyGridScope.items(items: List<T>, crossinline itemContent: @Composable (T) -> Unit) {
	items(count = items.size) { i -> itemContent(items[i]) }
}

inline fun <T> LazyGridScope.itemsIndexed(items: List<T>, crossinline itemContent: @Composable (Int, T) -> Unit) {
	items(count = items.size) { i -> itemContent(i, items[i]) }
}

// ==================
// MARK: LazyVerticalGrid
// ==================

/* Grid laid out as rows of N items each, where N is decided by the
   `columns` argument. Like the lazy lists in this codebase, this isn't
   truly virtualised — every cell composes each frame, then
   verticalScroll clips. API-compatible with upstream so swapping in a
   virtualised version later is non-breaking.

   GridCells.Adaptive uses an onSizeChanged hook to read the parent width
   on first layout, then computes the column count from it. The first
   frame may render with the default (4-column) layout before settling. */
@Composable
fun LazyVerticalGrid(
	columns: GridCells,
	modifier: Modifier = Modifier,
	state: LazyListState = rememberLazyListState(),
	verticalArrangement: Arrangement.Vertical = Arrangement.Top,
	horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
	content: LazyGridScope.() -> Unit,
) {
	val vScope = LazyGridScopeImpl()
	vScope.content()
	val vItems = vScope.composables

	var vMeasuredWidth by remember { mutableStateOf(0) }
	// Compute cross-axis cell sizes via the GridCells interface. Renderer
	// runs in logical points so density = 1; cell spacing is folded into
	// the row arrangement (horizontalArrangement) separately.
	val vDensity: Density = remember { Density(1f) }
	val vAvail = if (vMeasuredWidth > 0) vMeasuredWidth else 480 // first-frame fallback
	val vCellSizes: List<Int> = with(columns) { vDensity.calculateCrossAxisCellSizes(vAvail, 0) }
	val vColumnCount: Int = vCellSizes.size.coerceAtLeast(1)

	Column(
		modifier = modifier
			.fillMaxWidth()
			.onSizeChanged { vMeasuredWidth = it.width }
			.verticalScroll(state.scrollState),
		verticalArrangement = verticalArrangement,
	) {
		val vRows = (vItems.size + vColumnCount - 1) / vColumnCount
		for (vR in 0 until vRows) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = horizontalArrangement,
			) {
				for (vC in 0 until vColumnCount) {
					val vIdx = vR * vColumnCount + vC
					if (vIdx < vItems.size) {
						val vFactory = vItems[vIdx]
						Box(modifier = Modifier.weight(1f)) { vFactory() }
					} else {
						// Pad the trailing row with empty weighted slots so
						// the existing items don't stretch to fill.
						Box(modifier = Modifier.weight(1f)) {}
					}
				}
			}
		}
	}
}
