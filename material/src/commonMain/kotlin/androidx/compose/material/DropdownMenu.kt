package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onGloballyPositioned
import androidx.compose.foundation.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PositionedPopup

// ==================
// MARK: DropdownMenu
// ==================

/* Anchored popup menu shown when `expanded` is true; clicking outside fires
   onDismissRequest. There are two ways to position it:

   - `anchor` (recommended): a state object the caller updates from the
     trigger widget's onGloballyPositioned / onSizeChanged. The menu lands
     just under the trigger's bottom-left, regardless of where the trigger
     lives in the layout tree.
   - `offsetX` / `offsetY`: absolute window coordinates. Use when you want
     a precise position not tied to a widget (custom popups, context menus
     opened from a right-click coordinate).

   Standard pattern:

       val vAnchor = rememberMenuAnchor()
       var vOpen by remember { mutableStateOf(false) }
       Button(
           onClick = { vOpen = true },
           modifier = Modifier.menuAnchor(vAnchor),
       ) { Text("Open") }
       DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor) {
           DropdownMenuItem(onClick = { vOpen = false }) { Text("Item") }
       }
*/
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: MenuAnchorState? = null,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    minWidth: Dp = DropdownMenuDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    if (!expanded) return
    val vX: Dp = if (anchor != null) (anchor.position.x + offsetX.value.toInt()).dp else offsetX
    val vY: Dp = if (anchor != null) (anchor.position.y + anchor.size.height + offsetY.value.toInt()).dp else offsetY
    PositionedPopup(x = vX, y = vY, onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .width(minWidth)
                .background(MaterialTheme.colors.surface, DropdownMenuDefaults.Shape)
                .padding(vertical = 4.dp)
                .clickable { /* swallow clicks landed inside the menu */ }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

// ==================
// MARK: DropdownMenuItem
// ==================

/* Single row inside a DropdownMenu. Click triggers onClick (and is the
   caller's responsibility to also close the menu — typically:
       onClick = { selection = "Foo"; expanded = false } */
@Composable
fun DropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var vHover by remember { mutableStateOf(false) }
    val vBg = when {
        !enabled -> Color.Transparent
        vHover   -> Color(0x14FFFFFFL)
        else     -> Color.Transparent
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DropdownMenuDefaults.ItemHeight)
            .background(vBg)
            .hoverable { vHover = it }
            .clickable { if (enabled) onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

object DropdownMenuDefaults {
    val MinWidth: Dp = 180.dp
    val ItemHeight: Dp = 36.dp
    val Shape = RoundedCornerShape(4.dp)
}

// ==================
// MARK: MenuAnchorState
// ==================

/* State the trigger widget writes into via Modifier.menuAnchor(...). The
   DropdownMenu reads it to compute its on-screen position. Position is the
   trigger's top-left in window coords; size is its measured dimensions. */
class MenuAnchorState {
    var position: IntOffset by mutableStateOf(IntOffset.Zero)
        internal set
    var size: IntSize by mutableStateOf(IntSize.Zero)
        internal set
}

@Composable
fun rememberMenuAnchor(): MenuAnchorState = remember { MenuAnchorState() }

/* Captures the modified node's window-coordinate position + size into
   [inAnchor]. Apply to the widget that opens the menu (Button, TextField,
   etc.) — the menu will land just below its bottom-left. */
fun Modifier.menuAnchor(inAnchor: MenuAnchorState): Modifier = this
    .onGloballyPositioned { inAnchor.position = it }
    .onSizeChanged { inAnchor.size = it }
