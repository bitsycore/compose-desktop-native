package com.compose.sdl.graphics

import androidx.compose.ui.graphics.Color

/** 8-bit channel accessors used by the SDL3 and Skia draw scopes to feed
 *  colours into SDL_SetRenderDrawColor / SkPaint. Renderer-internal — not
 *  part of any app-facing surface. */

internal val Color.r8: Int get() = (red * 255f).toInt().coerceIn(0, 255)
internal val Color.g8: Int get() = (green * 255f).toInt().coerceIn(0, 255)
internal val Color.b8: Int get() = (blue * 255f).toInt().coerceIn(0, 255)
internal val Color.a8: Int get() = (alpha * 255f).toInt().coerceIn(0, 255)
