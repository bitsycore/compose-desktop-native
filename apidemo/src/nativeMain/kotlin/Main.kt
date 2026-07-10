package apidemo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.compose.sdl.*
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.outlined.MaterialSymbolsOutlined
import com.compose.sdl.widgets.HorizontalSplitPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================
// MARK: Entry point
// ==================

fun main() {
    nativeComposeWindow(title = "API Manager", width = 1240, height = 820) { App() }
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

    // First launch (or empty saved state) → boot from the default session (an
    // unsaved, multi-pack working set); otherwise restore the persisted session.
    val vFirst = !vInitial.launched || vInitial.packs.isEmpty()
    val vBoot = remember {
        if (vFirst) defaultSession()
        else Session(packs = vInitial.packs, root = vInitial.root, globalEnv = vInitial.globalEnv,
            globalHeaders = vInitial.globalHeaders, globalParams = vInitial.globalParams, globalCert = vInitial.globalCert, activePack = vInitial.activePack)
    }
    val vPacks = remember {
        mutableStateListOf<PackState>().apply {
            vBoot.packs.forEachIndexed { vI, vSp ->
                // Open tabs are restored from the persisted app state (never from a
                // session file), so a fresh / file-loaded session opens with no tabs.
                val vTabs = if (vFirst) emptyList() else vInitial.openTabs.getOrElse(vI) { emptyList() }
                val vActIdx = if (!vFirst && vI == vInitial.activePack) vInitial.activeReq else -1
                add(PackState(vSp.pack, vSp.path, vSp.dirty, vTabs, vActIdx))
            }
            // Wire linked-copy packs to their source by id (second pass).
            vBoot.packs.forEachIndexed { vI, vSp ->
                vSp.pack.linkedTo?.let { vSrcId -> getOrNull(vI)?.linkedSource = firstOrNull { it.id == vSrcId } }
            }
        }
    }
    val vGlobalEnv = remember { mutableStateListOf<KeyVal>().apply { addAll(vBoot.globalEnv) } }
    val vSessionHeaders = remember { mutableStateListOf<KeyVal>().apply { addAll(vBoot.globalHeaders) } }
    val vSessionParams = remember { mutableStateListOf<KeyVal>().apply { addAll(vBoot.globalParams) } }
    var vSessionCert by remember { mutableStateOf(vBoot.globalCert) }
    // The hidden session-root pack holding loose requests (not in any pack). Kept
    // out of vPacks so pack indices / persistence stay untouched; active when
    // vActivePackRef === vRoot.
    var vRoot by remember {
        mutableStateOf(PackState(vBoot.root ?: Pack(isRoot = true, name = "", requests = emptyList()),
            null, false, if (vFirst) emptyList() else vInitial.rootOpenTabs))
    }
    val vHistory = remember { mutableStateListOf<HistoryEntry>() }
    val vTreeDrag = remember { TreeDrag() }   // shared cross-pack drag state for the sidebar tree
    // Focus the saved active pack, but fall back to one that actually has tabs so
    // the strip shows whenever any tab is open.
    // The active pack/scope as a reference (a top-level pack, a sub-pack, or the
    // loose root) — a reference (not an index) so sub-packs can be active.
    var vActivePackRef by remember {
        mutableStateOf<PackState?>(
            vBoot.activePack.coerceIn(0, (vPacks.size - 1).coerceAtLeast(0)).let { vIdx ->
                if (vPacks.getOrNull(vIdx)?.openTabs?.isNotEmpty() == true) vIdx
                else vPacks.indexOfFirst { it.openTabs.isNotEmpty() }.takeIf { it >= 0 } ?: vIdx
            }.let { vPacks.getOrNull(it) }
        )
    }

    // The single open session (self-contained working set) + recent session files.
    var vSessionPath by remember { mutableStateOf(vInitial.currentSession) }
    val vRecent = remember { mutableStateListOf<String>().apply { addAll(vInitial.recentSessions) } }

    var vSideTab by remember { mutableStateOf(0) }      // 0 Requests, 1 History, 2 Var
    var vSettingsTab by remember { mutableStateOf(0) }  // session/pack settings sub-tab: 0 Var, 1 Header
    var vReqMsg by remember { mutableStateOf<String?>(null) }
    var vRenameTarget by remember { mutableStateOf<ReqState?>(null) }
    var vRenameText by remember { mutableStateOf("") }
    var vRenamePackTarget by remember { mutableStateOf<PackState?>(null) }
    var vRenameSession by remember { mutableStateOf(false) }
    var vRemovePackTarget by remember { mutableStateOf<PackState?>(null) }
    var vDeleteTarget by remember { mutableStateOf<ReqState?>(null) }
    var vImgSeq by remember { mutableStateOf(0) }   // unique-key counter for response images
    var vSwitchAction by remember { mutableStateOf<(() -> Unit)?>(null) }  // pending session switch awaiting confirm
    var vEnvActive by remember { mutableStateOf(false) }  // active tab is the active pack's env tab (vs a request)
    var vSessionTabOpen by remember { mutableStateOf(false) }  // the session-settings tab is open in the strip
    var vSessionActive by remember { mutableStateOf(false) }   // and it's the active main-panel tab

    fun activePack(): PackState? = vActivePackRef
    // Top-level index of a pack for persistence (-1 = loose root, sub-pack, or none).
    fun packIndex(inP: PackState?): Int = if (inP == null || inP === vRoot) -1 else vPacks.indexOf(inP)
    fun selectPack(inP: PackState) { vActivePackRef = inP; vReqMsg = null }
    // The scope chain from the outermost pack down to inP (root excluded). Used to
    // resolve inheritance: Session → outer pack → … → inner pack, innermost wins.
    fun scopeChain(inP: PackState?): List<PackState> {
        val vChain = ArrayList<PackState>()
        var vCur = inP
        while (vCur != null && vCur !== vRoot) { vChain.add(vCur); vCur = vCur.parent }
        return vChain.asReversed()
    }
    // Variables a request sees: session, then each enclosing pack (inner overrides).
    fun effective(inP: PackState): List<KeyVal> = vGlobalEnv.toList() + scopeChain(inP).flatMap { it.variables }
    // …plus the request's own variables (innermost — they win over everything above).
    fun effectiveReqVars(inReq: ApiRequest, inP: PackState): List<KeyVal> = effective(inP) + inReq.variables
    // Query params a request inherits: session, then each enclosing pack (inner wins by key).
    fun inheritedParams(inP: PackState?): List<KeyVal> {
        val vOut = LinkedHashMap<String, KeyVal>()
        vSessionParams.filter { it.key.isNotBlank() }.forEach { vOut[it.key] = it }
        scopeChain(inP).forEach { vP -> vP.params.filter { it.key.isNotBlank() }.forEach { vOut[it.key] = it } }
        return vOut.values.toList()
    }
    // The query params actually sent: inherited, then the request's own override by key.
    fun effectiveParams(inReq: ApiRequest, inP: PackState?): List<KeyVal> {
        val vOut = LinkedHashMap<String, KeyVal>()
        inheritedParams(inP).forEach { vOut[it.key] = it }
        inReq.params.filter { it.key.isNotBlank() }.forEach { vOut[it.key] = it }
        return vOut.values.toList()
    }
    // Headers a request inherits: session, then each enclosing pack (inner wins by key).
    fun inheritedHeaders(inP: PackState?): List<KeyVal> {
        val vOut = LinkedHashMap<String, KeyVal>()
        vSessionHeaders.filter { it.key.isNotBlank() }.forEach { vOut[it.key.lowercase()] = it }
        scopeChain(inP).forEach { vP -> vP.headers.filter { it.key.isNotBlank() }.forEach { vOut[it.key.lowercase()] = it } }
        return vOut.values.toList()
    }
    // The headers actually sent: inherited, then the request's own override by key.
    fun effectiveHeaders(inReq: ApiRequest, inP: PackState?): List<KeyVal> {
        val vOut = LinkedHashMap<String, KeyVal>()
        inheritedHeaders(inP).forEach { vOut[it.key.lowercase()] = it }
        inReq.headers.filter { it.key.isNotBlank() }.forEach { vOut[it.key.lowercase()] = it }
        return vOut.values.toList()
    }
    // Client cert a request inherits: nearest enclosing pack that sets one, else session.
    fun inheritedCert(inP: PackState?): CertConfig? {
        scopeChain(inP).asReversed().forEach { if (it.cert?.isSet == true) return it.cert }
        return vSessionCert?.takeIf { it.isSet }
    }
    // The cert actually used: the request's own if set, else the inherited one.
    fun effectiveCert(inReq: ApiRequest, inP: PackState?): CertConfig? =
        if (inReq.hasClientCert) inReq.certConfig() else inheritedCert(inP)

    // ============
    //  Source-aware inheritance (for the "what do I inherit, and from where?" UI).
    //  The sourced* helpers walk a chain (outer→inner, session first) and tag each
    //  surviving value with a short label ("Session" / "Pack") + its scopePath for
    //  the tooltip. inChain for a request = scopeChain(owningPack) (its own pack
    //  included); for a pack's settings = scopeChain(pack.parent) (ancestors only).
    // The full path of a scope for the source tooltip: "Methods" for a top-level
    // pack, "Methods / Nested" for a sub-pack (root excluded — it's never inherited).
    fun scopePath(inP: PackState): String {
        val vParts = ArrayList<String>()
        var vCur: PackState? = inP
        while (vCur != null && vCur !== vRoot) { vParts.add(0, vCur.name.ifBlank { "Pack" }); vCur = vCur.parent }
        return vParts.joinToString(" / ")
    }
    fun sourcedVars(inChain: List<PackState>): List<InheritedKv> {
        val vOut = LinkedHashMap<String, InheritedKv>()   // vars are case-sensitive ({{name}})
        vGlobalEnv.filter { it.key.isNotBlank() }.forEach { vOut[it.key] = InheritedKv(it, "Session", "Session") }
        inChain.forEach { vP -> vP.variables.filter { it.key.isNotBlank() }.forEach { vOut[it.key] = InheritedKv(it, "Pack", scopePath(vP)) } }
        return vOut.values.toList()
    }
    fun sourcedHeaders(inChain: List<PackState>): List<InheritedKv> {
        val vOut = LinkedHashMap<String, InheritedKv>()   // headers are case-insensitive
        vSessionHeaders.filter { it.key.isNotBlank() }.forEach { vOut[it.key.lowercase()] = InheritedKv(it, "Session", "Session") }
        inChain.forEach { vP -> vP.headers.filter { it.key.isNotBlank() }.forEach { vOut[it.key.lowercase()] = InheritedKv(it, "Pack", scopePath(vP)) } }
        return vOut.values.toList()
    }
    fun sourcedParams(inChain: List<PackState>): List<InheritedKv> {
        val vOut = LinkedHashMap<String, InheritedKv>()   // query keys are case-sensitive
        vSessionParams.filter { it.key.isNotBlank() }.forEach { vOut[it.key] = InheritedKv(it, "Session", "Session") }
        inChain.forEach { vP -> vP.params.filter { it.key.isNotBlank() }.forEach { vOut[it.key] = InheritedKv(it, "Pack", scopePath(vP)) } }
        return vOut.values.toList()
    }
    fun sourcedCert(inChain: List<PackState>): InheritedCert? {
        inChain.asReversed().forEach { vP -> vP.cert?.takeIf { it.isSet }?.let { return InheritedCert(it, "Pack", scopePath(vP)) } }
        return vSessionCert?.takeIf { it.isSet }?.let { InheritedCert(it, "Session", "Session") }
    }

    fun persist() {
        val vSaved = vPacks.map { SavedPack(it.path, it.dirty, it.toPack()) }
        val vGE = vGlobalEnv.toList()
        val vRootPack = vRoot.toPack()
        val vRootTabs = vRoot.openTabs.mapNotNull { vRs -> vRoot.requests.indexOf(vRs).takeIf { it >= 0 } }
        // Open-tab state — persisted in app state only, not in the session file.
        val vOpen = vPacks.map { vP -> vP.openTabs.mapNotNull { vRs -> vP.requests.indexOf(vRs).takeIf { it >= 0 } } }
        val vActiveReq = vActivePackRef?.let { it.requests.indexOf(it.active) } ?: -1
        saveAppState(AppState(
            launched = true,
            dark = vDark,
            globalEnv = vGE,
            globalHeaders = vSessionHeaders.toList(),
            globalParams = vSessionParams.toList(),
            globalCert = vSessionCert,
            packs = vSaved,
            root = vRootPack,
            rootOpenTabs = vRootTabs,
            activePack = packIndex(vActivePackRef),
            currentSession = vSessionPath,
            recentSessions = vRecent.toList(),
            openTabs = vOpen,
            activeReq = vActiveReq,
        ))
        // A session opened from / saved to a file auto-saves back to it on every
        // change — once it has a file, it's always in sync (best-effort). The
        // Session has no open-tab fields, so the file never carries them.
        vSessionPath?.let { exportSession(Session(packs = vSaved, root = vRootPack, globalEnv = vGE,
            globalHeaders = vSessionHeaders.toList(), globalParams = vSessionParams.toList(), globalCert = vSessionCert, activePack = packIndex(vActivePackRef)), it) }
    }

    // ============
    //  Request actions (operate on the active pack, read live)
    // Tabs are shown unified across all packs, so these act on the pack that
    // owns the request (not just the active one) and keep focus on a pack that
    // still has open tabs.
    fun packOf(inRs: ReqState): PackState? = vPacks.firstOrNull { vQ -> vQ.requests.any { it === inRs } }
    fun ensureFocusHasTabs() {
        if (activePack()?.active == null) {
            val vNext = (if (vRoot.openTabs.isNotEmpty()) vRoot else null) ?: vPacks.firstOrNull { it.openTabs.isNotEmpty() }
            if (vNext != null) { vActivePackRef = vNext; vNext.active = vNext.openTabs.firstOrNull() }
        }
    }
    // The unified tab strip, flattened across packs: each pack contributes its
    // env tab (if open) followed by its request tabs.
    fun stripTabs(): List<StripTab> = buildList {
        if (vSessionTabOpen) add(StripTab(null, null, isSession = true))
        vRoot.openTabs.forEach { add(StripTab(vRoot, it)) }   // loose request tabs (no env tab)
        fun addPack(vQ: PackState) {
            if (vQ.envOpen) add(StripTab(vQ, null))
            vQ.openTabs.forEach { add(StripTab(vQ, it)) }
            vQ.subPacks.forEach { addPack(it) }               // recurse into nested packs
        }
        vPacks.forEach { addPack(it) }
    }
    fun openSessionTab() { vSessionTabOpen = true; vSessionActive = true; vEnvActive = false; vReqMsg = null }
    fun closeSessionTab() { vSessionTabOpen = false; vSessionActive = false }
    // Open a request in a specific pack's context (a linked copy shares the
    // source's ReqState objects, so the pack must be passed — packOf would
    // resolve to the source, not the linked copy).
    fun open(inRs: ReqState, inPack: PackState) {
        if (inRs !in inPack.openTabs) inPack.openTabs.add(inRs)
        vActivePackRef = inPack; inPack.active = inRs
        vEnvActive = false; vSessionActive = false; vReqMsg = null
    }
    fun openEnv(inP: PackState) {
        vActivePackRef = inP
        inP.envOpen = true; vEnvActive = true; vSessionActive = false; vReqMsg = null
    }
    fun closeTab(inRs: ReqState, inPack: PackState) {
        val vIdx = inPack.openTabs.indexOf(inRs)
        inPack.openTabs.remove(inRs)
        if (inPack.active === inRs) inPack.active = inPack.openTabs.getOrNull(vIdx) ?: inPack.openTabs.lastOrNull()
        if (!vEnvActive) ensureFocusHasTabs()
    }
    fun closeEnv(inP: PackState) {
        inP.envOpen = false
        if (vEnvActive && activePack() === inP) vEnvActive = false   // fall back to the active request
    }
    // Close every tab except inTab (a request, a pack-env, or the session tab).
    fun closeOthers(inTab: StripTab) {
        vRoot.openTabs.removeAll { it !== inTab.req }
        vRoot.active = vRoot.openTabs.firstOrNull()
        vPacks.forEach { vQ ->
            vQ.openTabs.removeAll { it !== inTab.req }
            vQ.active = vQ.openTabs.firstOrNull()
            if (vQ !== inTab.pack || inTab.req != null) vQ.envOpen = false
        }
        if (inTab.isSession) {
            vSessionTabOpen = true; vSessionActive = true; vEnvActive = false
        } else {
            vSessionTabOpen = false; vSessionActive = false
            val vKeep = inTab.pack
            if (vKeep != null) {
                vActivePackRef = vKeep
                if (inTab.req != null) { vKeep.active = inTab.req; vEnvActive = false }
                else { vKeep.envOpen = true; vEnvActive = true }
            }
        }
    }
    fun closeAllTabs() {
        vRoot.openTabs.clear(); vRoot.active = null
        vPacks.forEach { it.openTabs.clear(); it.active = null; it.envOpen = false }
        vEnvActive = false; vSessionTabOpen = false; vSessionActive = false
    }
    // inFrom / inTo index into the unified strip; only request tabs reorder, and
    // the move is clamped to the dragged tab's own pack so it can't jump packs.
    fun reorderTabs(inFrom: Int, inTo: Int) {
        val vFlat = stripTabs()
        val vTab = vFlat.getOrNull(inFrom) ?: return
        val vRs = vTab.req ?: return
        val vP = vTab.pack ?: return
        // Flat index of this pack's first request tab — works for the root and any
        // (nested) pack without hand-computing offsets.
        val vReqStart = vFlat.indexOfFirst { it.pack === vP && it.req != null }
        if (vReqStart < 0) return
        val vFromL = vP.openTabs.indexOf(vRs)
        val vToL = (inTo - vReqStart).coerceIn(0, (vP.openTabs.size - 1).coerceAtLeast(0))
        if (vFromL >= 0 && vFromL != vToL) vP.openTabs.add(vToL, vP.openTabs.removeAt(vFromL))
    }
    fun edit(inT: (ApiRequest) -> ApiRequest) {
        val vP = activePack() ?: return
        if (vP.isLinked) return                 // linked copies are read-only; edit the source
        val vRs = vP.active ?: return
        vRs.req = inT(vRs.req); vP.dirty = true
    }
    fun newRequest() {
        val vP = activePack() ?: return
        val vRs = ReqState(ApiRequest(name = "Request ${vP.requests.size + 1}"))
        vP.requests.add(vRs); vP.openTabs.add(vRs); vP.active = vRs; vP.dirty = true; vReqMsg = null; vSideTab = 0
    }
    // A loose request at the session root (no pack — inherits session settings only).
    fun newLooseRequest() {
        val vRs = ReqState(ApiRequest(name = "Request ${vRoot.requests.size + 1}"))
        vRoot.requests.add(vRs); vRoot.openTabs.add(vRs); vRoot.active = vRs
        vActivePackRef = vRoot; vEnvActive = false; vSessionActive = false; vReqMsg = null; vSideTab = 0; persist()
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
        vP.openTabs.remove(inRs); vP.requests.remove(inRs)   // a pack may now be empty
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
        // Fold inherited (session + pack) headers and cert in before resolving vars.
        var vBase = vOriginal.copy(headers = effectiveHeaders(vOriginal, vP), params = effectiveParams(vOriginal, vP))
        if (!vOriginal.hasClientCert) effectiveCert(vOriginal, vP)?.let { vBase = vBase.withCert(it) }
        val vSend = resolveVars(vBase, effectiveReqVars(vOriginal, vP))
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
    // Probe the URL's TLS chain (libcurl handshake) and show it in a dialog.
    fun inspectChain(inRs: ReqState) {
        if (inRs.chainLoading) return
        val vP = activePack() ?: return
        var vBase = inRs.req
        if (!vBase.hasClientCert) effectiveCert(vBase, vP)?.let { vBase = vBase.withCert(it) }
        val vSend = resolveVars(vBase, effectiveReqVars(inRs.req, vP))
        inRs.chainLoading = true; inRs.chainUrl = vSend.url
        vScope.launch(Dispatchers.Main) {
            try {
                val vChain = withContext(Dispatchers.Default) { inspectTlsChain(vSend) }
                inRs.tlsChain = vChain; inRs.showChain = true
            } finally { inRs.chainLoading = false }
        }
    }

    // ============
    //  Pack actions
    fun newPack() {
        val vP = PackState(Pack(name = "Pack ${vPacks.size + 1}"), null, false)
        vPacks.add(vP); vActivePackRef = vP; vReqMsg = null
        vP.requests.firstOrNull()?.let { vP.openTabs.add(it); vP.active = it }   // open its request if any
        persist()
    }
    fun openPackFile() {
        showOpenFileDialog { vPath ->
            if (vPath != null) importPack(vPath).fold(
                onSuccess = { vPk ->
                    val vNewP = PackState(vPk, vPath, false); vPacks.add(vNewP); vActivePackRef = vNewP
                    vReqMsg = "Imported ${vPk.name}."; persist()
                },
                onFailure = { vReqMsg = "Import failed: ${it.message}" },
            )
        }
    }
    fun saveAsPack() {
        val vP = activePack() ?: return
        showSaveFileDialog("${vP.name}.json") { vPath ->
            if (vPath != null) {
                val vErr = exportPack(vP.toPack(), vPath)
                if (vErr == null) { vP.path = vPath; vP.dirty = false; vReqMsg = "Exported."; persist() }
                else vReqMsg = "Export failed: $vErr"
            }
        }
    }
    fun savePack() {
        val vP = activePack() ?: return
        val vPath = vP.path
        if (vPath == null) { saveAsPack(); return }
        val vErr = exportPack(vP.toPack(), vPath)
        if (vErr == null) { vP.dirty = false; vReqMsg = "Exported."; persist() } else vReqMsg = "Export failed: $vErr"
    }
    // Remove a pack from its parent (a sub-pack) or from the top level.
    fun removePack(inP: PackState) {
        val vPar = inP.parent
        if (vPar != null) vPar.subPacks.remove(inP) else vPacks.remove(inP)
        if (vActivePackRef === inP) vActivePackRef = vPar ?: vPacks.firstOrNull()
        vReqMsg = null; persist()
    }
    // A new empty sub-pack inside inParent.
    fun newSubPack(inParent: PackState) {
        val vSub = PackState(Pack(name = "Sub-pack ${inParent.subPacks.size + 1}"), null, false)
        vSub.parent = inParent
        inParent.subPacks.add(vSub); inParent.expanded = true
        vActivePackRef = vSub; vEnvActive = false; vSessionActive = false; vReqMsg = null; persist()
    }
    fun duplicatePack(inP: PackState) {
        val vAt = (vPacks.indexOf(inP) + 1).coerceIn(0, vPacks.size)
        val vNew = PackState(inP.toPack().copy(name = "${inP.name} copy", id = "", linkedTo = null), null, true)
        vPacks.add(vAt, vNew); vActivePackRef = vNew; vReqMsg = null; persist()
    }
    // A linked copy mirrors inP's requests read-only but gets its own (copied)
    // variables / headers / cert — for running the same calls against another env.
    fun createLinkedPack(inP: PackState) {
        val vSource = inP.linkedSource ?: inP   // link to the real source, never to another link
        val vAt = (vPacks.indexOf(inP) + 1).coerceIn(0, vPacks.size)
        val vNew = PackState(Pack(
            name = "${vSource.name} (linked)", requests = emptyList(),
            variables = vSource.variables.toList(), color = vSource.color,
            headers = vSource.headers.toList(), cert = vSource.cert, linkedTo = vSource.id,
        ), null, true)
        vNew.linkedSource = vSource
        vPacks.add(vAt, vNew); vActivePackRef = vNew; vReqMsg = null; persist()
    }
    fun renamePack(inP: PackState, inName: String) {
        if (inName.isNotBlank()) { inP.name = inName.trim(); inP.dirty = true; persist() }
    }

    // ============
    //  Cross-pack drag (move a request or a whole pack across the tree)
    // True when inMaybe is inRoot itself or nested anywhere beneath it.
    fun inSubtree(inMaybe: PackState, inRoot: PackState): Boolean {
        var vCur: PackState? = inMaybe
        while (vCur != null) { if (vCur === inRoot) return true; vCur = vCur.parent }
        return false
    }
    // Move inRs out of inFrom into inTo at inIndex. inIndex counts inTo's rows
    // *excluding* the dragged request (so for a same-pack reorder it is already an
    // index into the post-removal list — no shift needed; resolveReqDrop produces
    // exactly this). A request that was open / active follows to the new pack so
    // its tab survives the move.
    fun moveRequest(inRs: ReqState, inFrom: PackState, inTo: PackState, inIndex: Int) {
        if (inFrom.isLinked || inTo.isLinked) return            // mirrors are read-only
        val vFrom = inFrom.requests.indexOf(inRs)
        if (vFrom < 0) return
        if (inFrom === inTo && inIndex == vFrom) return         // dropped back in place
        val vWasActive = inFrom.active === inRs
        val vWasOpen = inRs in inFrom.openTabs
        inFrom.requests.removeAt(vFrom)
        inFrom.openTabs.remove(inRs)
        if (inFrom.active === inRs) inFrom.active = inFrom.openTabs.lastOrNull()
        inTo.requests.add(inIndex.coerceIn(0, inTo.requests.size), inRs)
        if (vWasOpen && inRs !in inTo.openTabs) inTo.openTabs.add(inRs)
        inFrom.dirty = true; inTo.dirty = true
        inTo.expanded = true
        if (vWasActive || vWasOpen) { vActivePackRef = inTo; inTo.active = inRs; vEnvActive = false; vSessionActive = false }
        vReqMsg = null; persist()
    }
    // Reparent inPack under inNewParent (null = top level) at sibling index inIndex.
    fun movePack(inPack: PackState, inNewParent: PackState?, inIndex: Int) {
        if (inNewParent != null && inSubtree(inNewParent, inPack)) return   // no cycles
        if (inNewParent?.isLinked == true) return                          // don't nest into a mirror
        val vOldList = inPack.parent?.subPacks ?: vPacks
        val vOldIdx = vOldList.indexOf(inPack)
        if (vOldIdx < 0) return
        val vNewList = inNewParent?.subPacks ?: vPacks
        var vAt = inIndex.coerceIn(0, vNewList.size)
        if (vOldList === vNewList && (vAt == vOldIdx || vAt == vOldIdx + 1)) return   // no-op
        vOldList.removeAt(vOldIdx)
        if (vOldList === vNewList && vOldIdx < vAt) vAt--
        inPack.parent = inNewParent
        vNewList.add(vAt.coerceIn(0, vNewList.size), inPack)
        inNewParent?.expanded = true
        vReqMsg = null; persist()
    }

    // Resolve where the dragged request would land for the cursor's absolute Y.
    // Each non-linked, rendered pack (plus the loose root) owns a contiguous Y
    // region [header/first-row top .. its own last-row bottom]; the cursor's region
    // picks the target pack, then the index is how many of that pack's row centres
    // sit above the cursor.
    fun resolveReqDrop(inCursorY: Int) {
        val vDrag = vTreeDrag.dragReq ?: return
        // (pack, regionTop, regionBottom) for every droppable container in view.
        val vRegions = ArrayList<Triple<PackState, Int, Int>>()
        fun rowBottom(inRs: ReqState): Int? = vTreeDrag.rowTop[inRs]?.let { it + (vTreeDrag.rowH[inRs] ?: 0) }
        fun addRegion(inP: PackState, inHeaderless: Boolean) {
            if (inP.isLinked) return
            val vTops = inP.requests.mapNotNull { vTreeDrag.rowTop[it] }
            val vBots = inP.requests.mapNotNull { rowBottom(it) }
            val vTop: Int; val vBot: Int
            if (inHeaderless) {
                if (vTops.isEmpty()) { vTreeDrag.headTop[inP]?.let { vTop = it; vBot = it + (vTreeDrag.headH[inP] ?: 0); vRegions.add(Triple(inP, vTop, vBot)) }; return }
                vTop = vTops.min(); vBot = vBots.max()
            } else {
                val vHt = vTreeDrag.headTop[inP] ?: return
                vTop = vHt
                vBot = if (inP.expanded && vBots.isNotEmpty()) vBots.max() else vHt + (vTreeDrag.headH[inP] ?: 0)
            }
            vRegions.add(Triple(inP, vTop, vBot))
        }
        fun walk(inP: PackState) {
            addRegion(inP, false)
            if (inP.expanded) inP.subPacks.forEach { walk(it) }
        }
        addRegion(vRoot, true)
        vPacks.forEach { walk(it) }
        if (vRegions.isEmpty()) { vTreeDrag.dropPack = null; vTreeDrag.dropIndex = -1; return }
        val vTarget = vRegions.firstOrNull { inCursorY >= it.second && inCursorY < it.third }?.first
            ?: vRegions.minByOrNull { kotlin.math.min(kotlin.math.abs(inCursorY - it.second), kotlin.math.abs(inCursorY - it.third)) }!!.first
        var vCount = 0
        vTarget.requests.forEach { vRs ->
            if (vRs !== vDrag) {
                val vCenter = (vTreeDrag.rowTop[vRs] ?: return@forEach) + (vTreeDrag.rowH[vRs] ?: 0) / 2
                if (inCursorY > vCenter) vCount++
            }
        }
        vTreeDrag.dropPack = vTarget; vTreeDrag.dropIndex = vCount
    }
    // Resolve where the dragged pack would land. The header the cursor is over (or
    // nearest) decides it: top third → before it, bottom third → after it (sibling
    // inserts), middle third → nest inside it as a child.
    fun resolvePackDrop(inCursorY: Int) {
        val vDrag = vTreeDrag.dragPack ?: return
        // Rendered headers (top, height, parent, sibling list), skipping the dragged subtree.
        data class HEntry(val pack: PackState, val top: Int, val h: Int, val parent: PackState?, val sibs: List<PackState>)
        val vEntries = ArrayList<HEntry>()
        fun walk(inP: PackState, inSibs: List<PackState>) {
            if (!inSubtree(inP, vDrag)) {
                vTreeDrag.headTop[inP]?.let { vEntries.add(HEntry(inP, it, vTreeDrag.headH[inP] ?: 0, inP.parent, inSibs)) }
            }
            if (inP.expanded) inP.subPacks.forEach { walk(it, inP.subPacks) }
        }
        vPacks.forEach { walk(it, vPacks) }
        fun before(inE: HEntry) { vTreeDrag.dropInto = null; vTreeDrag.dropParent = inE.parent; vTreeDrag.dropSibIndex = inE.sibs.indexOf(inE.pack) }
        fun after(inE: HEntry) { vTreeDrag.dropInto = null; vTreeDrag.dropParent = inE.parent; vTreeDrag.dropSibIndex = inE.sibs.indexOf(inE.pack) + 1 }
        if (vEntries.isEmpty()) { vTreeDrag.dropInto = null; vTreeDrag.dropParent = null; vTreeDrag.dropSibIndex = 0; return }
        val vHit = vEntries.firstOrNull { inCursorY >= it.top && inCursorY < it.top + it.h }
        if (vHit != null) {
            val vFrac = if (vHit.h > 0) (inCursorY - vHit.top).toFloat() / vHit.h else 0.5f
            when {
                vFrac < 0.30f -> before(vHit)
                vFrac > 0.70f -> after(vHit)
                !vHit.pack.isLinked -> { vTreeDrag.dropInto = vHit.pack; vTreeDrag.dropParent = null; vTreeDrag.dropSibIndex = -1 }
                else -> after(vHit)
            }
        } else {
            val vNear = vEntries.minByOrNull { kotlin.math.abs(inCursorY - (it.top + it.h / 2)) }!!
            if (inCursorY < vNear.top + vNear.h / 2) before(vNear) else after(vNear)
        }
    }
    // Commit the resolved request / pack drop, then clear the drag. A press that
    // never passed the slop (engaged == false) is a click, not a drop — skip it.
    fun reqDropEnd() {
        val vRs = vTreeDrag.dragReq; val vFrom = vTreeDrag.dragReqOwner; val vTo = vTreeDrag.dropPack
        if (vTreeDrag.engaged && vRs != null && vFrom != null && vTo != null && vTreeDrag.dropIndex >= 0) moveRequest(vRs, vFrom, vTo, vTreeDrag.dropIndex)
        vTreeDrag.clear()
    }
    fun packDropEnd() {
        val vP = vTreeDrag.dragPack
        if (vTreeDrag.engaged && vP != null) {
            val vInto = vTreeDrag.dropInto
            if (vInto != null) movePack(vP, vInto, vInto.subPacks.size)
            else if (vTreeDrag.dropSibIndex >= 0) movePack(vP, vTreeDrag.dropParent, vTreeDrag.dropSibIndex)
        }
        vTreeDrag.clear()
    }

    // ============
    //  Session actions (the whole working set; one session at a time)
    fun rememberRecent(inPath: String) {
        vRecent.remove(inPath); vRecent.add(0, inPath)
        while (vRecent.size > 8) vRecent.removeAt(vRecent.size - 1)
    }
    fun saveSessionTo(inPath: String) {
        vSessionPath = inPath; rememberRecent(inPath); persist()   // persist() writes the file
    }
    fun saveSessionAs(inThen: () -> Unit = {}) {
        showSaveFileDialog("session.json") { vPath -> if (vPath != null) { saveSessionTo(vPath); inThen() } }
    }
    // First save (no file yet) prompts for a location; afterwards the session
    // auto-saves on every change, so Ctrl+S just flushes via persist().
    fun saveSession() {
        if (vSessionPath == null) saveSessionAs() else persist()
    }
    fun loadSession(inSession: Session, inPath: String?) {
        vPacks.clear()
        inSession.packs.forEach { vPacks.add(PackState(it.pack, it.path, it.dirty)) }
        inSession.packs.forEachIndexed { vI, vSp ->   // wire linked copies to their source by id
            vSp.pack.linkedTo?.let { vSrcId -> vPacks.getOrNull(vI)?.linkedSource = vPacks.firstOrNull { it.id == vSrcId } }
        }
        if (vPacks.isEmpty()) vPacks.add(PackState(Pack(), null, false))
        vRoot = PackState(inSession.root ?: Pack(isRoot = true, name = "", requests = emptyList()), null, false)
        vGlobalEnv.clear(); vGlobalEnv.addAll(inSession.globalEnv)
        vSessionHeaders.clear(); vSessionHeaders.addAll(inSession.globalHeaders)
        vSessionParams.clear(); vSessionParams.addAll(inSession.globalParams)
        vSessionCert = inSession.globalCert
        vActivePackRef = vPacks.getOrNull(inSession.activePack) ?: vPacks.firstOrNull()
        vSessionPath = inPath
        if (inPath != null) rememberRecent(inPath)
        vReqMsg = null; persist()
    }
    fun openSession(inPath: String) {
        importSession(inPath).fold(
            onSuccess = { loadSession(it, inPath); vReqMsg = "Opened session." },
            onFailure = { vReqMsg = "Open session failed: ${it.message}" },
        )
    }
    fun openSessionFile() {
        showOpenFileDialog { vPath -> if (vPath != null) openSession(vPath) }
    }
    fun newSession() {
        vPacks.clear(); vPacks.add(PackState(Pack(name = "My Pack"), null, false))
        vRoot = PackState(Pack(isRoot = true, name = "", requests = emptyList()), null, false)
        vGlobalEnv.clear(); vSessionHeaders.clear(); vSessionParams.clear(); vSessionCert = null
        vActivePackRef = vPacks.firstOrNull(); vSessionPath = null; vReqMsg = null; persist()
    }
    fun loadDefaultSession() { loadSession(defaultSession(), null) }
    // Switching to another session replaces the current working set. If the
    // current session was never saved to a file, confirm first (and offer to
    // save it); a file-backed session auto-saves, so just switch.
    fun requestSwitch(inAction: () -> Unit) {
        if (vSessionPath == null) vSwitchAction = inAction else inAction()
    }
    fun renameSession(inName: String) {
        val vOld = vSessionPath ?: return
        if (inName.isBlank()) return
        renameFile(vOld, inName).fold(
            onSuccess = { vNew -> vRecent.remove(vOld); vSessionPath = vNew; rememberRecent(vNew); vReqMsg = "Session renamed."; persist() },
            onFailure = { vReqMsg = "Rename failed: ${it.message}" },
        )
    }

    // ============
    //  Save-and-close: the session is always persisted (state cache + its file
    //  if it has one), so closing never needs a warning.
    //  Plus app-wide keyboard shortcuts (work even with nothing focused).
    DisposableEffect(Unit) {
        vWindow.setOnCloseRequest { persist(); true }
        vWindow.setOnKeyShortcut { vKey ->
            if (vKey.type != KeyEventType.KeyDown) return@setOnKeyShortcut false
            // Confirm dialogs: Enter confirms, Escape cancels. Only one is ever
            // open at a time, so run whichever action applies and clear them all.
            if (vRenameTarget != null || vRenamePackTarget != null || vRenameSession || vRemovePackTarget != null || vDeleteTarget != null) {
                when (vKey.key) {
                    Key.Escape -> {
                        vRenameTarget = null; vRenamePackTarget = null; vRenameSession = false; vRemovePackTarget = null; vDeleteTarget = null
                        return@setOnKeyShortcut true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        vRenameTarget?.let { if (vRenameText.isNotBlank()) renameRequest(it, vRenameText.trim()) }
                        vRenamePackTarget?.let { renamePack(it, vRenameText) }
                        if (vRenameSession) renameSession(vRenameText)
                        vRemovePackTarget?.let { removePack(it) }
                        vDeleteTarget?.let { deleteRequest(it) }
                        vRenameTarget = null; vRenamePackTarget = null; vRenameSession = false; vRemovePackTarget = null; vDeleteTarget = null
                        return@setOnKeyShortcut true
                    }
                }
            }
            val vPrimary = vKey.isCtrlPressed || vKey.isMetaPressed
            when {
                vPrimary && vKey.key == Key.S -> { saveSession(); true }
                vPrimary && (vKey.key == Key.Enter || vKey.key == Key.NumPadEnter) -> {
                    if (!vEnvActive) activePack()?.active?.let { send(it) }; true
                }
                vPrimary && vKey.key == Key.N -> { newRequest(); true }
                vPrimary && vKey.key == Key.W -> {
                    val vP = activePack()
                    if (vEnvActive && vP != null) closeEnv(vP) else vP?.let { vQ -> vQ.active?.let { closeTab(it, vQ) } }
                    true
                }
                else -> false
            }
        }
        onDispose { vWindow.setOnCloseRequest(null); vWindow.setOnKeyShortcut(null) }
    }

    val vC = if (vDark) DarkColors else LightColors
    val vMat = if (vDark) {
        darkColorScheme(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    } else {
        lightColorScheme(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    }

    MaterialTheme(colorScheme = vMat) {
        CompositionLocalProvider(LocalAppColors provides vC) {
            val c = vC
            val vP = activePack()   // the active pack/scope (may be the loose root)

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                SessionMenu(
                                    inPath = vSessionPath,
                                    inRecent = vRecent,
                                    inOnSave = { saveSession() },
                                    inOnSaveAs = { saveSessionAs() },
                                    inOnRename = {
                                        vSessionPath?.let {
                                            vRenameSession = true; vRenameText = fileLeaf(it)
                                        }
                                    },
                                    inOnReveal = { vSessionPath?.let { revealInFileManager(it) } },
                                    inOnSettings = { openSessionTab() },
                                    inOnDefault = { requestSwitch { loadDefaultSession() } },
                                    inOnOpen = { requestSwitch { openSessionFile() } },
                                    inOnNew = { requestSwitch { newSession() } },
                                    inOnOpenRecent = { vPath -> requestSwitch { openSession(vPath) } },
                                    inModifier = Modifier.weight(1f),
                                )
                                OptionsMenu(
                                    inDark = vDark,
                                    inOnToggleTheme = { vDark = !vDark; persist() },
                                )
                            }
                            // Tabs centred via equal-weight side slots; the + sits in the
                            // left slot so it doesn't shift the centring.
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    AddPackMenu(
                                        inOnNewRequest = { newLooseRequest() },
                                        inOnNew = { newPack() },
                                        inOnImport = { openPackFile() })
                                }
                                TabBar(listOf("Requests", "History"), vSideTab) { vSideTab = it }
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        HorizontalDivider(color = c.border)

                        // Scrollable list area below the pinned header.
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (vSideTab) {
                                0 -> {
                                    // Loose requests (session root) render headerless at the top.
                                    // Shown even while empty during a request drag, so it's a drop target.
                                    if (vRoot.requests.isNotEmpty() || (vTreeDrag.draggingReq && vTreeDrag.engaged)) {
                                        PackSection(
                                            inPack = vRoot,
                                            inHeaderless = true,
                                            inHeaderActive = false,
                                            inActiveReq = if (vActivePackRef === vRoot && !vSessionActive) vRoot.active else null,
                                            inDrag = vTreeDrag,
                                            inResolveReqDrop = { resolveReqDrop(it) },
                                            inResolvePackDrop = {},
                                            inOnReqDropEnd = { reqDropEnd() },
                                            inOnPackDropEnd = {},
                                            inOnSelect = {},
                                            inOnToggle = {},
                                            inOnOpenRequest = { vRs -> open(vRs, vRoot) },
                                            inOnNewRequest = { newLooseRequest() },
                                            inOnRenameRequest = { vRs ->
                                                selectPack(vRoot); vRenameTarget = vRs; vRenameText = vRs.req.name
                                            },
                                            inOnDuplicateRequest = { vRs -> selectPack(vRoot); duplicate(vRs) },
                                            inOnCopyCurl = { vRs ->
                                                currentClipboard.setText(
                                                    AnnotatedString(
                                                        toCurl(
                                                            resolveVars(
                                                                vRs.req.copy(
                                                                    headers = effectiveHeaders(vRs.req, vRoot),
                                                                    params = effectiveParams(vRs.req, vRoot)
                                                                ), effectiveReqVars(vRs.req, vRoot)
                                                            )
                                                        )
                                                    )
                                                )
                                                vReqMsg = "Copied cURL."
                                            },
                                            inOnDeleteRequest = { vRs -> selectPack(vRoot); vDeleteTarget = vRs },
                                            inOnRenamePack = {},
                                            inOnDuplicatePack = {},
                                            inOnNewSubPack = {},
                                            inOnLinkedCopy = {},
                                            inOnSavePack = {},
                                            inOnSaveAsPack = {},
                                            inOnRemovePack = {},
                                            inOnSetColor = {},
                                        )
                                        if (vPacks.isNotEmpty()) {
                                            HorizontalDivider(color = c.border)
                                        }
                                    }
                                    if (vPacks.isEmpty() && vRoot.requests.isEmpty()) {
                                        Text(
                                            "Nothing here yet — use Add (+) for a request or pack, or Open above.",
                                            color = c.dim,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        val vOps = PackOps(
                                            onEnv = { openEnv(it) },
                                            onToggle = { it.expanded = !it.expanded },
                                            onOpenReq = { vQ, vRs -> open(vRs, vQ) },
                                            onNewReq = { selectPack(it); newRequest() },
                                            onRenameReq = { vQ, vRs ->
                                                selectPack(vQ); vRenameTarget = vRs; vRenameText = vRs.req.name
                                            },
                                            onDupReq = { vQ, vRs -> selectPack(vQ); duplicate(vRs) },
                                            onCopyCurl = { vQ, vRs ->
                                                currentClipboard.setText(
                                                    AnnotatedString(
                                                        toCurl(
                                                            resolveVars(
                                                                vRs.req.copy(
                                                                    headers = effectiveHeaders(vRs.req, vQ),
                                                                    params = effectiveParams(vRs.req, vQ)
                                                                ), effectiveReqVars(vRs.req, vQ)
                                                            )
                                                        )
                                                    )
                                                )
                                                vReqMsg = "Copied cURL."
                                            },
                                            onDelReq = { vQ, vRs -> selectPack(vQ); vDeleteTarget = vRs },
                                            onRenamePack = { vRenamePackTarget = it; vRenameText = it.name },
                                            onDupPack = { duplicatePack(it) },
                                            onLinkedCopy = { createLinkedPack(it) },
                                            onNewSubPack = { newSubPack(it) },
                                            onSave = { selectPack(it); savePack() },
                                            onSaveAs = { selectPack(it); saveAsPack() },
                                            onRemove = { vRemovePackTarget = it },
                                            onSetColor = { vQ, vCol -> vQ.color = vCol; vQ.dirty = true; persist() },
                                            drag = vTreeDrag,
                                            resolveReqDrop = { resolveReqDrop(it) },
                                            resolvePackDrop = { resolvePackDrop(it) },
                                            reqDropEnd = { reqDropEnd() },
                                            packDropEnd = { packDropEnd() },
                                        )
                                        val vHi = if (vSessionActive) null else vP
                                        vPacks.forEach { vPack -> PackTree(vPack, vPacks, 0, vOps, vHi, vEnvActive) }
                                        // Drop bar after the last top-level pack (append-to-root target).
                                        if (vTreeDrag.draggingPack && vTreeDrag.engaged && vTreeDrag.dropInto == null &&
                                            vTreeDrag.dropParent == null && vTreeDrag.dropSibIndex == vPacks.size
                                        )
                                            RowDropBar()
                                    }
                                }

                                1 -> {
                                    if (vHistory.isEmpty()) Text(
                                        "No requests sent yet.",
                                        color = c.dim,
                                        fontSize = 12.sp
                                    )
                                    vHistory.forEachIndexed { vI, vH ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val vTarget = activePack() ?: return@clickable
                                                    val vRs = ReqState(vH.request.copy())
                                                    vTarget.requests.add(vRs); vTarget.openTabs.add(vRs); vTarget.active =
                                                    vRs
                                                    vTarget.dirty = true; vSideTab = 0; vReqMsg = null
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            MethodTag(vH.method)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    vH.url,
                                                    color = c.text,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Text(
                                                    "#${vHistory.size - vI} · ${vH.timeMs} ms",
                                                    color = c.dim,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Text(
                                                if (vH.status > 0) "${vH.status}" else "ERR",
                                                color = statusColor(vH.status),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                },
                second = {
                    // ============
                    //  Main — unified tab strip over the editor (request) or pack env.
                    val vTabs = stripTabs()
                    val vReqActive = vP?.active
                    val vEnvShown = vEnvActive && vP != null && vP.envOpen
                    if (vTabs.isEmpty()) {
                        Column(modifier = Modifier.fillMaxSize().background(c.bg), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.weight(1f))
                            if (vPacks.isEmpty()) {
                                Text("No pack open.", color = c.dim)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedAction(MaterialSymbols.Add, "New pack") { newPack() }
                                    OutlinedAction(MaterialSymbols.Folder, "Import pack…") { openPackFile() }
                                    OutlinedAction(MaterialSymbols.Refresh, "Load default session") { loadDefaultSession() }
                                }
                            } else Text("Nothing open — click a request, a pack, or Session settings.", color = c.dim)
                            Spacer(Modifier.weight(1f))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
                            // Panel 1 — unified tab strip (session + pack-settings + request tabs).
                            RequestTabStrip(
                                inTabs = vTabs,
                                inActiveKey = when {
                                    vSessionActive -> kSessionTabKey; vEnvShown && vP != null -> TabKey(
                                        vP,
                                        null
                                    ); vReqActive != null && vP != null -> TabKey(vP, vReqActive); else -> null
                                },
                                inOnSelect = { vT ->
                                    when {
                                        vT.isSession -> openSessionTab(); vT.req != null && vT.pack != null -> open(
                                        vT.req,
                                        vT.pack
                                    ); vT.pack != null -> openEnv(vT.pack); else -> {}
                                    }
                                },
                                inOnClose = { vT ->
                                    when {
                                        vT.isSession -> closeSessionTab(); vT.req != null && vT.pack != null -> closeTab(
                                        vT.req,
                                        vT.pack
                                    ); vT.pack != null -> closeEnv(vT.pack); else -> {}
                                    }
                                },
                                inOnCloseOthers = { vT -> closeOthers(vT) },
                                inOnCloseAll = { closeAllTabs() },
                                inOnReorder = { vFrom, vTo -> reorderTabs(vFrom, vTo) },
                            )
                            HorizontalDivider(color = c.border)

                            when {
                                vSessionActive -> {
                                    ScopeSettings(
                                        inTab = vSettingsTab,
                                        inOnTab = { vSettingsTab = it },
                                        inHeader = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                MaterialSymbolsOutlined(
                                                    MaterialSymbols.Tune,
                                                    tint = c.accent,
                                                    size = 18.dp
                                                )
                                                Text("Session", color = c.text, fontSize = 19.sp)
                                            }
                                        },
                                        inVars = vGlobalEnv,
                                        inOnVars = { vGlobalEnv.clear(); vGlobalEnv.addAll(it); persist() },
                                        inVarHelp = "Shared across every pack; override a pack's own vars. Used as {{name}}.",
                                        inParams = vSessionParams,
                                        inOnParams = { vSessionParams.clear(); vSessionParams.addAll(it); persist() },
                                        inParamHelp = "Query params added to every request in the session. Packs and requests can override by key.",
                                        inHeaders = vSessionHeaders,
                                        inOnHeaders = { vSessionHeaders.clear(); vSessionHeaders.addAll(it); persist() },
                                        inHeaderHelp = "Sent with every request in the session. Packs and requests can override by key.",
                                        inCert = vSessionCert,
                                        inOnCert = { vSessionCert = it; persist() },
                                        inCertHelp = "Fallback client cert for every request, unless a pack or request sets its own.",
                                        inCertHeading = "Session client certificate",
                                    )
                                }

                                vEnvShown && vP != null -> {
                                    ScopeSettings(
                                        inTab = vSettingsTab,
                                        inOnTab = { vSettingsTab = it },
                                        inHeader = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ColorDot(vP.color)
                                                Text(vP.name, color = c.text, fontSize = 19.sp)
                                            }
                                        },
                                        inVars = vP.variables,
                                        inOnVars = {
                                            vP.variables.clear(); vP.variables.addAll(it); vP.dirty = true; persist()
                                        },
                                        inVarHelp = "Used by every request in this pack as {{name}}. Session Var overrides these.",
                                        inParams = vP.params,
                                        inOnParams = {
                                            vP.params.clear(); vP.params.addAll(it); vP.dirty = true; persist()
                                        },
                                        inParamHelp = "Query params added to every request in this pack. A request can override one by the same key.",
                                        inHeaders = vP.headers,
                                        inOnHeaders = {
                                            vP.headers.clear(); vP.headers.addAll(it); vP.dirty = true; persist()
                                        },
                                        inHeaderHelp = "Sent with every request in this pack. A request can override a header by the same key.",
                                        inCert = vP.cert,
                                        inOnCert = { vP.cert = it; vP.dirty = true; persist() },
                                        inCertHelp = "Used by every request in this pack unless the request sets its own. Overrides the session cert.",
                                        inCertHeading = "Pack client certificate",
                                        // What this pack inherits from above (session + ancestor packs — itself excluded).
                                        inInheritedVars = sourcedVars(scopeChain(vP.parent)),
                                        inInheritedParams = sourcedParams(scopeChain(vP.parent)),
                                        inInheritedHeaders = sourcedHeaders(scopeChain(vP.parent)),
                                        inInheritedCert = sourcedCert(scopeChain(vP.parent)),
                                    )
                                }

                                vReqActive != null && vP != null -> {
                                    val vReq = vReqActive.req
                                    if (vP.isLinked) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().background(c.accent.copy(alpha = 0.12f))
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            MaterialSymbolsOutlined(
                                                MaterialSymbols.Share,
                                                tint = c.accent,
                                                size = 14.dp
                                            )
                                            Text(
                                                "Linked copy — read-only. Runs with this pack's Var/Header/Cert; edit the request in “${vP.linkedSource?.name ?: "source"}”.",
                                                color = c.dim,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    // Panel 2 — unified method · url · send.
                                    UrlBar(
                                        inReq = vReq,
                                        inLoading = vReqActive.loading,
                                        inChainLoading = vReqActive.chainLoading,
                                        inOnMethod = { m -> edit { it.copy(method = m) } },
                                        inOnUrl = { v -> edit { it.copy(url = v) } },
                                        inOnSend = { send(vReqActive) },
                                        inOnCancel = { cancel(vReqActive) },
                                        inOnInspectChain = { inspectChain(vReqActive) },
                                        inReadOnly = vP.isLinked,
                                    )
                                    if (vReqActive.showChain) TlsChainDialog(
                                        vReqActive.tlsChain,
                                        vReqActive.chainUrl
                                    ) { vReqActive.showChain = false }
                                    HorizontalDivider(color = c.border)
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        HorizontalSplitPane(
                                            initialFirstSize = 460.dp,
                                            minFirstSize = 320.dp,
                                            minSecondSize = 320.dp,
                                            dividerColor = c.border,
                                            dividerHoverColor = c.accent,
                                            first = {
                                                RequestBuilder(
                                                    inReq = vReq,
                                                    inRs = vReqActive,
                                                    inUnresolved = unresolvedVars(vReq, effectiveReqVars(vReq, vP)),
                                                    inMsg = vReqMsg,
                                                    inInheritedVars = sourcedVars(scopeChain(vP)),
                                                    inInheritedParams = sourcedParams(scopeChain(vP)),
                                                    inInheritedHeaders = sourcedHeaders(scopeChain(vP)),
                                                    inInheritedCert = sourcedCert(scopeChain(vP)),
                                                    inReadOnly = vP.isLinked,
                                                    inEdit = { t -> edit(t) },
                                                )
                                            },
                                            second = {
                                                ViewerPanel(
                                                    inRs = vReqActive,
                                                    inResolved = resolveVars(
                                                        vReq.copy(
                                                            headers = effectiveHeaders(
                                                                vReq,
                                                                vP
                                                            ), params = effectiveParams(vReq, vP)
                                                        ), effectiveReqVars(vReq, vP)
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }

                                else -> Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Pick a tab above.", color = c.dim)
                                }
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

            val vRenPack = vRenamePackTarget
            if (vRenPack != null) {
                Dialog(onDismissRequest = { vRenamePackTarget = null }) {
                    Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(360.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Rename pack", color = c.text, fontSize = 16.sp)
                            ThinField(vRenameText, { vRenameText = it }, inModifier = Modifier.fillMaxWidth(), inPlaceholder = "Name")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    renamePack(vRenPack, vRenameText)
                                    vRenamePackTarget = null
                                }) { BtnContent(MaterialSymbols.Check, "Save", c.onAccent) }
                                OutlinedButton(onClick = { vRenamePackTarget = null }) { Text("Cancel", color = c.text) }
                            }
                        }
                    }
                }
            }

            // Confirm replacing an unsaved session (Default / New / Open).
            val vSwitch = vSwitchAction
            if (vSwitch != null) {
                Dialog(onDismissRequest = { vSwitchAction = null }) {
                    Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(420.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MaterialSymbolsOutlined(MaterialSymbols.Warning, tint = kWarnColor, size = 20.dp)
                                Text("Replace current session?", color = c.text, fontSize = 16.sp)
                            }
                            Text("This session hasn't been saved to a file — continuing will discard it. Save it first if you want to keep it.", color = c.dim, fontSize = 13.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { vSwitchAction = null }) { Text("Cancel", color = c.text) }
                                    Button(onClick = { vSwitchAction = null; saveSessionAs { vSwitch() } }) { BtnContent(MaterialSymbols.Save, "Save first…", c.onAccent) }
                                    DangerButton("Discard", MaterialSymbols.Delete) { vSwitchAction = null; vSwitch() }
                                }
                            }
                        }
                    }
                }
            }

            if (vRenameSession) {
                Dialog(onDismissRequest = { vRenameSession = false }) {
                    Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(360.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Rename session", color = c.text, fontSize = 16.sp)
                            ThinField(vRenameText, { vRenameText = it }, inModifier = Modifier.fillMaxWidth(), inPlaceholder = "File name")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    renameSession(vRenameText)
                                    vRenameSession = false
                                }) { BtnContent(MaterialSymbols.Check, "Rename", c.onAccent) }
                                OutlinedButton(onClick = { vRenameSession = false }) { Text("Cancel", color = c.text) }
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

            val vRmPack = vRemovePackTarget
            if (vRmPack != null) {
                Dialog(onDismissRequest = { vRemovePackTarget = null }) {
                    Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(380.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MaterialSymbolsOutlined(MaterialSymbols.Delete, tint = methodColor(ReqMethod.DELETE), size = 20.dp)
                                Text("Remove pack", color = c.text, fontSize = 16.sp)
                            }
                            Text("\"${vRmPack.name}\" will be removed from the session. Unsaved changes are lost — export it first to keep them.", color = c.dim, fontSize = 13.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { vRemovePackTarget = null }) { Text("Cancel", color = c.text) }
                                    DangerButton("Remove", MaterialSymbols.Delete) { removePack(vRmPack); vRemovePackTarget = null }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

