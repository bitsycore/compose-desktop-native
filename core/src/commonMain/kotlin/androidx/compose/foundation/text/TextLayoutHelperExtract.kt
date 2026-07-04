package androidx.compose.foundation.text

import androidx.compose.ui.text.TextLayoutResult

// ==================
// MARK: TextLayoutResult.getLineHeight — extract
// ==================

/*
 Extracted from upstream foundation.text.TextLayoutHelper.kt. The full file has
 a `canReuse(...)` extension that needs `FontFamily.Resolver` (unvendored — the
 font-family resolver stack routes through platform typeface loaders we don't
 host). MultiWidgetSelectionDelegate only needs `getLineHeight`, so this is a
 project-side extract byte-identical to the upstream one-liner. Delete when
 TextLayoutHelper.kt can vendor cleanly.
*/
internal fun TextLayoutResult.getLineHeight(offset: Int): Float {
	if (offset < 0 || layoutInput.text.isEmpty()) return 0f

	val line =
		minOf(
			multiParagraph.getLineForOffset(offset),
			multiParagraph.maxLines - 1,
			multiParagraph.lineCount - 1,
		)
	val lineEnd = multiParagraph.getLineEnd(line)
	if (offset > lineEnd) return 0f

	return multiParagraph.getLineHeight(line)
}
