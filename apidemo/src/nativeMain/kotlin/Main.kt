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
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined
import com.compose.desktop.native.nativeComposeWindow
import com.compose.desktop.native.showOpenFileDialog
import com.compose.desktop.native.showSaveFileDialog
import kotlinx.coroutines.Dispatchers
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
    nativeComposeWindow(title = "API Manager", width = 1240, height = 820) { Root() }
}

@Composable
private fun Root() {
    var vDark by remember { mutableStateOf(true) }
    val vC = if (vDark) DarkColors else LightColors
    val vMat = if (vDark) {
        darkColors(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    } else {
        lightColors(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    }
    MaterialTheme(colors = vMat) {
        CompositionLocalProvider(LocalAppColors provides vC) {
            App(vDark) { vDark = !vDark }
        }
    }
}

private class HistoryEntry(val method: ReqMethod, val url: String, val status: Int, val timeMs: Long, val request: ApiRequest)

// ==================
// MARK: App
// ==================

@Composable
private fun App(inDark: Boolean, inOnToggleTheme: () -> Unit) {
    val c = LocalAppColors.current
    val vRunner = remember { HttpRunner() }
    val vScope = rememberCoroutineScope()

    val vRequests = remember { mutableStateListOf<ApiRequest>().apply { addAll(Pack().requests) } }
    val vHistory = remember { mutableStateListOf<HistoryEntry>() }
    var vSelected by remember { mutableStateOf(0) }
    var vSideTab by remember { mutableStateOf(0) }     // 0 Requests, 1 History
    var vPackName by remember { mutableStateOf("My Pack") }
    var vNotice by remember { mutableStateOf<String?>(null) }
    var vPackOpen by remember { mutableStateOf(false) }
    var vRenameIdx by remember { mutableStateOf(-1) }
    var vRenameText by remember { mutableStateOf("") }
    var vDeleteIdx by remember { mutableStateOf(-1) }

    var vReqTab by remember { mutableStateOf(0) }
    var vRespTab by remember { mutableStateOf(0) }
    var vResponse by remember { mutableStateOf<ApiResponse?>(null) }
    var vLoading by remember { mutableStateOf(false) }

    fun edit(inT: (ApiRequest) -> ApiRequest) {
        if (vSelected in vRequests.indices) vRequests[vSelected] = inT(vRequests[vSelected])
    }

    Row(modifier = Modifier.fillMaxSize().background(c.bg)) {

        // ============
        //  Sidebar
        Column(
            modifier = Modifier.width(248.dp).fillMaxHeight().background(c.panel)
                .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(vPackName, color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconBtn(MaterialSymbols.Folder, "Pack") { vPackOpen = true }
                OptionsMenu(inDark, inOnToggleTheme)
            }
            TabBar(listOf("Requests", "History"), vSideTab) { vSideTab = it }
            Divider(color = c.border)

            if (vSideTab == 0) {
                vRequests.forEachIndexed { vI, vReq ->
                    val vSel = vI == vSelected
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (vSel) c.accent.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { vSelected = vI; vResponse = null }
                            .padding(start = 8.dp, top = 3.dp, bottom = 3.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MethodTag(vReq.method)
                        Text(vReq.name, color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconBtn(MaterialSymbols.Edit, "Rename", inSize = 16.dp) { vRenameIdx = vI; vRenameText = vReq.name }
                        IconBtn(MaterialSymbols.Delete, "Delete", inSize = 16.dp) { vDeleteIdx = vI }
                    }
                }
                OutlinedAction(MaterialSymbols.Add, "New request") {
                    vRequests.add(ApiRequest(name = "Request ${vRequests.size + 1}"))
                    vSelected = vRequests.size - 1; vResponse = null; vSideTab = 0
                }
            } else {
                if (vHistory.isEmpty()) Text("No requests sent yet.", color = c.dim, fontSize = 12.sp)
                vHistory.forEachIndexed { vI, vH ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable {
                                vRequests.add(vH.request.copy())
                                vSelected = vRequests.size - 1; vSideTab = 0; vResponse = null
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
        }

        // ============
        //  Main — request editor (left) | response panel (right)
        val vReq = vRequests.getOrNull(vSelected)
        if (vReq == null) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("No request selected.", color = c.dim)
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(vReq.name, color = c.text, fontSize = 19.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MethodPicker(vReq.method) { m -> edit { it.copy(method = m) } }
                    ThinField(vReq.url, { v -> edit { it.copy(url = v) } }, inModifier = Modifier.weight(1f), inPlaceholder = "https://example.com/path")
                    Button(
                        onClick = {
                            if (vLoading) return@Button
                            val vSend = vRequests[vSelected]
                            vLoading = true; vResponse = null
                            vScope.launch(Dispatchers.Main) {
                                val vR = withContext(Dispatchers.Default) { vRunner.run(vSend) }
                                vResponse = vR; vLoading = false
                                vHistory.add(0, HistoryEntry(vSend.method, vSend.url, vR.status, vR.timeMs, vSend))
                                if (vHistory.size > 50) vHistory.removeAt(vHistory.size - 1)
                            }
                        },
                    ) { BtnContent(MaterialSymbols.Send, if (vLoading) "Sending" else "Send", c.onAccent) }
                }

                TabBar(listOf("Query (${vReq.params.size})", "Headers (${vReq.headers.size})", "Body"), vReqTab) { vReqTab = it }
                when (vReqTab) {
                    0 -> KeyValEditor(vReq.params) { v -> edit { it.copy(params = v) } }
                    1 -> KeyValEditor(vReq.headers) { v -> edit { it.copy(headers = v) } }
                    else -> BodyEditor(vReq) { v -> edit(v) }
                }
            }

            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(c.border))

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ResponseView(vLoading, vResponse, vRespTab) { vRespTab = it }
            }
        }
    }

    if (vPackOpen) {
        PackDialog(
            inName = vPackName,
            inOnName = { vPackName = it },
            inNotice = vNotice,
            inOnExport = {
                showSaveFileDialog("${vPackName}.json") { vPath ->
                    if (vPath != null) {
                        val vErr = exportPack(Pack(vPackName, vRequests.toList()), vPath)
                        vNotice = vErr?.let { "Export failed: $it" } ?: "Saved ${vRequests.size} request(s)."
                    }
                }
            },
            inOnImport = {
                showOpenFileDialog { vPath ->
                    if (vPath != null) importPack(vPath).fold(
                        onSuccess = { vP ->
                            vPackName = vP.name
                            vRequests.clear(); vRequests.addAll(vP.requests.ifEmpty { listOf(ApiRequest()) })
                            vSelected = 0; vResponse = null
                            vNotice = "Imported ${vP.requests.size} request(s)."
                        },
                        onFailure = { vNotice = "Import failed: ${it.message}" },
                    )
                }
            },
            inOnClose = { vPackOpen = false },
        )
    }

    if (vRenameIdx in vRequests.indices) {
        Dialog(onDismissRequest = { vRenameIdx = -1 }) {
            Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(360.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Rename request", color = c.text, fontSize = 16.sp)
                    ThinField(vRenameText, { vRenameText = it }, inModifier = Modifier.fillMaxWidth(), inPlaceholder = "Name")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            val vIdx = vRenameIdx
                            if (vIdx in vRequests.indices && vRenameText.isNotBlank()) {
                                vRequests[vIdx] = vRequests[vIdx].copy(name = vRenameText.trim())
                            }
                            vRenameIdx = -1
                        }) { BtnContent(MaterialSymbols.Check, "Save", c.onAccent) }
                        OutlinedButton(onClick = { vRenameIdx = -1 }) { Text("Cancel", color = c.text) }
                    }
                }
            }
        }
    }

    if (vDeleteIdx in vRequests.indices) {
        val vName = vRequests[vDeleteIdx].name
        Dialog(onDismissRequest = { vDeleteIdx = -1 }) {
            Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(360.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbolsOutlined(MaterialSymbols.Delete, tint = methodColor(ReqMethod.DELETE), size = 20.dp)
                        Text("Delete request", color = c.text, fontSize = 16.sp)
                    }
                    Text("\"$vName\" will be removed from the pack. This can't be undone.", color = c.dim, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { vDeleteIdx = -1 }) { Text("Cancel", color = c.text) }
                            DangerButton("Delete", MaterialSymbols.Delete) {
                                val vIdx = vDeleteIdx
                                if (vIdx in vRequests.indices) {
                                    vRequests.removeAt(vIdx)
                                    if (vRequests.isEmpty()) vRequests.add(ApiRequest())
                                    vSelected = vSelected.coerceIn(0, vRequests.size - 1)
                                    vResponse = null
                                }
                                vDeleteIdx = -1
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================
// MARK: Pack dialog
// ==================

@Composable
private fun PackDialog(
    inName: String, inOnName: (String) -> Unit, inNotice: String?,
    inOnExport: () -> Unit, inOnImport: () -> Unit, inOnClose: () -> Unit,
) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = inOnClose) {
        Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(440.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbolsOutlined(MaterialSymbols.Folder, tint = c.accent, size = 20.dp)
                    Text("Pack", color = c.text, fontSize = 18.sp)
                }
                Text("Name", color = c.dim, fontSize = 12.sp)
                ThinField(inName, inOnName, inModifier = Modifier.fillMaxWidth(), inPlaceholder = "Pack name")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = inOnExport) { BtnContent(MaterialSymbols.Upload, "Export…", c.onAccent) }
                    OutlinedButton(onClick = inOnImport) { BtnContent(MaterialSymbols.Download, "Import…", c.accent) }
                }
                Text(
                    "Export saves every request to a .json file (native Save dialog); Import replaces the pack from a file.",
                    color = c.dim, fontSize = 12.sp,
                )
                inNotice?.let { Text(it, color = c.text, fontSize = 12.sp) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = inOnClose) { Text("Close", color = c.text) }
                }
            }
        }
    }
}

// ==================
// MARK: Options menu (dark / light)
// ==================

@Composable
private fun OptionsMenu(inDark: Boolean, inOnToggleTheme: () -> Unit) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Box {
        IconBtn(MaterialSymbols.Settings, "Options", inModifier = Modifier.menuAnchor(vAnchor)) { vOpen = true }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor) {
            DropdownMenuItem(onClick = { if (!inDark) inOnToggleTheme(); vOpen = false }) {
                Text("Dark mode", color = if (inDark) c.accent else c.text, fontSize = 13.sp)
            }
            DropdownMenuItem(onClick = { if (inDark) inOnToggleTheme(); vOpen = false }) {
                Text("Light mode", color = if (!inDark) c.accent else c.text, fontSize = 13.sp)
            }
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

/* Response result as a bordered panel: status header, Body/Headers tabs, a
   scrollable (selectable) content area, and a Copy / Save-as toolbar pinned to
   the bottom. Fills its parent so it sits as the right-hand pane. */
@Composable
private fun ResponseView(inLoading: Boolean, inResp: ApiResponse?, inTab: Int, inSelectTab: (Int) -> Unit) {
    val c = LocalAppColors.current
    var vMsg by remember { mutableStateOf<String?>(null) }

    val vBody = if (inResp != null) prettyJsonOrRaw(inResp.body).take(20000) else ""
    val vHeaders = if (inResp != null) inResp.headers.joinToString("\n") { (vK, vV) -> "$vK: $vV" }.ifEmpty { "(no headers)" } else ""
    val vShown = when {
        inResp == null -> ""
        inResp.error != null -> inResp.error
        inTab == 0 -> vBody
        else -> vHeaders
    }

    Column(modifier = Modifier.fillMaxSize().background(c.panel)) {
        // Header — label + status / time / size.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RESPONSE", color = c.dim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            when {
                inLoading -> Text("Sending…", color = c.dim, fontSize = 13.sp)
                inResp?.error != null -> StatusPill(0, "FAILED")
                inResp != null -> {
                    StatusPill(inResp.status, "${inResp.status} ${inResp.statusText}")
                    Text("${inResp.timeMs} ms", color = c.dim, fontSize = 12.sp)
                    Text("${inResp.sizeBytes} B", color = c.dim, fontSize = 12.sp)
                }
            }
        }
        Divider(color = c.border)

        // Tabs.
        Box(modifier = Modifier.padding(horizontal = 6.dp)) {
            TabBar(listOf("Body", "Headers" + (inResp?.let { " (${it.headers.size})" } ?: "")), inTab, inSelectTab)
        }
        Divider(color = c.border)

        // Content — recessed code area, selectable, scrolls when long.
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    .background(c.bg, RoundedCornerShape(8.dp))
                    .border(1.dp, c.border, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                if (inResp == null && !inLoading) {
                    Text("Send a request to see the response.", color = c.dim, fontSize = 13.sp)
                } else {
                    // Read-only BasicTextField → selectable + copyable (drag-select, Ctrl+C).
                    BasicTextField(
                        value = if (inLoading) "…" else vShown.ifEmpty { "(empty)" },
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

        // Bottom toolbar — copy / save the shown pane.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconLabelChip(MaterialSymbols.ContentCopy, "Copy") {
                currentClipboard.setText(vShown); vMsg = "Copied."
            }
            IconLabelChip(MaterialSymbols.Save, "Save as…") {
                showSaveFileDialog(if (inTab == 0) "response.json" else "headers.txt") { vPath ->
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

@Composable
private fun TabBar(inTabs: List<String>, inSelected: Int, inOnSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        inTabs.forEachIndexed { vI, vT ->
            val vSel = vI == inSelected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.clickable { inOnSelect(vI) }.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(vT, color = if (vSel) c.accent else c.dim, fontSize = 13.sp)
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

/* Small icon-only button (rename / delete / pack / options). */
@Composable
private fun IconBtn(inIcon: Int, inDesc: String, inModifier: Modifier = Modifier, inSize: Dp = 18.dp, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(modifier = inModifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = inOnClick).padding(6.dp)) {
        MaterialSymbolsOutlined(inIcon, contentDescription = inDesc, tint = c.dim, size = inSize)
    }
}

/* Outlined icon+label action (New request / Add row). */
@Composable
private fun OutlinedAction(inIcon: Int, inLabel: String, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    OutlinedButton(onClick = inOnClick) { BtnContent(inIcon, inLabel, c.accent) }
}

/* Filled red icon+label button for destructive confirmations (delete). */
@Composable
private fun DangerButton(inLabel: String, inIcon: Int, inOnClick: () -> Unit) {
    val vRed = methodColor(ReqMethod.DELETE)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(vRed, RoundedCornerShape(6.dp))
            .clickable(onClick = inOnClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { BtnContent(inIcon, inLabel, Color.White) }
}

/* Bordered icon+label chip (Copy / Save as). */
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
