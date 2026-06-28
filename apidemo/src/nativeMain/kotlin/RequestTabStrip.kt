package apidemo
import androidx.compose.ui.graphics.graphicsLayer
import com.compose.desktop.native.modifier.onDrag
import com.compose.desktop.native.modifier.onMiddleClick
import com.compose.desktop.native.modifier.onSecondaryClick
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import com.compose.desktop.native.text.currentTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.LocalComposeNativeWindow
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined
import com.compose.desktop.native.nativeComposeWindow
import com.compose.desktop.native.TextLayoutConfig
import com.compose.desktop.native.registerMemoryResource
import com.compose.desktop.native.removeMemoryResource
import com.compose.desktop.native.fileManagerName
import com.compose.desktop.native.revealInFileManager
import com.compose.desktop.native.showOpenFileDialog
import com.compose.desktop.native.showSaveFileDialog
import com.compose.desktop.native.widgets.HorizontalSplitPane
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================
// MARK: Request tab strip (open requests; drag a tab to reorder)
// ==================

/* The strip of open requests above the editor. Each tab carries a click handler
   (select) and an onDrag handler (reorder). While dragging, the grabbed tab
   floats — it lifts above its neighbours (zIndex), goes semi-transparent and
   tracks the cursor (translate) — without the others shuffling; the reorder is
   committed on drop, to the slot whose neighbours' centres the cursor passed.
   Per-tab window-x/width are tracked for that hit-test; key(tab) keeps each
   tab's LayoutNode stable so the captured drag node stays valid. */
@Composable
internal fun RequestTabStrip(
    inTabs: List<StripTab>,
    inActiveKey: Any?,
    inOnSelect: (StripTab) -> Unit,
    inOnClose: (StripTab) -> Unit,
    inOnCloseOthers: (StripTab) -> Unit,
    inOnCloseAll: () -> Unit,
    inOnReorder: (Int, Int) -> Unit,
) {
    val c = LocalAppColors.current
    val vScroll = rememberScrollState()                     // strip's horizontal scroll
    val vLeft = remember { mutableStateMapOf<Any, Int>() }   // window-x of each tab's left edge, by tab key
    val vWidth = remember { mutableStateMapOf<Any, Int>() }  // measured tab width, by tab key
    var vDragKey by remember { mutableStateOf<Any?>(null) }
    var vPressRelX by remember { mutableStateOf(0) }
    var vDragDx by remember { mutableStateOf(0f) }
    var vDragTarget by remember { mutableStateOf(-1) }

    // One shared context menu, opened at the cursor over the right-clicked tab.
    var vMenu by remember { mutableStateOf(false) }
    var vMenuTab by remember { mutableStateOf<StripTab?>(null) }
    var vMenuX by remember { mutableStateOf(0) }
    var vMenuY by remember { mutableStateOf(0) }

    var vListOpen by remember { mutableStateOf(false) }   // overflow list
    val vListAnchor = rememberMenuAnchor()

    val vShowBar = vDragKey != null && vDragDx != 0f
    val vOthers = if (vShowBar) inTabs.filter { it.tabKey != vDragKey } else emptyList()
    val vBarBeforeKey = if (vShowBar) vOthers.getOrNull(vDragTarget)?.tabKey else null
    val vBarAtEnd = vShowBar && vDragTarget >= vOthers.size

    Row(
        modifier = Modifier.fillMaxWidth().background(c.panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f)
            .horizontalScroll(vScroll).padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inTabs.forEach { vTab ->
            val vKey = vTab.tabKey
            if (vKey == vBarBeforeKey) DropBar()
            key(vKey) {
                val vSel = vKey == inActiveKey
                val vDragged = vKey == vDragKey

                var vMod = Modifier
                    .onGloballyPositioned { vLeft[vKey] = it.x }
                    .onSizeChanged { vWidth[vKey] = it.width }
                if (vDragged) vMod = vMod.zIndex(1f).alpha(0.65f).graphicsLayer(translationX = vDragDx, translationY = 0f)
                vMod = vMod
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (vSel) c.accent.copy(alpha = 0.20f) else c.field, RoundedCornerShape(7.dp))
                    .border(1.dp, if (vSel || vDragged) c.accent else c.border, RoundedCornerShape(7.dp))
                    .onDrag(
                        onStart = { vRelX, _ ->
                            vDragKey = vKey; vPressRelX = vRelX; vDragDx = 0f; vDragTarget = inTabs.indexOfFirst { it.tabKey == vKey }
                        },
                        onDrag = { vRelX, _ ->
                            val vd = vDragKey ?: return@onDrag
                            vDragDx = (vRelX - vPressRelX).toFloat()
                            val vCursorX = (vLeft[vd] ?: 0) + vRelX
                            var vCount = 0
                            inTabs.forEach { vT ->
                                if (vT.tabKey != vd) {
                                    val vCenter = (vLeft[vT.tabKey] ?: 0) + (vWidth[vT.tabKey] ?: 0) / 2
                                    if (vCursorX > vCenter) vCount++
                                }
                            }
                            vDragTarget = vCount
                        },
                        onEnd = {
                            val vd = vDragKey
                            if (vd != null) {
                                val vFrom = inTabs.indexOfFirst { it.tabKey == vd }
                                if (vFrom >= 0 && vDragTarget >= 0 && vDragTarget != vFrom) inOnReorder(vFrom, vDragTarget)
                            }
                            vDragKey = null; vDragDx = 0f; vDragTarget = -1
                        },
                    )
                    .onMiddleClick { inOnClose(vTab) }
                    .onSecondaryClick { x, y -> vMenuTab = vTab; vMenuX = x; vMenuY = y; vMenu = true }
                    .clickable { inOnSelect(vTab) }
                    .padding(start = 9.dp, top = 6.dp, bottom = 6.dp, end = 3.dp)

                Row(
                    modifier = vMod,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val vRs = vTab.req
                    if (vRs == null) {
                        // Session / pack settings tab.
                        MaterialSymbolsOutlined(MaterialSymbols.Tune, tint = if (vSel) c.accent else c.dim, size = 13.dp)
                        Text(if (vTab.isSession) "Session" else (vTab.pack?.name ?: ""), color = if (vSel) c.text else c.dim, fontSize = 13.sp)
                    } else {
                        Box(modifier = Modifier.size(7.dp).background(methodColor(vRs.req.method), RoundedCornerShape(4.dp)))
                        Text(vRs.req.name, color = if (vSel) c.text else c.dim, fontSize = 13.sp)
                        if (vRs.loading) CircularProgressIndicator(modifier = Modifier.size(12.dp), color = c.accent, strokeWidth = 1.5.dp)
                    }
                    IconBtn(MaterialSymbols.Close, "Close", inSize = 13.dp) { inOnClose(vTab) }
                }
            }
        }
        if (vBarAtEnd) DropBar()

        // Shared right-click menu, opened at the cursor over the clicked tab.
        val vMTab = vMenuTab
        if (vMenu && vMTab != null) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { vMenu = false },
                anchor = null,
                offsetX = vMenuX.dp,
                offsetY = vMenuY.dp,
                minWidth = 188.dp,
            ) {
                DropdownMenuItem(onClick = { vMenu = false; inOnClose(vMTab) }) { MenuRow(MaterialSymbols.Close, "Close tab") }
                DropdownMenuItem(onClick = { vMenu = false; inOnCloseOthers(vMTab) }) { MenuRow(MaterialSymbols.Clear, "Close other tabs") }
                DropdownMenuItem(onClick = { vMenu = false; inOnCloseAll() }) { MenuRow(MaterialSymbols.Clear, "Close all tabs") }
            }
        }
      }

        // Overflow → chevron opens a scrollable list of every open tab.
        if (vScroll.maxValue > 0) {
            Box(modifier = Modifier.padding(end = 4.dp)) {
                IconBtn(MaterialSymbols.ExpandMore, "All tabs", inModifier = Modifier.menuAnchor(vListAnchor)) { vListOpen = true }
                DropdownMenu(expanded = vListOpen, onDismissRequest = { vListOpen = false }, anchor = vListAnchor, minWidth = 240.dp) {
                    Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        inTabs.forEach { vTab ->
                            DropdownMenuItem(onClick = { vListOpen = false; inOnSelect(vTab) }) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val vSel = vTab.tabKey == inActiveKey
                                    val vRs = vTab.req
                                    if (vRs == null) {
                                        MaterialSymbolsOutlined(MaterialSymbols.Tune, tint = if (vSel) c.accent else c.dim, size = 13.dp)
                                        Text(if (vTab.isSession) "Session" else "${vTab.pack?.name} · var", color = if (vSel) c.accent else c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    } else {
                                        Box(modifier = Modifier.size(7.dp).background(methodColor(vRs.req.method), RoundedCornerShape(4.dp)))
                                        Text(vRs.req.name, color = if (vSel) c.accent else c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        if (vRs.loading) CircularProgressIndicator(modifier = Modifier.size(12.dp), color = c.accent, strokeWidth = 1.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* Thin accent bar marking where a dragged tab will be inserted. Rendered in-flow
   between tabs so the Row centres it vertically and reserves a little space. */
@Composable
internal fun DropBar() {
    val c = LocalAppColors.current
    Box(modifier = Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(c.accent, RoundedCornerShape(2.dp)))
}

