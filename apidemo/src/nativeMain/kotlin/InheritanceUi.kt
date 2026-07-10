@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package apidemo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.outlined.MaterialSymbolsOutlined

// ==================
// MARK: Headers tab (inherited read-only + own editable)
// ==================

/* A request tab pairing an inherited (read-only, source-tagged) list with the
   request's own editable list: Override copies an inherited entry down, and own
   rows that shadow an inherited one get an OverrideMark. Used by Query / Var /
   Headers — the only differences are the key case-sensitivity and the labels. */
@Composable
internal fun InheritedEditableTab(
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
        if (inInherited.any { it.kv.key.isNotBlank() }) {
            HorizontalDivider(color = c.border)
            Text(inOwnTitle, color = c.dim, fontSize = 11.sp)
        }
        KeyValEditor(inOwn, inOnChange = inOnChange, inOverrideInfo = { vR ->
            if (vR.key.isBlank()) null else inInherited.firstOrNull { norm(it.kv.key) == norm(vR.key) }?.path
        })
    }
}

/* Request Headers tab: inherited headers (case-insensitive) + the request's own. */
@Composable
internal fun HeadersTab(inReq: ApiRequest, inInherited: List<InheritedKv>, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) =
    InheritedEditableTab(inInherited, inReq.headers, inCaseInsensitive = true, inReadOnly = inReadOnly, inOwnTitle = "Request headers",
        inOnOverride = { vKv -> inEdit { it.copy(headers = it.headers + vKv) } },
        inOnChange = { v -> inEdit { it.copy(headers = v) } })

/* Request Query tab: inherited query params (case-sensitive) + the request's own. */
@Composable
internal fun QueryTab(inReq: ApiRequest, inInherited: List<InheritedKv>, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) =
    InheritedEditableTab(inInherited, inReq.params, inCaseInsensitive = false, inReadOnly = inReadOnly, inOwnTitle = "Request query params",
        inOnOverride = { vKv -> inEdit { it.copy(params = it.params + vKv) } },
        inOnChange = { v -> inEdit { it.copy(params = v) } })

/* Request Var tab: inherited variables (case-sensitive {{name}}) + the request's
   own. A request's own variable overrides the same name inherited from a pack /
   the session when the request is sent. */
@Composable
internal fun VarTab(inReq: ApiRequest, inInherited: List<InheritedKv>, inReadOnly: Boolean, inEdit: (((ApiRequest) -> ApiRequest)) -> Unit) =
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
internal fun SourceTag(inLabel: String, inPath: String) {
    val c = LocalAppColors.current
    val vPill = @Composable {
        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.accent.copy(alpha = 0.16f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text(inLabel, color = c.accent, fontSize = 9.sp)
        }
    }
    if (inPath.isBlank() || inPath == inLabel) {
        vPill()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(inPath) } },
            state = rememberTooltipState(),
        ) { vPill() }
    }
}

/* The tiny marker shown on an own value that shadows an inherited one — hover for
   "Overrides <key> from <path>". */
@Composable
internal fun OverrideMark(inKey: String, inPath: String) {
    val c = LocalAppColors.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text("Overrides “$inKey” from $inPath") } },
        state = rememberTooltipState(),
    ) {
        MaterialSymbolsOutlined(MaterialSymbols.ArrowUpward, contentDescription = "Overrides $inPath", tint = c.accent, size = 13.dp)
    }
}

/* Read-only list of inherited key/values, each tagged with its source. When not
   already overridden in this scope (own key absent) and inOnOverride != null, a
   row offers a one-click Override that copies it into this scope's own list. */
@Composable
internal fun InheritedKvSection(
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
internal fun KeyValEditor(inRows: List<KeyVal>, inOverrideInfo: (KeyVal) -> String? = { null }, inOnChange: (List<KeyVal>) -> Unit) {
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


