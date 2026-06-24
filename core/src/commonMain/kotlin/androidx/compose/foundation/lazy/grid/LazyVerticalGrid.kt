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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: GridCells
// ==================

/* Describes the column structure of a LazyVerticalGrid. Fixed(n) makes
   N equally-sized columns. Adaptive(minSize) computes the number of
   columns from the parent width as floor(width / minSize). */
sealed class GridCells {
	class Fixed(val count: Int) : GridCells()
	class Adaptive(val minSize: Dp) : GridCells()
}

// ==================
// MARK: LazyGridScope DSL
// ==================

interface LazyGridScope {
	fun item(content: @Composable () -> Unit)
	fun items(count: Int, itemContent: @Composable (Int) -> Unit)
	fun <T> items(items: List<T>, itemContent: @Composable (T) -> Unit)
	fun <T> itemsIndexed(items: List<T>, itemContent: @Composable (Int, T) -> Unit)
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
	override fun <T> items(items: List<T>, itemContent: @Composable (T) -> Unit) {
		for (vIt in items) composables.add { itemContent(vIt) }
	}
	override fun <T> itemsIndexed(items: List<T>, itemContent: @Composable (Int, T) -> Unit) {
		for ((vI, vV) in items.withIndex()) {
			val vIdx = vI
			val vVal = vV
			composables.add { itemContent(vIdx, vVal) }
		}
	}
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
	val vColumnCount: Int = when (columns) {
		is GridCells.Fixed    -> columns.count.coerceAtLeast(1)
		is GridCells.Adaptive -> {
			val vMin = columns.minSize.value.toInt().coerceAtLeast(1)
			if (vMeasuredWidth <= 0) 4 // first-frame fallback
			else (vMeasuredWidth / vMin).coerceAtLeast(1)
		}
	}

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
