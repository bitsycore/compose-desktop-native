package com.compose.sdl.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import kotlin.math.roundToInt

// ==================
// MARK: Per-line style runs (renderer helper)
// ==================

/* Project render glue with no official Compose equivalent — the text renderers
   turn an AnnotatedString's spans into per-line style runs (colour + weight +
   italic + background + decoration + size). Lives in
   `com.compose.sdl.text` rather than `androidx.compose.ui.text`. */

/* A run of one style within a wrapped line, line-local [start, end) cols.
   `weight` is the OpenType wght axis (100..900, 400 = default), `italic` =
   FontStyle.Italic (paragraph base or span), `background` = SpanStyle.background
   (Unspecified = none), `underline`/`lineThrough` = TextDecoration flags
   (paragraph base or span), `fontSize` = the span's size (Unspecified = the
   paragraph size; Em scales it, Sp resolves through the density).

   Measurement/wrapping is still base-style, so a bold/resized run's glyphs
   paint into a line box wrapped at base metrics; that's a known simplification
   (real fidelity needs a re-wrap per style run). Paint-time advances DO use
   the run's own style, so runs push each other over correctly. */
class ColorRun(
	val start: Int,
	val end: Int,
	val color: Color,
	val weight: Int = 400,
	val italic: Boolean = false,
	val background: Color = Color.Unspecified,
	val underline: Boolean = false,
	val lineThrough: Boolean = false,
	val fontSize: TextUnit = TextUnit.Unspecified,
)

/* Style runs for a single wrapped line, given the AnnotatedString spans (whose
   start/end index the ORIGINAL text) and this line's start offset. Gaps use
   the base style; overlapping spans compose colour/background/size last-wins,
   weight max-wins (bold beats regular), italic/underline/lineThrough OR-wins
   (also OR'd with the paragraph-level base flags — a span's explicit
   TextDecoration.None can't clear a base decoration; simplification).

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
	inBaseItalic: Boolean = false,
	inBaseUnderline: Boolean = false,
	inBaseLineThrough: Boolean = false,
): List<ColorRun> {
	val vN = inLine.length
	if (vN == 0) return emptyList()
	val vLineEnd = inLineStart + vN
	// Per-column style. Nullable arrays let us distinguish "explicit default" from
	// "no span touched me" — the coalesce step uses `inDefault` when a column stayed null.
	val vCols = arrayOfNulls<Color>(vN)
	val vWgt = IntArray(vN)
	val vIt = BooleanArray(vN)
	val vBg = arrayOfNulls<Color>(vN)
	val vUl = BooleanArray(vN)
	val vSt = BooleanArray(vN)
	val vSz = arrayOfNulls<TextUnit>(vN)
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
		val vSpanBg = vS.item.background
		val vDeco = vS.item.textDecoration
		val vSpanUl = vDeco != null && vDeco.contains(TextDecoration.Underline)
		val vSpanSt = vDeco != null && vDeco.contains(TextDecoration.LineThrough)
		val vSpanSz = vS.item.fontSize
		if (vColor == Color.Unspecified && vSpanWeight == 0 && !vSpanItalic &&
			vSpanBg == Color.Unspecified && !vSpanUl && !vSpanSt && vSpanSz == TextUnit.Unspecified
		) continue
		val vA = vS.start - inLineStart
		val vB = vS.end - inLineStart
		if (vB <= 0 || vA >= vN) continue
		var i = if (vA < 0) 0 else vA
		val vEnd = if (vB > vN) vN else vB
		while (i < vEnd) {
			if (vColor != Color.Unspecified) vCols[i] = vColor
			if (vSpanWeight > vWgt[i]) vWgt[i] = vSpanWeight
			if (vSpanItalic) vIt[i] = true
			if (vSpanBg != Color.Unspecified) vBg[i] = vSpanBg
			if (vSpanUl) vUl[i] = true
			if (vSpanSt) vSt[i] = true
			if (vSpanSz != TextUnit.Unspecified) vSz[i] = vSpanSz
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
		val vI = vIt[i] || inBaseItalic
		val vB = vBg[i] ?: Color.Unspecified
		val vU = vUl[i] || inBaseUnderline
		val vS2 = vSt[i] || inBaseLineThrough
		val vZ = vSz[i] ?: TextUnit.Unspecified
		var j = i + 1
		while (j < vN &&
			(vCols[j] ?: inDefault) == vC &&
			(if (vWgt[j] == 0) 400 else vWgt[j]) == vW &&
			(vIt[j] || inBaseItalic) == vI &&
			(vBg[j] ?: Color.Unspecified) == vB &&
			(vUl[j] || inBaseUnderline) == vU &&
			(vSt[j] || inBaseLineThrough) == vS2 &&
			(vSz[j] ?: TextUnit.Unspecified) == vZ
		) j++
		vRuns.add(ColorRun(i, j, vC, vW, vI, vB, vU, vS2, vZ))
		i = j
	}
	return vRuns
}

// ==================
// MARK: Style-aware metrics helpers
// ==================
// Shared by BOTH the renderers (paint-time advances) and SdlParagraph
// (layout-time widths/heights) so painted glyph runs land exactly inside the
// measured box — per-run fontSize / fontWeight change metrics, not just paint.

/* Resolves a run's SpanStyle.fontSize to pixels: Em scales the paragraph's
   base size, Sp resolves through the density the base size was resolved with,
   Unspecified inherits the base. */
fun resolveRunPx(inRun: ColorRun, inBasePx: Int, inDensity: Float): Int = when (inRun.fontSize.type) {
	TextUnitType.Em -> (inBasePx * inRun.fontSize.value).roundToInt().coerceAtLeast(1)
	TextUnitType.Sp -> (inRun.fontSize.value * inDensity).roundToInt().coerceAtLeast(1)
	else            -> inBasePx
}

/* A run's font axes: its own weight when set (400 = "no run weight"), else
   the paragraph's base axes. */
fun runVariations(
	inRun: ColorRun,
	inBaseVariations: List<FontVariation.Setting>?,
): List<FontVariation.Setting>? =
	if (inRun.weight != 400) listOf(FontVariation.weight(inRun.weight)) else inBaseVariations

/* Whether any span carries a metric-affecting style (size / weight) — the
   gate for the styled measurement paths, so plain/colour-only text keeps the
   cheap single-measure route. */
fun spansAffectMetrics(inSpans: List<Range<SpanStyle>>?): Boolean =
	inSpans != null && inSpans.any {
		it.item.fontSize != TextUnit.Unspecified || it.item.fontWeight != null
	}

/* Width of a text slice, span-aware: sums each style run's advance at its
   resolved size/weight through [inMeasurer]. `inGlobalStart` maps the slice
   into the spans' index space. */
fun styledSliceWidth(
	inSlice: String,
	inGlobalStart: Int,
	inSpans: List<Range<SpanStyle>>,
	inBasePx: Int,
	inDensity: Float,
	inMeasurer: TextMeasurer,
	inFontFamily: String?,
	inBaseVariations: List<FontVariation.Setting>?,
): Float {
	if (inSlice.isEmpty()) return 0f
	var vW = 0f
	for (vRun in lineColorRuns(inSlice, inGlobalStart, inSpans, Color.Unspecified)) {
		val vPx = resolveRunPx(vRun, inBasePx, inDensity)
		val vVars = runVariations(vRun, inBaseVariations)
		vW += inMeasurer.measure(
			inSlice.substring(vRun.start, vRun.end), vPx, Int.MAX_VALUE, inFontFamily, vVars,
		).width
	}
	return vW
}

/* Tallest run cell height on a line — the line's box height when spans carry
   their own sizes; the base cell height when they don't (or the line is empty). */
fun styledLineCellHeight(
	inLine: String,
	inGlobalStart: Int,
	inSpans: List<Range<SpanStyle>>,
	inBasePx: Int,
	inDensity: Float,
	inMeasurer: TextMeasurer,
	inFontFamily: String?,
	inBaseVariations: List<FontVariation.Setting>?,
): Float {
	var vH = inMeasurer.lineHeight(inBasePx, inFontFamily, inBaseVariations)
	for (vRun in lineColorRuns(inLine, inGlobalStart, inSpans, Color.Unspecified)) {
		val vPx = resolveRunPx(vRun, inBasePx, inDensity)
		if (vPx != inBasePx) {
			val vRunH = inMeasurer.lineHeight(vPx, inFontFamily, runVariations(vRun, inBaseVariations))
			if (vRunH > vH) vH = vRunH
		}
	}
	return vH
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
