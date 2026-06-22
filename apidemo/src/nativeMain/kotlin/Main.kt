package apidemo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.LocalComposeNativeWindow
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined
import com.compose.desktop.native.nativeComposeWindow
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
    var reqTab by mutableStateOf(0)
    var respTab by mutableStateOf(0)
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
        inRs.job = vScope.launch(Dispatchers.Main) {
            try {
                val vR = withContext(Dispatchers.Default) { vRunner.run(vSend) }
                inRs.response = vR
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
    DisposableEffect(Unit) {
        vWindow.setOnCloseRequest {
            persist()
            if (vPacks.any { it.dirty }) { vQuitDialog = true; false } else true
        }
        onDispose { vWindow.setOnCloseRequest(null) }
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
                    Column(
                        modifier = Modifier.fillMaxSize().background(c.panel)
                            .verticalScroll(rememberScrollState()).padding(12.dp),
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
                        Divider(color = c.border)

                        when (vSideTab) {
                            0 -> {
                                if (vP == null) {
                                    Text("No pack open — use the pack menu to open or create one.", color = c.dim, fontSize = 12.sp)
                                } else {
                                    vP.requests.forEach { vRs ->
                                        key(vRs) {
                                            RequestRow(
                                                inRs = vRs,
                                                inSelected = vRs === vP.active,
                                                inOnOpen = { open(vRs) },
                                                inOnRename = { vRenameTarget = vRs; vRenameText = vRs.req.name },
                                                inOnDuplicate = { duplicate(vRs) },
                                                inOnDelete = { vDeleteTarget = vRs },
                                            )
                                        }
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
                        Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
                            RequestTabStrip(
                                inTabs = vP.openTabs,
                                inActive = vAct,
                                inOnSelect = { open(it) },
                                inOnClose = { closeTab(it) },
                                inOnReorder = { vFrom, vTo -> reorderTabs(vFrom, vTo) },
                            )
                            Divider(color = c.border)
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                HorizontalSplitPane(
                                    initialFirstSize = 540.dp,
                                    minFirstSize = 360.dp,
                                    minSecondSize = 320.dp,
                                    dividerColor = c.border,
                                    dividerHoverColor = c.accent,
                                    first = {
                                        // Request editor (Request panel)
                                        val vReq = vAct.req
                                        Column(
                                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(vReq.name, color = c.text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                                                vReqMsg?.let { Text(it, color = c.dim, fontSize = 11.sp) }
                                                IconLabelChip(MaterialSymbols.FileCopy, "Duplicate") { duplicate(vAct) }
                                                IconLabelChip(MaterialSymbols.Terminal, "Copy as cURL") {
                                                    currentClipboard.setText(toCurl(resolveVars(vAct.req, effective(vP))))
                                                    vReqMsg = "Copied cURL."
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                MethodPicker(vReq.method) { m -> edit { it.copy(method = m) } }
                                                ThinField(vReq.url, { v -> edit { it.copy(url = v) } }, inModifier = Modifier.weight(1f), inPlaceholder = "https://example.com/path")
                                                if (vAct.loading) {
                                                    DangerButton("Cancel", MaterialSymbols.Stop) { cancel(vAct) }
                                                } else {
                                                    Button(onClick = { send(vAct) }) { BtnContent(MaterialSymbols.Send, "Send", c.onAccent) }
                                                }
                                            }

                                            val vMissing = unresolvedVars(vReq, effective(vP))
                                            if (vMissing.isNotEmpty()) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    MaterialSymbolsOutlined(MaterialSymbols.Warning, tint = kWarnColor, size = 15.dp)
                                                    Text("Undefined: ${vMissing.joinToString(", ") { "{{$it}}" }}", color = kWarnColor, fontSize = 11.sp)
                                                }
                                            }

                                            val vHasBody = vReq.method.allowsBody && vReq.bodyType != BodyType.NONE && vReq.body.isNotBlank()
                                            TabBar(
                                                listOf("Query (${vReq.params.size})", "Headers (${vReq.headers.size})", "Body"),
                                                vAct.reqTab,
                                                inDots = if (vHasBody) setOf(2) else emptySet(),
                                            ) { vAct.reqTab = it }
                                            when (vAct.reqTab) {
                                                0 -> KeyValEditor(vReq.params) { v -> edit { it.copy(params = v) } }
                                                1 -> KeyValEditor(vReq.headers) { v -> edit { it.copy(headers = v) } }
                                                else -> BodyEditor(vReq) { v -> edit(v) }
                                            }
                                        }
                                    },
                                    second = {
                                        ResponseView(vAct) { cancel(vAct) }
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
    inOnDelete: () -> Unit,
) {
    val c = LocalAppColors.current
    val vReq = inRs.req
    val vAnchor = rememberMenuAnchor()
    var vMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (inSelected) c.accent.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { inOnOpen() }
            .padding(start = 8.dp, top = 3.dp, bottom = 3.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MethodTag(vReq.method)
        Text(vReq.name, color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box {
            IconBtn(MaterialSymbols.MoreVert, "Options", inModifier = Modifier.menuAnchor(vAnchor), inSize = 18.dp) { vMenu = true }
            DropdownMenu(expanded = vMenu, onDismissRequest = { vMenu = false }, anchor = vAnchor, minWidth = 168.dp) {
                DropdownMenuItem(onClick = { vMenu = false; inOnRename() }) { MenuRow(MaterialSymbols.Edit, "Rename") }
                DropdownMenuItem(onClick = { vMenu = false; inOnDuplicate() }) { MenuRow(MaterialSymbols.FileCopy, "Duplicate") }
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

/* The strip of open requests above the editor. Each tab carries both a click
   handler (select) and an onDrag handler (reorder): a plain click selects, while
   dragging past a neighbour's centre swaps order. Per-tab window-x/width are
   tracked so the drag can hit-test neighbours; key(tab) keeps each tab's
   LayoutNode stable across reorders so the captured drag node stays valid. */
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

    Row(
        modifier = Modifier.fillMaxWidth().background(c.panel)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inTabs.forEach { vRs ->
            key(vRs) {
                val vSel = vRs === inActive
                val vReq = vRs.req
                Row(
                    modifier = Modifier
                        .onGloballyPositioned { vLeft[vRs] = it.x }
                        .onSizeChanged { vWidth[vRs] = it.width }
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (vSel) c.accent.copy(alpha = 0.20f) else c.field, RoundedCornerShape(7.dp))
                        .border(1.dp, if (vSel) c.accent else c.border, RoundedCornerShape(7.dp))
                        .onDrag(
                            onStart = { _, _ -> vDragging = vRs },
                            onDrag = { vRelX, _ ->
                                val vd = vDragging ?: return@onDrag
                                val vCur = inTabs.indexOf(vd)
                                if (vCur < 0) return@onDrag
                                val vPointerX = (vLeft[vd] ?: 0) + vRelX
                                if (vCur < inTabs.lastIndex) {
                                    val vR = inTabs[vCur + 1]
                                    val vCenter = (vLeft[vR] ?: 0) + (vWidth[vR] ?: 0) / 2
                                    if (vPointerX > vCenter) { inOnReorder(vCur, vCur + 1); return@onDrag }
                                }
                                if (vCur > 0) {
                                    val vL = inTabs[vCur - 1]
                                    val vCenter = (vLeft[vL] ?: 0) + (vWidth[vL] ?: 0) / 2
                                    if (vPointerX < vCenter) { inOnReorder(vCur, vCur - 1); return@onDrag }
                                }
                            },
                            onEnd = { vDragging = null },
                        )
                        .clickable { inOnSelect(vRs) }
                        .padding(start = 9.dp, top = 6.dp, bottom = 6.dp, end = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(7.dp).background(methodColor(vReq.method), RoundedCornerShape(4.dp)))
                    Text(vReq.name, color = if (vSel) c.text else c.dim, fontSize = 13.sp)
                    if (vRs.loading) Text("…", color = c.accent, fontSize = 13.sp)
                    IconBtn(MaterialSymbols.Close, "Close", inSize = 13.dp) { inOnClose(vRs) }
                }
            }
        }
    }
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

@Composable
private fun MethodPicker(inMethod: ReqMethod, inOnPick: (ReqMethod) -> Unit) {
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier.menuAnchor(vAnchor)
                .clip(RoundedCornerShape(6.dp))
                .background(methodColor(inMethod), RoundedCornerShape(6.dp))
                .clickable { vOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text(inMethod.name, color = Color.White, fontSize = 13.sp) }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor) {
            ReqMethod.entries.forEach { vM ->
                DropdownMenuItem(onClick = { inOnPick(vM); vOpen = false }) {
                    Text(vM.name, color = methodColor(vM), fontSize = 13.sp)
                }
            }
        }
    }
}

// ==================
// MARK: Body editor
// ==================

@Composable
private fun BodyEditor(inReq: ApiRequest, inEdit: ((ApiRequest) -> ApiRequest) -> Unit) {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!inReq.method.allowsBody) {
            Text("${inReq.method.name} requests don't send a body.", color = c.dim, fontSize = 13.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BodyType.entries.forEach { vT ->
                    TogglePill(vT.name, vT == inReq.bodyType) { inEdit { it.copy(bodyType = vT) } }
                }
            }
            if (inReq.bodyType != BodyType.NONE) {
                ThinField(
                    inReq.body, { v -> inEdit { it.copy(body = v) } },
                    inModifier = Modifier.fillMaxWidth().height(190.dp),
                    inPlaceholder = if (inReq.bodyType == BodyType.JSON) "{ }" else "body",
                    inSingleLine = false,
                )
            }
        }
    }
}

// ==================
// MARK: Response
// ==================

/* Response result as a bordered panel: status header (with a Cancel chip while
   in-flight), Body/Headers tabs, a scrollable (selectable) content area, and a
   Copy / Save-as toolbar pinned to the bottom. Reads its live state from the
   active ReqState so each open tab shows its own response. */
@Composable
private fun ResponseView(inRs: ReqState, inOnCancel: () -> Unit) {
    val c = LocalAppColors.current
    var vMsg by remember { mutableStateOf<String?>(null) }

    val vLoading = inRs.loading
    val vResp = inRs.response
    val vTab = inRs.respTab

    val vBody = if (vResp != null) prettyJsonOrRaw(vResp.body).take(20000) else ""
    val vHeaders = if (vResp != null) vResp.headers.joinToString("\n") { (vK, vV) -> "$vK: $vV" }.ifEmpty { "(no headers)" } else ""
    val vShown = when {
        vResp == null -> ""
        vResp.error != null -> vResp.error
        vTab == 0 -> vBody
        else -> vHeaders
    }

    Column(modifier = Modifier.fillMaxSize().background(c.panel)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RESPONSE", color = c.dim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            when {
                vLoading -> {
                    Text("Sending…", color = c.dim, fontSize = 13.sp)
                    IconLabelChip(MaterialSymbols.Stop, "Cancel") { inOnCancel() }
                }
                vResp?.error != null -> StatusPill(0, "FAILED")
                vResp != null -> {
                    StatusPill(vResp.status, "${vResp.status} ${vResp.statusText}")
                    Text("${vResp.timeMs} ms", color = c.dim, fontSize = 12.sp)
                    Text("${vResp.sizeBytes} B", color = c.dim, fontSize = 12.sp)
                }
            }
        }
        Divider(color = c.border)

        Box(modifier = Modifier.padding(horizontal = 6.dp)) {
            TabBar(listOf("Body", "Headers" + (vResp?.let { " (${it.headers.size})" } ?: "")), vTab, inOnSelect = { inRs.respTab = it })
        }
        Divider(color = c.border)

        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    .background(c.bg, RoundedCornerShape(8.dp))
                    .border(1.dp, c.border, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                if (vResp == null && !vLoading) {
                    Text("Send a request to see the response.", color = c.dim, fontSize = 13.sp)
                } else {
                    BasicTextField(
                        value = if (vLoading) "…" else vShown.ifEmpty { "(empty)" },
                        onValueChange = {},
                        readOnly = true,
                        color = c.text,
                        cursorColor = c.accent,
                        selectionColor = c.accent.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconLabelChip(MaterialSymbols.ContentCopy, "Copy") {
                currentClipboard.setText(vShown); vMsg = "Copied."
            }
            IconLabelChip(MaterialSymbols.Save, "Save as…") {
                showSaveFileDialog(if (vTab == 0) "response.json" else "headers.txt") { vPath ->
                    if (vPath != null) vMsg = writeTextFile(vPath, vShown)?.let { "Save failed: $it" } ?: "Saved."
                }
            }
            Spacer(Modifier.weight(1f))
            vMsg?.let { Text(it, color = c.dim, fontSize = 11.sp) }
        }
    }
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
                Spacer(Modifier.width(46.dp))
                Text("KEY", color = c.dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("VALUE", color = c.dim, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                Spacer(Modifier.width(30.dp))
            }
        }
        inRows.forEachIndexed { vI, vKv ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TogglePill(if (vKv.enabled) "on" else "off", vKv.enabled) {
                    inOnChange(inRows.mapIndexed { vJ, vR -> if (vJ == vI) vR.copy(enabled = !vR.enabled) else vR })
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

@Composable
private fun MethodTag(inMethod: ReqMethod) {
    Text(inMethod.name, color = methodColor(inMethod), fontSize = 10.sp, modifier = Modifier.width(40.dp))
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
private fun ThinField(inValue: String, inOnChange: (String) -> Unit, inModifier: Modifier = Modifier, inPlaceholder: String = "", inSingleLine: Boolean = true) {
    val c = LocalAppColors.current
    Box(
        modifier = inModifier.clip(RoundedCornerShape(6.dp))
            .background(c.field, RoundedCornerShape(6.dp))
            .border(1.dp, c.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
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

/* Small icon-only button (burger / pack / options / save / close). */
@Composable
private fun IconBtn(inIcon: Int, inDesc: String, inModifier: Modifier = Modifier, inSize: Dp = 18.dp, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(modifier = inModifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = inOnClick).padding(6.dp)) {
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

private fun methodColor(inM: ReqMethod): Color = when (inM) {
    ReqMethod.GET -> Color(0xFF4C9AFF)
    ReqMethod.POST -> Color(0xFF36B37E)
    ReqMethod.PUT -> Color(0xFFFF991F)
    ReqMethod.PATCH -> Color(0xFF00B8D9)
    ReqMethod.DELETE -> Color(0xFFFF5630)
    ReqMethod.HEAD, ReqMethod.OPTIONS -> Color(0xFF8777FF)
}

private fun statusColor(inStatus: Int): Color = when (inStatus) {
    in 200..299 -> Color(0xFF36B37E)
    in 300..399 -> Color(0xFF4C9AFF)
    in 400..599 -> Color(0xFFFF5630)
    else -> Color(0xFFFF991F)
}
