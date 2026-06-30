package androidx.compose.ui.text

// ==================
// MARK: CharHelpers — native actuals (naive)
// ==================

/**
 * Surrogate-pair-aware grapheme-cluster boundary iteration. Upstream's
 * skiko path uses ICU's BreakIterator; we approximate with single
 * surrogate-pair walks. Combining characters / emoji ZWJ sequences
 * collapse to one code point at a time — sufficient for cursor stepping
 * in BasicTextField's existing ASCII/BMP-heavy use cases.
 */
internal actual fun String.findPrecedingBreak(index: Int): Int {
	if (index <= 0) return -1
	val prev = index - 1
	if (prev > 0 && this[prev].isLowSurrogate() && this[prev - 1].isHighSurrogate()) {
		return prev - 1
	}
	return prev
}

internal actual fun String.findFollowingBreak(index: Int): Int {
	if (index >= length) return -1
	val next = index + 1
	if (this[index].isHighSurrogate() && next < length && this[next].isLowSurrogate()) {
		return next + 1
	}
	return next
}
