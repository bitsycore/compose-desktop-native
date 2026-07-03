package com.compose.desktop.native.foundation

import androidx.compose.foundation.lazy.LazyListState

// ==================
// MARK: Project-only Scroll helpers
// ==================

/* Non-upstream pixel accessors on LazyListState. Kept out of the
   androidx.compose.foundation namespace so that package stays a pure mirror
   of upstream. Mouse-wheel scrolling is now handled by the vendored upstream
   Modifier.scrollable (MouseWheelScrollingLogic) via the pointer processor,
   so the project's non-suspend smoothScrollByPx entry is retired. */

// ============
//  LazyListState pixel-based accessors
//
// Upstream's LazyListState is item-based (firstVisibleItemIndex /
// firstVisibleItemScrollOffset + suspend scrollToItem). Our impl is a
// thin pixel-based wrapper around a ScrollState, so these extensions
// expose the underlying offsets without inflating the upstream-named
// class's public surface.

val LazyListState.scrollOffsetPx: Int get() = scrollState.value
val LazyListState.maxScrollOffsetPx: Int get() = scrollState.maxValue
