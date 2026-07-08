package androidx.compose.foundation.lazy

import androidx.compose.runtime.Composable

// Native actual for the vendored LazyList.kt expect — matches every non-iOS platform (desktop /
// android / web all return 0): no extra beyond-bounds items composed past the visible window.
@Composable internal actual fun defaultLazyListBeyondBoundsItemCount(): Int = 0
