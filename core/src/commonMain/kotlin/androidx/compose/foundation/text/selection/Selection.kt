package androidx.compose.foundation.text.selection

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ==================
// MARK: Cross-element text selection
// ==================

/* A selected character range [start, end) within one Selectable's text. */
internal data class TextSel(val start: Int, val end: Int)

/* One registered selectable text block. Reports its window-absolute bounds and
   maps a point (in its own local coordinates) to a character offset, using the
   active TextMeasurer. SelectionContainer's registrar drives a single drag
   selection across every registered Selectable. */
internal interface Selectable {
	val id: Long
	val text: String
	val windowX: Int
	val windowY: Int
	val width: Int
	val height: Int
	fun offsetAt(inLocalX: Int, inLocalY: Int): Int
}

/* Coordinates one drag-selection that can span multiple Selectables. Provided
   to descendants via LocalSelectionRegistrar by SelectionContainer. Anchor =
   where the drag began; head = the current drag point — each is a
   (selectableId, charOffset). State is snapshot-backed so each block's
   highlight and the copy action recompose as the drag moves. */
internal class SelectionRegistrar {
	private var fNextId = 0L
	private val fSelectables = mutableStateListOf<Selectable>()

	var anchorId by mutableStateOf(-1L)
	var anchorOffset by mutableStateOf(0)
	var headId by mutableStateOf(-1L)
	var headOffset by mutableStateOf(0)

	val hasSelection: Boolean
		get() = anchorId >= 0 && headId >= 0 && !(anchorId == headId && anchorOffset == headOffset)

	fun nextId(): Long = fNextId++
	fun register(inSel: Selectable) { fSelectables.add(inSel) }
	fun unregister(inId: Long) { fSelectables.removeAll { it.id == inId } }

	// Reading order: top-to-bottom, then left-to-right (window coords).
	private fun ordered(): List<Selectable> =
		fSelectables.sortedWith(compareBy({ it.windowY }, { it.windowX }))

	private fun elementAt(inX: Int, inY: Int): Selectable? =
		fSelectables.firstOrNull {
			inX >= it.windowX && inX < it.windowX + it.width &&
			inY >= it.windowY && inY < it.windowY + it.height
		}

	// Nearest block by vertical distance — used when the drag point falls in a
	// gap so the selection still extends to the closest text.
	private fun nearest(inY: Int): Selectable? =
		ordered().minByOrNull { kotlin.math.abs((it.windowY + it.height / 2) - inY) }

	private fun resolve(inX: Int, inY: Int): Selectable? = elementAt(inX, inY) ?: nearest(inY)

	fun startAt(inWindowX: Int, inWindowY: Int) {
		val vS = resolve(inWindowX, inWindowY) ?: run { clear(); return }
		val vOff = vS.offsetAt(inWindowX - vS.windowX, inWindowY - vS.windowY)
		anchorId = vS.id; anchorOffset = vOff
		headId = vS.id; headOffset = vOff
	}

	fun dragTo(inWindowX: Int, inWindowY: Int) {
		if (anchorId < 0) return
		val vS = resolve(inWindowX, inWindowY) ?: return
		headId = vS.id; headOffset = vS.offsetAt(inWindowX - vS.windowX, inWindowY - vS.windowY)
	}

	/* Double-click: select the word under the point (within one block). */
	fun selectWordAt(inWindowX: Int, inWindowY: Int) {
		val vS = resolve(inWindowX, inWindowY) ?: run { clear(); return }
		val vOff = vS.offsetAt(inWindowX - vS.windowX, inWindowY - vS.windowY)
		val vR = androidx.compose.foundation.text.wordRangeAt(vS.text, vOff)
		anchorId = vS.id; anchorOffset = vR.start
		headId = vS.id; headOffset = vR.end
	}

	/* Triple-click: select the whole line under the point (within one block). */
	fun selectLineAt(inWindowX: Int, inWindowY: Int) {
		val vS = resolve(inWindowX, inWindowY) ?: run { clear(); return }
		val vOff = vS.offsetAt(inWindowX - vS.windowX, inWindowY - vS.windowY)
		val vR = androidx.compose.foundation.text.lineRangeAt(vS.text, vOff)
		anchorId = vS.id; anchorOffset = vR.start
		headId = vS.id; headOffset = vR.end
	}

	fun clear() { anchorId = -1L; headId = -1L; anchorOffset = 0; headOffset = 0 }

	/* Select every registered block, from the start of the first to the end of
	   the last (reading order) — the Ctrl/Cmd+A action. */
	fun selectAll() {
		val vOrd = ordered()
		if (vOrd.isEmpty()) return
		val vFirst = vOrd.first()
		val vLast = vOrd.last()
		anchorId = vFirst.id; anchorOffset = 0
		headId = vLast.id; headOffset = vLast.text.length
	}

	// Normalized selection in reading order as [startIdx, startOff, endIdx,
	// endOff] over ordered(), or null when there's no selection.
	private fun normalized(inOrdered: List<Selectable>): IntArray? {
		if (!hasSelection) return null
		val vAi = inOrdered.indexOfFirst { it.id == anchorId }
		val vHi = inOrdered.indexOfFirst { it.id == headId }
		if (vAi < 0 || vHi < 0) return null
		return if (vAi < vHi || (vAi == vHi && anchorOffset <= headOffset))
			intArrayOf(vAi, anchorOffset, vHi, headOffset)
		else
			intArrayOf(vHi, headOffset, vAi, anchorOffset)
	}

	/* The selected sub-range of the given block, or null when it's outside the
	   selection. First block is partial from startOff; last partial to endOff;
	   blocks in between are fully selected. */
	fun rangeFor(inId: Long): TextSel? {
		val vOrd = ordered()
		val vN = normalized(vOrd) ?: return null
		val vMyIdx = vOrd.indexOfFirst { it.id == inId }
		if (vMyIdx < vN[0] || vMyIdx > vN[2]) return null
		val vLen = vOrd[vMyIdx].text.length
		val vStart = (if (vMyIdx == vN[0]) vN[1] else 0).coerceIn(0, vLen)
		val vEnd = (if (vMyIdx == vN[2]) vN[3] else vLen).coerceIn(0, vLen)
		return if (vStart < vEnd) TextSel(vStart, vEnd) else null
	}

	/* All selected text concatenated in reading order, one block per line. */
	fun selectedText(): String {
		val vOrd = ordered()
		val vSb = StringBuilder()
		for (vS in vOrd) {
			val vR = rangeFor(vS.id) ?: continue
			if (vSb.isNotEmpty()) vSb.append('\n')
			vSb.append(vS.text.substring(vR.start, vR.end))
		}
		return vSb.toString()
	}
}

/* Non-null inside a SelectionContainer; SelectableText registers with it and
   reads its selection to paint highlights. */
internal val LocalSelectionRegistrar = compositionLocalOf<SelectionRegistrar?> { null }
