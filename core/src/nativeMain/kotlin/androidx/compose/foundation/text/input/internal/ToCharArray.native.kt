package androidx.compose.foundation.text.input.internal

// ==================
// MARK: CharSequence.toCharArray — native actual
// ==================

internal actual fun CharSequence.toCharArray(
	destination: CharArray,
	destinationOffset: Int,
	startIndex: Int,
	endIndex: Int,
) {
	for (i in startIndex until endIndex) {
		destination[destinationOffset + (i - startIndex)] = this[i]
	}
}
