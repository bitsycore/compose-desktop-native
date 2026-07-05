package com.compose.desktop.native.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

// ==================
// MARK: Per-line style runs (renderer helper)
// ==================

/* Project render glue with no official Compose equivalent — the text renderers
   turn an AnnotatedString's spans into per-line style runs (colour + weight +
   italic). Lives in `com.compose.desktop.native.text` rather than
   `androidx.compose.ui.text`. */

/* A run of one colour + weight + italic within a wrapped line, line-local
   [start, end) cols. `weight` is the OpenType wght axis (100..900, 400 = default),
   `italic` = true when the span's FontStyle == Italic. Measurement is still base-
   style, so a bold run's glyphs paint into the same rect as regular's; that's a
   known simplification (real fidelity needs a re-wrap per style run). */
class ColorRun(
	val start: Int,
	val end: Int,
	val color: Color,
	val weight: Int = 400,
	val italic: Boolean = false,
)

/* Style runs for a single wrapped line, given the AnnotatedString spans (whose
   start/end index the ORIGINAL text) and this line's start offset. Gaps use
   the base style; overlapping spans compose colour last-wins, weight
   max-wins (bold beats regular), italic OR-wins.

   Spans are assumed sorted by start with non-decreasing end (true for the
   tokenizers and for buildAnnotatedString appended in order). That lets us
   binary-search to the first span reaching this line and stop at the first span
   past it, so a visible line of a 20k-line highlighted body touches only its
   own handful of spans instead of scanning the whole (tens-of-thousands) list
   every frame — the difference between smooth and janky scrolling. */
fun lineColorRuns(
	inLine: String,
	inLineStart: Int,
	inSpans: List<Range<SpanStyle>>,
	inDefault: Color,
): List<ColorRun> {
	val vN = inLine.length
	if (vN == 0) return emptyList()
	val vLineEnd = inLineStart + vN
	// Per-column style. Nullable arrays let us distinguish "explicit default" from
	// "no span touched me" — the coalesce step uses `inDefault` when a column stayed null.
	val vCols = arrayOfNulls<Color>(vN)
	val vWgt = IntArray(vN)
	val vIt = BooleanArray(vN)
	// First span whose end reaches into this line (skip the prefix before it).
	var vIdx = firstSpanReaching(inSpans, inLineStart)
	while (vIdx < inSpans.size) {
		val vS = inSpans[vIdx]
		// Sorted by start → once a span starts at/after the line end, so do all
		// the rest; nothing more can overlap.
		if (vS.start >= vLineEnd) break
		vIdx++
		val vColor = vS.item.color
		val vSpanWeight = vS.item.fontWeight?.weight ?: 0
		val vSpanItalic = vS.item.fontStyle == FontStyle.Italic
		if (vColor == Color.Unspecified && vSpanWeight == 0 && !vSpanItalic) continue
		val vA = vS.start - inLineStart
		val vB = vS.end - inLineStart
		if (vB <= 0 || vA >= vN) continue
		var i = if (vA < 0) 0 else vA
		val vEnd = if (vB > vN) vN else vB
		while (i < vEnd) {
			if (vColor != Color.Unspecified) vCols[i] = vColor
			if (vSpanWeight > vWgt[i]) vWgt[i] = vSpanWeight
			if (vSpanItalic) vIt[i] = true
			i++
		}
	}
	// Coalesce equal-style columns into runs. Two adjacent columns coalesce
	// only when every dimension matches.
	val vRuns = ArrayList<ColorRun>()
	var i = 0
	while (i < vN) {
		val vC = vCols[i] ?: inDefault
		val vW = if (vWgt[i] == 0) 400 else vWgt[i]
		val vI = vIt[i]
		var j = i + 1
		while (j < vN &&
			(vCols[j] ?: inDefault) == vC &&
			(if (vWgt[j] == 0) 400 else vWgt[j]) == vW &&
			vIt[j] == vI
		) j++
		vRuns.add(ColorRun(i, j, vC, vW, vI))
		i = j
	}
	return vRuns
}

/* Index of the first span whose end is past inLineStart — i.e. the first that
   can reach into a line starting there. Binary search; relies on spans being
   sorted with non-decreasing end (see lineColorRuns). */
private fun firstSpanReaching(inSpans: List<Range<SpanStyle>>, inLineStart: Int): Int {
	var vLo = 0
	var vHi = inSpans.size
	while (vLo < vHi) {
		val vMid = (vLo + vHi) ushr 1
		if (inSpans[vMid].end <= inLineStart) vLo = vMid + 1 else vHi = vMid
	}
	return vLo
}
