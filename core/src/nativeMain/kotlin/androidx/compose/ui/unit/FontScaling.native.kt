package androidx.compose.ui.unit

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

// ==================
// MARK: FontScaling native actual
// ==================

/* Linear-scaling actual for the vendored `expect interface FontScaling`.
   Mirrors the non-Android upstream impl: TextUnit.toDp / Dp.toSp scale
   by `fontScale` directly — no font-scale curve lookup. */
@Immutable
actual interface FontScaling {

	@Stable actual val fontScale: Float

	@Stable
	actual fun Dp.toSp(): TextUnit = (value / fontScale).sp

	@Stable
	actual fun TextUnit.toDp(): Dp {
		check(type == TextUnitType.Sp) { "Only Sp can convert to Dp" }
		return Dp(value * fontScale)
	}
}
