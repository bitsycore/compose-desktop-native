package androidx.compose.ui.text

// ==================
// MARK: FontRasterizationSettings native actual (Skia-free)
// ==================

/**
 * Hand-written port of upstream `FontRasterizationSettings.skiko.kt` minus
 * the Skia-only `toSkFontEdging` / `toSkFontHinting` extensions and the
 * `currentPlatform()` lookup. Vendored TextStyle.native.kt references
 * `FontRasterizationSettings.PlatformDefault` so we need the class +
 * companion to exist; the renderer doesn't read these values yet so a
 * single shared "PlatformDefault" instance (AntiAlias / Normal /
 * subpixel-on / autohint-off — matches upstream's macOS/Linux default)
 * is fine.
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
		val PlatformDefault: FontRasterizationSettings = FontRasterizationSettings(
			smoothing = FontSmoothing.AntiAlias,
			hinting = FontHinting.Normal,
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
