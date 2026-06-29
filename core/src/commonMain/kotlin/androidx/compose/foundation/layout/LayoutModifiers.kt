package androidx.compose.foundation.layout

import androidx.compose.ui.*
import com.compose.desktop.native.element.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// ==================
// MARK: Layout Modifier Extensions
// ==================

// Padding extensions retired — provided verbatim by upstream
// `androidx.compose.foundation.layout.Padding.kt` (vendored).

// `Modifier.padding(PaddingValues)` + `Modifier.absolutePadding(...)` are
// provided by vendored upstream `Padding.kt`. Project versions retired.

// Size / Fill / widthIn / heightIn / sizeIn / requiredWidth / requiredHeight /
// requiredSize / defaultMinSize / fillMaxWidth / fillMaxHeight / fillMaxSize /
// wrapContentWidth / wrapContentHeight / wrapContentSize all retired here —
// provided verbatim by upstream
// `androidx.compose.foundation.layout.Size.kt` (vendored).

// Offset (post-layout visual nudge; doesn't change measured size)
fun Modifier.offset(x: Dp = 0.dp, y: Dp = 0.dp) =
    then(OffsetModifier(x.value.toInt(), y.value.toInt()))
