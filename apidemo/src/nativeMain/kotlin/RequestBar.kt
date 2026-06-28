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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.currentClipboard
import androidx.compose.ui.res.ResourceKind
import androidx.compose.ui.res.painterResource
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
// MARK: Options menu (theme + pack-file actions)
// ==================

@Composable
internal fun OptionsMenu(
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
internal fun UrlBar(
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
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) { inOnSend(); true } else false
            },
        ) {
            if (inReq.url.isEmpty()) Text("https://example.com/path", color = c.dim, fontSize = 14.sp)
            BasicTextField(
                value = inReq.url,
                onValueChange = inOnUrl,
                textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                selectionColor = c.accent.copy(alpha = 0.35f),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Inspect the server's TLS certificate chain (handshake-only probe).
        // During the probe the lock glyph swaps to a spinner IN PLACE — same
        // box, padding and size — so the bar doesn't shift.
        var vLockHover by remember { mutableStateOf(false) }
        TooltipBox(text = "Inspect TLS certificate chain") {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (vLockHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .hoverable { vLockHover = it }
                    .clickable(onClick = inOnInspectChain)
                    .padding(6.dp),
            ) {
                if (inChainLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                } else {
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
internal fun BodyTypeMenu(inType: BodyType, inEnabled: Boolean, inOnPick: (BodyType) -> Unit) {
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

