package androidx.compose.ui.text

import kotlin.experimental.ExperimentalNativeApi

// ==================
// MARK: FontRasterizationSettings native actual (Skia-free)
// ==================

/**
 * Hand-written port of upstream `FontRasterizationSettings.skiko.kt` minus the
 * Skia-only `toSkFontEdging` / `toSkFontHinting` extensions (the skiko renderer
 * maps `PlatformDefault` to skiko enums itself, in SkiaParagraphEngine).
 * `PlatformDefault` mirrors upstream's per-OS defaults: anti-aliased + subpixel
 * everywhere, hinting Slight on Linux and Normal on Windows/macOS (macOS
 * ignores hinting). Vendored TextStyle.native.kt also references
 * `FontRasterizationSettings.PlatformDefault`.
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
