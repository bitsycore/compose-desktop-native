package androidx.compose.foundation.layout

import androidx.compose.ui.*
import androidx.compose.ui.unit.Dp

// ==================
// MARK: Layout Modifier Extensions
// ==================

// Padding
fun Modifier.padding(all: Dp) = then(PaddingModifier(all.value.toInt(), all.value.toInt(), all.value.toInt(), all.value.toInt()))
fun Modifier.padding(horizontal: Dp = Dp.Zero, vertical: Dp = Dp.Zero) =
    then(PaddingModifier(horizontal.value.toInt(), vertical.value.toInt(), horizontal.value.toInt(), vertical.value.toInt()))
fun Modifier.padding(start: Dp = Dp.Zero, top: Dp = Dp.Zero, end: Dp = Dp.Zero, bottom: Dp = Dp.Zero) =
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
fun Modifier.offset(x: Dp = Dp.Zero, y: Dp = Dp.Zero) =
    then(OffsetModifier(x.value.toInt(), y.value.toInt()))

// Min/Max constraints
fun Modifier.requiredWidth(width: Dp) = then(SizeModifier(width = width.value.toInt()))
fun Modifier.requiredHeight(height: Dp) = then(SizeModifier(height = height.value.toInt()))
fun Modifier.defaultMinSize(minWidth: Dp = Dp.Zero, minHeight: Dp = Dp.Zero) =
    then(SizeModifier(
        minWidth = minWidth.value.toInt(),
        minHeight = minHeight.value.toInt(),
        isDefaultMin = true
    ))
