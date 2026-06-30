package androidx.compose.ui.text.font

// Native actual for the `internal expect fun FontSynthesis.synthesizeTypeface`
// declared in vendored commonMain `FontSynthesis.kt`. The project doesn't
// have a typeface-loading pipeline yet, so we return the passed-in
// `typeface` unchanged — no actual font synthesis is performed.
internal actual fun FontSynthesis.synthesizeTypeface(
	typeface: Any,
	font: Font,
	requestedWeight: FontWeight,
	requestedStyle: FontStyle,
): Any = typeface
