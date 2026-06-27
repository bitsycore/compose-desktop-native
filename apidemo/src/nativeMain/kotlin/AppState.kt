package apidemo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job

// ==================
// MARK: Working state (session / pack / request / tabs / drag)
// ==================

internal class HistoryEntry(val method: ReqMethod, val url: String, val status: Int, val timeMs: Long, val request: ApiRequest)

/* One request plus its live session state — response, in-flight job (for
   cancel) and which sub-tabs show. Stable identity lets the sidebar and the
   open-tab strip reference the same request without index juggling. */
internal class ReqState(inInitial: ApiRequest) {
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
    var bodyWrap by mutableStateOf(true)  // body view: true = soft-wrap, false = no wrap + horizontal scroll
    var tlsChain by mutableStateOf<TlsChain?>(null)  // last fetched server cert chain
    var chainLoading by mutableStateOf(false)
    var showChain by mutableStateOf(false)           // chain dialog open
    var chainUrl by mutableStateOf("")               // resolved URL the chain was fetched from
}

/* One open pack: its file path (null = never saved), a dirty flag (edits since
   last file save), and its own requests, variables and open-tab strip. Switching
   packs swaps the whole working set. */
internal class PackState(inPack: Pack, inPath: String?, inDirty: Boolean, inOpenTabs: List<Int> = emptyList(), inActive: Int = -1) {
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
internal fun newPackId(): String = "pk-" + kotlin.random.Random.nextLong().toULong().toString(16)

/* One entry in the unified tab strip: a request tab, a pack's settings tab when
   req == null, or the session settings tab when isSession (pack == null). The
   underlying ReqState / PackState (or the session key) is the stable identity. */
internal const val kSessionTabKey = "session-settings"
internal class StripTab(val pack: PackState?, val req: ReqState?, val isSession: Boolean = false)
/* Identity of a strip tab. A linked pack shares the source's ReqState objects,
   so the key must include the pack — otherwise the source's and linked's tabs
   for the same request collide (double selection, duplicate key()). Data class →
   structural equality, so compare keys with == (not ===). */
internal data class TabKey(val pack: PackState?, val req: ReqState?)
internal val StripTab.tabKey: Any get() = if (isSession) kSessionTabKey else TabKey(pack, req)

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
internal class TreeDrag {
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
internal const val kDragSlop = 5f

// An inherited variable / header tagged with where it comes from: `source` is the
// short pill label ("Session" / "Pack"), `path` the full path for the tooltip
// ("Session" or e.g. "Methods / Nested"). The single inherited cert is tagged the same.
internal data class InheritedKv(val kv: KeyVal, val source: String, val path: String)
internal data class InheritedCert(val cert: CertConfig, val source: String, val path: String)
