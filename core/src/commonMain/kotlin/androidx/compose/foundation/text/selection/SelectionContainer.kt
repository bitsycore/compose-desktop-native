package androidx.compose.foundation.text.selection

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.currentClipboard
import com.compose.desktop.native.modifier.onDrag

// ==================
// MARK: SelectionContainer
// ==================

/* Makes descendant Text blocks selectable with the mouse and copyable with the
   platform Copy shortcut — including ONE selection that spans MULTIPLE sibling
   Texts.

   Each descendant Text renders as a SelectableText, registering its window
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
	val vClipboard = currentClipboard
	CompositionLocalProvider(
		LocalInSelectionContainer provides true,
		LocalSelectionRegistrar provides vReg,
	) {
		Box(
			modifier = modifier
				.onGloballyPositioned { vWinX = it.x; vWinY = it.y }
				.focusable { }
				.onKeyEvent { ev ->
					val vK = ev.key
					// Cmd on macOS, Ctrl on Windows / Linux. Match the produced
					// character (layout-aware), not the physical scancode, so
					// Ctrl+A / Ctrl+C work on AZERTY, Dvorak, etc.
					val vPrimary = vK.modifiers.ctrl || vK.modifiers.meta
					if (vK.type != KeyEventType.Down || !vPrimary) return@onKeyEvent false
					when (vK.char?.lowercaseChar()) {
						'a' -> { vReg.selectAll(); true }
						'c' -> if (vReg.hasSelection) { vClipboard.setText(vReg.selectedText()); true } else false
						else -> false
					}
				}
				// onDrag coords are relative to this Box; add its window origin
				// to get the window coords the registrar matches against blocks.
				.onDrag(
					onStart = { rx, ry -> vReg.startAt(vWinX + rx, vWinY + ry) },
					onDrag = { rx, ry -> vReg.dragTo(vWinX + rx, vWinY + ry) },
				)
		) { content() }
	}
}


/* True when the composition is inside a SelectionContainer. Text composables
   observe this to switch to their selectable (SelectableText) render. */
val LocalInSelectionContainer = compositionLocalOf { false }

/* Disables selection for descendants — for widgets (chips, tabs) that want
   their own click semantics rather than being captured by a surrounding
   SelectionContainer. */
@Composable
fun DisableSelection(content: @Composable () -> Unit) {
	CompositionLocalProvider(
		LocalInSelectionContainer provides false,
		LocalSelectionRegistrar provides null,
	) { content() }
}
