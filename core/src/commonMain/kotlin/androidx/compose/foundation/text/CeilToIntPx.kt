package androidx.compose.foundation.text

import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.ceil

// ==================
// MARK: ceilToIntPx / DefaultMinLines
// ==================

// Extracted from upstream foundation.text.TextDelegate.kt + HeightInLinesModifier.kt
// (both unvendored — they pull the whole FontFamily.Resolver stack we don't have).
// A handful of vendored foundation.text.modifiers files need just these two helpers,
// so we ship a project-side extract byte-identical to the upstream one-liners.
// If TextDelegate + HeightInLinesModifier ever vendor, delete this file.
internal fun Float.ceilToIntPx(): Int = ceil(this).fastRoundToInt()

internal const val DefaultMinLines = 1
