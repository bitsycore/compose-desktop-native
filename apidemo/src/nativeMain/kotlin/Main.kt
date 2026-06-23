package apidemo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.translate
import androidx.compose.ui.draw.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.LocalComposeNativeWindow
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined
import com.compose.desktop.native.nativeComposeWindow
import com.compose.desktop.native.registerMemoryResource
import com.compose.desktop.native.removeMemoryResource
import com.compose.desktop.native.showOpenFileDialog
import com.compose.desktop.native.showSaveFileDialog
import com.compose.desktop.native.widgets.HorizontalSplitPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================
// MARK: Theme — switchable dark / light palette
// ==================

class AppColors(
    val bg: Color, val panel: Color, val field: Color, val border: Color,
    val accent: Color, val text: Color, val dim: Color, val onAccent: Color,
)

private val DarkColors = AppColors(
    bg = Color(0xFF15161A), panel = Color(0xFF23252C), field = Color(0xFF2D2F37),
    border = Color(0xFF474C57), accent = Color(0xFF9F88FF), text = Color(0xFFECEEF2),
    dim = Color(0xFFAEB4BD), onAccent = Color(0xFFFFFFFF),
)
private val LightColors = AppColors(
    bg = Color(0xFFF3F4F7), panel = Color(0xFFFFFFFF), field = Color(0xFFFFFFFF),
    border = Color(0xFFCED3DB), accent = Color(0xFF6B4BE6), text = Color(0xFF1B1D22),
    dim = Color(0xFF5C636E), onAccent = Color(0xFFFFFFFF),
)
private val LocalAppColors = staticCompositionLocalOf { DarkColors }

// ==================
// MARK: Entry point
// ==================

fun main() {
    nativeComposeWindow(title = "API Manager", width = 1240, height = 820) { App() }
}

private class HistoryEntry(val method: ReqMethod, val url: String, val status: Int, val timeMs: Long, val request: ApiRequest)

/* One request plus its live session state — response, in-flight job (for
   cancel) and which sub-tabs show. Stable identity lets the sidebar and the
   open-tab strip reference the same request without index juggling. */
private class ReqState(inInitial: ApiRequest) {
    var req by mutableStateOf(inInitial)
    var response by mutableStateOf<ApiResponse?>(null)
    var loading by mutableStateOf(false)
    var job: Job? = null
    var reqTab by mutableStateOf(0)       // panel 3 (build): 0 Query, 1 Headers, 2 Body
    var viewTab by mutableStateOf(1)      // panel 4 (view): 0 Request, 1 Response
    var preview by mutableStateOf(false)  // panel 4 showing the resolved, not-yet-sent request
    var sentReq by mutableStateOf<ApiRequest?>(null)  // resolved request actually sent (Request tab)
    var imageKey: String? = null          // memory-resource key when the response is an image
    // null = auto-detect from Content-Type each frame; non-null = user-pinned override.
    var respFormatOverride by mutableStateOf<BodyFormat?>(null)
    var reqFormat by mutableStateOf(BodyFormat.RAW)  // builder-side: how to highlight the JSON / TEXT body editor
}

/* One open pack: its file path (null = never saved), a dirty flag (edits since
   last file save), and its own requests, variables and open-tab strip. Switching
   packs swaps the whole working set. */
private class PackState(inPack: Pack, inPath: String?, inDirty: Boolean) {
    var name by mutableStateOf(inPack.name)
    var path by mutableStateOf(inPath)
    var dirty by mutableStateOf(inDirty)
    val requests = mutableStateListOf<ReqState>().apply {
        inPack.requests.ifEmpty { listOf(ApiRequest()) }.forEach { add(ReqState(it)) }
    }
    val variables = mutableStateListOf<KeyVal>().apply { addAll(inPack.variables) }
    val openTabs = mutableStateListOf<ReqState>().apply { requests.firstOrNull()?.let { add(it) } }
    var active by mutableStateOf(requests.firstOrNull())

    fun toPack(): Pack = Pack(name, requests.map { it.req }, variables.toList())
}

// ==================
// MARK: App
// ==================

@Composable
private fun App() {
    val vInitial = remember { loadAppState() }
    var vDark by remember { mutableStateOf(vInitial.dark) }

    val vRunner = remember { HttpRunner() }
    val vScope = rememberCoroutineScope()
    val vWindow = LocalComposeNativeWindow.current

    // First launch (or empty saved state) → load the httpbin starter pack once.
    val vPacks = remember {
        mutableStateListOf<PackState>().apply {
            val vSaved = if (!vInitial.launched || vInitial.packs.isEmpty())
                listOf(SavedPack(pack = defaultPack()))
            else vInitial.packs
            vSaved.forEach { add(PackState(it.pack, it.path, it.dirty)) }
        }
    }
    val vGlobalEnv = remember { mutableStateListOf<KeyVal>().apply { addAll(vInitial.globalEnv) } }
    val vHistory = remember { mutableStateListOf<HistoryEntry>() }
    var vActivePack by remember { mutableStateOf(vInitial.activePack.coerceIn(0, (vPacks.size - 1).coerceAtLeast(0))) }

    var vSideTab by remember { mutableStateOf(0) }      // 0 Requests, 1 History, 2 Env
    var vEnvScope by remember { mutableStateOf(0) }     // 0 Pack env, 1 Global env
    var vReqMsg by remember { mutableStateOf<String?>(null) }
    var vRenameTarget by remember { mutableStateOf<ReqState?>(null) }
    var vRenameText by remember { mutableStateOf("") }
    var vDeleteTarget by remember { mutableStateOf<ReqState?>(null) }
    var vQuitDialog by remember { mutableStateOf(false) }
    var vImgSeq by remember { mutableStateOf(0) }   // unique-key counter for response images

    // Drag-to-reorder state for the request list (mirrors the open-tab strip).
    var vDragRs by remember { mutableStateOf<ReqState?>(null) }   // row being dragged, or null
    var vDragPressY by remember { mutableStateOf(0) }            // press point inside the row
    var vDragDy by remember { mutableStateOf(0f) }               // visual follow offset
    var vDragTarget by remember { mutableStateOf(-1) }           // drop slot among the other rows
    val vRowTop = remember { mutableStateMapOf<ReqState, Int>() } // window-y of each row's top
    val vRowH = remember { mutableStateMapOf<ReqState, Int>() }   // measured row height

    fun activePack(): PackState? = vPacks.getOrNull(vActivePack)
    fun effective(inP: PackState): List<KeyVal> = inP.variables.toList() + vGlobalEnv.toList()

    fun persist() {
        saveAppState(AppState(
            launched = true,
            dark = vDark,
            globalEnv = vGlobalEnv.toList(),
            packs = vPacks.map { SavedPack(it.path, it.dirty, it.toPack()) },
            activePack = vActivePack,
        ))
    }

    // ============
    //  Request actions (operate on the active pack, read live)
    fun open(inRs: ReqState) {
        val vP = activePack() ?: return
        if (inRs !in vP.openTabs) vP.openTabs.add(inRs)
        vP.active = inRs; vReqMsg = null
    }
    fun closeTab(inRs: ReqState) {
        val vP = activePack() ?: return
        val vIdx = vP.openTabs.indexOf(inRs)
        vP.openTabs.remove(inRs)
        if (vP.active === inRs) vP.active = vP.openTabs.getOrNull(vIdx) ?: vP.openTabs.lastOrNull()
    }
    fun reorderTabs(inFrom: Int, inTo: Int) {
        val vP = activePack() ?: return
        if (inFrom == inTo || inFrom !in vP.openTabs.indices || inTo !in vP.openTabs.indices) return
        vP.openTabs.add(inTo, vP.openTabs.removeAt(inFrom))
    }
    fun edit(inT: (ApiRequest) -> ApiRequest) {
        val vP = activePack() ?: return
        val vRs = vP.active ?: return
        vRs.req = inT(vRs.req); vP.dirty = true
    }
    fun newRequest() {
        val vP = activePack() ?: return
        val vRs = ReqState(ApiRequest(name = "Request ${vP.requests.size + 1}"))
        vP.requests.add(vRs); vP.openTabs.add(vRs); vP.active = vRs; vP.dirty = true; vReqMsg = null; vSideTab = 0
    }
    fun duplicate(inRs: ReqState) {
        val vP = activePack() ?: return
        val vAt = (vP.requests.indexOf(inRs) + 1).coerceIn(0, vP.requests.size)
        val vCopy = ReqState(inRs.req.copy(name = "${inRs.req.name} copy"))
        vP.requests.add(vAt, vCopy); vP.openTabs.add(vCopy); vP.active = vCopy; vP.dirty = true; vReqMsg = null
    }
    fun deleteRequest(inRs: ReqState) {
        val vP = activePack() ?: return
        inRs.job?.cancel()
        inRs.imageKey?.let { removeMemoryResource(it) }
        vP.openTabs.remove(inRs); vP.requests.remove(inRs)
        if (vP.requests.isEmpty()) vP.requests.add(ReqState(ApiRequest()))
        if (vP.active === inRs) vP.active = vP.openTabs.lastOrNull()
        vP.dirty = true
    }
    fun renameRequest(inRs: ReqState, inName: String) {
        val vP = activePack() ?: return
        inRs.req = inRs.req.copy(name = inName); vP.dirty = true
    }
    fun send(inRs: ReqState) {
        if (inRs.loading) return
        val vP = activePack() ?: return
        val vOriginal = inRs.req
        val vSend = resolveVars(vOriginal, effective(vP))
        inRs.loading = true; inRs.response = null; vReqMsg = null
        inRs.sentReq = vSend                      // snapshot what we actually send
        inRs.preview = false; inRs.viewTab = 1    // sending → show the Response tab
        inRs.job = vScope.launch(Dispatchers.Main) {
            try {
                val vR = withContext(Dispatchers.Default) { vRunner.run(vSend) }
                inRs.response = vR
                // Image payload → register its bytes under a fresh key so the
                // response panel can render it via painterResource(key).
                inRs.imageKey?.let { removeMemoryResource(it) }
                inRs.imageKey = if (vR.isImage && vR.bytes.isNotEmpty()) {
                    vImgSeq += 1
                    "resp-image://$vImgSeq".also { registerMemoryResource(it, vR.bytes) }
                } else null
                vHistory.add(0, HistoryEntry(vSend.method, vSend.url, vR.status, vR.timeMs, vOriginal))
                if (vHistory.size > 50) vHistory.removeAt(vHistory.size - 1)
            } finally {
                inRs.loading = false; inRs.job = null
            }
        }
    }
    fun cancel(inRs: ReqState) { inRs.job?.cancel(); inRs.job = null; inRs.loading = false }

    // ============
    //  Pack actions
    fun newPack() {
        vPacks.add(PackState(Pack(name = "Pack ${vPacks.size + 1}"), null, false))
        vActivePack = vPacks.size - 1; vReqMsg = null; persist()
    }
    fun openPackFile() {
        showOpenFileDialog { vPath ->
            if (vPath != null) importPack(vPath).fold(
                onSuccess = { vPk ->
                    vPacks.add(PackState(vPk, vPath, false)); vActivePack = vPacks.size - 1
                    vReqMsg = "Opened ${vPk.name}."; persist()
                },
                onFailure = { vReqMsg = "Open failed: ${it.message}" },
            )
        }
    }
    fun saveAsPack() {
        val vP = activePack() ?: return
        showSaveFileDialog("${vP.name}.json") { vPath ->
            if (vPath != null) {
                val vErr = exportPack(vP.toPack(), vPath)
                if (vErr == null) { vP.path = vPath; vP.dirty = false; vReqMsg = "Saved."; persist() }
                else vReqMsg = "Save failed: $vErr"
            }
        }
    }
    fun savePack() {
        val vP = activePack() ?: return
        val vPath = vP.path
        if (vPath == null) { saveAsPack(); return }
        val vErr = exportPack(vP.toPack(), vPath)
        if (vErr == null) { vP.dirty = false; vReqMsg = "Saved."; persist() } else vReqMsg = "Save failed: $vErr"
    }
    fun closePack(inIdx: Int) {
        if (inIdx !in vPacks.indices) return
        vPacks.removeAt(inIdx)
        vActivePack = vActivePack.coerceIn(0, (vPacks.size - 1).coerceAtLeast(0))
        vReqMsg = null; persist()
    }
    fun loadDefaultPack() {
        vPacks.add(PackState(defaultPack(), null, false)); vActivePack = vPacks.size - 1; vReqMsg = null; persist()
    }

    // ============
    //  Warn-on-quit: persist always; veto if anything is unsaved-to-file.
    //  Plus app-wide keyboard shortcuts (work even with nothing focused).
    DisposableEffect(Unit) {
        vWindow.setOnCloseRequest {
            persist()
            if (vPacks.any { it.dirty }) { vQuitDialog = true; false } else true
        }
        vWindow.setOnKeyShortcut { vKey ->
            if (vKey.type != KeyEventType.Down) return@setOnKeyShortcut false
            // Confirm dialogs: Enter confirms, Escape cancels. Only one is ever
            // open at a time, so run whichever action applies and clear them all.
            if (vRenameTarget != null || vDeleteTarget != null || vQuitDialog) {
                when (vKey.keyCode) {
                    kScEscape -> {
                        vRenameTarget = null; vDeleteTarget = null; vQuitDialog = false
                        return@setOnKeyShortcut true
                    }
                    kScEnter, kScKpEnter -> {
                        vRenameTarget?.let { if (vRenameText.isNotBlank()) renameRequest(it, vRenameText.trim()) }
                        vDeleteTarget?.let { deleteRequest(it) }
                        if (vQuitDialog) savePack()
                        vRenameTarget = null; vDeleteTarget = null; vQuitDialog = false
                        return@setOnKeyShortcut true
                    }
                }
            }
            val vPrimary = vKey.modifiers.ctrl || vKey.modifiers.meta
            when {
                vPrimary && vKey.keyCode == kScS -> { savePack(); true }
                vPrimary && (vKey.keyCode == kScEnter || vKey.keyCode == kScKpEnter) -> {
                    activePack()?.active?.let { send(it) }; true
                }
                vPrimary && vKey.keyCode == kScN -> { newRequest(); true }
                vPrimary && vKey.keyCode == kScW -> { activePack()?.active?.let { closeTab(it) }; true }
                else -> false
            }
        }
        onDispose { vWindow.setOnCloseRequest(null); vWindow.setOnKeyShortcut(null) }
    }

    val vC = if (vDark) DarkColors else LightColors
    val vMat = if (vDark) {
        darkColors(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    } else {
        lightColors(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    }

    MaterialTheme(colors = vMat) {
        CompositionLocalProvider(LocalAppColors provides vC) {
            val c = vC
            val vP = vPacks.getOrNull(vActivePack)

            HorizontalSplitPane(
                modifier = Modifier.background(c.bg),
                initialFirstSize = 248.dp,
                minFirstSize = 190.dp,
                minSecondSize = 520.dp,
                dividerColor = c.border,
                dividerHoverColor = c.accent,
                first = {
                    // ============
                    //  Sidebar (Pack panel)
                    Column(modifier = Modifier.fillMaxSize().background(c.panel)) {
                        // Sticky header — pack switcher + section tabs stay pinned while the list scrolls.
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PackSwitcher(
                                    inPacks = vPacks,
                                    inActive = vActivePack,
                                    inOnSelect = { vActivePack = it; vReqMsg = null },
                                    inOnClose = { closePack(it) },
                                    inOnNew = { newPack() },
                                    inOnOpen = { openPackFile() },
                                    inModifier = Modifier.weight(1f),
                                )
                                IconBtn(MaterialSymbols.Save, "Save pack", inSize = 18.dp) { savePack() }
                                OptionsMenu(
                                    inDark = vDark,
                                    inOnToggleTheme = { vDark = !vDark; persist() },
                                    inOnSaveAs = { saveAsPack() },
                                    inOnClosePack = { closePack(vActivePack) },
                                    inOnLoadDefault = { loadDefaultPack() },
                                )
                            }
                            TabBar(listOf("Requests", "History", "Env (${vP?.variables?.size ?: 0})"), vSideTab) { vSideTab = it }
                        }
                        Divider(color = c.border)

                        // Scrollable list area below the pinned header.
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (vSideTab) {
                                0 -> {
                                    if (vP == null) {
                                        Text("No pack open — use the pack menu to open or create one.", color = c.dim, fontSize = 12.sp)
                                    } else {
                                        val vReqs = vP.requests
                                        // Drop indicator: before the row now sitting at the target
                                        // slot (among the non-dragged rows), or at the very end.
                                        val vDrag = vDragRs
                                        val vShowBar = vDrag != null && vDragDy != 0f
                                        val vOthers = if (vShowBar && vDrag != null) vReqs.filter { it !== vDrag } else emptyList()
                                        val vBarBefore = if (vShowBar) vOthers.getOrNull(vDragTarget) else null
                                        val vBarAtEnd = vShowBar && vDragTarget >= vOthers.size
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            vReqs.forEach { vRs ->
                                                if (vRs === vBarBefore) RowDropBar()
                                                key(vRs) {
                                                    val vDragged = vRs === vDragRs
                                                    var vMod = Modifier
                                                        .onGloballyPositioned { vRowTop[vRs] = it.y }
                                                        .onSizeChanged { vRowH[vRs] = it.height }
                                                    // translate is draw-only (doesn't shift absoluteY), so
                                                    // the follow offset below stays correct while dragging.
                                                    if (vDragged) vMod = vMod.zIndex(1f).alpha(0.65f).translate(0f, vDragDy)
                                                    Box(
                                                        modifier = vMod.onDrag(
                                                            onStart = { _, vRelY -> vDragRs = vRs; vDragPressY = vRelY; vDragDy = 0f; vDragTarget = vReqs.indexOf(vRs) },
                                                            onDrag = { _, vRelY ->
                                                                val vd = vDragRs ?: return@onDrag
                                                                vDragDy = (vRelY - vDragPressY).toFloat()
                                                                val vCursorY = (vRowTop[vd] ?: 0) + vRelY
                                                                // Target slot = how many *other* rows the cursor passed the centre of.
                                                                var vCount = 0
                                                                vReqs.forEach { vT ->
                                                                    if (vT !== vd) {
                                                                        val vCenter = (vRowTop[vT] ?: 0) + (vRowH[vT] ?: 0) / 2
                                                                        if (vCursorY > vCenter) vCount++
                                                                    }
                                                                }
                                                                vDragTarget = vCount
                                                            },
                                                            onEnd = {
                                                                val vd = vDragRs
                                                                if (vd != null) {
                                                                    val vFrom = vReqs.indexOf(vd)
                                                                    if (vFrom >= 0 && vDragTarget >= 0 && vDragTarget != vFrom) {
                                                                        vReqs.add(vDragTarget, vReqs.removeAt(vFrom)); vP.dirty = true
                                                                    }
                                                                }
                                                                vDragRs = null; vDragDy = 0f; vDragTarget = -1
                                                            },
                                                        ),
                                                    ) {
                                                        RequestRow(
                                                            inRs = vRs,
                                                            inSelected = vRs === vP.active,
                                                            inOnOpen = { open(vRs) },
                                                            inOnRename = { vRenameTarget = vRs; vRenameText = vRs.req.name },
                                                            inOnDuplicate = { duplicate(vRs) },
                                                            inOnCopyCurl = {
                                                                currentClipboard.setText(toCurl(resolveVars(vRs.req, effective(vP))))
                                                                vReqMsg = "Copied cURL."
                                                            },
                                                            inOnDelete = { vDeleteTarget = vRs },
                                                        )
                                                    }
                                                }
                                            }
                                            if (vBarAtEnd) RowDropBar()
                                        }
                                        OutlinedAction(MaterialSymbols.Add, "New request") { newRequest() }
                                    }
                                }
                                1 -> {
                                    if (vHistory.isEmpty()) Text("No requests sent yet.", color = c.dim, fontSize = 12.sp)
                                    vHistory.forEachIndexed { vI, vH ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val vTarget = activePack() ?: return@clickable
                                                    val vRs = ReqState(vH.request.copy())
                                                    vTarget.requests.add(vRs); vTarget.openTabs.add(vRs); vTarget.active = vRs
                                                    vTarget.dirty = true; vSideTab = 0; vReqMsg = null
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            MethodTag(vH.method)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(vH.url, color = c.text, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                                                Text("#${vHistory.size - vI} · ${vH.timeMs} ms", color = c.dim, fontSize = 10.sp)
                                            }
                                            Text(if (vH.status > 0) "${vH.status}" else "ERR", color = statusColor(vH.status), fontSize = 12.sp)
                                        }
                                    }
                                }
                                else -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        MaterialSymbolsOutlined(MaterialSymbols.Tune, tint = c.accent, size = 16.dp)
                                        Text("Variables", color = c.text, fontSize = 13.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TogglePill("Pack", vEnvScope == 0) { vEnvScope = 0 }
                                        TogglePill("Global", vEnvScope == 1) { vEnvScope = 1 }
                                    }
                                    if (vEnvScope == 0) {
                                        Text("Pack variables — {{name}} in this pack.", color = c.dim, fontSize = 11.sp)
                                        if (vP == null) Text("No pack open.", color = c.dim, fontSize = 12.sp)
                                        else KeyValEditor(vP.variables) { vNew -> vP.variables.clear(); vP.variables.addAll(vNew); vP.dirty = true }
                                    } else {
                                        Text("Global variables override pack variables in every pack.", color = c.dim, fontSize = 11.sp)
                                        KeyValEditor(vGlobalEnv) { vNew -> vGlobalEnv.clear(); vGlobalEnv.addAll(vNew); persist() }
                                    }
                                }
                            }
                        }
                    }
                },
                second = {
                    // ============
                    //  Main — tab strip over a resizable request | response split
                    val vAct = vP?.active
                    if (vP == null) {
                        Column(modifier = Modifier.fillMaxSize().background(c.bg), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.weight(1f))
                            Text("No pack open.", color = c.dim)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedAction(MaterialSymbols.Add, "New pack") { newPack() }
                                OutlinedAction(MaterialSymbols.Folder, "Open pack…") { openPackFile() }
                                OutlinedAction(MaterialSymbols.Refresh, "Load default") { loadDefaultPack() }
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    } else if (vAct == null) {
                        Box(modifier = Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                            Text("No request open — pick one from the sidebar.", color = c.dim)
                        }
                    } else {
                        val vReq = vAct.req
                        Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
                            // Panel 1 — open-request tabs.
                            RequestTabStrip(
                                inTabs = vP.openTabs,
                                inActive = vAct,
                                inOnSelect = { open(it) },
                                inOnClose = { closeTab(it) },
                                inOnReorder = { vFrom, vTo -> reorderTabs(vFrom, vTo) },
                            )
                            Divider(color = c.border)

                            // Panel 2 — unified method · url · send.
                            UrlBar(
                                inReq = vReq,
                                inLoading = vAct.loading,
                                inOnMethod = { m -> edit { it.copy(method = m) } },
                                inOnUrl = { v -> edit { it.copy(url = v) } },
                                inOnSend = { send(vAct) },
                                inOnCancel = { cancel(vAct) },
                            )
                            Divider(color = c.border)

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                HorizontalSplitPane(
                                    initialFirstSize = 460.dp,
                                    minFirstSize = 320.dp,
                                    minSecondSize = 320.dp,
                                    dividerColor = c.border,
                                    dividerHoverColor = c.accent,
                                    first = {
                                        // Panel 3 — request building.
                                        RequestBuilder(
                                            inReq = vReq,
                                            inRs = vAct,
                                            inUnresolved = unresolvedVars(vReq, effective(vP)),
                                            inMsg = vReqMsg,
                                            inEdit = { t -> edit(t) },
                                        )
                                    },
                                    second = {
                                        // Panel 4 — Request / Response viewer.
                                        ViewerPanel(
                                            inRs = vAct,
                                            inResolved = resolveVars(vReq, effective(vP)),
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
            )

            // ============
            //  Dialogs
            val vRen = vRenameTarget
            if (vRen != null) {
                Dialog(onDismissRequest = { vRenameTarget = null }) {
                    Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(360.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Rename request", color = c.text, fontSize = 16.sp)
                            ThinField(vRenameText, { vRenameText = it }, inModifier = Modifier.fillMaxWidth(), inPlaceholder = "Name")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    if (vRenameText.isNotBlank()) renameRequest(vRen, vRenameText.trim())
                                    vRenameTarget = null
                                }) { BtnContent(MaterialSymbols.Check, "Save", c.onAccent) }
                                OutlinedButton(onClick = { vRenameTarget = null }) { Text("Cancel", color = c.text) }
                            }
                        }
                    }
                }
            }

            val vDel = vDeleteTarget
            if (vDel != null) {
                val vName = vDel.req.name
                Dialog(onDismissRequest = { vDeleteTarget = null }) {
                    Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(360.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MaterialSymbolsOutlined(MaterialSymbols.Delete, tint = methodColor(ReqMethod.DELETE), size = 20.dp)
                                Text("Delete request", color = c.text, fontSize = 16.sp)
                            }
                            Text("\"$vName\" will be removed from the pack. This can't be undone.", color = c.dim, fontSize = 13.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { vDeleteTarget = null }) { Text("Cancel", color = c.text) }
                                    DangerButton("Delete", MaterialSymbols.Delete) { deleteRequest(vDel); vDeleteTarget = null }
                                }
                            }
                        }
                    }
                }
            }

            if (vQuitDialog) {
                val vDirty = vPacks.filter { it.dirty }.joinToString(", ") { it.name }
                Dialog(onDismissRequest = { vQuitDialog = false }) {
                    Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(440.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MaterialSymbolsOutlined(MaterialSymbols.Warning, tint = kWarnColor, size = 20.dp)
                                Text("Unsaved changes", color = c.text, fontSize = 18.sp)
                            }
                            Text("Unsaved edits in: $vDirty.", color = c.text, fontSize = 13.sp)
                            Text("Your session is kept and restored next launch, but export a pack to a .json file to save it permanently.", color = c.dim, fontSize = 12.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { vQuitDialog = false }) { Text("Keep editing", color = c.text) }
                                    Button(onClick = { vQuitDialog = false; savePack() }) { BtnContent(MaterialSymbols.Save, "Save pack", c.onAccent) }
                                    DangerButton("Quit anyway", MaterialSymbols.Close) { persist(); vWindow.close() }
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
// MARK: Pack switcher (active pack name → dropdown of open packs)
// ==================

@Composable
private fun PackSwitcher(
    inPacks: List<PackState>,
    inActive: Int,
    inOnSelect: (Int) -> Unit,
    inOnClose: (Int) -> Unit,
    inOnNew: () -> Unit,
    inOnOpen: () -> Unit,
    inModifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    val vActivePack = inPacks.getOrNull(inActive)
    Box(modifier = inModifier) {
        Row(
            modifier = Modifier.fillMaxWidth().menuAnchor(vAnchor).clip(RoundedCornerShape(6.dp))
                .clickable { vOpen = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(vActivePack?.name ?: "No pack", color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (vActivePack?.dirty == true) Box(Modifier.size(6.dp).background(c.accent, RoundedCornerShape(3.dp)))
            MaterialSymbolsOutlined(MaterialSymbols.ExpandMore, tint = c.dim, size = 18.dp)
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, minWidth = 224.dp) {
            inPacks.forEachIndexed { vI, vP ->
                DropdownMenuItem(onClick = { vOpen = false; inOnSelect(vI) }) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vI == inActive) MaterialSymbolsOutlined(MaterialSymbols.Check, tint = c.accent, size = 16.dp)
                        else Spacer(Modifier.width(16.dp))
                        Text(vP.name + if (vP.dirty) " •" else "", color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconBtn(MaterialSymbols.Close, "Close pack", inSize = 14.dp) { vOpen = false; inOnClose(vI) }
                    }
                }
            }
            Divider(color = c.border)
            DropdownMenuItem(onClick = { vOpen = false; inOnNew() }) { MenuRow(MaterialSymbols.Add, "New pack") }
            DropdownMenuItem(onClick = { vOpen = false; inOnOpen() }) { MenuRow(MaterialSymbols.Folder, "Open pack…") }
        }
    }
}

// ==================
// MARK: Sidebar request row (burger menu: rename / duplicate / delete)
// ==================

@Composable
private fun RequestRow(
    inRs: ReqState,
    inSelected: Boolean,
    inOnOpen: () -> Unit,
    inOnRename: () -> Unit,
    inOnDuplicate: () -> Unit,
    inOnCopyCurl: () -> Unit,
    inOnDelete: () -> Unit,
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
            .background(if (inSelected) c.accent.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(6.dp))
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
                DropdownMenuItem(onClick = { vMenu = false; inOnRename() }) { MenuRow(MaterialSymbols.Edit, "Rename") }
                DropdownMenuItem(onClick = { vMenu = false; inOnDuplicate() }) { MenuRow(MaterialSymbols.FileCopy, "Duplicate") }
                DropdownMenuItem(onClick = { vMenu = false; inOnCopyCurl() }) { MenuRow(MaterialSymbols.Terminal, "Copy as cURL") }
                DropdownMenuItem(onClick = { vMenu = false; inOnDelete() }) { MenuRow(MaterialSymbols.Delete, "Delete", methodColor(ReqMethod.DELETE)) }
            }
        }
    }
}

@Composable
private fun MenuRow(inIcon: Int, inLabel: String, inColor: Color? = null) {
    val c = LocalAppColors.current
    val vCol = inColor ?: c.text
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbolsOutlined(inIcon, tint = vCol, size = 16.dp)
        Text(inLabel, color = vCol, fontSize = 13.sp)
    }
}

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
private fun RequestTabStrip(
    inTabs: List<ReqState>,
    inActive: ReqState?,
    inOnSelect: (ReqState) -> Unit,
    inOnClose: (ReqState) -> Unit,
    inOnReorder: (Int, Int) -> Unit,
) {
    val c = LocalAppColors.current
    val vLeft = remember { mutableStateMapOf<ReqState, Int>() }   // window-x of each tab's left edge
    val vWidth = remember { mutableStateMapOf<ReqState, Int>() }  // measured tab width
    var vDragging by remember { mutableStateOf<ReqState?>(null) }
    var vPressRelX by remember { mutableStateOf(0) }              // press point inside the tab
    var vDragDx by remember { mutableStateOf(0f) }               // visual follow offset
    var vDragTarget by remember { mutableStateOf(-1) }           // slot to drop into

    // Where the drop indicator goes: before the tab now sitting at the target
    // slot (among the non-dragged tabs), or at the very end.
    val vDrag = vDragging
    val vShowBar = vDrag != null && vDragDx != 0f
    val vOthers = if (vShowBar && vDrag != null) inTabs.filter { it !== vDrag } else emptyList()
    val vBarBefore = if (vShowBar) vOthers.getOrNull(vDragTarget) else null
    val vBarAtEnd = vShowBar && vDragTarget >= vOthers.size

    Row(
        modifier = Modifier.fillMaxWidth().background(c.panel)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inTabs.forEach { vRs ->
            if (vRs === vBarBefore) DropBar()
            key(vRs) {
                val vSel = vRs === inActive
                val vReq = vRs.req
                val vDragged = vRs === vDragging

                var vMod = Modifier
                    .onGloballyPositioned { vLeft[vRs] = it.x }
                    .onSizeChanged { vWidth[vRs] = it.width }
                if (vDragged) vMod = vMod.zIndex(1f).alpha(0.65f).translate(vDragDx, 0f)
                vMod = vMod
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (vSel) c.accent.copy(alpha = 0.20f) else c.field, RoundedCornerShape(7.dp))
                    .border(1.dp, if (vSel || vDragged) c.accent else c.border, RoundedCornerShape(7.dp))
                    .onDrag(
                        onStart = { vRelX, _ ->
                            vDragging = vRs; vPressRelX = vRelX; vDragDx = 0f; vDragTarget = inTabs.indexOf(vRs)
                        },
                        onDrag = { vRelX, _ ->
                            val vd = vDragging ?: return@onDrag
                            vDragDx = (vRelX - vPressRelX).toFloat()
                            val vCursorX = (vLeft[vd] ?: 0) + vRelX
                            // Drop slot = how many *other* tabs the cursor has passed the centre of.
                            var vCount = 0
                            inTabs.forEach { vT ->
                                if (vT !== vd) {
                                    val vCenter = (vLeft[vT] ?: 0) + (vWidth[vT] ?: 0) / 2
                                    if (vCursorX > vCenter) vCount++
                                }
                            }
                            vDragTarget = vCount
                        },
                        onEnd = {
                            val vd = vDragging
                            if (vd != null) {
                                val vFrom = inTabs.indexOf(vd)
                                if (vFrom >= 0 && vDragTarget >= 0 && vDragTarget != vFrom) inOnReorder(vFrom, vDragTarget)
                            }
                            vDragging = null; vDragDx = 0f; vDragTarget = -1
                        },
                    )
                    .clickable { inOnSelect(vRs) }
                    .padding(start = 9.dp, top = 6.dp, bottom = 6.dp, end = 3.dp)

                Row(
                    modifier = vMod,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(7.dp).background(methodColor(vReq.method), RoundedCornerShape(4.dp)))
                    Text(vReq.name, color = if (vSel) c.text else c.dim, fontSize = 13.sp)
                    if (vRs.loading) CircularProgressIndicator(modifier = Modifier.size(12.dp), color = c.accent, strokeWidth = 1.5.dp)
                    IconBtn(MaterialSymbols.Close, "Close", inSize = 13.dp) { inOnClose(vRs) }
                }
            }
        }
        if (vBarAtEnd) DropBar()
    }
}

/* Thin accent bar marking where a dragged tab will be inserted. Rendered in-flow
   between tabs so the Row centres it vertically and reserves a little space. */
@Composable
private fun DropBar() {
    val c = LocalAppColors.current
    Box(modifier = Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(c.accent, RoundedCornerShape(2.dp)))
}

// ==================
// MARK: Options menu (theme + pack-file actions)
// ==================

@Composable
private fun OptionsMenu(
    inDark: Boolean,
    inOnToggleTheme: () -> Unit,
    inOnSaveAs: () -> Unit,
    inOnClosePack: () -> Unit,
    inOnLoadDefault: () -> Unit,
) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Box {
        IconBtn(MaterialSymbols.Settings, "Options", inModifier = Modifier.menuAnchor(vAnchor)) { vOpen = true }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, minWidth = 200.dp) {
            DropdownMenuItem(onClick = { if (!inDark) inOnToggleTheme(); vOpen = false }) {
                Text("Dark mode", color = if (inDark) c.accent else c.text, fontSize = 13.sp)
            }
            DropdownMenuItem(onClick = { if (inDark) inOnToggleTheme(); vOpen = false }) {
                Text("Light mode", color = if (!inDark) c.accent else c.text, fontSize = 13.sp)
            }
            Divider(color = c.border)
            DropdownMenuItem(onClick = { vOpen = false; inOnSaveAs() }) { MenuRow(MaterialSymbols.Save, "Save pack as…") }
            DropdownMenuItem(onClick = { vOpen = false; inOnClosePack() }) { MenuRow(MaterialSymbols.Close, "Close pack") }
            Divider(color = c.border)
            DropdownMenuItem(onClick = { vOpen = false; inOnLoadDefault() }) { MenuRow(MaterialSymbols.Refresh, "Load default pack") }
        }
    }
}

// ==================
// MARK: Method picker (dropdown)
// ==================

/* Panel 2 — one unified bar: a method dropdown (coloured label + unfold arrows),
   a borderless URL field that melts into the bar, and Send (or Cancel). */
@Composable
private fun UrlBar(
    inReq: ApiRequest,
    inLoading: Boolean,
    inOnMethod: (ReqMethod) -> Unit,
    inOnUrl: (String) -> Unit,
    inOnSend: () -> Unit,
    inOnCancel: () -> Unit,
) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            Row(
                modifier = Modifier.menuAnchor(vAnchor).clip(RoundedCornerShape(6.dp))
                    .clickable { vOpen = true }.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(inReq.method.name, color = methodColor(inReq.method), fontSize = 14.sp)
                MaterialSymbolsOutlined(MaterialSymbols.UnfoldMore, tint = methodColor(inReq.method), size = 16.dp)
            }
            DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor) {
                ReqMethod.entries.forEach { vM ->
                    DropdownMenuItem(onClick = { inOnMethod(vM); vOpen = false }) {
                        Text(vM.name, color = methodColor(vM), fontSize = 13.sp)
                    }
                }
            }
        }

        // Borderless URL field — no box, so it reads as part of the bar.
        Box(
            modifier = Modifier.weight(1f).onKeyEvent { ev ->
                if (ev.key.type == KeyEventType.Down && (ev.key.keyCode == kScEnter || ev.key.keyCode == kScKpEnter)) { inOnSend(); true } else false
            },
        ) {
            if (inReq.url.isEmpty()) Text("https://example.com/path", color = c.dim, fontSize = 14.sp)
            BasicTextField(
                value = inReq.url,
                onValueChange = inOnUrl,
                color = c.text,
                cursorColor = c.accent,
                selectionColor = c.accent.copy(alpha = 0.35f),
                fontSize = 14.sp,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (inLoading) DangerButton("Cancel", MaterialSymbols.Stop) { inOnCancel() }
        else Button(onClick = inOnSend) { BtnContent(MaterialSymbols.Send, "Send", c.onAccent) }
    }
}

/* Bottom-of-panel-3 dropdown choosing the body type (None/Json/Text/Form/File). */
@Composable
private fun BodyTypeMenu(inType: BodyType, inEnabled: Boolean, inOnPick: (BodyType) -> Unit) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.menuAnchor(vAnchor).clip(RoundedCornerShape(6.dp))
                .border(1.dp, c.border, RoundedCornerShape(6.dp))
                .clickable { if (inEnabled) vOpen = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(inType.name, color = if (inEnabled) c.text else c.dim, fontSize = 13.sp)
            MaterialSymbolsOutlined(MaterialSymbols.UnfoldMore, tint = c.dim, size = 15.dp)
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor) {
            BodyType.entries.forEach { vT ->
                DropdownMenuItem(onClick = { inOnPick(vT); vOpen = false }) {
                    Text(vT.name, color = if (vT == inType) c.accent else c.text, fontSize = 13.sp)
                }
            }
        }
    }
}

// ==================
// MARK: Panel 3 — request builder (Query / Headers / Body)
// ==================

/* Tabs to edit query params, headers and the body, a Preview toggle top-right,
   and a body-type dropdown pinned at the bottom. */
@Composable
private fun RequestBuilder(
    inReq: ApiRequest,
    inRs: ReqState,
    inUnresolved: List<String>,
    inMsg: String?,
    inEdit: ((ApiRequest) -> ApiRequest) -> Unit,
) {
    val c = LocalAppColors.current
    val vBodySet = inReq.method.allowsBody && when (inReq.bodyType) {
        BodyType.NONE -> false
        BodyType.FORM -> inReq.form.any { it.enabled && it.key.isNotBlank() }
        else -> inReq.body.isNotBlank()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (inUnresolved.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbolsOutlined(MaterialSymbols.Warning, tint = kWarnColor, size = 15.dp)
                Text("Undefined: ${inUnresolved.joinToString(", ") { "{{$it}}" }}", color = kWarnColor, fontSize = 11.sp)
            }
        }

        // Tabs, with the status message and a Preview toggle (icon only) on the
        // same line — the toggle no longer needs its own row.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabBar(
                listOf("Query (${inReq.params.size})", "Headers (${inReq.headers.size})", "Body"),
                inRs.reqTab,
                inDots = if (vBodySet) setOf(2) else emptySet(),
            ) { inRs.reqTab = it }
            Spacer(Modifier.weight(1f))
            inMsg?.let {
                Text(it, color = c.dim, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
            }
            val vPreviewOn = inRs.preview
            // Nudge up ~2dp so the icon's centre lines up with the tab text's
            // optical centre (the tab labels' descenders pull their geometric
            // centre down). offset is post-layout, so the row doesn't shift.
            Box(
                modifier = Modifier.offset(y = (-2).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (vPreviewOn) c.accent.copy(alpha = 0.20f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { inRs.preview = !inRs.preview; if (inRs.preview) inRs.viewTab = 0 }
                    .padding(6.dp),
            ) {
                MaterialSymbolsOutlined(MaterialSymbols.Visibility, contentDescription = "Preview", tint = if (vPreviewOn) c.accent else c.dim, size = 16.dp)
            }
        }
        Divider(color = c.border)

        // Tab content — scrolls.
        Box(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (inRs.reqTab) {
                0 -> KeyValEditor(inReq.params) { v -> inEdit { it.copy(params = v) } }
                1 -> KeyValEditor(inReq.headers) { v -> inEdit { it.copy(headers = v) } }
                else -> BodyContent(inReq, inRs) { v -> inEdit(v) }
            }
        }

        // Body-type selector — only on the Body tab, pinned at the bottom.
        if (inRs.reqTab == 2) {
            Divider(color = c.border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Body", color = c.dim, fontSize = 12.sp)
                BodyTypeMenu(inReq.bodyType, true) { vT ->
                    inEdit { it.copy(bodyType = vT) }
                    // Re-seed the highlighter format from bodyType so JSON →
                    // JSON colours kicks in automatically when the user picks
                    // it from the body-type menu.
                    inRs.reqFormat = when (vT) {
                        BodyType.JSON -> BodyFormat.JSON
                        BodyType.TEXT -> inRs.reqFormat
                        else -> BodyFormat.RAW
                    }
                }
                // Format selector — only relevant for JSON / TEXT bodies.
                if (inReq.bodyType == BodyType.JSON || inReq.bodyType == BodyType.TEXT) {
                    BodyFormatSelector(inSelected = inRs.reqFormat, inOnChange = { inRs.reqFormat = it })
                }
            }
        }
    }
}

/* The body editing area for the current body type (driven by the bottom menu). */
@Composable
private fun BodyContent(inReq: ApiRequest, inRs: ReqState, inEdit: ((ApiRequest) -> ApiRequest) -> Unit) {
    when (inReq.bodyType) {
        BodyType.NONE -> ViewerEmpty(MaterialSymbols.Block, "No Body", Modifier.fillMaxWidth().height(240.dp))
        BodyType.JSON, BodyType.TEXT -> BodyView(
            inText = inReq.body,
            modifier = Modifier.fillMaxWidth().height(240.dp),
            inOnChange = { v -> inEdit { it.copy(body = v) } },
            inPlaceholder = if (inReq.bodyType == BodyType.JSON) "{ }" else "text body",
            inFormat = inRs.reqFormat,
        )
        BodyType.FORM -> KeyValEditor(inReq.form) { v -> inEdit { it.copy(form = v) } }
        BodyType.FILE -> FileBody(inReq) { v -> inEdit(v) }
    }
}

/* FILE body — pick a file; its path is stored in body and sent as raw bytes. */
@Composable
private fun FileBody(inReq: ApiRequest, inEdit: ((ApiRequest) -> ApiRequest) -> Unit) {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedAction(MaterialSymbols.InsertDriveFile, "Choose file…") {
            showOpenFileDialog { vPath -> if (vPath != null) inEdit { it.copy(body = vPath) } }
        }
        if (inReq.body.isNotBlank()) Text(inReq.body, color = c.text, fontSize = 12.sp)
        else Text("No file selected.", color = c.dim, fontSize = 12.sp)
    }
}

// ==================
// MARK: Response
// ==================

/* Panel 4 — Request / Response viewer. Each tab stacks a HEADERS section over a
   BODY section. "Request" shows the resolved request that would be sent (the
   Preview); "Response" shows the result (image-aware). A copy / save toolbar
   acts on whichever view is showing. */
@Composable
private fun ViewerPanel(inRs: ReqState, inResolved: ApiRequest) {
    val c = LocalAppColors.current
    var vMsg by remember { mutableStateOf<String?>(null) }
    var vHeadersCollapsed by remember { mutableStateOf(false) }
    val vResp = inRs.response
    val vLoading = inRs.loading
    val vPreview = inRs.preview
    val vShowRequest = vPreview || inRs.viewTab == 0

    val vRespBody = if (vResp != null) (vResp.error ?: prettyJsonOrRaw(vResp.body).take(20000)) else ""
    val vRespImage = vResp?.isImage == true && vResp.error == null && inRs.imageKey != null

    Column(modifier = Modifier.fillMaxSize().background(c.panel)) {
        // ============
        //  Top bar: "Request GET" / "Response 200" tabs, plus loading / cancel.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (vPreview) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MaterialSymbolsOutlined(MaterialSymbols.Visibility, tint = c.accent, size = 16.dp)
                    Text("PREVIEW", color = c.accent, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("not sent", color = kWarnColor, fontSize = 11.sp)
            } else {
                // Request tab — adds the coloured method only once the
                // request has actually been sent (sentReq != null).
                val vSent = inRs.sentReq
                ViewerTab(
                    inLabel = "Request",
                    inAccent = vSent?.method?.name,
                    inAccentColor = vSent?.let { methodColor(it.method) } ?: c.dim,
                    inSelected = inRs.viewTab == 0,
                    inOnClick = { inRs.viewTab = 0 },
                )
                // Response tab — adds the coloured status only when a
                // response (or error) is in. No placeholder during the
                // pre-send / loading state.
                val (vRespAccent, vRespColor) = when {
                    vResp?.error != null -> "FAILED" to statusColor(0)
                    vResp != null        -> vResp.status.toString() to statusColor(vResp.status)
                    else                 -> null to c.dim
                }
                ViewerTab(
                    inLabel = "Response",
                    inAccent = vRespAccent,
                    inAccentColor = vRespColor,
                    inSelected = inRs.viewTab == 1,
                    inOnClick = { inRs.viewTab = 1 },
                )
                Spacer(Modifier.weight(1f))
                // Spinner only — the Cancel control already lives next to
                // Send in the URL bar; no need for a duplicate here.
                if (inRs.viewTab == 1 && vLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = c.accent, strokeWidth = 2.dp)
                }
            }
        }

        // The request shown: live resolved in Preview, else the snapshot we sent.
        val vReqShown = if (vPreview) inResolved else inRs.sentReq

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                vShowRequest && vReqShown == null -> ViewerEmpty(MaterialSymbols.Send, "Not sent")
                vShowRequest -> {
                    val vR = vReqShown!!
                    val vSentHeaders = if (!vPreview) inRs.response?.requestHeaders?.takeIf { it.isNotEmpty() } else null
                    // Synthesize wire-level headers (Host, Content-Length, User-Agent)
                    // the engine adds without telling Ktor — same set httpie shows.
                    val vRawHeaders = if (vSentHeaders != null) vSentHeaders else parseHeaderLines(requestHeadersText(vR))
                    val vHeaders = synthesizeRequestHeaders(vR, vRawHeaders)
                    val vBody = if (!vR.method.allowsBody || vR.bodyType == BodyType.NONE) null else requestBodyText(vR)
                    HttpFlowView(
                        inStatusLine = formatRequestLine(vR, inRs.response?.httpVersion ?: "HTTP/1.1", c),
                        inStatusLineAccentColor = methodColor(vR.method),
                        inHeaders = vHeaders,
                        inBody = vBody,
                        inIsImage = false,
                        inImagePainter = null,
                        inHeadersCollapsed = vHeadersCollapsed,
                        inOnToggleCollapse = { vHeadersCollapsed = !vHeadersCollapsed },
                        inShowSecureLock = isTlsValidated(vR.url, inRs.response),
                    )
                }
                vResp == null && !vLoading -> ViewerEmpty(MaterialSymbols.Download, "Not received")
                else -> {
                    val vHeaders = vResp?.headers ?: emptyList()
                    val vBody = if (vLoading) "…" else vRespBody
                    val vAutoFmt = autoFormatFor(vResp?.contentType)
                    val vBodyFmt = inRs.respFormatOverride ?: vAutoFmt
                    HttpFlowView(
                        inStatusLine = formatStatusLine(vResp, c),
                        inStatusLineAccentColor = if (vResp != null) statusColor(vResp.status) else c.dim,
                        inHeaders = vHeaders,
                        inBody = if (vRespImage) null else vBody,
                        inIsImage = vRespImage && !vLoading,
                        inImagePainter = if (vRespImage && !vLoading) {
                            val vKind = if (vResp?.contentType?.contains("svg", ignoreCase = true) == true) ResourceKind.Svg else ResourceKind.Raster
                            painterResource(inRs.imageKey!!, vKind)
                        } else null,
                        inHeadersCollapsed = vHeadersCollapsed,
                        inOnToggleCollapse = { vHeadersCollapsed = !vHeadersCollapsed },
                        inShowSecureLock = isTlsValidated(inRs.sentReq?.url ?: inResolved.url, vResp),
                        inBodyFormat = vBodyFmt,
                    )
                }
            }
        }

        // ============
        //  Bottom bar: status message on the left, timing/size + 3-dot
        //  overflow menu on the right.
        val vReqHasContent = vShowRequest && vReqShown != null
        val vRespHasContent = !vShowRequest && vResp != null
        if (vReqHasContent || vRespHasContent) {
            Divider(color = c.border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Response viewer: format selector for the body. Auto-detected
                // from Content-Type until the user overrides, then pinned.
                if (vRespHasContent && vResp != null && !vRespImage) {
                    val vAuto = autoFormatFor(vResp.contentType)
                    val vCurrent = inRs.respFormatOverride ?: vAuto
                    BodyFormatSelector(
                        inSelected = vCurrent,
                        inOnChange = { vNew ->
                            inRs.respFormatOverride = if (vNew == vAuto) null else vNew
                        },
                        inAutoLabel = if (inRs.respFormatOverride == null) vAuto.name else null,
                    )
                }
                vMsg?.let { Text(it, color = c.dim, fontSize = 11.sp) }
                Spacer(Modifier.weight(1f))
                if (vRespHasContent && vResp != null && vResp.error == null) {
                    Text(formatTimingSize(vResp), color = c.dim, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                }
                ViewerOverflowMenu(
                    inIsRequestTab = vShowRequest,
                    inRequest = if (vReqHasContent) vReqShown else null,
                    inSentRequestHeaders = inRs.response?.requestHeaders?.takeIf { it.isNotEmpty() },
                    inResponse = if (vRespHasContent) vResp else null,
                    inIsImage = vRespHasContent && vRespImage,
                    inResponseBody = vRespBody,
                    inOnMessage = { vMsg = it },
                    inOnClear = {
                        inRs.imageKey?.let { removeMemoryResource(it) }
                        inRs.imageKey = null
                        inRs.response = null
                        inRs.sentReq = null
                        inRs.preview = false
                    },
                )
            }
        }
    }
}

// ==================
// MARK: HttpFlowView — flat httpie-style request/response layout
// ==================

/* Renders the body of a Request or Response tab in a httpie-flavoured
   layout: status line (with collapse arrow) + headers table + raw body
   inline, no card / rounded-rect chrome around each section. The
   headers section can be collapsed via the arrow. */
@Composable
private fun HttpFlowView(
    inStatusLine: AnnotatedString,
    @Suppress("UNUSED_PARAMETER") inStatusLineAccentColor: Color,
    inHeaders: List<Pair<String, String>>,
    inBody: String?,
    inIsImage: Boolean,
    inImagePainter: Painter?,
    inHeadersCollapsed: Boolean,
    inOnToggleCollapse: () -> Unit,
    inShowSecureLock: Boolean = false,
    inBodyFormat: BodyFormat = BodyFormat.RAW,
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        // Status line: collapse arrow + optional lock + status text.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { inOnToggleCollapse() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbolsOutlined(
                icon = if (inHeadersCollapsed) MaterialSymbols.ChevronRight else MaterialSymbols.ExpandMore,
                tint = c.dim,
                size = 16.dp,
            )
            Spacer(Modifier.width(6.dp))
            if (inShowSecureLock) {
                MaterialSymbolsOutlined(
                    icon = MaterialSymbols.Lock,
                    tint = Color(0xFF36B37E),  // green — TLS verified by OS
                    size = 14.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(inStatusLine, color = c.text, fontSize = 13.sp)
        }
        // Headers as a key/value table — only when not collapsed.
        if (!inHeadersCollapsed && inHeaders.isNotEmpty()) {
            HeaderTable(inHeaders, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        if (inBody != null || inIsImage) {
            Divider(color = c.border, modifier = Modifier.padding(vertical = 8.dp))
            if (inIsImage && inImagePainter != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Image(painter = inImagePainter, contentDescription = "Response image", contentScale = ContentScale.Fit)
                }
            } else if (inBody != null) {
                BodyView(
                    inText = inBody,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    inFormat = inBodyFormat,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/* Header table: each row is (key | value) where the key column has a
   fixed width so values line up. Long keys wrap; long values wrap too.
   Keys render in accent colour, values in regular text. */
@Composable
private fun HeaderTable(inHeaders: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((vK, vV) in inHeaders) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = titleCaseHeader(vK),
                    color = c.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.width(220.dp).padding(end = 12.dp),
                )
                Text(
                    text = vV,
                    color = c.text,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/* Body view with line numbers in the gutter — mimics the code panel
   in httpie. Numbers are dim; body text uses the regular colour. */
@Composable
private fun BodyView(
    inText: String,
    modifier: Modifier = Modifier,
    inOnChange: ((String) -> Unit)? = null,
    inPlaceholder: String = "",
    inFormat: BodyFormat = BodyFormat.RAW,
) {
    val c = LocalAppColors.current
    val vLines = if (inText.isEmpty()) listOf("") else inText.split('\n')
    // Tight gutter: width = digits in the max line number × glyph width at
    // 12sp (~7dp/digit for monospace digits). One char of padding either
    // side keeps the numbers from kissing the body text or the panel edge.
    val vDigits = vLines.size.toString().length
    val vGutterWidth = (vDigits * 7 + 4).dp
    Row(modifier = modifier) {
        // Numbers are reference-only — half-alpha so they stay legible
        // without competing with the body content for attention.
        val vNumColor = c.dim.copy(alpha = 0.45f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(vGutterWidth),
        ) {
            for (vI in vLines.indices) {
                Text("${vI + 1}", color = vNumColor, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(6.dp))
        // Body. Two render paths:
        //  - RAW or editable: BasicTextField, selectable/editable, no
        //    syntax colouring (BasicTextField can't show per-char spans).
        //  - JSON / XML / HTML / YAML read-only: Text(AnnotatedString)
        //    with the tokeniser's colour spans. Selection is lost but the
        //    overflow menu's Copy actions cover that case.
        Box(modifier = Modifier.weight(1f)) {
            if (inText.isEmpty() && inPlaceholder.isNotEmpty()) {
                Text(inPlaceholder, color = c.dim, fontSize = 12.sp)
            }
            if (inFormat == BodyFormat.RAW || inOnChange != null) {
                BasicTextField(
                    value = inText,
                    onValueChange = inOnChange ?: {},
                    readOnly = inOnChange == null,
                    color = c.text, cursorColor = c.accent, selectionColor = c.accent.copy(alpha = 0.35f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Pick a palette by background luminance — light theme gets
                // dark-on-light VS Code colours, dark gets the inverse.
                val vPalette = SyntaxPalette.forDark(isDarkBg(c.bg))
                Text(highlight(inText, inFormat, vPalette), color = c.text, fontSize = 12.sp)
            }
        }
    }
}

/* "Request" + colored "GET" tab. inAccent is shown next to the label
   when non-null (e.g. method name once sent, status code once received);
   when null the tab is just the label, no placeholder dash. Selected
   tab gets full-strength labels and a 2dp underline; unselected tabs
   stay dimmed. */
@Composable
private fun ViewerTab(
    inLabel: String,
    inAccent: String?,
    inAccentColor: Color,
    inSelected: Boolean,
    inOnClick: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.clickable { inOnClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(inLabel, color = if (inSelected) c.dim else c.dim.copy(alpha = 0.6f), fontSize = 14.sp)
            if (inAccent != null) {
                Text(inAccent, color = if (inSelected) inAccentColor else inAccentColor.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(60.dp)
                .background(if (inSelected) c.accent else Color.Transparent),
        ) {}
    }
}

// ==================
// MARK: Helpers — header parsing / formatting
// ==================

/* "HTTP/1.1   200 OK" status line for a Response, with the protocol
   token dimmed (it's structural) and the status code + reason in the
   status colour. Just "HTTP/1.1" pre-response — no placeholder. Triple-
   spaced to match the request-line formatting. */
private fun formatStatusLine(inResp: ApiResponse?, inColors: AppColors): AnnotatedString {
    if (inResp == null) return buildAnnotatedString {
        pushStyle(SpanStyle(color = inColors.dim))
        append(inResp?.httpVersion ?: "HTTP/1.1")
        pop()
    }
    if (inResp.error != null) return buildAnnotatedString {
        pushStyle(SpanStyle(color = inColors.dim))
        append(inResp?.httpVersion ?: "HTTP/1.1")
        pop()
        append("   ")
        pushStyle(SpanStyle(color = Color(0xFFFF5630), fontWeight = FontWeight.Bold))
        append("FAILED")
        pop()
    }
    return buildAnnotatedString {
        pushStyle(SpanStyle(color = inColors.dim))
        append(inResp?.httpVersion ?: "HTTP/1.1")
        pop()
        append("   ")
        pushStyle(SpanStyle(color = statusColor(inResp.status), fontWeight = FontWeight.Bold))
        append("${inResp.status} ${inResp.statusText.uppercase()}")
        pop()
    }
}

/* "GET   /path   HTTP/1.1" status line for a Request. Each part takes
   its own colour: method in its canonical method colour, URL in the
   default text colour, protocol token dimmed so the eye lands on the
   request target first. Triple-spaced so the columns read at a glance.
   The protocol string comes from the matching response when we have
   one (so HTTP/2 servers show "HTTP/2"); fallback "HTTP/1.1" before
   send. */
private fun formatRequestLine(inReq: ApiRequest, inHttpVersion: String, inColors: AppColors): AnnotatedString = buildAnnotatedString {
    pushStyle(SpanStyle(color = methodColor(inReq.method), fontWeight = FontWeight.Bold))
    append(inReq.method.name)
    pop()
    append("   ")
    pushStyle(SpanStyle(color = inColors.text))
    // urlWithParams appends the Query-tab key/values percent-encoded;
    // urlPathOnly then strips scheme+host so the request line reads
    // "GET /search?q=foo&n=10 HTTP/1.1" like httpie / curl trace.
    append(urlPathOnly(urlWithParams(inReq)).ifEmpty { "/" })
    pop()
    append("   ")
    pushStyle(SpanStyle(color = inColors.dim))
    append(inHttpVersion)
    pop()
}

/* Pull the path (+ query) out of a URL, dropping scheme + host. The
   request line in HTTP is "METHOD path HTTP/x.y" — the host lives on a
   separate Host: header line, not in the path. Falls back to the input
   string when no "://" is present (relative URL). */
private fun urlPathOnly(inUrl: String): String {
    val vIdx = inUrl.indexOf("://")
    if (vIdx < 0) return inUrl
    val vAfterScheme = inUrl.substring(vIdx + 3)
    val vSlash = vAfterScheme.indexOf('/')
    return if (vSlash < 0) "/" else vAfterScheme.substring(vSlash)
}

/* Extract the host (+ port) from a URL — used to synthesize a Host
   header for display when the engine didn't surface one. */
private fun urlHost(inUrl: String): String? {
    val vIdx = inUrl.indexOf("://")
    if (vIdx < 0) return null
    val vAfterScheme = inUrl.substring(vIdx + 3)
    val vSlash = vAfterScheme.indexOf('/')
    val vAuthority = if (vSlash < 0) vAfterScheme else vAfterScheme.substring(0, vSlash)
    return vAuthority.ifEmpty { null }
}

/* The user-agent string our Darwin engine sends is opaque to Ktor —
   NSURLSession picks the default. Match what httpie does: identify
   ourselves so the wire log isn't missing the field entirely. */
private const val kUserAgent: String = "compose-apidemo/1.0"

/* Combine Ktor's reported request headers with the ones the engine
   adds at the wire level (Host, Content-Length, User-Agent) so the
   Request tab shows the actual on-the-wire header set rather than
   just the subset Ktor sees. Sorted alphabetically. */
private fun synthesizeRequestHeaders(
    inReq: ApiRequest,
    inReported: List<Pair<String, String>>,
): List<Pair<String, String>> {
    val vByKey = inReported.associate { it.first.lowercase() to it }.toMutableMap()
    urlHost(inReq.url)?.let { vHost ->
        vByKey.getOrPut("host") { "Host" to vHost }
    }
    if (!vByKey.containsKey("user-agent")) {
        vByKey["user-agent"] = "User-Agent" to kUserAgent
    }
    if (inReq.method.allowsBody && inReq.bodyType != BodyType.NONE && !vByKey.containsKey("content-length")) {
        val vLen = computedBodyLength(inReq)
        if (vLen != null) vByKey["content-length"] = "Content-Length" to vLen.toString()
    }
    return vByKey.values.sortedBy { it.first.lowercase() }
}

/* Body length in bytes for the headers synthesis. JSON / TEXT use the
   raw UTF-8 byte count; FORM serialises and counts; FILE skips
   (loading the file just for the count would be wasteful — the engine
   sets the field anyway). */
private fun computedBodyLength(inReq: ApiRequest): Int? = when (inReq.bodyType) {
    BodyType.JSON, BodyType.TEXT -> inReq.body.encodeToByteArray().size
    BodyType.FORM -> formEncode(inReq.form).encodeToByteArray().size
    BodyType.FILE, BodyType.NONE -> null
}

/* Format the timing / size footer text shown bottom-right. */
private fun formatTimingSize(inResp: ApiResponse): String {
    val vSize = when {
        inResp.sizeBytes < 1024 -> "${inResp.sizeBytes} B"
        inResp.sizeBytes < 1024 * 1024 -> "${(inResp.sizeBytes + 512) / 1024} KB"
        else -> "${(inResp.sizeBytes + 512 * 1024) / (1024 * 1024)} MB"
    }
    return "$vSize, ${inResp.timeMs} ms"
}

// ==================
// MARK: ViewerOverflowMenu — 3-dot menu in the bottom-right of the viewer
// ==================

/* Replaces the inline Copy / Save chips with a single MoreHoriz menu.
   Copy actions target whichever tab is showing (request or response);
   Clear is global — wipes the response, sentReq, preview state, and any
   memory-backed image resource for the current request. */
@Composable
private fun ViewerOverflowMenu(
    inIsRequestTab: Boolean,
    inRequest: ApiRequest?,
    inSentRequestHeaders: List<Pair<String, String>>?,
    inResponse: ApiResponse?,
    inIsImage: Boolean,
    inResponseBody: String,
    inOnMessage: (String) -> Unit,
    inOnClear: () -> Unit,
) {
    val c = LocalAppColors.current
    var vOpen by remember { mutableStateOf(false) }
    val vAnchor = rememberMenuAnchor()

    val vHeadersText = if (inIsRequestTab && inRequest != null) {
        val vRaw = inSentRequestHeaders ?: parseHeaderLines(requestHeadersText(inRequest))
        headersText(synthesizeRequestHeaders(inRequest, vRaw))
    } else if (inIsRequestTab) {
        ""
    } else {
        inResponse?.headers?.let { headersText(it) } ?: ""
    }
    val vBodyText = if (inIsRequestTab) inRequest?.let { requestBodyText(it) } ?: "" else inResponseBody
    val vCanCopyBody = !(inIsImage && !inIsRequestTab)

    fun statusLineText(): String = if (inIsRequestTab && inRequest != null) {
        "${inRequest.method.name} ${urlPathOnly(urlWithParams(inRequest)).ifEmpty { "/" }} ${inResponse?.httpVersion ?: "HTTP/1.1"}"
    } else if (!inIsRequestTab && inResponse != null) {
        val vVer = inResponse.httpVersion
        if (inResponse.error != null) "$vVer FAILED"
        else "$vVer ${inResponse.status} ${inResponse.statusText.uppercase()}"
    } else ""

    fun copyAll(): String = buildString {
        appendLine(statusLineText())
        if (vHeadersText.isNotBlank() && vHeadersText != "(no headers)") {
            appendLine(vHeadersText)
        }
        if (vBodyText.isNotBlank() && vBodyText != "(no body)") {
            appendLine()
            append(vBodyText)
        }
    }.trimEnd()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { vOpen = true }
            .menuAnchor(vAnchor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        MaterialSymbolsOutlined(icon = MaterialSymbols.MoreHoriz, tint = c.dim, size = 16.dp)
    }
    DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, offsetY = (-4).dp) {
        DropdownMenuItem(onClick = {
            currentClipboard.setText(copyAll())
            inOnMessage("Copied all.")
            vOpen = false
        }) { Text("Copy all") }
        DropdownMenuItem(onClick = {
            currentClipboard.setText(vHeadersText)
            inOnMessage("Copied headers.")
            vOpen = false
        }) { Text("Copy headers") }
        if (vCanCopyBody) {
            DropdownMenuItem(onClick = {
                currentClipboard.setText(vBodyText)
                inOnMessage("Copied body.")
                vOpen = false
            }) { Text("Copy body") }
        }
        DropdownMenuItem(onClick = {
            if (inIsImage && !inIsRequestTab) {
                val vBytes = inResponse?.bytes ?: ByteArray(0)
                showSaveFileDialog(imageFileName(inResponse?.contentType)) { vPath ->
                    if (vPath != null) inOnMessage(writeBytesFile(vPath, vBytes)?.let { "Save failed: $it" } ?: "Saved.")
                }
            } else {
                showSaveFileDialog(if (inIsRequestTab) "request.txt" else "response.json") { vPath ->
                    if (vPath != null) inOnMessage(writeTextFile(vPath, vBodyText)?.let { "Save failed: $it" } ?: "Saved.")
                }
            }
            vOpen = false
        }) { Text("Save body") }
        Divider(color = c.border, modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(onClick = { inOnClear(); vOpen = false }) {
            Text("Clear", color = Color(0xFFFF5630))
        }
    }
}

/* Perceptual luminance check — true when the colour reads as "dark"
   (background gets a light foreground). Standard Rec. 709 weights. */
private fun isDarkBg(inColor: Color): Boolean {
    val vY = 0.299f * inColor.red + 0.587f * inColor.green + 0.114f * inColor.blue
    return vY < 0.5f
}

// ==================
// MARK: BodyFormatSelector — small dropdown for RAW / JSON / XML / YAML / HTML
// ==================

@Composable
private fun BodyFormatSelector(
    inSelected: BodyFormat,
    inOnChange: (BodyFormat) -> Unit,
    inAutoLabel: String? = null,  // when set, shown as "FORMAT (auto)"
) {
    val c = LocalAppColors.current
    var vOpen by remember { mutableStateOf(false) }
    val vAnchor = rememberMenuAnchor()
    val vLabel = if (inAutoLabel != null) "$inAutoLabel (auto)" else inSelected.name
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { vOpen = true }
            .menuAnchor(vAnchor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(vLabel, color = c.dim, fontSize = 11.sp)
            MaterialSymbolsOutlined(icon = MaterialSymbols.ArrowDropDown, tint = c.dim, size = 14.dp)
        }
    }
    DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, offsetY = (-4).dp) {
        for (vF in BodyFormat.values()) {
            DropdownMenuItem(onClick = { inOnChange(vF); vOpen = false }) {
                Text(vF.name, color = if (vF == inSelected) c.accent else c.text)
            }
        }
    }
}

/* TLS validation indicator: only true when the URL is https AND we got
   a real response back (i.e. the OS engine completed the TLS handshake
   without an error). The engines we ship — NSURLSession on macOS,
   WinHttp on Windows, libcurl on Linux — all reject an untrusted
   certificate by default, so a non-error response is implicit proof
   that the OS validated the chain. */
private fun isTlsValidated(inUrl: String, inResp: ApiResponse?): Boolean {
    if (!inUrl.startsWith("https://", ignoreCase = true)) return false
    if (inResp == null) return false
    if (inResp.error != null) return false
    return inResp.status > 0
}

/* Parse "Key: value\nKey2: value2" into a list of pairs so the headers
   table renderer can lay them out as a key/value grid. */
private fun parseHeaderLines(inText: String): List<Pair<String, String>> {
    if (inText.isBlank() || inText == "(no headers)") return emptyList()
    return inText.split('\n').mapNotNull { vLine ->
        val vIdx = vLine.indexOf(':')
        if (vIdx < 0) null else vLine.substring(0, vIdx).trim() to vLine.substring(vIdx + 1).trim()
    }
}

/* "content-type" → "Content-Type"; preserves single-word keys; leaves
   already-correct ones unchanged. */
private fun titleCaseHeader(inKey: String): String =
    inKey.split('-').joinToString("-") { vWord ->
        if (vWord.isEmpty()) vWord
        else vWord[0].uppercaseChar() + vWord.drop(1).lowercase()
    }

/* Centered icon + label placeholder for the viewer (Not sent / Not received /
   No Body). Fills its parent unless a sized modifier is supplied. */
@Composable
private fun ViewerEmpty(inIcon: Int, inText: String, inModifier: Modifier = Modifier.fillMaxSize()) {
    val c = LocalAppColors.current
    Box(modifier = inModifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialSymbolsOutlined(inIcon, tint = c.dim, size = 40.dp)
            Text(inText, color = c.dim, fontSize = 14.sp)
        }
    }
}

/* A labelled, read-only (selectable) code block used by the viewer sections. */
@Composable
private fun CodeSection(inLabel: String, inText: String) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(inLabel, color = c.dim, fontSize = 11.sp)
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(c.bg, RoundedCornerShape(8.dp)).border(1.dp, c.border, RoundedCornerShape(8.dp)).padding(12.dp),
        ) {
            BasicTextField(
                value = inText.ifEmpty { "(empty)" }, onValueChange = {}, readOnly = true,
                color = c.text, cursorColor = c.accent, selectionColor = c.accent.copy(alpha = 0.35f),
                fontSize = 12.sp, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* Format an actual header list (key: value per line). */
private fun headersText(inHeaders: List<Pair<String, String>>): String =
    inHeaders.joinToString("\n") { (vK, vV) -> "$vK: $vV" }.ifEmpty { "(no headers)" }

/* The headers that *would* be sent — explicit enabled headers plus the inferred
   Content-Type for the body type (unless one is already set). Used for Preview;
   a sent request shows the real headers via headersText(response.requestHeaders). */
private fun requestHeadersText(inReq: ApiRequest): String {
    val vLines = mutableListOf<String>()
    inReq.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { vLines.add("${it.key}: ${it.value}") }
    val vCt = when (inReq.bodyType) {
        BodyType.JSON -> "application/json"
        BodyType.TEXT -> "text/plain"
        BodyType.FORM -> "application/x-www-form-urlencoded"
        BodyType.FILE -> "application/octet-stream"
        BodyType.NONE -> null
    }
    if (inReq.method.allowsBody && vCt != null && inReq.headers.none { it.enabled && it.key.equals("content-type", ignoreCase = true) }) {
        vLines.add("Content-Type: $vCt")
    }
    return vLines.joinToString("\n").ifEmpty { "(no headers)" }
}

/* The body that would be sent, rendered as text for the preview. */
private fun requestBodyText(inReq: ApiRequest): String = when (inReq.bodyType) {
    BodyType.NONE -> "(no body)"
    BodyType.JSON, BodyType.TEXT -> inReq.body.ifEmpty { "(empty)" }
    BodyType.FORM -> formEncode(inReq.form).ifEmpty { "(empty form)" }
    BodyType.FILE -> if (inReq.body.isBlank()) "(no file)" else "(file) ${inReq.body}"
}

@Composable
private fun StatusPill(inStatus: Int, inLabel: String) {
    val vC = statusColor(inStatus)
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(vC.copy(alpha = 0.20f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(inLabel, color = vC, fontSize = 14.sp)
    }
}

// ==================
// MARK: Key/value editor
// ==================

@Composable
private fun KeyValEditor(inRows: List<KeyVal>, inOnChange: (List<KeyVal>) -> Unit) {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (inRows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.width(28.dp))
                Text("KEY", color = c.dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("VALUE", color = c.dim, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                Spacer(Modifier.width(30.dp))
            }
        }
        inRows.forEachIndexed { vI, vKv ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Whole cell toggles (a bare 20dp checkbox is a fiddly target in a
                // taller row); the inner Checkbox is just the visual.
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable { inOnChange(inRows.mapIndexed { vJ, vR -> if (vJ == vI) vR.copy(enabled = !vR.enabled) else vR }) }
                        .padding(4.dp),
                ) {
                    Checkbox(
                        checked = vKv.enabled,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.dim, checkmarkColor = c.onAccent),
                    )
                }
                ThinField(vKv.key, { v -> inOnChange(inRows.mapIndexed { vJ, vR -> if (vJ == vI) vR.copy(key = v) else vR }) }, inModifier = Modifier.weight(1f), inPlaceholder = "key")
                ThinField(vKv.value, { v -> inOnChange(inRows.mapIndexed { vJ, vR -> if (vJ == vI) vR.copy(value = v) else vR }) }, inModifier = Modifier.weight(1.4f), inPlaceholder = "value")
                IconBtn(MaterialSymbols.Close, "Remove", inSize = 16.dp) { inOnChange(inRows.filterIndexed { vJ, _ -> vJ != vI }) }
            }
        }
        OutlinedAction(MaterialSymbols.Add, "Add row") { inOnChange(inRows + KeyVal()) }
    }
}

// ==================
// MARK: Reusable bits
// ==================

/* Method as a compact rounded badge (soft colour fill + coloured label), fixed
   width so request names line up. */
@Composable
private fun MethodTag(inMethod: ReqMethod) {
    val vCol = methodColor(inMethod)
    // No background — just the coloured method name, fixed width so the request
    // names still line up in the sidebar list.
    Box(
        modifier = Modifier.width(46.dp).padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(inMethod.name, color = vCol, fontSize = 10.sp)
    }
}

/* Thin horizontal accent line showing where a dragged request row will drop
   (the open-tab strip uses the vertical DropBar). */
@Composable
private fun RowDropBar() {
    val c = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(c.accent, RoundedCornerShape(1.dp)))
}

/* Tab indices listed in inDots get a small accent dot after their label — used
   to flag a tab that holds content (e.g. a non-empty request body). */
@Composable
private fun TabBar(inTabs: List<String>, inSelected: Int, inDots: Set<Int> = emptySet(), inOnSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        inTabs.forEachIndexed { vI, vT ->
            val vSel = vI == inSelected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.clickable { inOnSelect(vI) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(vT, color = if (vSel) c.accent else c.dim, fontSize = 13.sp)
                    if (vI in inDots) {
                        Box(modifier = Modifier.size(6.dp).background(c.accent, RoundedCornerShape(3.dp)))
                    }
                }
                Box(modifier = Modifier.height(2.dp).width(if (vSel) 24.dp else 0.dp).background(c.accent))
            }
        }
    }
}

@Composable
private fun TogglePill(inLabel: String, inSelected: Boolean, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (inSelected) c.accent else c.field, RoundedCornerShape(6.dp))
            .border(1.dp, c.border, RoundedCornerShape(6.dp))
            .clickable(onClick = inOnClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(inLabel, color = if (inSelected) c.onAccent else c.dim, fontSize = 12.sp) }
}

/* Compact single-line (or fixed-height multi-line) input — a BasicTextField in
   a slim bordered box, much shorter than the 56 dp Material OutlinedTextField. */
@Composable
private fun ThinField(inValue: String, inOnChange: (String) -> Unit, inModifier: Modifier = Modifier, inPlaceholder: String = "", inSingleLine: Boolean = true, inOnEnter: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    // Box is an ancestor of the field, so it sees Enter the single-line field
    // leaves unconsumed (it propagates up the focus → root chain).
    var vBoxMod = inModifier.clip(RoundedCornerShape(6.dp))
        .background(c.field, RoundedCornerShape(6.dp))
        .border(1.dp, c.border, RoundedCornerShape(6.dp))
        .padding(horizontal = 10.dp, vertical = 9.dp)
    if (inOnEnter != null) vBoxMod = vBoxMod.onKeyEvent { ev ->
        if (ev.key.type == KeyEventType.Down && (ev.key.keyCode == kScEnter || ev.key.keyCode == kScKpEnter)) { inOnEnter(); true } else false
    }
    Box(
        modifier = vBoxMod,
        contentAlignment = if (inSingleLine) Alignment.CenterStart else Alignment.TopStart,
    ) {
        if (inValue.isEmpty() && inPlaceholder.isNotEmpty()) Text(inPlaceholder, color = c.dim, fontSize = 13.sp)
        BasicTextField(
            value = inValue,
            onValueChange = inOnChange,
            color = c.text,
            cursorColor = c.accent,
            selectionColor = c.accent.copy(alpha = 0.35f),
            fontSize = 13.sp,
            singleLine = inSingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* Small icon-only button (burger / pack / options / save / close). inPadding
   tightens the tap area for slimmer rows. */
@Composable
private fun IconBtn(inIcon: Int, inDesc: String, inModifier: Modifier = Modifier, inSize: Dp = 18.dp, inPadding: Dp = 6.dp, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(modifier = inModifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = inOnClick).padding(inPadding)) {
        MaterialSymbolsOutlined(inIcon, contentDescription = inDesc, tint = c.dim, size = inSize)
    }
}

/* Outlined icon+label action (New request / Add row / pack actions). */
@Composable
private fun OutlinedAction(inIcon: Int, inLabel: String, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    OutlinedButton(onClick = inOnClick) { BtnContent(inIcon, inLabel, c.accent) }
}

/* Filled red icon+label button for destructive / stop actions (delete, cancel, quit). */
@Composable
private fun DangerButton(inLabel: String, inIcon: Int, inOnClick: () -> Unit) {
    val vRed = methodColor(ReqMethod.DELETE)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(vRed, RoundedCornerShape(6.dp))
            .clickable(onClick = inOnClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { BtnContent(inIcon, inLabel, Color.White) }
}

/* Bordered icon+label chip (Copy / Save as / Cancel). */
@Composable
private fun IconLabelChip(inIcon: Int, inLabel: String, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.border, RoundedCornerShape(6.dp))
            .clickable(onClick = inOnClick).padding(horizontal = 8.dp, vertical = 5.dp),
    ) { BtnContent(inIcon, inLabel, c.dim, 14.dp) }
}

/* Icon + label row used inside buttons. */
@Composable
private fun BtnContent(inIcon: Int, inLabel: String, inColor: Color, inSize: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbolsOutlined(inIcon, tint = inColor, size = inSize)
        Text(inLabel, color = inColor, fontSize = 13.sp)
    }
}

// Amber used to flag undefined {{variables}} and unsaved-changes warnings.
private val kWarnColor = Color(0xFFFFAB00)

// SDL scancodes used for app keyboard shortcuts.
private const val kScS = 22
private const val kScN = 17
private const val kScW = 26
private const val kScEnter = 40
private const val kScKpEnter = 88
private const val kScEscape = 41

private fun methodColor(inM: ReqMethod): Color = when (inM) {
    ReqMethod.GET -> Color(0xFF4C9AFF)
    ReqMethod.POST -> Color(0xFF36B37E)
    ReqMethod.PUT -> Color(0xFFFF991F)
    ReqMethod.PATCH -> Color(0xFF00B8D9)
    ReqMethod.DELETE -> Color(0xFFFF5630)
    ReqMethod.HEAD, ReqMethod.OPTIONS -> Color(0xFF8777FF)
}

/* Default save name for an image response, by content type. */
private fun imageFileName(inContentType: String?): String = when {
    inContentType == null -> "image.bin"
    inContentType.contains("png", ignoreCase = true) -> "image.png"
    inContentType.contains("jpeg", ignoreCase = true) || inContentType.contains("jpg", ignoreCase = true) -> "image.jpg"
    inContentType.contains("gif", ignoreCase = true) -> "image.gif"
    inContentType.contains("webp", ignoreCase = true) -> "image.webp"
    inContentType.contains("svg", ignoreCase = true) -> "image.svg"
    else -> "image.bin"
}

private fun statusColor(inStatus: Int): Color = when (inStatus) {
    in 200..299 -> Color(0xFF36B37E) // success — green
    in 300..399 -> Color(0xFF4C9AFF) // redirect — blue
    in 400..499 -> Color(0xFFFF991F) // client error — orange (warning)
    in 500..599 -> Color(0xFFFF5630) // server error — red
    else -> Color(0xFFFF991F)        // unknown / pending — orange
}
