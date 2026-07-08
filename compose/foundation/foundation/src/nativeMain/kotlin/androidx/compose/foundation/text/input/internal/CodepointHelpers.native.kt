package androidx.compose.foundation.text.input.internal

// ==================
// MARK: CodepointHelpers — native actuals
// ==================

internal actual fun CharSequence.codePointAt(index: Int): Int {
	val high = this[index]
	if (high.isHighSurrogate() && index + 1 < length) {
		val low = this[index + 1]
		if (low.isLowSurrogate()) {
			return (((high - Char.MIN_HIGH_SURROGATE) shl 10) or (low - Char.MIN_LOW_SURROGATE)) + 0x10000
		}
	}
	return high.code
}

internal actual fun charCount(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

internal actual fun CharSequence.codePointBefore(index: Int): Int {
	val low = this[index - 1]
	if (low.isLowSurrogate() && index - 2 >= 0) {
		val high = this[index - 2]
		if (high.isHighSurrogate()) {
			return (((high - Char.MIN_HIGH_SURROGATE) shl 10) or (low - Char.MIN_LOW_SURROGATE)) + 0x10000
		}
	}
	return low.code
}
