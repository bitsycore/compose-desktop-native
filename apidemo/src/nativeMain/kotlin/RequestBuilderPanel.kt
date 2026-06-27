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
// MARK: Panel 3 — request builder (Query / Headers / Body)
// ==================

/* Tabs to edit query params, headers and the body, a Preview toggle top-right,
   and a body-type dropdown pinned at the bottom. */
@Composable
internal fun RequestBuilder(
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
            HoverIconBtn(
                MaterialSymbols.Visibility, "Preview request",
                inOnClick = { inRs.preview = !inRs.preview; if (inRs.preview) inRs.viewTab = 0 },
                inActive = vPreviewOn,
                inSize = 16.dp,
                inModifier = Modifier.offset(y = (-2).dp),
            )
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
                    // Magic-wand auto-format, pushed to the right edge of the bar.
                    if (inReq.bodyFormat == BodyFormat.JSON || inReq.bodyFormat == BodyFormat.XML) {
                        Spacer(Modifier.weight(1f))
                        TabSizeSelector()
                        FormatButton { inEdit { it.copy(body = formatBody(it.body, it.bodyFormat)) } }
                    }
                }
            }
        }
    }
}

/* The body editing area for the current body type (driven by the bottom menu). A
   Text body is highlighted per its chosen format (bodyFormat). */
@Composable
internal fun BodyContent(inReq: ApiRequest, inEdit: ((ApiRequest) -> ApiRequest) -> Unit) {
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

/* Magic-wand button that pretty-prints the current body in place (JSON /
   XML). Lives at the right end of the body type/format bar. */
@Composable
internal fun FormatButton(inOnClick: () -> Unit) {
    HoverIconBtn(MaterialSymbols.AutoFixHigh, "Format (pretty-print)", inOnClick)
}

/* Icon button with the toolbar's standard hover treatment — accent-tinted
   background + accent icon on hover (matching the TLS chain button). inActive
   keeps it lit, for toggles such as the preview eye. */
@Composable
internal fun HoverIconBtn(
    inIcon: Int,
    inTooltip: String,
    inOnClick: () -> Unit,
    inActive: Boolean = false,
    inSize: Dp = 18.dp,
    inModifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    var vHover by remember { mutableStateOf(false) }
    val vBg = when {
        inActive -> c.accent.copy(alpha = 0.20f)
        vHover   -> c.accent.copy(alpha = 0.18f)
        else     -> Color.Transparent
    }
    TooltipBox(text = inTooltip) {
        Box(
            modifier = inModifier
                .clip(RoundedCornerShape(6.dp))
                .background(vBg, RoundedCornerShape(6.dp))
                .hoverable { vHover = it }
                .clickable(onClick = inOnClick)
                .padding(6.dp),
        ) {
            MaterialSymbolsOutlined(inIcon, contentDescription = inTooltip,
                tint = if (inActive || vHover) c.accent else c.dim, size = inSize)
        }
    }
}

/* Tab-size picker (2 / 4 / 8), shown left of the format button. Sets the global
   editor tab width via TextLayoutConfig — how wide a typed '\t' renders AND how
   deep the formatter indents. Snapshot-backed, so the change is live. */
@Composable
internal fun TabSizeSelector() {
    val c = LocalAppColors.current
    var vOpen by remember { mutableStateOf(false) }
    var vHover by remember { mutableStateOf(false) }
    val vAnchor = rememberMenuAnchor()
    val vSize = TextLayoutConfig.tabWidth
    TooltipBox(text = "Editor tab size") {
        Box {
            Row(
                modifier = Modifier.menuAnchor(vAnchor)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (vHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .hoverable { vHover = it }
                    .clickable { vOpen = true }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Tab $vSize", color = if (vHover) c.accent else c.dim, fontSize = 12.sp)
                MaterialSymbolsOutlined(MaterialSymbols.UnfoldMore, tint = if (vHover) c.accent else c.dim, size = 14.dp)
            }
            DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, anchor = vAnchor, offsetY = (-4).dp) {
                for (vN in listOf(2, 4, 8)) {
                    DropdownMenuItem(onClick = { TextLayoutConfig.tabWidth = vN; vOpen = false }) {
                        Text("$vN spaces", color = if (vN == vSize) c.accent else c.text)
                    }
                }
            }
        }
    }
}

/* FILE body — pick a file; its path is stored in body and sent as raw bytes. */
@Composable
internal fun FileBody(inReq: ApiRequest, inEdit: ((ApiRequest) -> ApiRequest) -> Unit) {
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
internal fun CertConfigEditor(inCert: CertConfig, inHeading: String = "Client certificate (mTLS)", inOverrideSource: String? = null, inOnChange: (CertConfig) -> Unit) {
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
internal fun InheritedCertCard(inInherited: InheritedCert, inOwnSet: Boolean, inReadOnly: Boolean, inOnOverride: () -> Unit) {
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
internal fun RequestCertTab(inReq: ApiRequest, inInheritedCert: InheritedCert?, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) {
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
internal fun ScopeSettings(
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
internal fun CertFormatMenu(inFormat: CertFormat, inOnPick: (CertFormat) -> Unit) {
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

