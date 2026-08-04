package androidx.compose.ui.text

import kotlin.experimental.ExperimentalNativeApi

// VENDOR-BASE: compose/ui/ui-text/src/skikoMain/kotlin/androidx/compose/ui/text/FontRasterizationSettings.skiko.kt @ v1.12.0-beta03+dev4483

// ==================
// MARK: FontRasterizationSettings native actual (Skia-free)
// ==================

/**
 * Rule-3 manual vendor of upstream `FontRasterizationSettings.skiko.kt` minus the
 * Skia-only `toSkFontEdging` / `toSkFontHinting` extensions (the skiko renderer
 * maps `PlatformDefault` to skiko enums itself, in SkiaParagraphEngine). It lives
 * in the skiko-FREE `nativeMain` because vendored `TextStyle.native.kt` references
 * `FontRasterizationSettings.PlatformDefault`, and `nativeMain` is the shared parent
 * of the official-skiko (mac/linux) and fork-skiko (mingw) legs — so it can't carry
 * skiko. `PlatformDefault` reproduces upstream's per-OS defaults VERBATIM (verified
 * against the pin): anti-aliased + subpixel everywhere, hinting Slight on Linux and
 * Normal on Windows/macOS (macOS ignores hinting). This split — and the hand port —
 * goes away if the port hosts the `Paragraph` actual in the skiko source set and
 * vendors upstream's skiko text files verbatim (PLAN.md §6).
 */

@ExperimentalTextApi
enum class FontSmoothing {
	None,
	AntiAlias,
	SubpixelAntiAlias;
}

@ExperimentalTextApi
enum class FontHinting {
	None,
	Slight,
	Normal,
	Full;
}

@ExperimentalTextApi
class FontRasterizationSettings(
	val smoothing: FontSmoothing,
	val hinting: FontHinting,
	val subpixelPositioning: Boolean,
	val autoHintingForced: Boolean,
) {
	companion object {
		@OptIn(ExperimentalNativeApi::class)
		val PlatformDefault: FontRasterizationSettings = FontRasterizationSettings(
			smoothing = FontSmoothing.AntiAlias,
			hinting = if (Platform.osFamily == OsFamily.LINUX) FontHinting.Slight else FontHinting.Normal,
			subpixelPositioning = true,
			autoHintingForced = false,
		)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is FontRasterizationSettings) return false
		return smoothing == other.smoothing &&
			hinting == other.hinting &&
			subpixelPositioning == other.subpixelPositioning &&
			autoHintingForced == other.autoHintingForced
	}

	override fun hashCode(): Int {
		var h = smoothing.hashCode()
		h = 31 * h + hinting.hashCode()
		h = 31 * h + subpixelPositioning.hashCode()
		h = 31 * h + autoHintingForced.hashCode()
		return h
	}

	override fun toString(): String =
		"FontRasterizationSettings(smoothing=$smoothing, hinting=$hinting, " +
			"subpixelPositioning=$subpixelPositioning, autoHintingForced=$autoHintingForced)"
}
