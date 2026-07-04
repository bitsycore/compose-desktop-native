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
import com.compose.desktop.native.scrollbar.*
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
import com.compose.desktop.native.res.ResourceKind
import com.compose.desktop.native.res.painterResource
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
// MARK: Response
// ==================

/* Panel 4 — Request / Response viewer. Each tab stacks a HEADERS section over a
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
                            val vKind = if (vResp?.contentType?.contains("svg", ignoreCase = true) == true) ResourceKind.Svg else ResourceKind.Raster
                            painterResource(inRs.imageKey!!, vKind)
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
                            MaterialSymbolsOutlined(MaterialSymbols.WrapText, tint = if (vWrapOn) c.accent else c.dim, size = 14.dp)
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
    val vScroll = rememberScrollState()
    // contentAlignment pins the narrow scrollbar to the right edge; the
    // fillMaxSize Column fills the rest (this project's Box has no BoxScope, so
    // there's no Modifier.align — alignment is via contentAlignment).
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(vScroll),
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
                    inSoftWrap = inSoftWrap,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        }
        // Overlay scrollbar on the right, themed for the dark panel.
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(vScroll),
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

/* Header table: each row is (key | value) where the key column has a
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

/* Body view with line numbers in the gutter — mimics the code panel
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
    // Body wrap width (logical px), reported by the body Box once it's laid out
    // (0 on the first frame). The gutter numbering depends on it — see vNumbers.
    var vBodyWidthPx by remember { mutableStateOf(0) }
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
        val vNumbers = remember(inText, vBodyWidthPx, inSoftWrap) {
            val vM = if (inSoftWrap && vBodyWidthPx > 0) currentTextMeasurer else null
            buildString {
                for ((vIdx, vSrc) in vLines.withIndex()) {
                    if (vIdx > 0) append('\n')
                    append(vIdx + 1)
                    if (vM != null) {
                        val vRows = vM.wrap(vSrc, 12, vBodyWidthPx, monoFontFamily).lines.size.coerceAtLeast(1)
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
        Box(modifier = Modifier.weight(1f)
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
                    androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
                        androidx.compose.foundation.text.selection.TextSelectionColors(
                            handleColor = c.accent,
                            backgroundColor = c.accent.copy(alpha = 0.35f),
                        ),
                ) {
                    BasicTextField(
                        value = inText,
                        onValueChange = inOnChange,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = monoFontFamily?.let { com.compose.desktop.native.text.namedFontFamily(it) },
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

/* "Request" + colored "GET" tab. inAccent is shown next to the label
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

/* "GET   /path   HTTP/1.1" status line for a Request. Each part takes
   its own colour: method in its canonical method colour, URL in the
   default text colour, protocol token dimmed so the eye lands on the
   request target first. Triple-spaced so the columns read at a glance.
   The protocol string comes from the matching response when we have
   one (so HTTP/2 servers show "HTTP/2"); fallback "HTTP/1.1" before
   send. */
internal fun formatRequestLine(inReq: ApiRequest, inHttpVersion: String, inColors: AppColors): AnnotatedString = buildAnnotatedString {
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
internal fun urlPathOnly(inUrl: String): String {
    val vIdx = inUrl.indexOf("://")
    if (vIdx < 0) return inUrl
    val vAfterScheme = inUrl.substring(vIdx + 3)
    val vSlash = vAfterScheme.indexOf('/')
    return if (vSlash < 0) "/" else vAfterScheme.substring(vSlash)
}

/* Extract the host (+ port) from a URL — used to synthesize a Host
   header for display when the engine didn't surface one. */
internal fun urlHost(inUrl: String): String? {
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
internal const val kUserAgent: String = "compose-apidemo/1.0"

/* Combine Ktor's reported request headers with the ones the engine
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

/* Body length in bytes for the headers synthesis. JSON / TEXT use the
   raw UTF-8 byte count; FORM serialises and counts; FILE skips
   (loading the file just for the count would be wasteful — the engine
   sets the field anyway). */
internal fun computedBodyLength(inReq: ApiRequest): Int? = when (inReq.bodyType) {
    BodyType.TEXT -> inReq.body.encodeToByteArray().size
    BodyType.FORM -> formEncode(inReq.form).encodeToByteArray().size
    BodyType.FILE, BodyType.NONE -> null
}

/* Format the timing / size footer text shown bottom-right. */
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

/* Replaces the inline Copy / Save chips with a single MoreHoriz menu.
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
            currentClipboard.setText(AnnotatedString(copyAll()))
            inOnMessage("Copied all.")
            vOpen = false
        }) { Text("Copy all") }
        DropdownMenuItem(onClick = {
            currentClipboard.setText(AnnotatedString(vHeadersText))
            inOnMessage("Copied headers.")
            vOpen = false
        }) { Text("Copy headers") }
        if (vCanCopyBody) {
            DropdownMenuItem(onClick = {
                currentClipboard.setText(AnnotatedString(vBodyText))
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
internal fun isDarkBg(inColor: Color): Boolean {
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
internal fun BodyFormatSelector(
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
internal fun isTlsValidated(inUrl: String, inResp: ApiResponse?): Boolean {
    if (!inUrl.startsWith("https://", ignoreCase = true)) return false
    if (inResp == null) return false
    if (inResp.error != null) return false
    return inResp.status > 0
}

/* Parse "Key: value\nKey2: value2" into a list of pairs so the headers
   table renderer can lay them out as a key/value grid. */
internal fun parseHeaderLines(inText: String): List<Pair<String, String>> {
    if (inText.isBlank() || inText == "(no headers)") return emptyList()
    return inText.split('\n').mapNotNull { vLine ->
        val vIdx = vLine.indexOf(':')
        if (vIdx < 0) null else vLine.substring(0, vIdx).trim() to vLine.substring(vIdx + 1).trim()
    }
}

/* "content-type" → "Content-Type"; preserves single-word keys; leaves
   already-correct ones unchanged. */
internal fun titleCaseHeader(inKey: String): String =
    inKey.split('-').joinToString("-") { vWord ->
        if (vWord.isEmpty()) vWord
        else vWord[0].uppercaseChar() + vWord.drop(1).lowercase()
    }

/* Centered icon + label placeholder for the viewer (Not sent / Not received /
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

/* A labelled, read-only (selectable) code block used by the viewer sections. */
@Composable
internal fun CodeSection(inLabel: String, inText: String) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(inLabel, color = c.dim, fontSize = 11.sp)
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(c.bg, RoundedCornerShape(8.dp)).border(1.dp, c.border, RoundedCornerShape(8.dp)).padding(12.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
                    androidx.compose.foundation.text.selection.TextSelectionColors(
                        handleColor = c.accent,
                        backgroundColor = c.accent.copy(alpha = 0.35f),
                    ),
            ) {
                BasicTextField(
                    value = inText.ifEmpty { "(empty)" }, onValueChange = {}, readOnly = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 12.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/* Format an actual header list (key: value per line). */
internal fun headersText(inHeaders: List<Pair<String, String>>): String =
    inHeaders.joinToString("\n") { (vK, vV) -> "$vK: $vV" }.ifEmpty { "(no headers)" }

/* The headers that *would* be sent — explicit enabled headers plus the inferred
   Content-Type for the body type (unless one is already set). Used for Preview;
   a sent request shows the real headers via headersText(response.requestHeaders). */
internal fun requestHeadersText(inReq: ApiRequest): String {
    val vLines = mutableListOf<String>()
    inReq.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { vLines.add("${it.key}: ${it.value}") }
    val vCt = inReq.bodyContentType()
    if (inReq.method.allowsBody && vCt != null && inReq.headers.none { it.enabled && it.key.equals("content-type", ignoreCase = true) }) {
        vLines.add("Content-Type: $vCt")
    }
    return vLines.joinToString("\n").ifEmpty { "(no headers)" }
}

/* The body that would be sent, rendered as text for the preview. */
internal fun requestBodyText(inReq: ApiRequest): String = when (inReq.bodyType) {
    BodyType.NONE -> "(no body)"
    BodyType.TEXT -> inReq.body.ifEmpty { "(empty)" }
    BodyType.FORM -> formEncode(inReq.form).ifEmpty { "(empty form)" }
    BodyType.FILE -> if (inReq.body.isBlank()) "(no file)" else "(file) ${inReq.body}"
}

@Composable
internal fun StatusPill(inStatus: Int, inLabel: String) {
    val vC = statusColor(inStatus)
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(vC.copy(alpha = 0.20f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(inLabel, color = vC, fontSize = 14.sp)
    }
}

