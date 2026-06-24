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
    var tlsChain by mutableStateOf<TlsChain?>(null)  // last fetched server cert chain
    var chainLoading by mutableStateOf(false)
    var showChain by mutableStateOf(false)           // chain dialog open
    var chainUrl by mutableStateOf("")               // resolved URL the chain was fetched from
}

/* One open pack: its file path (null = never saved), a dirty flag (edits since
   last file save), and its own requests, variables and open-tab strip. Switching
   packs swaps the whole working set. */
private class PackState(inPack: Pack, inPath: String?, inDirty: Boolean, inOpenTabs: List<Int> = emptyList(), inActive: Int = -1) {
    var name by mutableStateOf(inPack.name)
    var path by mutableStateOf(inPath)
    var dirty by mutableStateOf(inDirty)
    var color by mutableStateOf(inPack.color)
    // A linked copy mirrors its source's requests read-only; non-linked packs own
    // their requests. `requests` resolves to whichever applies.
    var linkedSource by mutableStateOf<PackState?>(null)
    private val fOwnRequests = mutableStateListOf<ReqState>().apply {
        inPack.requests.forEach { add(ReqState(it)) }   // packs (and the loose root) may be empty
    }
    val requests: androidx.compose.runtime.snapshots.SnapshotStateList<ReqState>
        get() = linkedSource?.requests ?: fOwnRequests
    val isLinked: Boolean get() = linkedSource != null
    var parent: PackState? = null                                                  // enclosing pack (null = top-level / root)
    val subPacks = mutableStateListOf<PackState>().apply {
        inPack.subPacks.forEach { add(PackState(it, null, false).also { vSp -> vSp.parent = this@PackState }) }
    }
    val variables = mutableStateListOf<KeyVal>().apply { addAll(inPack.variables) }
    val headers = mutableStateListOf<KeyVal>().apply { addAll(inPack.headers) }   // pack-level, inherited by requests
    val params = mutableStateListOf<KeyVal>().apply { addAll(inPack.params) }      // pack-level query params (inherited)
    var cert by mutableStateOf(inPack.cert)                                        // pack-level client cert (inherited)
    val id: String = inPack.id.ifBlank { newPackId() }                            // stable id (linked copies reference it)
    val isRoot: Boolean = inPack.isRoot                                            // the session-root "pack" (loose requests)
    // No auto-open: tabs start empty and are restored from persisted state only.
    val openTabs = mutableStateListOf<ReqState>().apply {
        inOpenTabs.forEach { vIdx -> requests.getOrNull(vIdx)?.let { add(it) } }
    }
    var active by mutableStateOf(requests.getOrNull(inActive)?.takeIf { it in openTabs } ?: openTabs.firstOrNull())
    var expanded by mutableStateOf(true)   // sidebar fold state (transient — not part of the pack file)
    var envOpen by mutableStateOf(false)   // whether this pack's env tab is open in the strip (transient)

    fun toPack(): Pack = Pack(
        name = name,
        requests = if (isLinked) emptyList() else requests.map { it.req },   // linked: requests live in the source
        variables = variables.toList(), color = color, headers = headers.toList(), params = params.toList(), cert = cert,
        id = id, linkedTo = linkedSource?.id, isRoot = isRoot,
        subPacks = subPacks.map { it.toPack() },
    )
}

/* A fresh random id for a pack (used to wire linked-copy packs to their source
   across save / load). */
private fun newPackId(): String = "pk-" + kotlin.random.Random.nextLong().toULong().toString(16)

/* One entry in the unified tab strip: a request tab, a pack's settings tab when
   req == null, or the session settings tab when isSession (pack == null). The
   underlying ReqState / PackState (or the session key) is the stable identity. */
private const val kSessionTabKey = "session-settings"
private class StripTab(val pack: PackState?, val req: ReqState?, val isSession: Boolean = false)
/* Identity of a strip tab. A linked pack shares the source's ReqState objects,
   so the key must include the pack — otherwise the source's and linked's tabs
   for the same request collide (double selection, duplicate key()). Data class →
   structural equality, so compare keys with == (not ===). */
private data class TabKey(val pack: PackState?, val req: ReqState?)
private val StripTab.tabKey: Any get() = if (isSession) kSessionTabKey else TabKey(pack, req)

// ==================
// MARK: Cross-pack drag (sidebar tree)
// ==================

/* Shared state for dragging within and across packs in the sidebar tree. One
   instance is hoisted to App so any PackSection can be the drag source or the
   drop target. Two drag kinds: a request row (dragReq, owned by dragReqOwner) or
   a whole pack header (dragPack). Every rendered row / header registers its
   absolute window position + height here so onDrag can hit-test across packs,
   even into collapsed or empty ones. The resolved drop target is published back
   so the target section draws the indicator. */
private class TreeDrag {
    // What's being dragged (at most one is non-null while a drag is live).
    var dragReq by mutableStateOf<ReqState?>(null)
    var dragReqOwner by mutableStateOf<PackState?>(null)
    var dragPack by mutableStateOf<PackState?>(null)
    var dy by mutableStateOf(0f)            // draw-only follow offset for the grabbed element
    var pressRel by mutableStateOf(0)       // relY at capture (cursor = grabbed-element top + relY)
    // A press doesn't become a drag until the pointer moves past kDragSlop — so a
    // plain click (which carries a pixel or two of jitter) still selects / opens.
    var engaged by mutableStateOf(false)

    // Geometry registry (absolute window coords), filled each frame by every section.
    val rowTop = mutableStateMapOf<ReqState, Int>()
    val rowH = mutableStateMapOf<ReqState, Int>()
    val headTop = mutableStateMapOf<PackState, Int>()
    val headH = mutableStateMapOf<PackState, Int>()

    // Resolved request-drop target: the request lands in dropPack at dropIndex
    // (index among that pack's rows, excluding the dragged one).
    var dropPack by mutableStateOf<PackState?>(null)
    var dropIndex by mutableStateOf(-1)

    // Resolved pack-drop target. dropInto != null → nest as the last child of that
    // pack (header highlight). Otherwise a sibling insert: parent dropParent (null =
    // top level) at dropSibIndex (a between-siblings bar).
    var dropParent by mutableStateOf<PackState?>(null)
    var dropSibIndex by mutableStateOf(-1)
    var dropInto by mutableStateOf<PackState?>(null)

    val draggingReq: Boolean get() = dragReq != null
    val draggingPack: Boolean get() = dragPack != null

    fun clear() {
        dragReq = null; dragReqOwner = null; dragPack = null; dy = 0f; engaged = false
        dropPack = null; dropIndex = -1
        dropParent = null; dropSibIndex = -1; dropInto = null
    }
}

// Pixels the pointer must travel before a press is treated as a drag (drag slop).
private const val kDragSlop = 5f

// An inherited variable / header tagged with where it comes from: `source` is the
// short pill label ("Session" / "Pack"), `path` the full path for the tooltip
// ("Session" or e.g. "Methods / Nested"). The single inherited cert is tagged the same.
private data class InheritedKv(val kv: KeyVal, val source: String, val path: String)
private data class InheritedCert(val cert: CertConfig, val source: String, val path: String)

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
            if (vKey.type != KeyEventType.Down) return@setOnKeyShortcut false
            // Confirm dialogs: Enter confirms, Escape cancels. Only one is ever
            // open at a time, so run whichever action applies and clear them all.
            if (vRenameTarget != null || vRenamePackTarget != null || vRenameSession || vRemovePackTarget != null || vDeleteTarget != null) {
                when (vKey.keyCode) {
                    kScEscape -> {
                        vRenameTarget = null; vRenamePackTarget = null; vRenameSession = false; vRemovePackTarget = null; vDeleteTarget = null
                        return@setOnKeyShortcut true
                    }
                    kScEnter, kScKpEnter -> {
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
            val vPrimary = vKey.modifiers.ctrl || vKey.modifiers.meta
            when {
                vPrimary && vKey.keyCode == kScS -> { saveSession(); true }
                vPrimary && (vKey.keyCode == kScEnter || vKey.keyCode == kScKpEnter) -> {
                    if (!vEnvActive) activePack()?.active?.let { send(it) }; true
                }
                vPrimary && vKey.keyCode == kScN -> { newRequest(); true }
                vPrimary && vKey.keyCode == kScW -> {
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
        darkColors(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    } else {
        lightColors(primary = vC.accent, background = vC.bg, surface = vC.panel, onPrimary = vC.onAccent, onBackground = vC.text, onSurface = vC.text)
    }

    MaterialTheme(colors = vMat) {
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                SessionMenu(
                                    inPath = vSessionPath,
                                    inRecent = vRecent,
                                    inOnSave = { saveSession() },
                                    inOnSaveAs = { saveSessionAs() },
                                    inOnRename = { vSessionPath?.let { vRenameSession = true; vRenameText = fileLeaf(it) } },
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
                                    AddPackMenu(inOnNewRequest = { newLooseRequest() }, inOnNew = { newPack() }, inOnImport = { openPackFile() })
                                }
                                TabBar(listOf("Packs", "History"), vSideTab) { vSideTab = it }
                                Spacer(modifier = Modifier.weight(1f))
                            }
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
                                            inOnSelect = {}, inOnToggle = {},
                                            inOnOpenRequest = { vRs -> open(vRs, vRoot) },
                                            inOnNewRequest = { newLooseRequest() },
                                            inOnRenameRequest = { vRs -> selectPack(vRoot); vRenameTarget = vRs; vRenameText = vRs.req.name },
                                            inOnDuplicateRequest = { vRs -> selectPack(vRoot); duplicate(vRs) },
                                            inOnCopyCurl = { vRs ->
                                                currentClipboard.setText(toCurl(resolveVars(vRs.req.copy(headers = effectiveHeaders(vRs.req, vRoot), params = effectiveParams(vRs.req, vRoot)), effectiveReqVars(vRs.req, vRoot))))
                                                vReqMsg = "Copied cURL."
                                            },
                                            inOnDeleteRequest = { vRs -> selectPack(vRoot); vDeleteTarget = vRs },
                                            inOnRenamePack = {}, inOnDuplicatePack = {}, inOnNewSubPack = {}, inOnLinkedCopy = {},
                                            inOnSavePack = {}, inOnSaveAsPack = {}, inOnRemovePack = {}, inOnSetColor = {},
                                        )
                                        if (vPacks.isNotEmpty()) Divider(color = c.border)
                                    }
                                    if (vPacks.isEmpty() && vRoot.requests.isEmpty()) {
                                        Text("Nothing here yet — use Add (+) for a request or pack, or Open above.", color = c.dim, fontSize = 12.sp)
                                    } else {
                                        val vOps = PackOps(
                                            onEnv = { openEnv(it) },
                                            onToggle = { it.expanded = !it.expanded },
                                            onOpenReq = { vQ, vRs -> open(vRs, vQ) },
                                            onNewReq = { selectPack(it); newRequest() },
                                            onRenameReq = { vQ, vRs -> selectPack(vQ); vRenameTarget = vRs; vRenameText = vRs.req.name },
                                            onDupReq = { vQ, vRs -> selectPack(vQ); duplicate(vRs) },
                                            onCopyCurl = { vQ, vRs ->
                                                currentClipboard.setText(toCurl(resolveVars(vRs.req.copy(headers = effectiveHeaders(vRs.req, vQ), params = effectiveParams(vRs.req, vQ)), effectiveReqVars(vRs.req, vQ))))
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
                                            vTreeDrag.dropParent == null && vTreeDrag.dropSibIndex == vPacks.size)
                                            RowDropBar()
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
                                inActiveKey = when { vSessionActive -> kSessionTabKey; vEnvShown && vP != null -> TabKey(vP, null); vReqActive != null && vP != null -> TabKey(vP, vReqActive); else -> null },
                                inOnSelect = { vT -> when { vT.isSession -> openSessionTab(); vT.req != null && vT.pack != null -> open(vT.req, vT.pack); vT.pack != null -> openEnv(vT.pack); else -> {} } },
                                inOnClose = { vT -> when { vT.isSession -> closeSessionTab(); vT.req != null && vT.pack != null -> closeTab(vT.req, vT.pack); vT.pack != null -> closeEnv(vT.pack); else -> {} } },
                                inOnCloseOthers = { vT -> closeOthers(vT) },
                                inOnCloseAll = { closeAllTabs() },
                                inOnReorder = { vFrom, vTo -> reorderTabs(vFrom, vTo) },
                            )
                            Divider(color = c.border)

                            when {
                                vSessionActive -> {
                                    ScopeSettings(
                                        inTab = vSettingsTab, inOnTab = { vSettingsTab = it },
                                        inHeader = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                MaterialSymbolsOutlined(MaterialSymbols.Tune, tint = c.accent, size = 18.dp)
                                                Text("Session", color = c.text, fontSize = 19.sp)
                                            }
                                        },
                                        inVars = vGlobalEnv, inOnVars = { vGlobalEnv.clear(); vGlobalEnv.addAll(it); persist() },
                                        inVarHelp = "Shared across every pack; override a pack's own vars. Used as {{name}}.",
                                        inParams = vSessionParams, inOnParams = { vSessionParams.clear(); vSessionParams.addAll(it); persist() },
                                        inParamHelp = "Query params added to every request in the session. Packs and requests can override by key.",
                                        inHeaders = vSessionHeaders, inOnHeaders = { vSessionHeaders.clear(); vSessionHeaders.addAll(it); persist() },
                                        inHeaderHelp = "Sent with every request in the session. Packs and requests can override by key.",
                                        inCert = vSessionCert, inOnCert = { vSessionCert = it; persist() },
                                        inCertHelp = "Fallback client cert for every request, unless a pack or request sets its own.",
                                        inCertHeading = "Session client certificate",
                                    )
                                }
                                vEnvShown && vP != null -> {
                                    ScopeSettings(
                                        inTab = vSettingsTab, inOnTab = { vSettingsTab = it },
                                        inHeader = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                ColorDot(vP.color)
                                                Text(vP.name, color = c.text, fontSize = 19.sp)
                                            }
                                        },
                                        inVars = vP.variables, inOnVars = { vP.variables.clear(); vP.variables.addAll(it); vP.dirty = true; persist() },
                                        inVarHelp = "Used by every request in this pack as {{name}}. Session Var overrides these.",
                                        inParams = vP.params, inOnParams = { vP.params.clear(); vP.params.addAll(it); vP.dirty = true; persist() },
                                        inParamHelp = "Query params added to every request in this pack. A request can override one by the same key.",
                                        inHeaders = vP.headers, inOnHeaders = { vP.headers.clear(); vP.headers.addAll(it); vP.dirty = true; persist() },
                                        inHeaderHelp = "Sent with every request in this pack. A request can override a header by the same key.",
                                        inCert = vP.cert, inOnCert = { vP.cert = it; vP.dirty = true; persist() },
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
                                            modifier = Modifier.fillMaxWidth().background(c.accent.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            MaterialSymbolsOutlined(MaterialSymbols.Share, tint = c.accent, size = 14.dp)
                                            Text("Linked copy — read-only. Runs with this pack's Var/Header/Cert; edit the request in “${vP.linkedSource?.name ?: "source"}”.", color = c.dim, fontSize = 11.sp)
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
                                    if (vReqActive.showChain) TlsChainDialog(vReqActive.tlsChain, vReqActive.chainUrl) { vReqActive.showChain = false }
                                    Divider(color = c.border)
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
                                                    inResolved = resolveVars(vReq.copy(headers = effectiveHeaders(vReq, vP), params = effectiveParams(vReq, vP)), effectiveReqVars(vReq, vP)),
                                                )
                                            },
                                        )
                                    }
                                }
                                else -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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

// ==================
// MARK: Pack colour (palette dot + picker)
// ==================

/* A small rounded square in the pack's palette colour (neutral when unset).
   The pack's visual identity in the sidebar's foldable pack header. */
@Composable
private fun ColorDot(inColor: Int, inSize: Dp = 11.dp) {
    val c = LocalAppColors.current
    val vCol = packColor(inColor) ?: c.dim.copy(alpha = 0.35f)
    Box(Modifier.size(inSize).background(vCol, RoundedCornerShape(3.dp)))
}

/* A 5×4 grid of swatches; tapping one sets the pack's colour (1-based index).
   The current colour gets a ring. Sized to fit inside the (fixed-width) ⋮ menu. */
@Composable
private fun PackColorPicker(inSelected: Int, inOnPick: (Int) -> Unit) {
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
private fun AddPackMenu(inOnNewRequest: () -> Unit, inOnNew: () -> Unit, inOnImport: () -> Unit) {
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
private fun fileLeaf(inPath: String): String =
    inPath.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

/* Shortens a long path to inMax chars keeping the start and end (so the root
   and file stay visible), joined by an ellipsis. The menu has no text-overflow
   primitive, so we trim the string ourselves. */
private fun ellipsizeMiddle(inText: String, inMax: Int = 46): String {
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
private fun SessionMenu(
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
private class PackOps(
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
private fun PackTree(inPack: PackState, inSiblings: List<PackState>, inDepth: Int, inOps: PackOps, inActive: PackState?, inEnvActive: Boolean) {
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
private fun PackSection(
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
        if (vHeaderDragged) vHeadMod = vHeadMod.zIndex(1f).alpha(0.65f).translate(0f, inDrag.dy)
        vHeadMod = vHeadMod.clip(RoundedCornerShape(6.dp))
            .background(if (inHeaderActive || vIntoHi) c.accent.copy(alpha = if (vIntoHi) 0.24f else 0.14f) else Color.Transparent, RoundedCornerShape(6.dp))
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
                        if (vDragged) vMod = vMod.zIndex(1f).alpha(0.65f).translate(0f, inDrag.dy)
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
private fun RequestRow(
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
                if (!inReadOnly) DropdownMenuItem(onClick = { vMenu = false; inOnRename() }) { MenuRow(MaterialSymbols.Edit, "Rename") }
                if (!inReadOnly) DropdownMenuItem(onClick = { vMenu = false; inOnDuplicate() }) { MenuRow(MaterialSymbols.FileCopy, "Duplicate") }
                DropdownMenuItem(onClick = { vMenu = false; inOnCopyCurl() }) { MenuRow(MaterialSymbols.Terminal, "Copy as cURL") }
                if (!inReadOnly) DropdownMenuItem(onClick = { vMenu = false; inOnDelete() }) { MenuRow(MaterialSymbols.Delete, "Delete", methodColor(ReqMethod.DELETE)) }
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
                if (vDragged) vMod = vMod.zIndex(1f).alpha(0.65f).translate(vDragDx, 0f)
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
            DropdownMenuItem(onClick = { vOpen = false; openSettingsFolder() }) {
                MenuRow(MaterialSymbols.Folder, "Open settings folder")
            }
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
    inChainLoading: Boolean,
    inOnMethod: (ReqMethod) -> Unit,
    inOnUrl: (String) -> Unit,
    inOnSend: () -> Unit,
    inOnCancel: () -> Unit,
    inOnInspectChain: () -> Unit,
    inReadOnly: Boolean = false,
) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.alpha(if (inReadOnly) 0.55f else 1f)) {
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
            modifier = Modifier.weight(1f).alpha(if (inReadOnly) 0.55f else 1f).onKeyEvent { ev ->
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

        // Inspect the server's TLS certificate chain (handshake-only probe).
        if (inChainLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
        else {
            var vLockHover by remember { mutableStateOf(false) }
            TooltipBox(text = "Inspect TLS certificate chain") {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (vLockHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .hoverable { vLockHover = it }
                        .clickable(onClick = inOnInspectChain)
                        .padding(6.dp),
                ) {
                    MaterialSymbolsOutlined(MaterialSymbols.Lock, contentDescription = "Inspect TLS chain",
                        tint = if (vLockHover) c.accent else c.dim, size = 18.dp)
                }
            }
        }

        // Send and Cancel are the same Material Button (same MinHeight + padding)
        // so the bar never changes height when toggling — Cancel is just red.
        if (inLoading) Button(
            onClick = inOnCancel,
            colors = ButtonDefaults.buttonColors(backgroundColor = methodColor(ReqMethod.DELETE), contentColor = Color.White),
        ) { BtnContent(MaterialSymbols.Stop, "Cancel", Color.White) }
        else Button(onClick = inOnSend) { BtnContent(MaterialSymbols.Send, "Send", c.onAccent) }
    }
}

/* Bottom-of-panel-3 dropdown choosing the body type (None/Text/Form/File). */
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
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MaterialSymbolsOutlined(MaterialSymbols.Menu, tint = c.dim, size = 15.dp)
            Text(inType.label, color = if (inEnabled) c.text else c.dim, fontSize = 13.sp)
            MaterialSymbolsOutlined(MaterialSymbols.UnfoldMore, tint = c.dim, size = 15.dp)
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor) {
            BodyType.entries.forEach { vT ->
                DropdownMenuItem(onClick = { inOnPick(vT); vOpen = false }) {
                    Text(vT.label, color = if (vT == inType) c.accent else c.text, fontSize = 13.sp)
                }
            }
        }
    }
}

/* Dialog showing the server's TLS certificate chain (one card per cert, plus a
   Copy PEM action). Fetched on demand by the lock button in the URL bar. */
@Composable
private fun TlsChainDialog(inChain: TlsChain?, inUrl: String, inOnDismiss: () -> Unit) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = inOnDismiss) {
        // Width must stay within DialogDefaults.MaxWidth (560dp) or content
        // overflows the dialog's click-swallow box onto the scrim — which would
        // make clicks there dismiss the dialog and swallow hover.
        Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(540.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbolsOutlined(MaterialSymbols.Lock, tint = c.accent, size = 20.dp)
                    Text("TLS certificate chain", color = c.text, fontSize = 16.sp)
                }
                val vCerts = inChain?.certs.orEmpty()
                when {
                    inChain == null -> Text("Probing…", color = c.dim, fontSize = 13.sp)
                    vCerts.isEmpty() -> Text(inChain.error ?: "No chain reported.", color = kWarnColor, fontSize = 13.sp)
                    else -> {
                        Text("${vCerts.size} certificate(s) presented by ${hostOf(inUrl)}", color = c.dim, fontSize = 12.sp)
                        Column(
                            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) { vCerts.forEachIndexed { vI, vCert -> CertCard(vI, vCert) } }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val vChainPem = vCerts.mapNotNull { certField(it.fields, "Cert") }.joinToString("\n")
                        if (vChainPem.isNotEmpty()) CopyButton(vChainPem, "Copy chain")
                        OutlinedButton(onClick = inOnDismiss) { Text("Close", color = c.text) }
                    }
                }
            }
        }
    }
}

/* One certificate's summary card (subject / issuer / validity). Server-presented
   certs get a solid border; derived ones (issuer pulled from the OS store, or a
   name-only placeholder) get an accent-coloured border + a label. A self-signed
   cert shows "Self-signed" in green instead of repeating its issuer. */
@Composable
private fun CertCard(inIndex: Int, inCert: ChainCert) {
    val c = LocalAppColors.current
    val vFields = inCert.fields
    val vSubject = certField(vFields, "Subject")
    val vIssuer = certField(vFields, "Issuer")
    val vSelfSigned = vIssuer != null && vIssuer == vSubject
    val vPem = certField(vFields, "Cert")
    // Backends like Schannel only expose Subject/Issuer/PEM via CURLINFO_CERTINFO,
    // so parse the rest (dates, serial, SAN, algorithms) straight from the PEM.
    val vParsed = remember(vPem) { vPem?.let { parseCertDetails(it) } }
    val vFrom = certField(vFields, "Start date") ?: certField(vFields, "Start Date") ?: vParsed?.notBefore
    val vTo = certField(vFields, "Expire date") ?: certField(vFields, "Expire Date") ?: vParsed?.notAfter
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(c.field.copy(alpha = if (inCert.fromServer) 1f else 0.4f), RoundedCornerShape(8.dp))
            .border(if (inCert.fromServer) 1.dp else 1.5.dp, if (inCert.fromServer) c.border else c.accent, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val vTitle = vSubject?.let { cnOf(it) } ?: if (inIndex == 0) "Leaf certificate" else "Issuer #$inIndex"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(vTitle, color = c.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (!inCert.fromServer) Text(if (vPem != null) "from OS store" else "not presented", color = c.dim, fontSize = 10.sp)
            if (vPem != null) CopyButton(vPem)
        }
        vSubject?.let { CertLine("Subject", it) }
        if (vSelfSigned) CertLine("Issuer", "Self-signed", kSelfSignedColor)
        else vIssuer?.let { CertLine("Issuer", it) }
        // Hosts this cert is valid for — the field that actually has to match the URL.
        (certField(vFields, "X509v3 Subject Alternative Name")
            ?: certField(vFields, "Subject Alternative Name")
            ?: vParsed?.sans?.takeIf { it.isNotEmpty() }?.joinToString(", "))
            ?.let { CertLine("SAN", it) }
        vFrom?.let { CertLine("Issued", it) }
        vTo?.let { CertLine("Expires", it) }
        (certField(vFields, "Serial Number") ?: vParsed?.serial)?.let { CertLine("Serial", it) }
        (certField(vFields, "Signature Algorithm") ?: vParsed?.sigAlg)?.let { CertLine("Signature", it) }
        (certField(vFields, "Public Key Algorithm") ?: vParsed?.keyAlg)?.let { CertLine("Key", it) }
        (certField(vFields, "Version") ?: vParsed?.version?.let { "v$it" })?.let { CertLine("Version", it) }
    }
}

private val kSelfSignedColor = Color(0xFF3FB950L)

/* Copy button — rounded, hover overlay, real click that copies inText and shows
   a green check. Icon-only (per cert) flashes a small floating "Copied" bubble
   for 2s; the labelled variant (Copy chain) flips its label to "Copied" instead.
   Uses a non-catching Popup so it never dismisses the dialog or eats clicks. */
@Composable
private fun CopyButton(inText: String, inLabel: String? = null) {
    val c = LocalAppColors.current
    var vCopied by remember { mutableStateOf(false) }
    var vHover by remember { mutableStateOf(false) }
    var vX by remember { mutableStateOf(0) }
    var vY by remember { mutableStateOf(0) }
    var vHeight by remember { mutableStateOf(0) }
    LaunchedEffect(vCopied) { if (vCopied) { delay(2000); vCopied = false } }
    Row(
        modifier = Modifier
            .onGloballyPositioned { vX = it.x; vY = it.y }
            .onSizeChanged { vHeight = it.height }
            .clip(RoundedCornerShape(7.dp))
            .background(if (vHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(7.dp))
            .hoverable { vHover = it }
            .clickable { currentClipboard.setText(inText); vCopied = true }
            .padding(horizontal = if (inLabel != null) 10.dp else 5.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MaterialSymbolsOutlined(
            if (vCopied) MaterialSymbols.Check else MaterialSymbols.ContentCopy,
            contentDescription = inLabel ?: "Copy PEM",
            tint = if (vCopied) kSelfSignedColor else c.dim,
            size = 15.dp,
        )
        if (inLabel != null) Text(if (vCopied) "Copied" else inLabel, color = if (vCopied) kSelfSignedColor else c.text, fontSize = 12.sp)
    }
    // Icon-only: float a tiny "Copied" bubble (non-catching popup → no dismiss).
    if (vCopied && inLabel == null) {
        Popup(modal = false) {
            Box(
                modifier = Modifier.offset(vX.dp, (vY + vHeight + 4).dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xE6111111L), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("Copied", color = Color.White, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun CertLine(inLabel: String, inValue: String, inValueColor: Color? = null) {
    val c = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(inLabel, color = c.dim, fontSize = 11.sp, modifier = Modifier.width(64.dp))
        Text(inValue, color = inValueColor ?: c.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

/* Look up a CURLINFO_CERTINFO field by name (case-insensitive). */
private fun certField(inFields: List<Pair<String, String>>, inName: String): String? =
    inFields.firstOrNull { it.first.equals(inName, ignoreCase = true) }?.second

/* The CN= value out of a distinguished-name string. Handles every
   format the platform backends emit:
     "CN=R3, O=Let's Encrypt, C=US"        (Windows CertGetNameStringA)
     "/C=US/O=Let's Encrypt/CN=R3"          (OpenSSL X509_NAME_oneline)
     "CN = R3, O = Let's Encrypt, C = US"   (OpenSSL X509_NAME_print_ex
                                             default, with spaces — what
                                             libcurl on macOS emits)
   Null when there's no CN. */
private fun cnOf(inDn: String): String? {
    val vMatch = Regex("""(?i)\bCN\s*=\s*([^,/]+)""").find(inDn) ?: return null
    return vMatch.groupValues[1].trim().ifBlank { null }
}

/* The host portion of a URL (for the chain dialog header). */
private fun hostOf(inUrl: String): String =
    inUrl.substringAfter("://", inUrl).substringBefore("/").substringBefore("?").ifBlank { inUrl }

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
    inInheritedVars: List<InheritedKv>,
    inInheritedParams: List<InheritedKv>,
    inInheritedHeaders: List<InheritedKv>,
    inInheritedCert: InheritedCert?,
    inReadOnly: Boolean,
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
                listOf("Query (${inReq.params.size})", "Var (${inReq.variables.size})", "Headers (${inReq.headers.size})", "Body", "Cert"),
                inRs.reqTab,
                inDots = buildSet { if (vBodySet) add(3); if (inReq.hasClientCert) add(4) },
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

        // Tab content — scrolls, except a Text body which fills the whole panel
        // (so it stays one editable surface you can click anywhere in). Greyed when
        // read-only (a linked-copy request).
        val vScroll = rememberScrollState()
        val vFillBody = inRs.reqTab == 3 && inReq.bodyType == BodyType.TEXT
        Box(modifier = Modifier.fillMaxWidth().weight(1f)
            .then(if (vFillBody) Modifier else Modifier.verticalScroll(vScroll))
            .padding(16.dp).alpha(if (inReadOnly) 0.55f else 1f)) {
            when (inRs.reqTab) {
                0 -> QueryTab(inReq, inInheritedParams, inReadOnly, inEdit)
                1 -> VarTab(inReq, inInheritedVars, inReadOnly, inEdit)
                2 -> HeadersTab(inReq, inInheritedHeaders, inReadOnly, inEdit)
                3 -> BodyContent(inReq) { v -> inEdit(v) }
                else -> RequestCertTab(inReq, inInheritedCert, inReadOnly, inEdit)
            }
        }

        // Body-type selector — only on the Body tab, pinned at the bottom. For a
        // Text body the format/type picker sits beside it; that type drives both the
        // syntax colours and the sent Content-Type.
        if (inRs.reqTab == 3) {
            Divider(color = c.border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BodyTypeMenu(inReq.bodyType, true) { vT -> inEdit { it.copy(bodyType = vT) } }
                if (inReq.bodyType == BodyType.TEXT) {
                    BodyFormatSelector(inSelected = inReq.bodyFormat, inBordered = true, inOnChange = { vF -> inEdit { it.copy(bodyFormat = vF) } })
                }
            }
        }
    }
}

/* The body editing area for the current body type (driven by the bottom menu). A
   Text body is highlighted per its chosen format (bodyFormat). */
@Composable
private fun BodyContent(inReq: ApiRequest, inEdit: ((ApiRequest) -> ApiRequest) -> Unit) {
    when (inReq.bodyType) {
        BodyType.NONE -> ViewerEmpty(MaterialSymbols.Block, "No Body", Modifier.fillMaxWidth().height(240.dp))
        BodyType.TEXT -> BodyView(
            inText = inReq.body,
            modifier = Modifier.fillMaxSize(),
            inOnChange = { v -> inEdit { it.copy(body = v) } },
            inFormat = inReq.bodyFormat,
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

/* Client-certificate (mTLS) editor — certificate + optional separate key +
   passphrase. PEM/DER are used directly by OpenSSL on macOS/Linux; on Windows
   they're imported into the certificate store for the request then removed
   (see CurlMtls). PKCS#12 bundles its own private key. */
@Composable
private fun CertConfigEditor(inCert: CertConfig, inHeading: String = "Client certificate (mTLS)", inOverrideSource: String? = null, inOnChange: (CertConfig) -> Unit) {
    val c = LocalAppColors.current
    val vHasCert = inCert.certPath.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(inHeading, color = c.text, fontSize = 14.sp)
            if (vHasCert && inOverrideSource != null) OverrideMark("client cert", inOverrideSource)
        }
        Text(
            "Presents your certificate to the server. PKCS#12 (.p12/.pfx) bundles its private key; PEM/DER take a separate key file unless the certificate file already contains it.",
            color = c.dim, fontSize = 11.sp,
        )

        // ============
        //  Certificate file + format
        Text("Certificate", color = c.dim, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedAction(MaterialSymbols.InsertDriveFile, "Choose certificate…") {
                showOpenFileDialog { vPath -> if (vPath != null) inOnChange(inCert.copy(certPath = vPath)) }
            }
            CertFormatMenu(inCert.certFormat) { vF -> inOnChange(inCert.copy(certFormat = vF)) }
            if (vHasCert) IconBtn(MaterialSymbols.Close, "Clear certificate") {
                inOnChange(CertConfig())
            }
        }
        Text(if (vHasCert) inCert.certPath else "No certificate selected.", color = if (vHasCert) c.text else c.dim, fontSize = 12.sp)

        // ============
        //  Private key (PKCS#12 carries its own key)
        if (inCert.certFormat != CertFormat.PKCS12) {
            Text("Private key", color = c.dim, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedAction(MaterialSymbols.InsertDriveFile, "Choose key…") {
                    showOpenFileDialog { vPath -> if (vPath != null) inOnChange(inCert.copy(keyPath = vPath)) }
                }
                CertFormatMenu(inCert.keyFormat) { vF -> inOnChange(inCert.copy(keyFormat = vF)) }
                if (inCert.keyPath.isNotBlank()) IconBtn(MaterialSymbols.Close, "Clear key") { inOnChange(inCert.copy(keyPath = "", keyFormat = CertFormat.PEM)) }
            }
            Text(
                if (inCert.keyPath.isNotBlank()) inCert.keyPath else "Optional — only if the key is in a separate file.",
                color = if (inCert.keyPath.isNotBlank()) c.text else c.dim, fontSize = 12.sp,
            )
        }

        // ============
        //  Passphrase
        Text("Passphrase", color = c.dim, fontSize = 12.sp)
        ThinField(inCert.certPassword, { v -> inOnChange(inCert.copy(certPassword = v)) }, inPlaceholder = "Key / PKCS#12 password (optional)")
    }
}

/* A read-only card for an inherited client cert: its source pill + path, plus an
   Override action (copy it into this scope) when this scope hasn't set its own. */
@Composable
private fun InheritedCertCard(inInherited: InheritedCert, inOwnSet: Boolean, inReadOnly: Boolean, inOnOverride: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, c.border, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceTag(inInherited.source, inInherited.path)
            Text("Inherited client cert", color = c.dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            when {
                inOwnSet -> Text("overridden", color = c.dim.copy(alpha = 0.6f), fontSize = 10.sp)
                !inReadOnly -> Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.border, RoundedCornerShape(6.dp))
                        .clickable { inOnOverride() }.padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text("Override", color = c.accent, fontSize = 11.sp) }
            }
        }
        Text(inInherited.cert.certPath, color = c.text, fontSize = 12.sp)
    }
}

/* The request's Cert tab: the inherited cert (from pack / session) shown read-only
   with its source + an Override action when the request has none, then the
   request's own cert editor (its own cert overrides the inherited one). */
@Composable
private fun RequestCertTab(inReq: ApiRequest, inInheritedCert: InheritedCert?, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (inInheritedCert != null) InheritedCertCard(inInheritedCert, inOwnSet = inReq.hasClientCert, inReadOnly = inReadOnly) {
            inEdit { it.withCert(inInheritedCert.cert) }
        }
        CertConfigEditor(inReq.certConfig(), inOverrideSource = if (inReq.hasClientCert) inInheritedCert?.path else null) { vCc -> inEdit { it.withCert(vCc) } }
    }
}

/* Scope settings editor (Variables / Query / Headers / Cert sub-tabs) — shared by
   the session settings tab and each pack's settings tab. Each sub-tab shows what
   the scope inherits from above (source-tagged, with Override) over its own
   editable list, mirroring the request panels. */
@Composable
private fun ScopeSettings(
    inTab: Int,
    inOnTab: (Int) -> Unit,
    inHeader: @Composable () -> Unit,
    inVars: List<KeyVal>, inOnVars: (List<KeyVal>) -> Unit, inVarHelp: String,
    inParams: List<KeyVal>, inOnParams: (List<KeyVal>) -> Unit, inParamHelp: String,
    inHeaders: List<KeyVal>, inOnHeaders: (List<KeyVal>) -> Unit, inHeaderHelp: String,
    inCert: CertConfig?, inOnCert: (CertConfig?) -> Unit, inCertHelp: String, inCertHeading: String,
    // What this scope inherits from above (empty for the session — it's the top).
    inInheritedVars: List<InheritedKv> = emptyList(),
    inInheritedParams: List<InheritedKv> = emptyList(),
    inInheritedHeaders: List<InheritedKv> = emptyList(),
    inInheritedCert: InheritedCert? = null,
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        inHeader()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TogglePill("Var", inTab == 0) { inOnTab(0) }
            TogglePill("Query", inTab == 1) { inOnTab(1) }
            TogglePill("Header", inTab == 2) { inOnTab(2) }
            TogglePill("Cert", inTab == 3) { inOnTab(3) }
        }
        when (inTab) {
            0 -> {
                Text(inVarHelp, color = c.dim, fontSize = 12.sp)
                InheritedEditableTab(inInheritedVars, inVars, inCaseInsensitive = false, inReadOnly = false,
                    inOwnTitle = "This scope's variables", inInheritedTitle = "Inherited variables",
                    inOnOverride = { vKv -> inOnVars(inVars + vKv) }, inOnChange = inOnVars)
            }
            1 -> {
                Text(inParamHelp, color = c.dim, fontSize = 12.sp)
                InheritedEditableTab(inInheritedParams, inParams, inCaseInsensitive = false, inReadOnly = false,
                    inOwnTitle = "This scope's query params", inInheritedTitle = "Inherited query params",
                    inOnOverride = { vKv -> inOnParams(inParams + vKv) }, inOnChange = inOnParams)
            }
            2 -> {
                Text(inHeaderHelp, color = c.dim, fontSize = 12.sp)
                InheritedEditableTab(inInheritedHeaders, inHeaders, inCaseInsensitive = true, inReadOnly = false,
                    inOwnTitle = "This scope's headers", inInheritedTitle = "Inherited headers",
                    inOnOverride = { vKv -> inOnHeaders(inHeaders + vKv) }, inOnChange = inOnHeaders)
            }
            else -> {
                Text(inCertHelp, color = c.dim, fontSize = 12.sp)
                val vOwnSet = inCert?.isSet == true
                if (inInheritedCert != null) InheritedCertCard(inInheritedCert, inOwnSet = vOwnSet, inReadOnly = false) { inOnCert(inInheritedCert.cert) }
                CertConfigEditor(inCert ?: CertConfig(), inCertHeading, inOverrideSource = if (vOwnSet) inInheritedCert?.path else null) { vCc -> inOnCert(vCc.takeIf { it.isSet }) }
            }
        }
    }
}

/* Certificate / key encoding dropdown (PEM / DER / PKCS#12). */
@Composable
private fun CertFormatMenu(inFormat: CertFormat, inOnPick: (CertFormat) -> Unit) {
    val c = LocalAppColors.current
    val vAnchor = rememberMenuAnchor()
    var vOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.menuAnchor(vAnchor).clip(RoundedCornerShape(6.dp))
                .border(1.dp, c.border, RoundedCornerShape(6.dp))
                .clickable { vOpen = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(inFormat.label, color = c.text, fontSize = 13.sp)
            MaterialSymbolsOutlined(MaterialSymbols.UnfoldMore, tint = c.dim, size = 15.dp)
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor) {
            CertFormat.entries.forEach { vF ->
                DropdownMenuItem(onClick = { inOnPick(vF); vOpen = false }) {
                    Text(vF.label, color = if (vF == inFormat) c.accent else c.text, fontSize = 13.sp)
                }
            }
        }
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
                        inAutoLabel = if (inRs.respFormatOverride == null) vAuto.label else null,
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
        // When editable, the field fills the whole panel height so a click
        // anywhere (not just on the text line) focuses it and starts writing.
        // Read-only (response) keeps wrap-height so it grows with content.
        val vEditable = inOnChange != null
        Box(modifier = Modifier.weight(1f).then(if (vEditable) Modifier.fillMaxHeight() else Modifier)) {
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
                    modifier = if (vEditable) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
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
    BodyType.TEXT -> inReq.body.encodeToByteArray().size
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

/* Format / "type" picker. inBordered renders it as a full dropdown matching
   BodyTypeMenu (the Body-tab Text-type picker); otherwise it's the compact inline
   control used by the response viewer. */
@Composable
private fun BodyFormatSelector(
    inSelected: BodyFormat,
    inOnChange: (BodyFormat) -> Unit,
    inAutoLabel: String? = null,  // when set, shown as "FORMAT (auto)"
    inBordered: Boolean = false,
) {
    val c = LocalAppColors.current
    var vOpen by remember { mutableStateOf(false) }
    val vAnchor = rememberMenuAnchor()
    val vLabel = if (inAutoLabel != null) "$inAutoLabel (auto)" else inSelected.label
    Box {
        Row(
            modifier = Modifier.menuAnchor(vAnchor)
                .clip(RoundedCornerShape(if (inBordered) 6.dp else 4.dp))
                .then(if (inBordered) Modifier.border(1.dp, c.border, RoundedCornerShape(6.dp)) else Modifier)
                .clickable { vOpen = true }
                .padding(horizontal = if (inBordered) 10.dp else 8.dp, vertical = if (inBordered) 6.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (inBordered) 5.dp else 4.dp),
        ) {
            Text(vLabel, color = if (inBordered) c.text else c.dim, fontSize = if (inBordered) 13.sp else 11.sp)
            MaterialSymbolsOutlined(if (inBordered) MaterialSymbols.UnfoldMore else MaterialSymbols.ArrowDropDown, tint = c.dim, size = if (inBordered) 15.dp else 14.dp)
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, offsetY = (-4).dp) {
            for (vF in BodyFormat.values()) {
                DropdownMenuItem(onClick = { inOnChange(vF); vOpen = false }) {
                    Text(vF.label, color = if (vF == inSelected) c.accent else c.text)
                }
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
    val vCt = inReq.bodyContentType()
    if (inReq.method.allowsBody && vCt != null && inReq.headers.none { it.enabled && it.key.equals("content-type", ignoreCase = true) }) {
        vLines.add("Content-Type: $vCt")
    }
    return vLines.joinToString("\n").ifEmpty { "(no headers)" }
}

/* The body that would be sent, rendered as text for the preview. */
private fun requestBodyText(inReq: ApiRequest): String = when (inReq.bodyType) {
    BodyType.NONE -> "(no body)"
    BodyType.TEXT -> inReq.body.ifEmpty { "(empty)" }
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
// MARK: Headers tab (inherited read-only + own editable)
// ==================

/* A request tab pairing an inherited (read-only, source-tagged) list with the
   request's own editable list: Override copies an inherited entry down, and own
   rows that shadow an inherited one get an OverrideMark. Used by Query / Var /
   Headers — the only differences are the key case-sensitivity and the labels. */
@Composable
private fun InheritedEditableTab(
    inInherited: List<InheritedKv>,
    inOwn: List<KeyVal>,
    inCaseInsensitive: Boolean,
    inReadOnly: Boolean,
    inOwnTitle: String,
    inOnOverride: (KeyVal) -> Unit,        // copy an inherited entry into the own list
    inOnChange: (List<KeyVal>) -> Unit,    // own editor change
    inInheritedTitle: String = "Inherited",
) {
    val c = LocalAppColors.current
    fun norm(inKey: String) = if (inCaseInsensitive) inKey.lowercase() else inKey
    val vOwnKeys = inOwn.filter { it.key.isNotBlank() }.map { norm(it.key) }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InheritedKvSection(inInheritedTitle, inInherited, vOwnKeys, inCaseInsensitive,
            inOnOverride = if (inReadOnly) null else inOnOverride)
        if (inInherited.any { it.kv.key.isNotBlank() }) { Divider(color = c.border); Text(inOwnTitle, color = c.dim, fontSize = 11.sp) }
        KeyValEditor(inOwn, inOnChange = inOnChange, inOverrideInfo = { vR ->
            if (vR.key.isBlank()) null else inInherited.firstOrNull { norm(it.kv.key) == norm(vR.key) }?.path
        })
    }
}

/* Request Headers tab: inherited headers (case-insensitive) + the request's own. */
@Composable
private fun HeadersTab(inReq: ApiRequest, inInherited: List<InheritedKv>, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) =
    InheritedEditableTab(inInherited, inReq.headers, inCaseInsensitive = true, inReadOnly = inReadOnly, inOwnTitle = "Request headers",
        inOnOverride = { vKv -> inEdit { it.copy(headers = it.headers + vKv) } },
        inOnChange = { v -> inEdit { it.copy(headers = v) } })

/* Request Query tab: inherited query params (case-sensitive) + the request's own. */
@Composable
private fun QueryTab(inReq: ApiRequest, inInherited: List<InheritedKv>, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) =
    InheritedEditableTab(inInherited, inReq.params, inCaseInsensitive = false, inReadOnly = inReadOnly, inOwnTitle = "Request query params",
        inOnOverride = { vKv -> inEdit { it.copy(params = it.params + vKv) } },
        inOnChange = { v -> inEdit { it.copy(params = v) } })

/* Request Var tab: inherited variables (case-sensitive {{name}}) + the request's
   own. A request's own variable overrides the same name inherited from a pack /
   the session when the request is sent. */
@Composable
private fun VarTab(inReq: ApiRequest, inInherited: List<InheritedKv>, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) =
    InheritedEditableTab(inInherited, inReq.variables, inCaseInsensitive = false, inReadOnly = inReadOnly, inOwnTitle = "Request variables",
        inOnOverride = { vKv -> inEdit { it.copy(variables = it.variables + vKv) } },
        inOnChange = { v -> inEdit { it.copy(variables = v) } })

// ==================
// MARK: Inheritance UI (source tags / override markers / inherited lists)
// ==================

/* A small pill naming the kind of scope an inherited value comes from ("Session"
   / "Pack"); hover shows the full path (e.g. "Methods / Nested"). The path tooltip
   is skipped when it would just repeat the label (the session). */
@Composable
private fun SourceTag(inLabel: String, inPath: String) {
    val c = LocalAppColors.current
    val vPill = @Composable {
        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.accent.copy(alpha = 0.16f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text(inLabel, color = c.accent, fontSize = 9.sp)
        }
    }
    if (inPath.isBlank() || inPath == inLabel) vPill() else TooltipBox(text = inPath) { vPill() }
}

/* The tiny marker shown on an own value that shadows an inherited one — hover for
   "Overrides <key> from <path>". */
@Composable
private fun OverrideMark(inKey: String, inPath: String) {
    val c = LocalAppColors.current
    TooltipBox(text = "Overrides “$inKey” from $inPath") {
        MaterialSymbolsOutlined(MaterialSymbols.ArrowUpward, contentDescription = "Overrides $inPath", tint = c.accent, size = 13.dp)
    }
}

/* Read-only list of inherited key/values, each tagged with its source. When not
   already overridden in this scope (own key absent) and inOnOverride != null, a
   row offers a one-click Override that copies it into this scope's own list. */
@Composable
private fun InheritedKvSection(
    inTitle: String,
    inItems: List<InheritedKv>,
    inOwnKeys: Set<String>,            // keys present in this scope's own list (already-normalised)
    inCaseInsensitive: Boolean,        // headers: compare keys case-insensitively
    inOnOverride: ((KeyVal) -> Unit)?, // null → no override action (e.g. a request's inherited vars)
) {
    val c = LocalAppColors.current
    val vShown = inItems.filter { it.kv.key.isNotBlank() }
    if (vShown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(inTitle, color = c.dim, fontSize = 11.sp)
        vShown.forEach { vIt ->
            val vKey = if (inCaseInsensitive) vIt.kv.key.lowercase() else vIt.kv.key
            val vOverridden = vKey in inOwnKeys
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceTag(vIt.source, vIt.path)
                Text(vIt.kv.key, color = c.dim, fontSize = 12.sp, modifier = Modifier.weight(0.42f))
                Text(vIt.kv.value, color = c.dim.copy(alpha = if (vOverridden) 0.4f else 1f), fontSize = 12.sp, modifier = Modifier.weight(0.58f))
                when {
                    vOverridden -> Text("overridden", color = c.dim.copy(alpha = 0.6f), fontSize = 10.sp)
                    inOnOverride != null -> Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.border, RoundedCornerShape(6.dp))
                            .clickable { inOnOverride(KeyVal(vIt.kv.key, vIt.kv.value)) }.padding(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("Override", color = c.accent, fontSize = 11.sp) }
                }
            }
        }
    }
}

// ==================
// MARK: Key/value editor
// ==================

/* inOverrideInfo maps an own row to the source label it shadows (or null) so the
   editor can flag overriding rows with an OverrideMark. It sits before inOnChange
   so existing trailing-lambda calls (KeyValEditor(rows) { … }) still bind to onChange. */
@Composable
private fun KeyValEditor(inRows: List<KeyVal>, inOverrideInfo: (KeyVal) -> String? = { null }, inOnChange: (List<KeyVal>) -> Unit) {
    val c = LocalAppColors.current
    val vAnyOverride = inRows.any { inOverrideInfo(it) != null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (inRows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.width(28.dp))
                if (vAnyOverride) Spacer(Modifier.width(18.dp))
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
                if (vAnyOverride) Box(modifier = Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                    inOverrideInfo(vKv)?.let { vSrc -> OverrideMark(vKv.key, vSrc) }
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

// 20-colour palette for the per-pack box icon. Pack.color is a 1-based index
// into this list (0 = none → a neutral dot). Two rows of ten in the picker.
private val kPackColors: List<Long> = listOf(
    0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2, 0xFF5C6BC0,
    0xFF42A5F5, 0xFF29B6F6, 0xFF26C6DA, 0xFF26A69A, 0xFF66BB6A,
    0xFF9CCC65, 0xFFD4E157, 0xFFFFEE58, 0xFFFFCA28, 0xFFFFA726,
    0xFFFF7043, 0xFF8D6E63, 0xFF78909C, 0xFFBDBDBD, 0xFF5C7CFA,
)

/* The palette colour for a 1-based pack-colour index, or null when unset. */
private fun packColor(inIndex: Int): Color? =
    if (inIndex in 1..kPackColors.size) Color(kPackColors[inIndex - 1]) else null

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
