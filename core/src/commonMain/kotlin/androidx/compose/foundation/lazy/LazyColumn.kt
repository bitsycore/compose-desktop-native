package androidx.compose.foundation.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// ==================
// MARK: LazyListState
// ==================

/* Thin wrapper around ScrollState that exposes the scroll offset under a
   "lazy"-flavoured API. Upstream Compose tracks
   firstVisibleItemIndex/firstVisibleItemScrollOffset + suspend
   scrollToItem(index)/animateScrollToItem(index) — item-based; ours is
   pixel-based since our LazyColumn doesn't actually virtualise yet.
   Members are named with the `Px` suffix to flag the project-only
   pixel-based design — keeping upstream's names (`value`/`scrollBy`/
   `scrollTo`) would collide with their item-based / suspend signatures. */
class LazyListState(initialFirstVisibleItemIndex: Int = 0) {
    internal val scrollState: ScrollState = ScrollState()

    val scrollOffsetPx: Int get() = scrollState.value
    val maxScrollOffsetPx: Int get() = scrollState.maxValue

    fun scrollByPx(inDelta: Int) = scrollState.scrollByPx(inDelta)
    fun scrollToPx(inPosition: Int) = scrollState.scrollToPx(inPosition)
}

@Composable
fun rememberLazyListState(): LazyListState = remember { LazyListState() }

// ==================
// MARK: LazyListScope DSL
// ==================

interface LazyListScope {
    /* Insert a single item at the current position. */
    fun item(content: @Composable () -> Unit)

    /* Insert `count` items, calling content(index) for each. */
    fun items(count: Int, itemContent: @Composable (Int) -> Unit)

    /* Insert one item per element in the list. */
    fun <T> items(items: List<T>, itemContent: @Composable (T) -> Unit)

    /* Insert one item per element, exposing the index alongside the value. */
    fun <T> itemsIndexed(items: List<T>, itemContent: @Composable (Int, T) -> Unit)
}

private class LazyListScopeImpl : LazyListScope {
    val composables: MutableList<@Composable () -> Unit> = mutableListOf()

    override fun item(content: @Composable () -> Unit) {
        composables.add(content)
    }

    override fun items(count: Int, itemContent: @Composable (Int) -> Unit) {
        for (i in 0 until count) {
            val idx = i
            composables.add { itemContent(idx) }
        }
    }

    override fun <T> items(items: List<T>, itemContent: @Composable (T) -> Unit) {
        for (item in items) {
            composables.add { itemContent(item) }
        }
    }

    override fun <T> itemsIndexed(items: List<T>, itemContent: @Composable (Int, T) -> Unit) {
        for ((i, item) in items.withIndex()) {
            val idx = i
            val v = item
            composables.add { itemContent(idx, v) }
        }
    }
}

// ==================
// MARK: LazyColumn
// ==================

/* Compose-shaped lazy list API. NOTE: this implementation isn't yet truly
   virtualised — every item is composed each frame, then the surrounding
   verticalScroll clips off-screen rows. Fine for "tens-to-low-hundreds of
   simple items"; for large lists or expensive items we need a custom
   MeasurePolicy that only composes the visible window (a SubcomposeLayout-
   equivalent). API stays compatible when virtualisation lands. */
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    val scope = LazyListScopeImpl()
    scope.content()
    Column(
        modifier = modifier.verticalScroll(state.scrollState),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
    ) {
        for (factory in scope.composables) {
            factory()
        }
    }
}
