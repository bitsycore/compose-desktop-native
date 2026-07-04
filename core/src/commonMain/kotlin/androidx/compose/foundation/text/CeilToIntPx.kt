package androidx.compose.foundation.text

import androidx.compose.foundation.internal.requirePrecondition
import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.ceil

// ==================
// MARK: ceilToIntPx / DefaultMinLines / validateMinMaxLines
// ==================

// Extracted from upstream foundation.text.TextDelegate.kt + HeightInLinesModifier.kt
// (both unvendored — they pull the whole FontFamily.Resolver stack we don't have).
// A handful of vendored foundation.text.modifiers files need just these helpers,
// so we ship a project-side extract byte-identical to the upstream one-liners.
// TODO: delete when TextDelegate + HeightInLinesModifier can vendor.
internal fun Float.ceilToIntPx(): Int = ceil(this).fastRoundToInt()

internal const val DefaultMinLines = 1

/** Validates the min/max lines args to a text call. */
internal fun validateMinMaxLines(minLines: Int, maxLines: Int) {
	requirePrecondition(minLines > 0 && maxLines > 0) {
		"both minLines $minLines and maxLines $maxLines must be greater than zero"
	}
	requirePrecondition(minLines <= maxLines) {
		"minLines $minLines must be less than or equal to maxLines $maxLines"
	}
}
