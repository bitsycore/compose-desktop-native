package com.compose.desktop.native.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle

// ==================
// MARK: Per-line colour runs (renderer helper)
// ==================

/* Project render glue with no official Compose equivalent — the text renderers
   turn an AnnotatedString's spans into per-line colour runs. Lives in the
   com.compose.desktop.native layer rather than androidx.compose.ui.text. */

/* A run of one colour within a wrapped line, in line-local [start, end) cols. */
class ColorRun(val start: Int, val end: Int, val color: Color)

/* Colour runs for a single wrapped line, given the AnnotatedString spans (whose
   start/end index the ORIGINAL text) and this line's start offset. Gaps and
   Unspecified span colours use inDefault; later spans win on overlap.

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
	// Per-column colour (null = default), painted by each overlapping span in
	// order so the last one wins.
	val vCols = arrayOfNulls<Color>(vN)
	// First span whose end reaches into this line (skip the prefix before it).
	var vIdx = firstSpanReaching(inSpans, inLineStart)
	while (vIdx < inSpans.size) {
		val vS = inSpans[vIdx]
		// Sorted by start → once a span starts at/after the line end, so do all
		// the rest; nothing more can overlap.
		if (vS.start >= vLineEnd) break
		vIdx++
		val vColor = vS.item.color
		if (vColor == Color.Unspecified) continue
		val vA = vS.start - inLineStart
		val vB = vS.end - inLineStart
		if (vB <= 0 || vA >= vN) continue
		var i = if (vA < 0) 0 else vA
		val vEnd = if (vB > vN) vN else vB
		while (i < vEnd) { vCols[i] = vColor; i++ }
	}
	// Coalesce equal-colour columns into runs.
	val vRuns = ArrayList<ColorRun>()
	var i = 0
	while (i < vN) {
		val vC = vCols[i] ?: inDefault
		var j = i + 1
		while (j < vN && (vCols[j] ?: inDefault) == vC) j++
		vRuns.add(ColorRun(i, j, vC))
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
