package androidx.compose.ui.text.style

// ==================
// MARK: TextDecoration
// ==================

/* Underline / line-through bitmask (matches upstream's API shape).
   Combined via .plus() so callers write `Underline + LineThrough`. */
class TextDecoration internal constructor(val mask: Int) {

	operator fun plus(inOther: TextDecoration): TextDecoration =
		TextDecoration(mask or inOther.mask)

	operator fun contains(inOther: TextDecoration): Boolean =
		(mask and inOther.mask) == inOther.mask

	override fun equals(other: Any?): Boolean = other is TextDecoration && other.mask == mask
	override fun hashCode(): Int = mask

	companion object {
		val None        = TextDecoration(0)
		val Underline   = TextDecoration(1)
		val LineThrough = TextDecoration(2)
	}
}

// TextOverflow lives in its own vendored file (androidx.compose.ui.text.style.TextOverflow).
