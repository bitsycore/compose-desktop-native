package androidx.compose.foundation.text.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.WrappedText
import androidx.compose.ui.text.currentTextMeasurer
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: SelectableText
// ==================

/* A text block that joins cross-element selection when inside a
   SelectionContainer. Renders exactly like BasicText (colour spans honoured),
   and — when a SelectionRegistrar is present — registers its window bounds +
   offset mapping and paints the highlight for its slice of the global
   selection. The container owns the drag and the Copy shortcut; this block
   has no drag of its own. With no registrar it's a plain BasicText. */
@Composable
fun SelectableText(
	text: AnnotatedString,
	modifier: Modifier = Modifier,
	color: Color = Color.White,
	fontSize: Sp = 16.sp,
	softWrap: Boolean = true,
	fontFamily: String? = null,
	selectionColor: Color = Color(0x661E88E5L),
) {
	val vReg = LocalSelectionRegistrar.current
	if (vReg == null) {
		BasicText(text = text, modifier = modifier, color = color, fontSize = fontSize, softWrap = softWrap, fontFamily = fontFamily)
		return
	}

	val vFontSize = fontSize.value.toInt()
	val vId = remember { vReg.nextId() }
	var vWinX by remember { mutableStateOf(0) }
	var vWinY by remember { mutableStateOf(0) }
	var vW by remember { mutableStateOf(0) }
	var vH by remember { mutableStateOf(0) }

	// Live holder the registrar queries during a drag. Updated each composition
	// so its geometry / text / font stay current; offsetAt re-wraps with the
	// latest width via the active measurer.
	val vSelectable = remember(vId) { TextSelectable(vId) }
	vSelectable.text = text.text
	vSelectable.fontSizePx = vFontSize
	vSelectable.family = fontFamily
	vSelectable.softWrap = softWrap
	vSelectable.windowX = vWinX
	vSelectable.windowY = vWinY
	vSelectable.width = vW
	vSelectable.height = vH

	DisposableEffect(vReg, vId) {
		vReg.register(vSelectable)
		onDispose { vReg.unregister(vId) }
	}

	Box(
		modifier = modifier
			.onGloballyPositioned { vWinX = it.x; vWinY = it.y }
			.onSizeChanged { vW = it.width; vH = it.height }
	) {
		// Highlight this block's selected slice (behind the glyphs), one rect
		// per wrapped line — same math as BasicTextField's selection.
		val vSel = vReg.rangeFor(vId)
		if (vSel != null) {
			val vWrapW = if (softWrap && vW > 0) vW else Int.MAX_VALUE
			val vWrap = currentTextMeasurer.wrap(text.text, vFontSize, vWrapW, fontFamily)
			val vLh = currentTextMeasurer.lineHeight(vFontSize, fontFamily).coerceAtLeast(1f)
			val (vMinLine, vMinCol) = wrappedPosOf(vWrap, vSel.start)
			val (vMaxLine, vMaxCol) = wrappedPosOf(vWrap, vSel.end)
			for (vLine in vMinLine..vMaxLine) {
				val vLineStr = vWrap.lines.getOrElse(vLine) { "" }
				val vSc = if (vLine == vMinLine) vMinCol else 0
				val vEc = if (vLine == vMaxLine) vMaxCol else vLineStr.length
				val vSx = prefixW(vLineStr, vSc, vFontSize, fontFamily)
				val vEx = prefixW(vLineStr, vEc, vFontSize, fontFamily)
				Box(
					modifier = Modifier
						.offset(x = vSx.dp, y = (vLine * vLh).dp)
						.width((vEx - vSx).coerceAtLeast(1).dp)
						.height(vLh.dp)
						.background(selectionColor)
				)
			}
		}
		BasicText(
			text = text,
			color = color,
			fontSize = fontSize,
			softWrap = softWrap,
			fontFamily = fontFamily,
			modifier = Modifier.fillMaxWidth(),
		)
	}
}

/* Mutable Selectable backing one SelectableText; the composable refreshes its
   fields each pass and the registrar reads them during a drag. */
private class TextSelectable(override val id: Long) : Selectable {
	override var text: String = ""
	override var windowX: Int = 0
	override var windowY: Int = 0
	override var width: Int = 0
	override var height: Int = 0
	var fontSizePx: Int = 16
	var family: String? = null
	var softWrap: Boolean = true

	override fun offsetAt(inLocalX: Int, inLocalY: Int): Int {
		val vWrapW = if (softWrap && width > 0) width else Int.MAX_VALUE
		val vWrap = currentTextMeasurer.wrap(text, fontSizePx, vWrapW, family)
		val vLh = currentTextMeasurer.lineHeight(fontSizePx, family).coerceAtLeast(1f)
		return offsetForPoint(vWrap, fontSizePx, family, inLocalX, inLocalY, vLh)
	}
}

// ==================
// MARK: Measurement helpers (mirror BasicTextField's wrap-aware mapping)
// ==================

private fun prefixW(inLine: String, inCol: Int, inFontSize: Int, inFamily: String?): Int {
	if (inCol <= 0 || inLine.isEmpty()) return 0
	val vEnd = inCol.coerceAtMost(inLine.length)
	return currentTextMeasurer.measure(inLine.substring(0, vEnd), inFontSize, inFontFamily = inFamily).width
}

/* Offset → (wrapped line, column). At a hard-'\n' boundary the cursor stays at
   the END of the preceding line. */
private fun wrappedPosOf(inWrap: WrappedText, inOffset: Int): Pair<Int, Int> {
	if (inWrap.lines.isEmpty()) return 0 to 0
	val vClamped = inOffset.coerceAtLeast(0)
	var lo = 0; var hi = inWrap.lines.size - 1
	while (lo < hi) {
		val mid = (lo + hi + 1) / 2
		if (inWrap.lineStarts[mid] <= vClamped) lo = mid else hi = mid - 1
	}
	val vCol = (vClamped - inWrap.lineStarts[lo]).coerceIn(0, inWrap.lines[lo].length)
	return lo to vCol
}

private fun charIndexAtX(inLine: String, inFontSize: Int, inFamily: String?, inX: Int): Int {
	if (inX <= 0 || inLine.isEmpty()) return 0
	var vPrev = 0
	for (i in inLine.indices) {
		val vNext = currentTextMeasurer.measure(inLine.substring(0, i + 1), inFontSize, inFontFamily = inFamily).width
		if (vNext > inX) return if ((inX - vPrev) < (vNext - inX)) i else i + 1
		vPrev = vNext
	}
	return inLine.length
}

private fun offsetForPoint(inWrap: WrappedText, inFontSize: Int, inFamily: String?, inX: Int, inY: Int, inLineHeight: Float): Int {
	if (inWrap.lines.isEmpty()) return 0
	val vLine = (inY / inLineHeight).toInt().coerceIn(0, inWrap.lines.size - 1)
	val vCol = charIndexAtX(inWrap.lines[vLine], inFontSize, inFamily, inX)
	return inWrap.lineStarts[vLine] + vCol
}
