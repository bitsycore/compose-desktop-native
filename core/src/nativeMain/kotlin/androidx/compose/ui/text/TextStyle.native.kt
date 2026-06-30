@file:OptIn(ExperimentalTextApi::class)

package androidx.compose.ui.text

/*
 * Hand-written port of upstream `TextStyle.skiko.kt`. Mirrors the skiko
 * actuals byte-for-byte (PlatformTextStyle / PlatformParagraphStyle /
 * PlatformSpanStyle + their `lerp` extensions) but reaches into the
 * hand-written `FontRasterizationSettings.native.kt` (Skia-free), so it
 * compiles for both the Skia AND the SDL3 renderer builds.
 */

actual class PlatformTextStyle {
	actual val spanStyle: PlatformSpanStyle?
	actual val paragraphStyle: PlatformParagraphStyle?

	constructor(spanStyle: PlatformSpanStyle?, paragraphStyle: PlatformParagraphStyle?) {
		this.spanStyle = spanStyle
		this.paragraphStyle = paragraphStyle
	}

	@ExperimentalTextApi
	constructor(textDecorationLineStyle: TextDecorationLineStyle?) : this(
		spanStyle = PlatformSpanStyle(textDecorationLineStyle),
		paragraphStyle = null,
	)

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is PlatformTextStyle) return false
		return paragraphStyle == other.paragraphStyle && spanStyle == other.spanStyle
	}

	override fun hashCode(): Int {
		var r = spanStyle?.hashCode() ?: 0
		r = 31 * r + (paragraphStyle?.hashCode() ?: 0)
		return r
	}
}

internal actual fun createPlatformTextStyle(
	spanStyle: PlatformSpanStyle?,
	paragraphStyle: PlatformParagraphStyle?,
): PlatformTextStyle = PlatformTextStyle(spanStyle, paragraphStyle)

actual class PlatformParagraphStyle {
	actual companion object {
		actual val Default: PlatformParagraphStyle = PlatformParagraphStyle()
	}

	@ExperimentalTextApi
	val fontRasterizationSettings: FontRasterizationSettings

	constructor() {
		this.fontRasterizationSettings = FontRasterizationSettings.PlatformDefault
	}

	@ExperimentalTextApi
	constructor(fontRasterizationSettings: FontRasterizationSettings = FontRasterizationSettings.PlatformDefault) {
		this.fontRasterizationSettings = fontRasterizationSettings
	}

	actual fun merge(other: PlatformParagraphStyle?): PlatformParagraphStyle = other ?: this

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is PlatformParagraphStyle) return false
		return fontRasterizationSettings == other.fontRasterizationSettings
	}

	override fun hashCode(): Int = fontRasterizationSettings.hashCode()
}

actual class PlatformSpanStyle @ExperimentalTextApi constructor(
	val textDecorationLineStyle: TextDecorationLineStyle?,
) {

	constructor() : this(textDecorationLineStyle = null)

	actual companion object {
		actual val Default: PlatformSpanStyle = PlatformSpanStyle()
	}

	actual fun merge(other: PlatformSpanStyle?): PlatformSpanStyle = this

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is PlatformSpanStyle) return false
		return textDecorationLineStyle == other.textDecorationLineStyle
	}

	override fun hashCode(): Int = textDecorationLineStyle.hashCode()
}

actual fun lerp(
	start: PlatformParagraphStyle,
	stop: PlatformParagraphStyle,
	fraction: Float,
): PlatformParagraphStyle = start

actual fun lerp(
	start: PlatformSpanStyle,
	stop: PlatformSpanStyle,
	fraction: Float,
): PlatformSpanStyle {
	if (start.textDecorationLineStyle == stop.textDecorationLineStyle) return start
	return PlatformSpanStyle(
		textDecorationLineStyle = lerpDiscrete(
			start.textDecorationLineStyle,
			stop.textDecorationLineStyle,
			fraction,
		),
	)
}
