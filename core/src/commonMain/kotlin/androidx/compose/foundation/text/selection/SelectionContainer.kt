package androidx.compose.foundation.text.selection

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.kMultiClickMs
import androidx.compose.foundation.text.kMultiClickSlopPx
import androidx.compose.foundation.text.monotonicMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.currentClipboard
import com.compose.desktop.native.layout.x
import com.compose.desktop.native.layout.y
import com.compose.desktop.native.modifier.onDrag

// ==================
// MARK: SelectionContainer
// ==================

/* Makes descendant Text blocks selectable with the mouse and copyable with the
   platform Copy shortcut — including ONE selection that spans MULTIPLE sibling
   Texts.

   Each descendant BasicText is selection-aware, registering its window
   bounds + offset mapping with the SelectionRegistrar provided here. This
   container owns the gesture: a press sets the anchor (block, offset); each
   move extends the head to the block/offset under the cursor; every block
   paints the slice of the selection that falls in it (keeping its own
   colours). Ctrl/Cmd+C copies the concatenated selection, blocks joined by
   newlines.

   Limitations: blocks are ordered by window position (top, then left), so the
   common vertical / reading-order layouts work; exotic layouts and editable
   fields nested inside aren't special-cased. */
@Composable
fun SelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
	val vReg = remember { SelectionRegistrar() }
	var vWinX by remember { mutableStateOf(0) }
	var vWinY by remember { mutableStateOf(0) }
	// Multi-click: 1 = caret, 2 = word, 3 = line (wraps back to 1).
	var vClicks by remember { mutableStateOf(0) }
	var vLastClickMs by remember { mutableStateOf(0L) }
	var vLastClickX by remember { mutableStateOf(0) }
	var vLastClickY by remember { mutableStateOf(0) }
	val vClipboard = currentClipboard
	CompositionLocalProvider(
		LocalSelectionRegistrar provides vReg,
	) {
		Box(
			modifier = modifier
				.onGloballyPositioned { vWinX = it.x; vWinY = it.y }
				.focusable { }
				.onKeyEvent { ev ->
					val vK = ev
					// Cmd on macOS, Ctrl on Windows / Linux. Match the produced
					// character (layout-aware), not the physical scancode, so
					// Ctrl+A / Ctrl+C work on AZERTY, Dvorak, etc.
					val vPrimary = vK.isCtrlPressed || vK.isMetaPressed
					if (vK.type != KeyEventType.KeyDown || !vPrimary) return@onKeyEvent false
					val vChar = vK.utf16CodePoint.let { if (it in 0x20..0x7E) it.toChar() else null }
					when (vChar?.lowercaseChar()) {
						'a' -> { vReg.selectAll(); true }
						'c' -> if (vReg.hasSelection) { vClipboard.setText(androidx.compose.ui.text.AnnotatedString(vReg.selectedText())); true } else false
						else -> false
					}
				}
				// onDrag coords are relative to this Box; add its window origin
				// to get the window coords the registrar matches against blocks.
				.onDrag(
					onStart = { rx, ry ->
						val vWx = vWinX + rx
						val vWy = vWinY + ry
						val vNow = monotonicMillis()
						// Same spot within the window steps the count 1→2→3→1.
						val vMulti = kotlin.math.abs(vWx - vLastClickX) <= kMultiClickSlopPx &&
							kotlin.math.abs(vWy - vLastClickY) <= kMultiClickSlopPx &&
							(vNow - vLastClickMs) < kMultiClickMs
						vClicks = if (vMulti) (vClicks % 3) + 1 else 1
						vLastClickMs = vNow; vLastClickX = vWx; vLastClickY = vWy
						when (vClicks) {
							2 -> vReg.selectWordAt(vWx, vWy)   // double-click: word
							3 -> vReg.selectLineAt(vWx, vWy)   // triple-click: line
							else -> vReg.startAt(vWx, vWy)     // single: caret
						}
					},
					onDrag = { rx, ry -> vReg.dragTo(vWinX + rx, vWinY + ry) },
				)
		) { content() }
	}
}

/* Disables selection for descendants — for widgets (chips, tabs) that want
   their own click semantics rather than being captured by a surrounding
   SelectionContainer. */
@Composable
fun DisableSelection(content: @Composable () -> Unit) {
	CompositionLocalProvider(
		LocalSelectionRegistrar provides null,
	) { content() }
}
