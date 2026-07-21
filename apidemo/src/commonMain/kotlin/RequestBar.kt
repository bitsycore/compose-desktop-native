@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package apidemo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined

// ==================
// MARK: Options menu (theme + pack-file actions)
// ==================

@Composable
internal fun OptionsMenu(
    inDark: Boolean,
    inOnToggleTheme: () -> Unit,
    inPalette: VolticPalette,
    inOnPalette: (VolticPalette) -> Unit,
) {
    val c = LocalAppColors.current
    var vOpen by remember { mutableStateOf(false) }
    Box {
        IconBtn(MaterialSymbols.Settings, "Options") { vOpen = true }
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, modifier = Modifier.widthIn(min = 200.dp)) {
            DropdownMenuItem(text = {
                Text("Dark mode", color = if (inDark) c.accent else c.text, fontSize = 13.sp)
            }, onClick = { if (!inDark) inOnToggleTheme(); vOpen = false })
            DropdownMenuItem(text = {
                Text("Light mode", color = if (!inDark) c.accent else c.text, fontSize = 13.sp)
            }, onClick = { if (inDark) inOnToggleTheme(); vOpen = false })
            HorizontalDivider(color = c.border)
            // Palette picker — a swatch of each theme's primary + a check on the active one.
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 2.dp)) {
                Text("Theme", color = c.dim, fontSize = 11.sp)
            }
            VolticPalette.entries.forEach { vPal ->
                val vSel = vPal == inPalette
                DropdownMenuItem(text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(vPal.scheme.light.primary))
                        Text(vPal.label, color = if (vSel) c.accent else c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (vSel) MaterialSymbolsOutlined(MaterialSymbols.Check, tint = c.accent, size = 16.dp)
                    }
                }, onClick = { inOnPalette(vPal); vOpen = false })
            }
            HorizontalDivider(color = c.border)
            DropdownMenuItem(text = {
                MenuRow(MaterialSymbols.Folder, "Open settings folder")
            }, onClick = { vOpen = false; openSettingsFolder() })
        }
    }
}

// ==================
// MARK: Method picker (dropdown)
// ==================

/** Panel 2 — one unified bar: a method dropdown (coloured label + unfold arrows),
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
    var vOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.alpha(if (inReadOnly) 0.55f else 1f)) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { vOpen = true }.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(inReq.method.name, color = methodColor(inReq.method), fontSize = 14.sp)
                MaterialSymbolsOutlined(MaterialSymbols.UnfoldMore, tint = methodColor(inReq.method), size = 16.dp)
            }
            DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
                ReqMethod.entries.forEach { vM ->
                    DropdownMenuItem(text = {
                        Text(vM.name, color = methodColor(vM), fontSize = 13.sp)
                    }, onClick = { inOnMethod(vM); vOpen = false })
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
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
                    androidx.compose.foundation.text.selection.TextSelectionColors(
                        handleColor = c.accent,
                        backgroundColor = c.accent.copy(alpha = 0.35f),
                    ),
            ) {
                BasicTextField(
                    value = inReq.url,
                    onValueChange = inOnUrl,
                    textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Inspect the server's TLS certificate chain (handshake-only probe).
        // During the probe the lock glyph swaps to a spinner IN PLACE — same
        // box, padding and size — so the bar doesn't shift.
        val vLockHoverSrc = remember { MutableInteractionSource() }
        val vLockHover by vLockHoverSrc.collectIsHoveredAsState()
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text("Inspect TLS certificate chain") } },
            state = rememberTooltipState(),
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (vLockHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .hoverable(vLockHoverSrc)
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
            colors = ButtonDefaults.buttonColors(containerColor = methodColor(ReqMethod.DELETE), contentColor = Color.White),
        ) { BtnContent(MaterialSymbols.Stop, "Cancel", Color.White) }
        else Button(onClick = inOnSend) { BtnContent(MaterialSymbols.Send, "Send", c.onAccent) }
    }
}

/** Bottom-of-panel-3 dropdown choosing the body type (None/Text/Form/File). */
@Composable
internal fun BodyTypeMenu(inType: BodyType, inEnabled: Boolean, inOnPick: (BodyType) -> Unit) {
    val c = LocalAppColors.current
    var vOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
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
        DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
            BodyType.entries.forEach { vT ->
                DropdownMenuItem(text = {
                    Text(vT.label, color = if (vT == inType) c.accent else c.text, fontSize = 13.sp)
                }, onClick = { inOnPick(vT); vOpen = false })
            }
        }
    }
}

