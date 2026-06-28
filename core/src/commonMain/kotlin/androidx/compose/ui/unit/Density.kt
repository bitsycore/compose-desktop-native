package androidx.compose.ui.unit

// ==================
// MARK: Density (reduced)
// ==================

/* Reduced reimplementation of androidx.compose.ui.unit.Density. Upstream
   models it as `interface Density : FontScaling` where `FontScaling` is an
   `expect interface` with no nonJvm actual upstream — implementing the full
   surface would require pulling FontScaling + JvmDefaultWithCompatibility +
   per-target actuals, none of which would be exercised by this build. We
   keep the official two `val` fields + the official factory function so
   call sites match upstream verbatim, and supply linear `toPx` / `toDp` /
   `toSp` helpers for layout code. Listed in CLAUDE.md fidelity-cheat-sheet
   as a documented reduced impl. */
interface Density {
	val density: Float
	val fontScale: Float

	fun Dp.toPx(): Float = value * density
	fun Dp.roundToPx(): Int = (value * density + 0.5f).toInt()
	fun Dp.toSp(): TextUnit = (value / fontScale).sp
	fun TextUnit.toDp(): Dp {
		check(type == TextUnitType.Sp) { "Only Sp can convert to Dp" }
		return Dp(value * fontScale)
	}
	fun TextUnit.toPx(): Float = when (type) {
		TextUnitType.Sp -> value * fontScale * density
		else -> value * density
	}
	fun Int.toDp(): Dp = (this / density).dp
	fun Float.toDp(): Dp = (this / density).dp
}

fun Density(density: Float, fontScale: Float = 1f): Density = DensityImpl(density, fontScale)

private data class DensityImpl(
	override val density: Float,
	override val fontScale: Float,
) : Density
