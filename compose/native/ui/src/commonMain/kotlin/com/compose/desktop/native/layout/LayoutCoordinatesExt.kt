package com.compose.desktop.native.layout

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset

// ==================
// MARK: LayoutCoordinates project sugar (non-official)
// ==================

/** Logical-point X of this layout in the window. Project-only sugar that
   reads `positionInWindow().x` and rounds to Int — matches the project's
   old `Modifier.onGloballyPositioned: (IntOffset) -> Unit` payload. */
val LayoutCoordinates.x: Int get() = positionInWindow().x.toInt()

/** Logical-point Y of this layout in the window. */
val LayoutCoordinates.y: Int get() = positionInWindow().y.toInt()

/** Bundle [x] / [y] as a [IntOffset]. Used by call sites that store the
   absolute position into an `IntOffset` field (e.g. Material's
   `MenuAnchorState.position` and Tooltip's `vPos`). */
val LayoutCoordinates.intOffset: IntOffset get() = IntOffset(x, y)
