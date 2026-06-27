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
import androidx.compose.ui.text.currentTextMeasurer
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
// MARK: Pack colour (palette dot + picker)
// ==================

/* A small rounded square in the pack's palette colour (neutral when unset).
   The pack's visual identity in the sidebar's foldable pack header. */
@Composable
internal fun ColorDot(inColor: Int, inSize: Dp = 11.dp) {
    val c = LocalAppColors.current
    val vCol = packColor(inColor) ?: c.dim.copy(alpha = 0.35f)
    Box(Modifier.size(inSize).background(vCol, RoundedCornerShape(3.dp)))
}

/* A 5×4 grid of swatches; tapping one sets the pack's colour (1-based index).
   The current colour gets a ring. Sized to fit inside the (fixed-width) ⋮ menu. */
@Composable
internal fun PackColorPicker(inSelected: Int, inOnPick: (Int) -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Pack colour", color = c.dim, fontSize = 11.sp)
        listOf(0, 5, 10, 15).forEach { vBase ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (vK in vBase until (vBase + 5)) {
                    val vIdx = vK + 1
                    Box(
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(5.dp))
                            .background(Color(kPackColors[vK]), RoundedCornerShape(5.dp))
                            .border(if (inSelected == vIdx) 2.dp else 0.dp, c.text, RoundedCornerShape(5.dp))
                            .clickable { inOnPick(vIdx) },
                    )
                }
            }
        }
    }
}

// ==================
// MARK: Add-pack menu (header '+')
// ==================

/* The header '+' — a small menu to add a pack to the open session, either blank
   or imported from a .json file. Importing a pack always lands it in the session. */
@Composable
internal fun AddPackMenu(inOnNewRequest: () -> Unit, inOnNew: () -> Unit, inOnImport: () -> Unit) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Box {
        IconBtn(MaterialSymbols.Add, "Add", inModifier = Modifier.menuAnchor(vAnchor), inSize = 18.dp) { vOpen = true }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, minWidth = 200.dp) {
            DropdownMenuItem(onClick = { vOpen = false; inOnNewRequest() }) { MenuRow(MaterialSymbols.Send, "New request (loose)") }
            Divider(color = c.border)
            DropdownMenuItem(onClick = { vOpen = false; inOnNew() }) { MenuRow(MaterialSymbols.Add, "New pack") }
            DropdownMenuItem(onClick = { vOpen = false; inOnImport() }) { MenuRow(MaterialSymbols.Download, "Import pack…") }
        }
    }
}

// ==================
// MARK: Session menu (save / open / new / recent)
// ==================

/* The last path segment of inPath (the file name), tolerant of either slash. */
internal fun fileLeaf(inPath: String): String =
    inPath.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

/* Shortens a long path to inMax chars keeping the start and end (so the root
   and file stay visible), joined by an ellipsis. The menu has no text-overflow
   primitive, so we trim the string ourselves. */
internal fun ellipsizeMiddle(inText: String, inMax: Int = 46): String {
    if (inText.length <= inMax) return inText
    val vKeep = inMax - 1
    val vHead = vKeep / 2
    return inText.take(vHead) + "…" + inText.takeLast(vKeep - vHead)
}

/* Header dropdown for the one open session. Shows its file name with the full
   path underneath when it has a file (so you can see it's saved — file-backed
   sessions auto-save), or "Untitled session" with a dot when it has no file yet.
   Menu: Save / Save as… / Rename / Reveal / Open… / New + a recent list. */
@Composable
internal fun SessionMenu(
    inPath: String?,
    inRecent: List<String>,
    inOnSave: () -> Unit,
    inOnSaveAs: () -> Unit,
    inOnRename: () -> Unit,
    inOnReveal: () -> Unit,
    inOnSettings: () -> Unit,
    inOnDefault: () -> Unit,
    inOnOpen: () -> Unit,
    inOnNew: () -> Unit,
    inOnOpenRecent: (String) -> Unit,
    inModifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    var vHover by remember { mutableStateOf(false) }
    val vSaved = inPath != null
    val vName = inPath?.let { fileLeaf(it) } ?: "Untitled session"
    val vDisabled = c.dim.copy(alpha = 0.5f)
    Box(modifier = inModifier) {
        // Highlight on hover and stay highlighted while the menu is open.
        Row(
            modifier = Modifier.fillMaxWidth().menuAnchor(vAnchor).clip(RoundedCornerShape(6.dp))
                .background(if (vHover || vOpen) c.accent.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(6.dp))
                .hoverable { vHover = it }
                .clickable { vOpen = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MaterialSymbolsOutlined(MaterialSymbols.InsertDriveFile, tint = c.dim, size = 15.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(vName, color = c.text, fontSize = 14.sp)
                inPath?.let { Text(ellipsizeMiddle(it, 34), color = c.dim, fontSize = 9.sp, softWrap = false) }
            }
            // No file yet → a dot; once it has a file it auto-saves, so no dot.
            if (!vSaved) Box(Modifier.size(6.dp).background(c.accent, RoundedCornerShape(3.dp)))
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, minWidth = 248.dp) {
            DropdownMenuItem(onClick = { vOpen = false; inOnSettings() }) { MenuRow(MaterialSymbols.Tune, "Session settings (Var / Header / Cert)") }
            Divider(color = c.border)
            DropdownMenuItem(onClick = { vOpen = false; inOnSave() }) { MenuRow(MaterialSymbols.Save, "Save session") }
            DropdownMenuItem(onClick = { vOpen = false; inOnSaveAs() }) { MenuRow(MaterialSymbols.Download, "Save session as…") }
            DropdownMenuItem(enabled = vSaved, onClick = { vOpen = false; inOnRename() }) { MenuRow(MaterialSymbols.Edit, "Rename session…", if (vSaved) c.text else vDisabled) }
            DropdownMenuItem(enabled = vSaved, onClick = { vOpen = false; inOnReveal() }) { MenuRow(MaterialSymbols.Folder, "Reveal in ${fileManagerName()}", if (vSaved) c.text else vDisabled) }
            Divider(color = c.border)
            DropdownMenuItem(onClick = { vOpen = false; inOnDefault() }) { MenuRow(MaterialSymbols.Refresh, "Default session") }
            DropdownMenuItem(onClick = { vOpen = false; inOnNew() }) { MenuRow(MaterialSymbols.Add, "New session") }
            DropdownMenuItem(onClick = { vOpen = false; inOnOpen() }) { MenuRow(MaterialSymbols.Folder, "Open session…") }
            if (inRecent.isNotEmpty()) {
                Divider(color = c.border)
                // Aligned to the icon column (the DropdownMenuItem's 16dp pad), so
                // it lines up with the recent items' file icon. Padding goes on a
                // wrapping Box — a leaf Text's own start padding isn't applied to its
                // draw position by this layout engine.
                Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 2.dp)) {
                    Text("Recent", color = c.dim, fontSize = 11.sp)
                }
                inRecent.forEach { vPath ->
                    key(vPath) {
                        var vRevealHover by remember { mutableStateOf(false) }
                        DropdownMenuItem(onClick = { vOpen = false; inOnOpenRecent(vPath) }) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MaterialSymbolsOutlined(MaterialSymbols.InsertDriveFile, tint = c.dim, size = 16.dp)
                                Column(modifier = Modifier.weight(1f).padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(fileLeaf(vPath), color = c.text, fontSize = 12.sp)
                                    Text(ellipsizeMiddle(vPath, 32), color = c.dim, fontSize = 10.sp, softWrap = false)
                                }
                                // Reveal this session's folder — its own hover + tooltip so it
                                // reads as a separate action from "open the session".
                                TooltipBox(text = "Reveal in ${fileManagerName()}") {
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(if (vRevealHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                                            .hoverable { vRevealHover = it }
                                            .clickable { vOpen = false; revealInFileManager(vPath) }
                                            .padding(5.dp),
                                    ) {
                                        MaterialSymbolsOutlined(MaterialSymbols.Folder, contentDescription = "Reveal", tint = if (vRevealHover) c.accent else c.dim, size = 15.dp)
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

// ==================
// MARK: Pack tree (recursive sidebar rendering)
// ==================

/* The per-pack operations a PackSection needs, bundled so PackTree can bind them
   recursively for sub-packs while PackSection itself stays callback-driven. */
internal class PackOps(
    val onEnv: (PackState) -> Unit,
    val onToggle: (PackState) -> Unit,
    val onOpenReq: (PackState, ReqState) -> Unit,
    val onNewReq: (PackState) -> Unit,
    val onRenameReq: (PackState, ReqState) -> Unit,
    val onDupReq: (PackState, ReqState) -> Unit,
    val onCopyCurl: (PackState, ReqState) -> Unit,
    val onDelReq: (PackState, ReqState) -> Unit,
    val onRenamePack: (PackState) -> Unit,
    val onDupPack: (PackState) -> Unit,
    val onLinkedCopy: (PackState) -> Unit,
    val onNewSubPack: (PackState) -> Unit,
    val onSave: (PackState) -> Unit,
    val onSaveAs: (PackState) -> Unit,
    val onRemove: (PackState) -> Unit,
    val onSetColor: (PackState, Int) -> Unit,
    // Cross-pack drag wiring (shared controller + the App-level resolvers / commit).
    val drag: TreeDrag,
    val resolveReqDrop: (Int) -> Unit,
    val resolvePackDrop: (Int) -> Unit,
    val reqDropEnd: () -> Unit,
    val packDropEnd: () -> Unit,
)

/* Renders a pack then its sub-packs recursively, each indented by its depth.
   inSiblings is the list this pack belongs to (top-level vPacks, or a parent's
   subPacks) — used to position the pack-reorder drop bars by index. */
@Composable
internal fun PackTree(inPack: PackState, inSiblings: List<PackState>, inDepth: Int, inOps: PackOps, inActive: PackState?, inEnvActive: Boolean) {
    val vDrag = inOps.drag
    val vMyIndex = inSiblings.indexOf(inPack)
    val vMoving = vDrag.draggingPack && vDrag.engaged && vDrag.dropInto == null
    // Pack-reorder indicators (between siblings) sit at this pack's indent.
    val vPad = Modifier.padding(start = (inDepth * 14).dp)
    val vBarBefore = vMoving && vDrag.dropParent === inPack.parent && vDrag.dropSibIndex == vMyIndex
    val vBarAfter = vMoving && vDrag.dropParent === inPack.parent &&
        vMyIndex == inSiblings.size - 1 && vDrag.dropSibIndex == inSiblings.size
    key(inPack) {
        if (vBarBefore) Box(modifier = vPad) { RowDropBar() }
        Box(modifier = vPad) {
            PackSection(
                inPack = inPack,
                inHeaderActive = inEnvActive && inPack === inActive,
                inActiveReq = if (!inEnvActive && inPack === inActive) inPack.active else null,
                inDrag = vDrag,
                inResolveReqDrop = inOps.resolveReqDrop,
                inResolvePackDrop = inOps.resolvePackDrop,
                inOnReqDropEnd = inOps.reqDropEnd,
                inOnPackDropEnd = inOps.packDropEnd,
                inOnSelect = { inOps.onEnv(inPack) },
                inOnToggle = { inOps.onToggle(inPack) },
                inOnOpenRequest = { inOps.onOpenReq(inPack, it) },
                inOnNewRequest = { inOps.onNewReq(inPack) },
                inOnRenameRequest = { inOps.onRenameReq(inPack, it) },
                inOnDuplicateRequest = { inOps.onDupReq(inPack, it) },
                inOnCopyCurl = { inOps.onCopyCurl(inPack, it) },
                inOnDeleteRequest = { inOps.onDelReq(inPack, it) },
                inOnRenamePack = { inOps.onRenamePack(inPack) },
                inOnDuplicatePack = { inOps.onDupPack(inPack) },
                inOnNewSubPack = { inOps.onNewSubPack(inPack) },
                inOnLinkedCopy = { inOps.onLinkedCopy(inPack) },
                inOnSavePack = { inOps.onSave(inPack) },
                inOnSaveAsPack = { inOps.onSaveAs(inPack) },
                inOnRemovePack = { inOps.onRemove(inPack) },
                inOnSetColor = { vCol -> inOps.onSetColor(inPack, vCol) },
            )
        }
    }
    if (inPack.expanded) inPack.subPacks.forEach { PackTree(it, inPack.subPacks, inDepth + 1, inOps, inActive, inEnvActive) }
    if (vBarAfter) Box(modifier = vPad) { RowDropBar() }
}

// ==================
// MARK: Pack section (foldable pack in the sidebar)
// ==================

/* One open pack rendered as a foldable section: a header (fold chevron, colour
   box, name, dirty dot, request count, ⋮ menu) over the pack's request list
   when expanded. Clicking the header body selects the pack (so the Env tab and
   main panel follow it); the chevron only folds. The request list drags to
   reorder, scoped to this pack. Right-click the header for the pack menu. */
@Composable
internal fun PackSection(
    inPack: PackState,
    inHeaderActive: Boolean,        // this pack's env tab is the active tab
    inActiveReq: ReqState?,         // the globally-active request (for row highlight)
    inDrag: TreeDrag,               // shared cross-pack drag controller
    inResolveReqDrop: (Int) -> Unit,
    inResolvePackDrop: (Int) -> Unit,
    inOnReqDropEnd: () -> Unit,
    inOnPackDropEnd: () -> Unit,
    inHeaderless: Boolean = false,  // root (loose) section: no header, always expanded
    inOnSelect: () -> Unit,
    inOnToggle: () -> Unit,
    inOnOpenRequest: (ReqState) -> Unit,
    inOnNewRequest: () -> Unit,
    inOnRenameRequest: (ReqState) -> Unit,
    inOnDuplicateRequest: (ReqState) -> Unit,
    inOnCopyCurl: (ReqState) -> Unit,
    inOnDeleteRequest: (ReqState) -> Unit,
    inOnRenamePack: () -> Unit,
    inOnDuplicatePack: () -> Unit,
    inOnNewSubPack: () -> Unit,
    inOnLinkedCopy: () -> Unit,
    inOnSavePack: () -> Unit,
    inOnSaveAsPack: () -> Unit,
    inOnRemovePack: () -> Unit,
    inOnSetColor: (Int) -> Unit,
) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vMenu by remember { mutableStateOf(false) }
    var vHover by remember { mutableStateOf(false) }
    var vAtCursor by remember { mutableStateOf(false) }
    var vMenuX by remember { mutableStateOf(0) }
    var vMenuY by remember { mutableStateOf(0) }
    val vMoving = inDrag.engaged   // a press becomes a real drag only past the slop

    Column(modifier = Modifier.fillMaxWidth()) {
        // ============
        //  Pack header (skipped for the headerless loose-root section). Draggable
        //  to reorder / reparent; highlighted when it's the "drop inside" target.
        val vHeaderDragged = inDrag.dragPack === inPack && vMoving
        val vIntoHi = vMoving && ((inDrag.draggingPack && inDrag.dropInto === inPack) ||
            (inDrag.draggingReq && inDrag.dropPack === inPack && !inPack.expanded))
        var vHeadMod = Modifier.fillMaxWidth()
            .onGloballyPositioned { inDrag.headTop[inPack] = it.y }
            .onSizeChanged { inDrag.headH[inPack] = it.height }
        if (vHeaderDragged) vHeadMod = vHeadMod.zIndex(1f).alpha(0.65f).graphicsLayer(translationX = 0f, translationY = inDrag.dy)
        vHeadMod = vHeadMod.clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    vIntoHi -> c.accent.copy(alpha = 0.24f)
                    inHeaderActive -> c.accent.copy(alpha = 0.14f)
                    vHover -> c.accent.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
        if (vIntoHi) vHeadMod = vHeadMod.border(1.dp, c.accent, RoundedCornerShape(6.dp))
        vHeadMod = vHeadMod
            .hoverable { vHover = it }
            .onSecondaryClick { x, y -> vMenuX = x; vMenuY = y; vAtCursor = true; vMenu = true }
            .onDrag(
                onStart = { _, vRelY -> inDrag.clear(); inDrag.dragPack = inPack; inDrag.pressRel = vRelY; inDrag.dy = 0f },
                onDrag = { _, vRelY ->
                    if (inDrag.dragPack !== inPack) return@onDrag
                    inDrag.dy = (vRelY - inDrag.pressRel).toFloat()
                    if (!inDrag.engaged && kotlin.math.abs(inDrag.dy) >= kDragSlop) inDrag.engaged = true
                    if (inDrag.engaged) inResolvePackDrop((inDrag.headTop[inPack] ?: 0) + vRelY)
                },
                onEnd = { inOnPackDropEnd() },
            )
            .padding(end = 2.dp)
        if (!inHeaderless) Row(
            modifier = vHeadMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconBtn(if (inPack.expanded) MaterialSymbols.ExpandMore else MaterialSymbols.ChevronRight, "Fold", inSize = 18.dp, inPadding = 3.dp) { inOnToggle() }
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).clickable { inOnSelect() }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ColorDot(inPack.color)
                if (inPack.isLinked) MaterialSymbolsOutlined(MaterialSymbols.Share, tint = c.dim, size = 12.dp)
                Text(inPack.name, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${inPack.requests.size}", color = c.dim, fontSize = 11.sp)
            }
            // + (new request) and ⋮ (pack menu) — both reveal on hover only, so the
            // header width never shifts (alpha, not conditional layout). A linked
            // copy mirrors its source's requests, so it has no "new request".
            Row(modifier = Modifier.alpha(if (vHover || vMenu) 1f else 0f), verticalAlignment = Alignment.CenterVertically) {
                if (!inPack.isLinked) IconBtn(MaterialSymbols.Add, "New request", inSize = 16.dp, inPadding = 4.dp) { inOnNewRequest() }
                Box {
                    IconBtn(MaterialSymbols.MoreHoriz, "Pack menu", inModifier = Modifier.menuAnchor(vAnchor), inSize = 16.dp, inPadding = 4.dp) { vAtCursor = false; vMenu = true }
                    DropdownMenu(
                        expanded = vMenu,
                        onDismissRequest = { vMenu = false },
                        anchor = if (vAtCursor) null else vAnchor,
                        offsetX = if (vAtCursor) vMenuX.dp else 0.dp,
                        offsetY = if (vAtCursor) vMenuY.dp else 0.dp,
                        minWidth = 220.dp,
                    ) {
                        DropdownMenuItem(onClick = { vMenu = false; inOnRenamePack() }) { MenuRow(MaterialSymbols.Edit, "Rename pack") }
                        DropdownMenuItem(onClick = { vMenu = false; inOnDuplicatePack() }) { MenuRow(MaterialSymbols.FileCopy, "Duplicate pack") }
                        DropdownMenuItem(onClick = { vMenu = false; inOnNewSubPack() }) { MenuRow(MaterialSymbols.Add, "New sub-pack") }
                        DropdownMenuItem(onClick = { vMenu = false; inOnLinkedCopy() }) { MenuRow(MaterialSymbols.Share, "Linked copy") }
                        DropdownMenuItem(onClick = { vMenu = false; inOnSavePack() }) { MenuRow(MaterialSymbols.Save, "Export pack") }
                        DropdownMenuItem(onClick = { vMenu = false; inOnSaveAsPack() }) { MenuRow(MaterialSymbols.Folder, "Export pack as…") }
                        Divider(color = c.border)
                        PackColorPicker(inPack.color) { inOnSetColor(it) }
                        Divider(color = c.border)
                        DropdownMenuItem(onClick = { vMenu = false; inOnRemovePack() }) { MenuRow(MaterialSymbols.Delete, "Remove pack", methodColor(ReqMethod.DELETE)) }
                    }
                }
            }
        }

        // ============
        //  Pack body — request list (expanded, or always for the loose root).
        //  Rows drag within and across packs via the shared controller; the drop
        //  bar shows here whenever this pack is the resolved target.
        if (inPack.expanded || inHeaderless) {
            val vReqs = inPack.requests
            val vIsTarget = vMoving && inDrag.draggingReq && inDrag.dropPack === inPack
            val vDragReq = inDrag.dragReq
            val vOthers = if (vIsTarget) vReqs.filter { it !== vDragReq } else emptyList()
            val vBarBefore = if (vIsTarget) vOthers.getOrNull(inDrag.dropIndex) else null
            val vBarAtEnd = vIsTarget && inDrag.dropIndex >= vOthers.size
            // The loose root has no header, so register the body box as its drop
            // anchor — that gives an empty root a region to target while dragging.
            var vBodyMod = Modifier.fillMaxWidth().padding(start = 10.dp, top = 2.dp, bottom = 4.dp)
            if (inHeaderless) vBodyMod = vBodyMod
                .onGloballyPositioned { inDrag.headTop[inPack] = it.y }
                .onSizeChanged { inDrag.headH[inPack] = it.height }
            Column(
                modifier = vBodyMod,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (inHeaderless && vReqs.isEmpty())
                    Text("Drop a request here to make it loose", color = c.dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                vReqs.forEach { vRs ->
                    if (vRs === vBarBefore) RowDropBar()
                    key(vRs) {
                        val vDragged = vRs === inDrag.dragReq && vMoving
                        var vMod: Modifier = Modifier
                        // Linked copies mirror the source's requests — read-only, so
                        // their rows neither register geometry (would clobber the
                        // source's) nor drag.
                        if (!inPack.isLinked) vMod = vMod
                            .onGloballyPositioned { inDrag.rowTop[vRs] = it.y }
                            .onSizeChanged { inDrag.rowH[vRs] = it.height }
                        // translate is draw-only (doesn't shift absoluteY), so the
                        // cursor offset below stays correct while dragging.
                        if (vDragged) vMod = vMod.zIndex(1f).alpha(0.65f).graphicsLayer(translationX = 0f, translationY = inDrag.dy)
                        if (!inPack.isLinked) vMod = vMod.onDrag(
                                onStart = { _, vRelY ->
                                    inDrag.clear()
                                    inDrag.dragReq = vRs; inDrag.dragReqOwner = inPack
                                    inDrag.pressRel = vRelY; inDrag.dy = 0f
                                    inDrag.dropPack = inPack; inDrag.dropIndex = vReqs.indexOf(vRs)
                                },
                                onDrag = { _, vRelY ->
                                    val vd = inDrag.dragReq ?: return@onDrag
                                    inDrag.dy = (vRelY - inDrag.pressRel).toFloat()
                                    if (!inDrag.engaged && kotlin.math.abs(inDrag.dy) >= kDragSlop) inDrag.engaged = true
                                    if (inDrag.engaged) inResolveReqDrop((inDrag.rowTop[vd] ?: 0) + vRelY)
                                },
                                onEnd = { inOnReqDropEnd() },
                            )
                        Box(modifier = vMod) {
                            RequestRow(
                                inRs = vRs,
                                inSelected = vRs === inActiveReq,
                                inReadOnly = inPack.isLinked,
                                inOnOpen = { inOnOpenRequest(vRs) },
                                inOnRename = { inOnRenameRequest(vRs) },
                                inOnDuplicate = { inOnDuplicateRequest(vRs) },
                                inOnCopyCurl = { inOnCopyCurl(vRs) },
                                inOnDelete = { inOnDeleteRequest(vRs) },
                            )
                        }
                    }
                }
                if (vBarAtEnd) RowDropBar()
            }
        }
    }
}

// ==================
// MARK: Sidebar request row (burger menu: rename / duplicate / delete)
// ==================

@Composable
internal fun RequestRow(
    inRs: ReqState,
    inSelected: Boolean,
    inOnOpen: () -> Unit,
    inOnRename: () -> Unit,
    inOnDuplicate: () -> Unit,
    inOnCopyCurl: () -> Unit,
    inOnDelete: () -> Unit,
    inReadOnly: Boolean = false,   // linked-pack rows: mirror only, no rename/duplicate/delete
) {
    val c = LocalAppColors.current
    val vReq = inRs.req
    val vAnchor = rememberMenuAnchor()
    var vMenu by remember { mutableStateOf(false) }
    var vHover by remember { mutableStateOf(false) }
    // Right-click opens the menu at the cursor; the ⋮ button anchors it to itself.
    var vAtCursor by remember { mutableStateOf(false) }
    var vMenuX by remember { mutableStateOf(0) }
    var vMenuY by remember { mutableStateOf(0) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    inSelected -> c.accent.copy(alpha = 0.22f)
                    vHover -> c.accent.copy(alpha = 0.10f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
            .hoverable { vHover = it }
            .clickable { inOnOpen() }
            .onSecondaryClick { x, y -> vMenuX = x; vMenuY = y; vAtCursor = true; vMenu = true }
            .padding(start = 8.dp, top = 1.dp, bottom = 1.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MethodTag(vReq.method)
        Text(vReq.name, color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        // Overflow (vertical-dots) menu — always laid out so the row width
        // never changes; only its opacity toggles on hover / while open. Delete
        // lives inside this menu (no separate button on the row).
        Box(modifier = Modifier.alpha(if (vHover || vMenu) 1f else 0f)) {
            IconBtn(MaterialSymbols.MoreVert, "Options", inModifier = Modifier.menuAnchor(vAnchor), inSize = 16.dp, inPadding = 4.dp) { vAtCursor = false; vMenu = true }
            DropdownMenu(
                expanded = vMenu,
                onDismissRequest = { vMenu = false },
                anchor = if (vAtCursor) null else vAnchor,
                offsetX = if (vAtCursor) vMenuX.dp else 0.dp,
                offsetY = if (vAtCursor) vMenuY.dp else 0.dp,
                minWidth = 168.dp,
            ) {
                if (!inReadOnly) DropdownMenuItem(onClick = { vMenu = false; inOnRename() }) { MenuRow(MaterialSymbols.Edit, "Rename") }
                if (!inReadOnly) DropdownMenuItem(onClick = { vMenu = false; inOnDuplicate() }) { MenuRow(MaterialSymbols.FileCopy, "Duplicate") }
                DropdownMenuItem(onClick = { vMenu = false; inOnCopyCurl() }) { MenuRow(MaterialSymbols.Terminal, "Copy as cURL") }
                if (!inReadOnly) DropdownMenuItem(onClick = { vMenu = false; inOnDelete() }) { MenuRow(MaterialSymbols.Delete, "Delete", methodColor(ReqMethod.DELETE)) }
            }
        }
    }
}

