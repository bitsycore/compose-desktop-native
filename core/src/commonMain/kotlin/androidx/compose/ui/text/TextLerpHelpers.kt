package androidx.compose.ui.text

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.lerp

// ==================
// MARK: lerp helpers (extracted from upstream SpanStyle.kt internals)
// ==================

/* Internal helpers extracted from upstream's androidx.compose.ui.text
   SpanStyle.kt — TextIndent vendor needs them, but the rest of SpanStyle.kt
   (the whole interpolation engine) is engine-tied so we don't vendor the
   whole file. Kept package-internal here so the vendored TextIndent.kt
   compiles unchanged. */

internal fun lerpTextUnitInheritable(a: TextUnit, b: TextUnit, t: Float): TextUnit {
	if (a.isUnspecified || b.isUnspecified) return lerpDiscrete(a, b, t)
	return lerp(a, b, t)
}

/* Lerp between two values that cannot be transitioned. Returns [a] if
   [fraction] is smaller than 0.5 otherwise [b]. */
internal fun <T> lerpDiscrete(a: T, b: T, fraction: Float): T =
	if (fraction < 0.5) a else b
