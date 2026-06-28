package androidx.compose.foundation.lazy

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// ==================
// MARK: LazyRow
// ==================

/* Horizontal counterpart of LazyColumn. Reuses LazyListScope so items()
   / item() / itemsIndexed() work identically. Like LazyColumn, this is
   not yet virtualised — every item composes each frame; horizontalScroll
   clips off-screen content. */
@Composable
fun LazyRow(
	modifier: Modifier = Modifier,
	state: LazyListState = rememberLazyListState(),
	horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
	verticalAlignment: Alignment.Vertical = Alignment.Top,
	content: LazyListScope.() -> Unit,
) {
	val vScope = LazyListScopeImplInternal()
	vScope.content()
	Row(
		modifier = modifier.horizontalScroll(state.scrollState),
		horizontalArrangement = horizontalArrangement,
		verticalAlignment = verticalAlignment,
	) {
		for (vFactory in vScope.composables) vFactory()
	}
}

/* Local copy of LazyListScopeImpl so LazyRow doesn't reach into
   LazyColumn's file-private impl. Same semantics. */
internal class LazyListScopeImplInternal : LazyListScope {
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
