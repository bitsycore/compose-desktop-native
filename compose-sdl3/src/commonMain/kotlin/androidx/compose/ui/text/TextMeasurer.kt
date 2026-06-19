package androidx.compose.ui.text

import androidx.compose.ui.unit.IntSize

// ==================
// MARK: TextMeasurer
// ==================

/* Shared abstraction so that the commonMain layout pass (TextMeasurePolicy) can
   get the same width/height that the native renderer will actually draw. The
   nativeMain backend installs an SDL3_ttf-backed implementation at startup.
   When unset, falls back to a rough 0.6*fontSize-per-char estimate. */
fun interface TextMeasurer
	{
	fun measure(inText: String, inFontSize: Int): IntSize
	}

// ==================
// MARK: Default fallback
// ==================

private val kFallbackTextMeasurer = TextMeasurer { inText, inFontSize ->
	val vCharW = (inFontSize * 0.6f).toInt().coerceAtLeast(1)
	IntSize(vCharW * inText.length, (inFontSize * 1.3f).toInt())
	}

var currentTextMeasurer: TextMeasurer = kFallbackTextMeasurer
