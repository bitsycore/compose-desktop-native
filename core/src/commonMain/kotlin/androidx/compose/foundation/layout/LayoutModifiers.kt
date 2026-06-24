package androidx.compose.foundation.layout

import androidx.compose.ui.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: Layout Modifier Extensions
// ==================

// Padding
fun Modifier.padding(all: Dp) = then(PaddingModifier(all.value.toInt(), all.value.toInt(), all.value.toInt(), all.value.toInt()))
fun Modifier.padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp) =
    then(PaddingModifier(horizontal.value.toInt(), vertical.value.toInt(), horizontal.value.toInt(), vertical.value.toInt()))
fun Modifier.padding(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp) =
    then(PaddingModifier(start.value.toInt(), top.value.toInt(), end.value.toInt(), bottom.value.toInt()))

// Size
fun Modifier.size(size: Dp) = then(SizeModifier(width = size.value.toInt(), height = size.value.toInt()))
fun Modifier.size(width: Dp, height: Dp) = then(SizeModifier(width = width.value.toInt(), height = height.value.toInt()))
fun Modifier.width(width: Dp) = then(SizeModifier(width = width.value.toInt()))
fun Modifier.height(height: Dp) = then(SizeModifier(height = height.value.toInt()))

// Fill
fun Modifier.fillMaxSize(fraction: Float = 1f) = then(SizeModifier(fillMaxWidth = true, fillMaxHeight = true))
fun Modifier.fillMaxWidth(fraction: Float = 1f) = then(SizeModifier(fillMaxWidth = true))
fun Modifier.fillMaxHeight(fraction: Float = 1f) = then(SizeModifier(fillMaxHeight = true))

// Offset (post-layout visual nudge; doesn't change measured size)
fun Modifier.offset(x: Dp = 0.dp, y: Dp = 0.dp) =
    then(OffsetModifier(x.value.toInt(), y.value.toInt()))

// Min/Max constraints
fun Modifier.requiredWidth(width: Dp) = then(SizeModifier(width = width.value.toInt()))
fun Modifier.requiredHeight(height: Dp) = then(SizeModifier(height = height.value.toInt()))
fun Modifier.defaultMinSize(minWidth: Dp = Dp.Unspecified, minHeight: Dp = Dp.Unspecified) =
    then(SizeModifier(
        minWidth = minWidth.value.toInt(),
        minHeight = minHeight.value.toInt(),
        isDefaultMin = true
    ))

/* Bound the width to [min, max] hard. Either bound can be Dp.Unspecified
   (Float.NaN) — comparing to it via == would never be true (NaN != NaN), so
   we check .value.isNaN() and map unspecified to -1 so the layout pass
   ignores that side. */
private fun Dp.toBound(): Int = if (value.isNaN()) -1 else value.toInt()

fun Modifier.widthIn(min: Dp = Dp.Unspecified, max: Dp = Dp.Unspecified) =
    then(SizeModifier(minWidth = min.toBound(), maxWidth = max.toBound()))

fun Modifier.heightIn(min: Dp = Dp.Unspecified, max: Dp = Dp.Unspecified) =
    then(SizeModifier(minHeight = min.toBound(), maxHeight = max.toBound()))

fun Modifier.sizeIn(
    minWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
) = then(SizeModifier(
    minWidth = minWidth.toBound(),
    minHeight = minHeight.toBound(),
    maxWidth = maxWidth.toBound(),
    maxHeight = maxHeight.toBound(),
))
