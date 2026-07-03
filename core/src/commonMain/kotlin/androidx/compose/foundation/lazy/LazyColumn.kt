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
   "lazy"-flavoured API. Upstream Compose's LazyListState is item-based
   (firstVisibleItemIndex/firstVisibleItemScrollOffset + suspend
   scrollToItem) — ours is pixel-based since our LazyColumn doesn't yet
   virtualise. The project-only pixel-based accessors
   (`scrollOffsetPx`/`maxScrollOffsetPx`) live as extensions in
   com.compose.desktop.native.foundation.lazy so the upstream-named class
   here stays close to a pure upstream-matching surface. */
class LazyListState(initialFirstVisibleItemIndex: Int = 0) {
    // Internal — the project bridge to ScrollState; module-local. Upstream
    // LazyListState doesn't expose this field, so keep it off the public ABI.
    internal val scrollState: ScrollState = ScrollState(0)

    /* Bypass-the-mutex push of a pixel delta. Returns the delta actually
       consumed after clamping at edges. Matches ScrollableState's same-named
       method (LazyListState's upstream design doesn't expose this directly,
       but we need a non-suspend mutation entry for the renderer's scroll
       modifier to drive). */
    fun dispatchRawDelta(delta: Float): Float = scrollState.dispatchRawDelta(delta)
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
}

// ==================
// MARK: items / itemsIndexed extensions
// ==================

/* Top-level inline extensions matching upstream placement (LazyListScope
   has only `item` / `items(count)` as interface members; List<T> /
   Array<T> overloads are extensions that fold to `items(count) { i ->
   content(items[i]) }`). */

inline fun <T> LazyListScope.items(items: List<T>, crossinline itemContent: @Composable (T) -> Unit) {
    items(count = items.size) { i -> itemContent(items[i]) }
}

inline fun <T> LazyListScope.itemsIndexed(items: List<T>, crossinline itemContent: @Composable (Int, T) -> Unit) {
    items(count = items.size) { i -> itemContent(i, items[i]) }
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
