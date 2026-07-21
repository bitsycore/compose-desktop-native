@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package apidemo

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined

// ==================
// MARK: Request tab strip (open requests; drag a tab to reorder)
// ==================

/** Strip of open requests above the editor. Each tab is clickable to select
and drag-reorderable. While dragging, the grabbed tab lifts (zIndex),
fades, and follows the cursor via a graphicsLayer translation — its
neighbours don't shuffle; the reorder commits on drop, to the slot whose
neighbours' centres the cursor passed. key(tab) keeps each tab's
LayoutNode stable so the drag session survives the reorder. */
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

    // One shared context menu, anchored to the tab that opened it (m3 DropdownMenu
    // is parent-anchored, not cursor-anchored — the old x/y capture is gone).
    var vMenu by remember { mutableStateOf(false) }
    var vMenuTab by remember { mutableStateOf<StripTab?>(null) }

    var vListOpen by remember { mutableStateOf(false) }   // overflow list

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
                    // NOTE: `alpha` and `translationX` MUST be on the same graphicsLayer.
                    // `Modifier.alpha` expands to `graphicsLayer(alpha, clip = true)` — clip is
                    // applied in the ALPHA layer's local coord space (before any child layer's
                    // translation), so `.alpha().graphicsLayer(translationX = X)` clips the drag
                    // ghost inside its original bounds and the ghost disappears as it slides. One
                    // layer with both properties = clip stays false, alpha modulates via layerPaint.
                    if (vDragged) vMod =
                        vMod.zIndex(1f).graphicsLayer(alpha = 0.65f, translationX = vDragDx, translationY = 0f)
                    vMod = vMod
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (vSel) c.accent.copy(alpha = 0.20f) else c.field, RoundedCornerShape(7.dp))
                        .border(1.dp, if (vSel || vDragged) c.accent else c.border, RoundedCornerShape(7.dp))
                        .pointerInput(vKey) {
                            // Accumulate the per-frame delta rather than reading change.position.
                            // The tab wears a graphicsLayer(translationX = vDragDx) while dragging,
                            // which transforms the modifier's local pointer frame — so position.x
                            // would compensate for the translation each frame and the tab would
                            // trail the mouse by roughly half its offset. dragAmount is the
                            // root-frame delta and doesn't feed back through the layer transform.
                            detectDragGestures(
                                onDragStart = { offset ->
                                    vDragKey = vKey
                                    vPressRelX = offset.x.toInt()
                                    vDragDx = 0f
                                    vDragTarget = inTabs.indexOfFirst { it.tabKey == vKey }
                                },
                                onDrag = { _, dragAmount ->
                                    val vd = vDragKey ?: return@detectDragGestures
                                    vDragDx += dragAmount.x
                                    val vRelX = vPressRelX + vDragDx.toInt()
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
                                onDragEnd = {
                                    val vd = vDragKey
                                    if (vd != null) {
                                        val vFrom = inTabs.indexOfFirst { it.tabKey == vd }
                                        if (vFrom >= 0 && vDragTarget >= 0 && vDragTarget != vFrom) inOnReorder(
                                            vFrom,
                                            vDragTarget
                                        )
                                    }
                                    vDragKey = null; vDragDx = 0f; vDragTarget = -1
                                },
                                onDragCancel = { vDragKey = null; vDragDx = 0f; vDragTarget = -1 },
                            )
                        }
                        // Middle-click closes the tab; right-click opens the tab menu. Compose
                        // has no first-class match for either — inline pointerInput checks the
                        // pressed PointerButton at each Press.
                        .pointerInput(vTab) {
                            awaitPointerEventScope {
                                while (true) {
                                    val vEv = awaitPointerEvent()
                                    if (vEv.type != PointerEventType.Press) continue
                                    when (vEv.button) {
                                        PointerButton.Tertiary -> {
                                            vEv.changes.firstOrNull()?.consume()
                                            inOnClose(vTab)
                                        }

                                        PointerButton.Secondary -> {
                                            vEv.changes.firstOrNull()?.consume()
                                            vMenuTab = vTab; vMenu = true
                                        }
                                    }
                                }
                            }
                        }
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
                            MaterialSymbolsOutlined(
                                MaterialSymbols.Tune,
                                tint = if (vSel) c.accent else c.dim,
                                size = 13.dp
                            )
                            Text(
                                if (vTab.isSession) "Session" else (vTab.pack?.name ?: ""),
                                color = if (vSel) c.text else c.dim,
                                fontSize = 13.sp
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(7.dp)
                                    .background(methodColor(vRs.req.method), RoundedCornerShape(4.dp))
                            )
                            Text(vRs.req.name, color = if (vSel) c.text else c.dim, fontSize = 13.sp)
                            if (vRs.loading) CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = c.accent,
                                strokeWidth = 1.5.dp
                            )
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
                    modifier = Modifier.widthIn(min = 188.dp),
                ) {
                    DropdownMenuItem(
                        text = { MenuRow(MaterialSymbols.Close, "Close tab") },
                        onClick = { vMenu = false; inOnClose(vMTab) })
                    DropdownMenuItem(
                        text = { MenuRow(MaterialSymbols.Clear, "Close other tabs") },
                        onClick = { vMenu = false; inOnCloseOthers(vMTab) })
                    DropdownMenuItem(
                        text = { MenuRow(MaterialSymbols.Clear, "Close all tabs") },
                        onClick = { vMenu = false; inOnCloseAll() })
                }
            }
        }

        // Overflow → chevron opens a scrollable list of every open tab.
        if (vScroll.maxValue > 0) {
            Box(modifier = Modifier.padding(end = 4.dp)) {
                IconBtn(MaterialSymbols.ExpandMore, "All tabs") { vListOpen = true }
                DropdownMenu(
                    expanded = vListOpen,
                    onDismissRequest = { vListOpen = false },
                    modifier = Modifier.widthIn(min = 240.dp)
                ) {
                    Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        inTabs.forEach { vTab ->
                            DropdownMenuItem(text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val vSel = vTab.tabKey == inActiveKey
                                    val vRs = vTab.req
                                    if (vRs == null) {
                                        MaterialSymbolsOutlined(
                                            MaterialSymbols.Tune,
                                            tint = if (vSel) c.accent else c.dim,
                                            size = 13.dp
                                        )
                                        Text(
                                            if (vTab.isSession) "Session" else "${vTab.pack?.name} · var",
                                            color = if (vSel) c.accent else c.text,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(7.dp)
                                                .background(methodColor(vRs.req.method), RoundedCornerShape(4.dp))
                                        )
                                        Text(
                                            vRs.req.name,
                                            color = if (vSel) c.accent else c.text,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (vRs.loading) CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            color = c.accent,
                                            strokeWidth = 1.5.dp
                                        )
                                    }
                                }
                            }, onClick = { vListOpen = false; inOnSelect(vTab) })
                        }
                    }
                }
            }
        }
    }
}

/** Thin accent bar marking where a dragged tab will be inserted. Rendered in-flow
between tabs so the Row centres it vertically and reserves a little space. */
@Composable
internal fun DropBar() {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp))
            .background(c.accent, RoundedCornerShape(2.dp))
    )
}

