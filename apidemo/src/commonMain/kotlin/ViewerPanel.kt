package apidemo

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined
import kotlinx.coroutines.launch

// ==================
// MARK: Response
// ==================

/** Panel 4 — Request / Response viewer. Each tab stacks a HEADERS section over a
BODY section. "Request" shows the resolved request that would be sent (the
Preview); "Response" shows the result (image-aware). A copy / save toolbar
acts on whichever view is showing. */
@Composable
internal fun ViewerPanel(inRs: ReqState, inResolved: ApiRequest) {
    val c = LocalAppColors.current
    var vMsg by remember { mutableStateOf<String?>(null) }
    var vHeadersCollapsed by remember { mutableStateOf(false) }
    val vResp = inRs.response
    val vLoading = inRs.loading
    val vPreview = inRs.preview
    val vShowRequest = vPreview || inRs.viewTab == 0

    // Pretty-print once per received body (not every recomposition) — the whole
    // body is shown; the renderer line-culls + wrap-caches so even a 20k-line
    // payload stays cheap. (Previously capped at 20000 chars, which silently cut
    // long responses off around line ~1100.)
    val vRespBody = remember(vResp?.body, vResp?.error) {
        if (vResp != null) (vResp.error ?: prettyJsonOrRaw(vResp.body)) else ""
    }
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
                Text("not sent", color = VolticTheme.extended.warning, fontSize = 11.sp)
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
                    vResp != null -> vResp.status.toString() to statusColor(vResp.status)
                    else -> null to c.dim
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
                    val vSentHeaders =
                        if (!vPreview) inRs.response?.requestHeaders?.takeIf { it.isNotEmpty() } else null
                    // Synthesize wire-level headers (Host, Content-Length, User-Agent)
                    // the engine adds without telling Ktor — same set httpie shows.
                    val vRawHeaders =
                        if (vSentHeaders != null) vSentHeaders else parseHeaderLines(requestHeadersText(vR))
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
                        inSoftWrap = inRs.bodyWrap,
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
                            val vSvg = vResp.contentType?.contains("svg", ignoreCase = true) == true
                            memoryImagePainter(inRs.imageKey!!, vSvg)
                        } else null,
                        inHeadersCollapsed = vHeadersCollapsed,
                        inOnToggleCollapse = { vHeadersCollapsed = !vHeadersCollapsed },
                        inShowSecureLock = isTlsValidated(inRs.sentReq?.url ?: inResolved.url, vResp),
                        inBodyFormat = vBodyFmt,
                        inSoftWrap = inRs.bodyWrap,
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
            HorizontalDivider(color = c.border)
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
                // Wrap toggle — soft-wrap long lines (default) vs. no wrap +
                // horizontal scroll (one source line per row). Shown for any text
                // body (request preview or response), hidden for an image.
                val vHasTextBody = (vRespHasContent && vResp != null && !vRespImage) || vReqHasContent
                if (vHasTextBody) {
                    val vWrapOn = inRs.bodyWrap
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { inRs.bodyWrap = !inRs.bodyWrap }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MaterialSymbolsOutlined(
                                MaterialSymbols.WrapText,
                                tint = if (vWrapOn) c.accent else c.dim,
                                size = 14.dp
                            )
                            Text("Wrap", color = if (vWrapOn) c.accent else c.dim, fontSize = 11.sp)
                        }
                    }
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
                        inRs.imageKey?.let { removeMemoryImage(it) }
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

/** Renders the body of a Request or Response tab in a httpie-flavoured
layout: status line (with collapse arrow) + headers table + raw body
inline, no card / rounded-rect chrome around each section. The
headers section can be collapsed via the arrow. */
@Composable
internal fun HttpFlowView(
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
    inSoftWrap: Boolean = true,
) {
    val c = LocalAppColors.current
    val vState = rememberLazyListState()
    // Editor-style selection state — anchor + caret in absolute character offsets
    // into inBody. Resets when the body changes so a stale range never highlights
    // a shorter/different response. Lives outside any SelectionContainer so it
    // survives scroll and spans off-screen chunks (see the BodySel comment).
    var vSel: BodySel? by remember(inBody) { mutableStateOf(null) }
    // Selection background from the app theme — same colour that
    // LocalTextSelectionColors publishes to Compose text selection elsewhere.
    val vSelColor = LocalTextSelectionColors.current.backgroundColor
    val vFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val vScope = rememberCoroutineScope()
    val vClipboard = LocalClipboard.current
    // Latest pointer position while dragging (in the outer Box's local px frame),
    // null when not dragging. Driven by the pointerInput below and read by an
    // auto-scroll effect that scrolls the LazyColumn while the pointer sits in
    // the top / bottom edge zone.
    var vDragPos: Offset? by remember { mutableStateOf(null) }
    // The body is highlighted ONCE, then sliced into BLOCKS of lines — one BasicText
    // per block, NOT one per line. Far fewer nodes / selectables / paragraph set-ups
    // churn through the viewport while scrolling (the smoothness win), and the
    // O(total-spans) AnnotatedString.subSequence runs once per block at build time
    // (memoised) instead of per visible line every frame. LazyColumn still virtualizes,
    // so only the ~2-3 on-screen blocks are composed/measured — 12k lines stay cheap.
    val vDark = isDarkBg(c.bg)
    val vChunks = remember(inBody, inBodyFormat, vDark) {
        if (inBody == null) emptyList()
        else {
            val vAnn = if (inBodyFormat == BodyFormat.RAW) AnnotatedString(inBody)
            else highlight(inBody, inBodyFormat, SyntaxPalette.forDark(vDark))
            buildBodyChunks(inBody, vAnn, kLinesPerChunk)
        }
    }
    // Per-visible-chunk TextLayoutResult, keyed by chunk index. Populated as each
    // BodyChunkRow's BasicText lays out, dropped implicitly when the chunks list
    // changes (new response) via the remember key. Off-screen chunks aren't in
    // this map — we can't hit-test them via mouse either since they're not painted.
    val vChunkLayouts = remember(vChunks) { mutableStateMapOf<Int, TextLayoutResult>() }
    val vTotalLines = remember(inBody) { if (inBody == null) 0 else inBody.count { it == '\n' } + 1 }
    // No-wrap mode pans horizontally; every block shares this one scroll state so the
    // whole body pans together (the gutter stays pinned — not scrolled).
    val vHScroll = rememberScrollState()
    val vGutterWidth = (vTotalLines.coerceAtLeast(1).toString().length * 7 + 4).dp
    // Per-line styles hoisted once (not allocated per line per frame). The rows use
    // BasicText with these fixed styles instead of material3 Text — no per-node
    // LocalTextStyle merge / LocalContentColor read — which is the main scroll-
    // smoothness win when thousands of line items churn through the viewport.
    val vBodyStyle = remember(c.text) { TextStyle(color = c.text, fontSize = 12.sp, fontFamily = monoFontFamily) }
    val vNumStyle = remember(c.dim) {
        TextStyle(
            color = c.dim.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontFamily = monoFontFamily,
            textAlign = TextAlign.End
        )
    }

    // Body-relative x offset (px) of the block's BasicText inside the outer Box.
    // Mirrors BodyChunkRow's layout: 4dp Row start padding + gutter width + 6dp spacer.
    val vTextStartPx = with(density) { (4.dp + vGutterWidth + 6.dp).toPx() }
    // LazyColumn item index of chunk index 0 — status row + optional header
    // table + divider. Used to scroll a specific chunk into view when the caret
    // moves off-screen via keyboard. Only stable while we're on the "have body"
    // branch, but that's the only branch that renders chunks in the first place.
    val vChunkItemOffset = 1 +
            (if (!inHeadersCollapsed && inHeaders.isNotEmpty()) 1 else 0) +
            (if (inBody != null || inIsImage) 1 else 0)

    // After a keyboard action that moves the caret, if the caret's chunk is not
    // in the visible items list scroll it in. Instant (no anim) so rapid
    // Shift+Down keeps up. No-op when already visible so the chunk doesn't jump.
    val vScrollCaretIntoView: () -> Unit = scr@{
        val vSel0 = vSel ?: return@scr
        val vCi = chunkContaining(vChunks, vSel0.caret) ?: return@scr
        val vKey = "chunk_$vCi"
        val vVisible = vState.layoutInfo.visibleItemsInfo.any { it.key == vKey }
        if (!vVisible) vScope.launch { vState.scrollToItem(vChunkItemOffset + vCi) }
    }

    // Hit-test — turns a pointer position (outer Box local frame) into a global
    // character offset into inBody, or null if the pointer isn't over any chunk.
    // Clamps past-viewport pointers to the nearest visible chunk boundary so a
    // drag that overshoots still keeps extending toward that end.
    val vHitTest: (Offset) -> Int? = hit@{ inPos ->
        val vBody = inBody ?: return@hit null
        val vInfos = vState.layoutInfo.visibleItemsInfo
            .filter { (it.key as? String)?.startsWith("chunk_") == true }
            .sortedBy { it.offset }
        if (vInfos.isEmpty()) return@hit null
        val vInfo = vInfos.firstOrNull { inPos.y >= it.offset && inPos.y < it.offset + it.size }
            ?: if (inPos.y < vInfos.first().offset) vInfos.first() else vInfos.last()
        val vCi = (vInfo.key as String).removePrefix("chunk_").toIntOrNull() ?: return@hit null
        val vChunk = vChunks.getOrNull(vCi) ?: return@hit null
        // If we clamped y past the top / bottom, snap the caret to that chunk end so
        // the range degrades gracefully without auto-scroll.
        if (inPos.y < vInfo.offset) return@hit vChunk.startCharOffset
        if (inPos.y >= vInfo.offset + vInfo.size) return@hit vChunk.startCharOffset + vChunk.body.length
        val vLayout = vChunkLayouts[vCi] ?: return@hit null
        val vHScrollPx = if (!inSoftWrap) vHScroll.value.toFloat() else 0f
        val vLocalX = (inPos.x - vTextStartPx + vHScrollPx).coerceAtLeast(0f)
        val vLocalY = (inPos.y - vInfo.offset).coerceIn(0f, vInfo.size.toFloat() - 1f)
        val vLocal = vLayout.getOffsetForPosition(Offset(vLocalX, vLocalY))
        (vChunk.startCharOffset + vLocal).coerceIn(0, vBody.length)
    }

    // Drag auto-scroll: while a drag is in progress (vDragPos != null), tick
    // every frame and scroll the LazyColumn when the pointer sits in a 40dp
    // edge zone at the top / bottom. Speed ramps linearly with depth into the
    // zone up to 24dp per frame — roughly a page per second at 60fps at max
    // depth. After each scroll we re-hit-test the current pointer position so
    // the selection keeps extending onto the newly revealed chunks.
    val vEdgePx = with(density) { 40.dp.toPx() }
    val vMaxScrollPx = with(density) { 24.dp.toPx() }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val vPos = vDragPos ?: continue
            val vVpH = vState.layoutInfo.viewportSize.height.toFloat()
            val vDelta = when {
                vPos.y < vEdgePx -> -((vEdgePx - vPos.y) / vEdgePx * vMaxScrollPx)
                vPos.y > vVpH - vEdgePx -> (vPos.y - (vVpH - vEdgePx)) / vEdgePx * vMaxScrollPx
                else -> 0f
            }
            if (vDelta != 0f) {
                vState.scrollBy(vDelta)
                val vHit = vHitTest(vPos)
                if (vHit != null) vSel = vSel?.copy(caret = vHit)
            }
        }
    }

    // contentAlignment pins the narrow scrollbar to the right edge; the
    // fillMaxSize content fills the rest (this project's Box has no BoxScope, so
    // there's no Modifier.align — alignment is via contentAlignment).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(vFocus)
            .focusable()
            // Mouse: press starts a selection, drag extends it, Shift+press extends
            // from the existing anchor. We drive this by hand instead of going
            // through SelectionContainer because SC only sees composed selectables
            // (visible chunks) — Ctrl+A and Shift+Arrow can't reach off-screen text
            // through it. Header items above the chunks stay non-selectable, which
            // matches the previous DisableSelection wrappers.
            .pointerInput(vChunks) {
                // Track click count for double/triple-click. A press within
                // 400ms and 5px of the previous one counts as a repeat, so
                // press #2 selects the word under the caret, press #3 selects
                // the whole line. Anything past 3 wraps back to 1.
                var vLastPressUptime = 0L
                var vLastPressPos = Offset.Zero
                var vPressCount = 0
                awaitPointerEventScope {
                    while (true) {
                        val vDown = awaitPointerEvent()
                        if (vDown.type != PointerEventType.Press) continue
                        val vChange = vDown.changes.firstOrNull() ?: continue
                        // Skip presses a child already handled — most importantly the
                        // VerticalScrollbar sibling in this Box. Without this, dragging
                        // the scrollbar would also start (and continuously extend) a
                        // body selection because my hit-test still finds a chunk under
                        // the scrollbar-anchored pointer position.
                        if (vChange.isConsumed) continue
                        if (vDown.buttons.isPrimaryPressed.not()) continue
                        val vOffset = vHitTest(vChange.position) ?: continue
                        val vBody = inBody ?: continue
                        vFocus.requestFocus()
                        val vShift = vDown.keyboardModifiers.isShiftPressed

                        val vNow = vChange.uptimeMillis
                        val vDx = vChange.position.x - vLastPressPos.x
                        val vDy = vChange.position.y - vLastPressPos.y
                        val vNear = (vDx * vDx + vDy * vDy) < 25f    // 5px * 5px
                        vPressCount = if (!vShift && vNear && vNow - vLastPressUptime < 400L)
                            (vPressCount + 1).coerceAtMost(3) else 1
                        vLastPressUptime = vNow
                        vLastPressPos = vChange.position

                        val vExisting = vSel
                        vSel = when {
                            vShift && vExisting != null -> vExisting.copy(caret = vOffset)
                            vPressCount == 2 -> {
                                val vR = wordRangeAt(vBody, vOffset)
                                BodySel(anchor = vR.first, caret = vR.last)
                            }

                            vPressCount >= 3 -> BodySel(
                                anchor = lineStartAt(vBody, vOffset),
                                caret = lineEndAt(vBody, vOffset),
                            )

                            else -> BodySel(anchor = vOffset, caret = vOffset)
                        }
                        vChange.consume()

                        // Drag loop: keep updating the caret end on Move until Release.
                        // Break only on Release so scroll-wheel / other stray events
                        // during the drag don't abort it. vDragPos drives the edge
                        // auto-scroll effect above.
                        vDragPos = vChange.position
                        while (true) {
                            val vEv = awaitPointerEvent()
                            if (vEv.type == PointerEventType.Release) break
                            if (vEv.type != PointerEventType.Move) continue
                            val vCh = vEv.changes.firstOrNull() ?: continue
                            vDragPos = vCh.position
                            val vHit = vHitTest(vCh.position)
                            if (vHit != null) vSel = vSel?.copy(caret = vHit)
                            vCh.consume()
                        }
                        vDragPos = null
                    }
                }
            }
            // Ctrl/Cmd+A: select the whole body. Ctrl/Cmd+C: copy the current
            // selection (falls through when empty so the toolbar Copy button and
            // other handlers can still act). Shift+Arrow / Home / End: editor-style
            // caret movement that extends the selection from the current anchor.
            // Escape clears the selection.
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                val vBody = inBody ?: return@onKeyEvent false
                val vMod = ev.isCtrlPressed || ev.isMetaPressed
                val vShift = ev.isShiftPressed
                when {
                    vMod && ev.key == Key.A -> {
                        vSel = BodySel(0, vBody.length); true
                    }

                    vMod && ev.key == Key.C -> {
                        val vS = vSel
                        if (vS != null && !vS.isEmpty) {
                            val vText = vBody.substring(vS.start, vS.end)
                            vScope.launch { vClipboard.setClipEntry(clipEntryOfText(vText)) }
                            true
                        } else false
                    }

                    ev.key == Key.Escape -> {
                        if (vSel != null) {
                            vSel = null; true
                        } else false
                    }

                    vShift && ev.key == Key.DirectionRight -> {
                        val vCur = vSel ?: BodySel(0, 0)
                        vSel = vCur.copy(caret = (vCur.caret + 1).coerceAtMost(vBody.length))
                        vScrollCaretIntoView(); true
                    }

                    vShift && ev.key == Key.DirectionLeft -> {
                        val vCur = vSel ?: BodySel(0, 0)
                        vSel = vCur.copy(caret = (vCur.caret - 1).coerceAtLeast(0))
                        vScrollCaretIntoView(); true
                    }

                    vShift && ev.key == Key.DirectionDown -> {
                        val vCur = vSel ?: BodySel(0, 0)
                        vSel = vCur.copy(caret = moveDown(vBody, vCur.caret))
                        vScrollCaretIntoView(); true
                    }

                    vShift && ev.key == Key.DirectionUp -> {
                        val vCur = vSel ?: BodySel(0, 0)
                        vSel = vCur.copy(caret = moveUp(vBody, vCur.caret))
                        vScrollCaretIntoView(); true
                    }

                    vShift && ev.key == Key.MoveHome -> {
                        val vCur = vSel ?: BodySel(0, 0)
                        vSel = vCur.copy(caret = if (vMod) 0 else lineStartAt(vBody, vCur.caret))
                        vScrollCaretIntoView(); true
                    }

                    vShift && ev.key == Key.MoveEnd -> {
                        val vCur = vSel ?: BodySel(0, 0)
                        vSel = vCur.copy(caret = if (vMod) vBody.length else lineEndAt(vBody, vCur.caret))
                        vScrollCaretIntoView(); true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        LazyColumn(state = vState, modifier = Modifier.fillMaxSize()) {
            // Status line: collapse arrow + optional lock + status text.
            item {
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
            }
            // Headers as a key/value table — only when not collapsed.
            if (!inHeadersCollapsed && inHeaders.isNotEmpty()) {
                item {
                    HeaderTable(inHeaders, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            if (inBody != null || inIsImage) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = c.border) }
                if (inIsImage && inImagePainter != null) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Image(
                                painter = inImagePainter,
                                contentDescription = "Response image",
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                } else if (vChunks.isNotEmpty()) {
                    // One BasicText per BLOCK of lines (not per line) — far fewer
                    // nodes to churn while scrolling. Chunk key lets the pointer
                    // hit-test map a visible item back to its chunk index.
                    items(vChunks.size, key = { "chunk_$it" }, contentType = { "chunk" }) { vCi ->
                        BodyChunkRow(
                            inChunk = vChunks[vCi],
                            inSoftWrap = inSoftWrap,
                            inGutterWidth = vGutterWidth,
                            inHScroll = vHScroll,
                            inBodyStyle = vBodyStyle,
                            inNumStyle = vNumStyle,
                            inSel = vSel,
                            inSelColor = vSelColor,
                            inCaretColor = c.accent,
                            inOnLayout = { vChunkLayouts[vCi] = it },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        // Overlay scrollbar on the right, themed for the dark panel.
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(vState),
            modifier = Modifier.fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = c.text.copy(alpha = 0.16f),
                hoverColor = c.text.copy(alpha = 0.42f),
            ),
        )
    }
}

// ==================
// MARK: Body selection (editor-style, spans virtualized chunks)
// ==================

/** A response body's selection is a pair of character offsets in the whole body
string: [anchor] is where the user pressed / started the selection, [caret] is
where they've moved to. When they differ, the selected range is [min..max].
When equal, it's a zero-width caret still useful as a starting point for
subsequent Shift+Arrow / Shift+Click extensions.

Note this lives OUTSIDE the SelectionContainer machinery — that machinery only
tracks selectables composed by the LazyColumn (visible chunks), which is why
Ctrl+A and drag-select cannot reach off-screen text through it. This state is
plain integer offsets so it survives scroll and covers the whole body. */
private data class BodySel(val anchor: Int, val caret: Int) {
    val start get() = minOf(anchor, caret)
    val end get() = maxOf(anchor, caret)
    val isEmpty get() = anchor == caret
}

/** Character offset of the start of the line containing [inOffset]. */
private fun lineStartAt(inText: String, inOffset: Int): Int {
    if (inOffset <= 0) return 0
    val vClamped = inOffset.coerceAtMost(inText.length)
    val vPrev = inText.lastIndexOf('\n', vClamped - 1)
    return if (vPrev < 0) 0 else vPrev + 1
}

/** Character offset of the end of the line containing [inOffset] — the position of
its '\n', or the length of [inText] if this is the last line. */
private fun lineEndAt(inText: String, inOffset: Int): Int {
    val vClamped = inOffset.coerceIn(0, inText.length)
    val vNext = inText.indexOf('\n', vClamped)
    return if (vNext < 0) inText.length else vNext
}

/** Move a caret one visual "row" down while trying to keep its column — i.e. the
number of chars past the current line's start. If the target line is shorter,
the caret snaps to its end. Off-by-one at EOF collapses to the body length. */
private fun moveDown(inText: String, inOffset: Int): Int {
    val vLineStart = lineStartAt(inText, inOffset)
    val vCol = inOffset - vLineStart
    val vNext = inText.indexOf('\n', inOffset)
    if (vNext < 0) return inText.length
    val vNextStart = vNext + 1
    val vNextEnd = lineEndAt(inText, vNextStart)
    return (vNextStart + vCol).coerceAtMost(vNextEnd)
}

/** The Up counterpart to [moveDown]. */
private fun moveUp(inText: String, inOffset: Int): Int {
    val vLineStart = lineStartAt(inText, inOffset)
    if (vLineStart == 0) return 0
    val vCol = inOffset - vLineStart
    val vPrevEnd = vLineStart - 1                                   // the '\n' before this line
    val vPrevStart = lineStartAt(inText, vPrevEnd)
    return (vPrevStart + vCol).coerceAtMost(vPrevEnd)
}

/** Word boundary rule for double-click selection: contiguous run of "word chars"
(letters, digits, underscore) containing [inOffset], or an empty range at the
click point when it lands on non-word text (whitespace, punctuation). Matches
the boundary editors like VS Code / IntelliJ use for double-click select. */
private fun wordRangeAt(inText: String, inOffset: Int): IntRange {
    val vN = inText.length
    if (vN == 0) return 0..0
    val vClamped = inOffset.coerceIn(0, vN)
    fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'
    // A click just after the last char of a word should still select that word.
    val vProbe = if (vClamped < vN && isWord(inText[vClamped])) vClamped
    else if (vClamped > 0 && isWord(inText[vClamped - 1])) vClamped - 1
    else return vClamped..vClamped
    var vStart = vProbe
    while (vStart > 0 && isWord(inText[vStart - 1])) vStart--
    var vEnd = vProbe
    while (vEnd < vN && isWord(inText[vEnd])) vEnd++
    return vStart..vEnd
}

/** The index of the chunk in [inChunks] whose character range contains
[inOffset], or null when the body is empty. Uses a linear scan — the chunk
count is O(body / kLinesPerChunk) which stays modest even for huge bodies. */
private fun chunkContaining(inChunks: List<BodyChunk>, inOffset: Int): Int? {
    if (inChunks.isEmpty()) return null
    for (i in inChunks.indices) {
        val vC = inChunks[i]
        if (inOffset <= vC.startCharOffset + vC.body.length) return i
    }
    return inChunks.lastIndex
}

// How many source lines each body BasicText covers. A block is measured whole when
// any of it is on screen, so keep it near a screenful; small enough to bound over-
// measure at the viewport edges, large enough to slash node/selectable count.
private const val kLinesPerChunk = 48

/** One BLOCK of a read-only body: a right-aligned column of line numbers in the
gutter + the block's (syntax-coloured) text, each a single BasicText. Rendering
blocks (not one-BasicText-per-line) keeps the node / selectable / paragraph-set-up
count low as blocks scroll through the viewport. The gutter is pinned outside
the shared h-scroll and never joins the selection.

Gutter alignment under soft-wrap: the gutter starts as plain 1:1 numbers, then the
body's own onTextLayout tells us how many VISUAL rows each source line wrapped into,
and we pad the gutter with that many blank rows so number N still sits on line N's
first row. It reuses the body's layout (no extra measurement) and only recomputes on
(re)layout, not per frame. No-wrap mode is 1 row per line, so the plain gutter is exact.

The block's body text carries the current selection as a background SpanStyle
intersected with [chunk.startCharOffset, +chunk.body.length). onTextLayout also
reports the layout back up to the viewer so hit-testing can convert pointer
positions into character offsets across the whole body. */
@Composable
private fun BodyChunkRow(
    inChunk: BodyChunk,
    inSoftWrap: Boolean,
    inGutterWidth: androidx.compose.ui.unit.Dp,
    inHScroll: ScrollState,
    inBodyStyle: TextStyle,
    inNumStyle: TextStyle,
    inSel: BodySel?,
    inSelColor: Color,
    inCaretColor: Color,
    inOnLayout: (TextLayoutResult) -> Unit,
) {
    val vPlain = remember(inChunk) {
        buildString {
            for (j in 0 until inChunk.lineCount) {
                if (j > 0) append('\n')
                append(inChunk.firstLine + j)
            }
        }
    }
    // Reset when the block OR the wrap mode changes (a stale padded gutter must not
    // survive a switch back to no-wrap).
    var vGutter by remember(inChunk, inSoftWrap) { mutableStateOf(vPlain) }
    // Track the block's own TextLayoutResult locally so `drawWithContent` can
    // read it for the highlight + caret paint. Also forwarded to the viewer via
    // inOnLayout for its pointer hit-tests.
    var vLayout: TextLayoutResult? by remember { mutableStateOf(null) }
    // Precompute this block's slice of the selection in LOCAL offsets. `null`
    // when the selection is empty or doesn't touch this block. Also computes the
    // caret's local offset when the caret itself falls inside this block (drawn
    // even when the range is empty — that's the read-only viewer's cursor).
    val vChunkStart = inChunk.startCharOffset
    val vChunkEnd = vChunkStart + inChunk.body.length
    val vLocalRange: IntRange? = if (inSel == null || inSel.isEmpty) null else {
        val vS = maxOf(inSel.start, vChunkStart)
        val vE = minOf(inSel.end, vChunkEnd)
        if (vS < vE) (vS - vChunkStart) until (vE - vChunkStart) else null
    }
    val vLocalCaret: Int? = if (inSel != null && inSel.caret in vChunkStart..vChunkEnd) {
        inSel.caret - vChunkStart
    } else null

    // Draw the selection background BEHIND the text, then the text, then the
    // caret on top. The port's SpanStyle(background = …) doesn't render across
    // line breaks (no upstream text-background pass in the SDL/Skia paragraph
    // draws), so we paint the highlight ourselves via
    // TextLayoutResult.getPathForRange, which returns per-visual-line rects.
    fun Modifier.selectionOverlay() = drawWithContent {
        val vLay = vLayout
        if (vLay != null && vLocalRange != null) {
            val vPath = vLay.getPathForRange(vLocalRange.first, vLocalRange.last + 1)
            drawPath(vPath, color = inSelColor)
        }
        drawContent()
        if (vLay != null && vLocalCaret != null) {
            val vRect = vLay.getCursorRect(vLocalCaret)
            drawRect(
                color = inCaretColor,
                topLeft = Offset(vRect.left, vRect.top),
                size = Size(1.5f, vRect.height),
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp)) {
        BasicText(text = vGutter, style = inNumStyle, softWrap = false, modifier = Modifier.width(inGutterWidth))
        Spacer(Modifier.width(6.dp))
        if (inSoftWrap) {
            BasicText(
                text = inChunk.body,
                style = inBodyStyle,
                softWrap = true,
                modifier = Modifier.weight(1f).selectionOverlay(),
                onTextLayout = { layout ->
                    vLayout = layout
                    inOnLayout(layout)
                    val vLen = inChunk.body.length
                    val vMaxOff = maxOf(0, vLen - 1)
                    val vSb = StringBuilder()
                    for (j in 0 until inChunk.lineCount) {
                        if (j > 0) vSb.append('\n')
                        vSb.append(inChunk.firstLine + j)
                        if (vLen > 0) {
                            val vStart = inChunk.lineStartsInBlock[j].coerceIn(0, vMaxOff)
                            val vEnd =
                                (if (j + 1 < inChunk.lineCount) inChunk.lineStartsInBlock[j + 1] - 1 else vMaxOff)
                                    .coerceIn(vStart, vMaxOff)
                            val vRows = layout.getLineForOffset(vEnd) - layout.getLineForOffset(vStart) + 1
                            repeat((vRows - 1).coerceAtLeast(0)) { vSb.append('\n') }
                        }
                    }
                    val vNew = vSb.toString()
                    if (vNew != vGutter) vGutter = vNew
                },
            )
        } else {
            // No-wrap: each source line is exactly one row, so the plain gutter is correct.
            Box(modifier = Modifier.weight(1f).horizontalScroll(inHScroll)) {
                BasicText(
                    text = inChunk.body,
                    style = inBodyStyle,
                    softWrap = false,
                    modifier = Modifier.selectionOverlay(),
                    onTextLayout = { layout -> vLayout = layout; inOnLayout(layout) },
                )
            }
        }
    }
}

/** A block of consecutive source lines: 1-based number of the first line, the line
count, each source line's start offset WITHIN the block text (to map a wrapped body
layout back to line numbers), the character offset of the block's first char in the
full body (used to map local text offsets to global ones for cross-chunk selection),
and the highlighted block text. */
private class BodyChunk(
    val firstLine: Int,
    val lineCount: Int,
    val lineStartsInBlock: IntArray,
    val startCharOffset: Int,
    val body: AnnotatedString,
)

/** Slice a highlighted body into blocks of [inLinesPerChunk] lines. The O(total-spans)
subSequence runs once per block HERE (build time, memoised) instead of once per
visible line every frame — the difference between smooth and janky on a big body. */
private fun buildBodyChunks(inText: String, inAnn: AnnotatedString, inLinesPerChunk: Int): List<BodyChunk> {
    val vStarts = lineStartOffsets(inText)
    val vN = vStarts.size
    val vLen = inAnn.length
    val vOut = ArrayList<BodyChunk>((vN + inLinesPerChunk - 1) / inLinesPerChunk)
    var vLine = 0
    while (vLine < vN) {
        val vLast = minOf(vLine + inLinesPerChunk, vN)                       // exclusive
        val vChunkStart = vStarts[vLine].coerceIn(0, vLen)
        val vChunkEnd = (if (vLast < vN) vStarts[vLast] - 1 else vLen)       // drop the trailing '\n'
            .coerceIn(vChunkStart, vLen)
        val vBody = inAnn.subSequence(vChunkStart, vChunkEnd)
        val vStartsInBlock = IntArray(vLast - vLine) { (vStarts[vLine + it] - vChunkStart).coerceAtLeast(0) }
        vOut.add(BodyChunk(vLine + 1, vLast - vLine, vStartsInBlock, vChunkStart, vBody))
        vLine = vLast
    }
    return vOut
}

/** Offsets where each source line starts (line i spans [starts[i], starts[i+1]-1),
the -1 dropping the '\n'; the last line runs to the string end). One cheap O(n)
scan, memoised — replaces the old gutter pre-pass that ran measurer.wrap() on
every one of N lines on each width change (the other half of the freeze). */
private fun lineStartOffsets(inText: String): IntArray {
    val vStarts = ArrayList<Int>(64)
    vStarts.add(0)
    for (vI in inText.indices) if (inText[vI] == '\n') vStarts.add(vI + 1)
    return vStarts.toIntArray()
}

/** Header table: each row is (key | value) where the key column has a
fixed width so values line up. Long keys wrap; long values wrap too.
Keys render in accent colour, values in regular text. */
@Composable
internal fun HeaderTable(inHeaders: List<Pair<String, String>>, modifier: Modifier = Modifier) {
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

/** Body view with line numbers in the gutter — mimics the code panel
in httpie. Numbers are dim; body text uses the regular colour. */
@Composable
internal fun BodyView(
    inText: String,
    modifier: Modifier = Modifier,
    inOnChange: ((String) -> Unit)? = null,
    inPlaceholder: String = "",
    inFormat: BodyFormat = BodyFormat.RAW,
    inSoftWrap: Boolean = true,
) {
    val c = LocalAppColors.current
    val vLines = if (inText.isEmpty()) listOf("") else inText.split('\n')
    // Tight gutter: width = digits in the max line number × glyph width at
    // 12sp (~7dp/digit for monospace digits). One char of padding either
    // side keeps the numbers from kissing the body text or the panel edge.
    val vDigits = vLines.size.toString().length
    val vGutterWidth = (vDigits * 7 + 4).dp
    // Body wrap width (physical px, from onSizeChanged), reported by the body
    // Box once it's laid out (0 on the first frame). The gutter numbering
    // depends on it — see vNumbers. NativeTextMeasurer.wrap expects the font
    // size + max-width in the SAME unit (both physical px on Retina), so we
    // scale 12.sp through the current density here, matching what SdlParagraph
    // does when it builds the body's real paragraph.
    var vBodyWidthPx by remember { mutableStateOf(0) }
    val vFontPx = with(LocalDensity.current) { 12.sp.toPx().toInt() }
    // Horizontal scroll for no-wrap mode (read-only body only); dormant when wrapping.
    val vHScroll = rememberScrollState()
    Row(modifier = modifier) {
        // Numbers are reference-only — half-alpha so they stay legible without
        // competing with the body. Rendered as ONE '\n'-joined multi-line Text
        // (not one Text per line): a 1000-line gutter is then a single node the
        // renderer line-culls + wrap-caches exactly like the body, instead of
        // 1000 leaf nodes — which lagged and tripped SDL's ~16384px draw-
        // coordinate limit (line numbers vanished past ~955).
        //
        // When the body soft-wraps, a source line can span several visual rows.
        // To keep the numbers aligned we emit each line's number once (on its
        // first row) followed by one blank row per wrapped continuation — by
        // re-running the SAME wrap the body uses (same measurer, font, width),
        // so the gutter ends up with exactly as many rows as the body. Without
        // this, a line wrapping into 3 rows would read 10/11/12 instead of
        // 10/blank/blank and the columns drift apart. In no-wrap mode each
        // source line is exactly one row, so a naive 1..N is correct (and we
        // also use it for the first frame, before the body reports its width).
        val vNumColor = c.dim.copy(alpha = 0.45f)
        val vNumbers = remember(inText, vBodyWidthPx, inSoftWrap, vFontPx) {
            val vWrapRows = inSoftWrap && vBodyWidthPx > 0
            buildString {
                for ((vIdx, vSrc) in vLines.withIndex()) {
                    if (vIdx > 0) append('\n')
                    append(vIdx + 1)
                    if (vWrapRows) {
                        // Pass the density-scaled font size so this wrap matches the body's
                        // actual glyph pixel widths — passing 12 (sp) here counted each
                        // char as half its rendered size, so long lines that visually
                        // wrapped into 3 rows in the body were estimated as 1 or 2 rows
                        // here and the gutter numbers drifted upward.
                        val vRows = wrappedRowCount(vSrc, vFontPx, vBodyWidthPx, monoFontFamilyName).coerceAtLeast(1)
                        repeat(vRows - 1) { append('\n') }
                    }
                }
            }
        }
        Text(
            vNumbers,
            color = vNumColor, fontSize = 12.sp, fontFamily = monoFontFamily,
            textAlign = TextAlign.End, softWrap = false,
            modifier = Modifier.width(vGutterWidth),
        )
        Spacer(Modifier.width(6.dp))
        // Body. Two render paths:
        //  - RAW or editable: BasicTextField. A colour-only visualTransform
        //    feeds the tokeniser's spans so the EDITABLE field is syntax-
        //    coloured while the cursor/selection still map to the plain text.
        //  - JSON / XML / HTML / YAML read-only: Text(AnnotatedString) with
        //    the tokeniser's colour spans.
        // When editable, the field fills the whole panel height so a click
        // anywhere (not just on the text line) focuses it and starts writing.
        // Read-only (response) keeps wrap-height so it grows with content.
        val vEditable = inOnChange != null
        Box(
            modifier = Modifier.weight(1f)
                .onSizeChanged { vBodyWidthPx = it.width }
                .then(if (vEditable) Modifier.fillMaxHeight() else Modifier)) {
            if (inText.isEmpty() && inPlaceholder.isNotEmpty()) {
                Text(inPlaceholder, color = c.dim, fontSize = 12.sp, fontFamily = monoFontFamily)
            }
            if (inOnChange != null) {
                // EDITABLE request body: BasicTextField with a colour-only
                // syntax-highlight visualTransform — cursor / selection map to
                // the plain text. RAW = no highlight. Remembered per (format,
                // theme) so typing and cursor-blink reuse it.
                val vDark = isDarkBg(c.bg)
                val vHlTransform: androidx.compose.ui.text.input.VisualTransformation? =
                    remember(inFormat, vDark) {
                        if (inFormat == BodyFormat.RAW) null
                        else androidx.compose.ui.text.input.VisualTransformation { plain ->
                            androidx.compose.ui.text.input.TransformedText(
                                highlight(plain.text, inFormat, SyntaxPalette.forDark(vDark)),
                                androidx.compose.ui.text.input.OffsetMapping.Identity,
                            )
                        }
                    }
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTextSelectionColors provides
                            androidx.compose.foundation.text.selection.TextSelectionColors(
                                handleColor = c.accent,
                                backgroundColor = c.accent.copy(alpha = 0.35f),
                            ),
                ) {
                    BasicTextField(
                        value = inText,
                        onValueChange = inOnChange,
                        textStyle = TextStyle(
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = monoFontFamily,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        visualTransformation = vHlTransform ?: androidx.compose.ui.text.input.VisualTransformation.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                // READ-ONLY body (the received response): drag-selectable across
                // the block + Ctrl/Cmd+C, and syntax-coloured. The palette picks
                // dark-on-light vs the inverse by background luminance. Highlight
                // is memoised so a huge body is tokenised once, not every frame.
                val vDark = isDarkBg(c.bg)
                val vBody = remember(inText, inFormat, vDark) {
                    if (inFormat == BodyFormat.RAW) AnnotatedString(inText)
                    else highlight(inText, inFormat, SyntaxPalette.forDark(vDark))
                }
                SelectionContainer {
                    // No-wrap mode: one source line per row, long lines overflow
                    // and the body pans horizontally (the gutter stays pinned —
                    // it's outside this scroll). The scroll must sit on a PARENT
                    // of the Text: a node's own scroll offset shifts its children,
                    // not itself. Wrap mode: plain soft-wrapping Text.
                    if (inSoftWrap) {
                        Text(vBody, color = c.text, fontSize = 12.sp, fontFamily = monoFontFamily, softWrap = true)
                    } else {
                        Box(modifier = Modifier.horizontalScroll(vHScroll)) {
                            Text(vBody, color = c.text, fontSize = 12.sp, fontFamily = monoFontFamily, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** "Request" + colored "GET" tab. inAccent is shown next to the label
when non-null (e.g. method name once sent, status code once received);
when null the tab is just the label, no placeholder dash. Selected
tab gets full-strength labels and a 2dp underline; unselected tabs
stay dimmed. */
@Composable
internal fun ViewerTab(
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
                Text(
                    inAccent,
                    color = if (inSelected) inAccentColor else inAccentColor.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
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

/** "HTTP/1.1   200 OK" status line for a Response, with the protocol
token dimmed (it's structural) and the status code + reason in the
status colour. Just "HTTP/1.1" pre-response — no placeholder. Triple-
spaced to match the request-line formatting. */
internal fun formatStatusLine(inResp: ApiResponse?, inColors: AppColors): AnnotatedString {
    if (inResp == null) return buildAnnotatedString {
        pushStyle(SpanStyle(color = inColors.dim))
        append(inResp?.httpVersion ?: "HTTP/1.1")
        pop()
    }
    if (inResp.error != null) return buildAnnotatedString {
        pushStyle(SpanStyle(color = inColors.dim))
        append(inResp.httpVersion)
        pop()
        append("   ")
        pushStyle(SpanStyle(color = Color(0xFFFF5630), fontWeight = FontWeight.Bold))
        append("FAILED")
        pop()
    }
    return buildAnnotatedString {
        pushStyle(SpanStyle(color = inColors.dim))
        append(inResp.httpVersion)
        pop()
        append("   ")
        pushStyle(SpanStyle(color = statusColor(inResp.status), fontWeight = FontWeight.Bold))
        append("${inResp.status} ${inResp.statusText.uppercase()}")
        pop()
    }
}

/** "GET   /path   HTTP/1.1" status line for a Request. Each part takes
its own colour: method in its canonical method colour, URL in the
default text colour, protocol token dimmed so the eye lands on the
request target first. Triple-spaced so the columns read at a glance.
The protocol string comes from the matching response when we have
one (so HTTP/2 servers show "HTTP/2"); fallback "HTTP/1.1" before
send. */
internal fun formatRequestLine(inReq: ApiRequest, inHttpVersion: String, inColors: AppColors): AnnotatedString =
    buildAnnotatedString {
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

/** Pull the path (+ query) out of a URL, dropping scheme + host. The
request line in HTTP is "METHOD path HTTP/x.y" — the host lives on a
separate Host: header line, not in the path. Falls back to the input
string when no "://" is present (relative URL). */
internal fun urlPathOnly(inUrl: String): String {
    val vIdx = inUrl.indexOf("://")
    if (vIdx < 0) return inUrl
    val vAfterScheme = inUrl.substring(vIdx + 3)
    val vSlash = vAfterScheme.indexOf('/')
    return if (vSlash < 0) "/" else vAfterScheme.substring(vSlash)
}

/** Extract the host (+ port) from a URL — used to synthesize a Host
header for display when the engine didn't surface one. */
internal fun urlHost(inUrl: String): String? {
    val vIdx = inUrl.indexOf("://")
    if (vIdx < 0) return null
    val vAfterScheme = inUrl.substring(vIdx + 3)
    val vSlash = vAfterScheme.indexOf('/')
    val vAuthority = if (vSlash < 0) vAfterScheme else vAfterScheme.substring(0, vSlash)
    return vAuthority.ifEmpty { null }
}

/** The user-agent string our Darwin engine sends is opaque to Ktor —
NSURLSession picks the default. Match what httpie does: identify
ourselves so the wire log isn't missing the field entirely. */
internal const val kUserAgent: String = "compose-apidemo/1.0"

/** Combine Ktor's reported request headers with the ones the engine
adds at the wire level (Host, Content-Length, User-Agent) so the
Request tab shows the actual on-the-wire header set rather than
just the subset Ktor sees. Sorted alphabetically. */
internal fun synthesizeRequestHeaders(
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

/** Body length in bytes for the headers synthesis. JSON / TEXT use the
raw UTF-8 byte count; FORM serialises and counts; FILE skips
(loading the file just for the count would be wasteful — the engine
sets the field anyway). */
internal fun computedBodyLength(inReq: ApiRequest): Int? = when (inReq.bodyType) {
    BodyType.TEXT -> inReq.body.encodeToByteArray().size
    BodyType.FORM -> formEncode(inReq.form).encodeToByteArray().size
    BodyType.FILE, BodyType.NONE -> null
}

/** Format the timing / size footer text shown bottom-right. */
internal fun formatTimingSize(inResp: ApiResponse): String {
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

/** Replaces the inline Copy / Save chips with a single MoreHoriz menu.
Copy actions target whichever tab is showing (request or response);
Clear is global — wipes the response, sentReq, preview state, and any
memory-backed image resource for the current request. */
@Composable
internal fun ViewerOverflowMenu(
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
    val vClipboard = LocalClipboard.current
    val vScope = rememberCoroutineScope()

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

    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { vOpen = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            MaterialSymbolsOutlined(icon = MaterialSymbols.MoreHoriz, tint = c.dim, size = 16.dp)
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
            DropdownMenuItem(text = { Text("Copy all") }, onClick = {
                vScope.launch { vClipboard.setClipEntry(clipEntryOfText(copyAll())) }
                inOnMessage("Copied all.")
                vOpen = false
            })
            DropdownMenuItem(text = { Text("Copy headers") }, onClick = {
                vScope.launch { vClipboard.setClipEntry(clipEntryOfText(vHeadersText)) }
                inOnMessage("Copied headers.")
                vOpen = false
            })
            if (vCanCopyBody) {
                DropdownMenuItem(text = { Text("Copy body") }, onClick = {
                    vScope.launch { vClipboard.setClipEntry(clipEntryOfText(vBodyText)) }
                    inOnMessage("Copied body.")
                    vOpen = false
                })
            }
            DropdownMenuItem(text = { Text("Save body") }, onClick = {
                if (inIsImage && !inIsRequestTab) {
                    val vBytes = inResponse?.bytes ?: ByteArray(0)
                    showSaveFileDialog(imageFileName(inResponse?.contentType)) { vPath ->
                        if (vPath != null) inOnMessage(writeBytesFile(vPath, vBytes)?.let { "Save failed: $it" }
                            ?: "Saved.")
                    }
                } else {
                    showSaveFileDialog(if (inIsRequestTab) "request.txt" else "response.json") { vPath ->
                        if (vPath != null) inOnMessage(writeTextFile(vPath, vBodyText)?.let { "Save failed: $it" }
                            ?: "Saved.")
                    }
                }
                vOpen = false
            })
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = c.border)
            DropdownMenuItem(
                text = { Text("Clear", color = Color(0xFFFF5630)) },
                onClick = { inOnClear(); vOpen = false })
        }
    }
}

/** Perceptual luminance check — true when the colour reads as "dark"
(background gets a light foreground). Standard Rec. 709 weights. */
internal fun isDarkBg(inColor: Color): Boolean {
    val vY = 0.299f * inColor.red + 0.587f * inColor.green + 0.114f * inColor.blue
    return vY < 0.5f
}

// ==================
// MARK: BodyFormatSelector — small dropdown for RAW / JSON / XML / YAML / HTML
// ==================

/** Format / "type" picker. inBordered renders it as a full dropdown matching
BodyTypeMenu (the Body-tab Text-type picker); otherwise it's the compact inline
control used by the response viewer. */
@Composable
internal fun BodyFormatSelector(
    inSelected: BodyFormat,
    inOnChange: (BodyFormat) -> Unit,
    inAutoLabel: String? = null,  // when set, shown as "FORMAT (auto)"
    inBordered: Boolean = false,
) {
    val c = LocalAppColors.current
    var vOpen by remember { mutableStateOf(false) }
    val vLabel = if (inAutoLabel != null) "$inAutoLabel (auto)" else inSelected.label
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(if (inBordered) 6.dp else 4.dp))
                .then(if (inBordered) Modifier.border(1.dp, c.border, RoundedCornerShape(6.dp)) else Modifier)
                .clickable { vOpen = true }
                .padding(horizontal = if (inBordered) 10.dp else 8.dp, vertical = if (inBordered) 6.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (inBordered) 5.dp else 4.dp),
        ) {
            Text(vLabel, color = if (inBordered) c.text else c.dim, fontSize = if (inBordered) 13.sp else 11.sp)
            MaterialSymbolsOutlined(
                if (inBordered) MaterialSymbols.UnfoldMore else MaterialSymbols.ArrowDropDown,
                tint = c.dim,
                size = if (inBordered) 15.dp else 14.dp
            )
        }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
            for (vF in BodyFormat.values()) {
                DropdownMenuItem(
                    text = { Text(vF.label, color = if (vF == inSelected) c.accent else c.text) },
                    onClick = { inOnChange(vF); vOpen = false })
            }
        }
    }
}

/** TLS validation indicator: only true when the URL is https AND we got
a real response back (i.e. the OS engine completed the TLS handshake
without an error). The engines we ship — NSURLSession on macOS,
WinHttp on Windows, libcurl on Linux — all reject an untrusted
certificate by default, so a non-error response is implicit proof
that the OS validated the chain. */
internal fun isTlsValidated(inUrl: String, inResp: ApiResponse?): Boolean {
    if (!inUrl.startsWith("https://", ignoreCase = true)) return false
    if (inResp == null) return false
    if (inResp.error != null) return false
    return inResp.status > 0
}

/** Parse "Key: value\nKey2: value2" into a list of pairs so the headers
table renderer can lay them out as a key/value grid. */
internal fun parseHeaderLines(inText: String): List<Pair<String, String>> {
    if (inText.isBlank() || inText == "(no headers)") return emptyList()
    return inText.split('\n').mapNotNull { vLine ->
        val vIdx = vLine.indexOf(':')
        if (vIdx < 0) null else vLine.substring(0, vIdx).trim() to vLine.substring(vIdx + 1).trim()
    }
}

/** "content-type" → "Content-Type"; preserves single-word keys; leaves
already-correct ones unchanged. */
internal fun titleCaseHeader(inKey: String): String =
    inKey.split('-').joinToString("-") { vWord ->
        if (vWord.isEmpty()) vWord
        else vWord[0].uppercaseChar() + vWord.drop(1).lowercase()
    }

/** Centered icon + label placeholder for the viewer (Not sent / Not received /
No Body). Fills its parent unless a sized modifier is supplied. */
@Composable
internal fun ViewerEmpty(inIcon: Int, inText: String, inModifier: Modifier = Modifier.fillMaxSize()) {
    val c = LocalAppColors.current
    Box(modifier = inModifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialSymbolsOutlined(inIcon, tint = c.dim, size = 40.dp)
            Text(inText, color = c.dim, fontSize = 14.sp)
        }
    }
}

/** A labelled, read-only (selectable) code block used by the viewer sections. */
@Composable
internal fun CodeSection(inLabel: String, inText: String) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(inLabel, color = c.dim, fontSize = 11.sp)
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(c.bg, RoundedCornerShape(8.dp)).border(1.dp, c.border, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalTextSelectionColors provides
                        androidx.compose.foundation.text.selection.TextSelectionColors(
                            handleColor = c.accent,
                            backgroundColor = c.accent.copy(alpha = 0.35f),
                        ),
            ) {
                BasicTextField(
                    value = inText.ifEmpty { "(empty)" }, onValueChange = {}, readOnly = true,
                    textStyle = TextStyle(color = c.text, fontSize = 12.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Format an actual header list (key: value per line). */
internal fun headersText(inHeaders: List<Pair<String, String>>): String =
    inHeaders.joinToString("\n") { (vK, vV) -> "$vK: $vV" }.ifEmpty { "(no headers)" }

/** The headers that *would* be sent — explicit enabled headers plus the inferred
Content-Type for the body type (unless one is already set). Used for Preview;
a sent request shows the real headers via headersText(response.requestHeaders). */
internal fun requestHeadersText(inReq: ApiRequest): String {
    val vLines = mutableListOf<String>()
    inReq.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { vLines.add("${it.key}: ${it.value}") }
    val vCt = inReq.bodyContentType()
    if (inReq.method.allowsBody && vCt != null && inReq.headers.none {
            it.enabled && it.key.equals(
                "content-type",
                ignoreCase = true
            )
        }) {
        vLines.add("Content-Type: $vCt")
    }
    return vLines.joinToString("\n").ifEmpty { "(no headers)" }
}

/** The body that would be sent, rendered as text for the preview. */
internal fun requestBodyText(inReq: ApiRequest): String = when (inReq.bodyType) {
    BodyType.NONE -> "(no body)"
    BodyType.TEXT -> inReq.body.ifEmpty { "(empty)" }
    BodyType.FORM -> formEncode(inReq.form).ifEmpty { "(empty form)" }
    BodyType.FILE -> if (inReq.body.isBlank()) "(no file)" else "(file) ${inReq.body}"
}

@Composable
internal fun StatusPill(inStatus: Int, inLabel: String) {
    val vC = statusColor(inStatus)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(vC.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(inLabel, color = vC, fontSize = 14.sp)
    }
}

