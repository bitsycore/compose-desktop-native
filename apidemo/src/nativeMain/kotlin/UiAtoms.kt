package apidemo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined

// ==================
// MARK: Small UI atoms (shared across panels / dialogs / sidebar)
// ==================

/* Method as a compact rounded badge (soft colour fill + coloured label), fixed
   width so request names line up. */
@Composable
internal fun MethodTag(inMethod: ReqMethod) {
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
internal fun RowDropBar() {
    val c = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(c.accent, RoundedCornerShape(1.dp)))
}

/* Tab indices listed in inDots get a small accent dot after their label — used
   to flag a tab that holds content (e.g. a non-empty request body). */
@Composable
internal fun TabBar(inTabs: List<String>, inSelected: Int, inDots: Set<Int> = emptySet(), inOnSelect: (Int) -> Unit) {
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
internal fun TogglePill(inLabel: String, inSelected: Boolean, inOnClick: () -> Unit) {
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
internal fun ThinField(inValue: String, inOnChange: (String) -> Unit, inModifier: Modifier = Modifier, inPlaceholder: String = "", inSingleLine: Boolean = true, inOnEnter: (() -> Unit)? = null) {
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
internal fun IconBtn(inIcon: Int, inDesc: String, inModifier: Modifier = Modifier, inSize: Dp = 18.dp, inPadding: Dp = 6.dp, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    var vHover by remember { mutableStateOf(false) }
    Box(
        modifier = inModifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (vHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
            .hoverable { vHover = it }
            .clickable(onClick = inOnClick)
            .padding(inPadding),
    ) {
        MaterialSymbolsOutlined(inIcon, contentDescription = inDesc, tint = if (vHover) c.accent else c.dim, size = inSize)
    }
}

/* Outlined icon+label action (New request / Add row / pack actions). */
@Composable
internal fun OutlinedAction(inIcon: Int, inLabel: String, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    OutlinedButton(onClick = inOnClick) { BtnContent(inIcon, inLabel, c.accent) }
}

/* Filled red icon+label button for destructive / stop actions (delete, cancel, quit). */
@Composable
internal fun DangerButton(inLabel: String, inIcon: Int, inOnClick: () -> Unit) {
    val vRed = methodColor(ReqMethod.DELETE)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(vRed, RoundedCornerShape(6.dp))
            .clickable(onClick = inOnClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { BtnContent(inIcon, inLabel, Color.White) }
}

/* Bordered icon+label chip (Copy / Save as / Cancel). */
@Composable
internal fun IconLabelChip(inIcon: Int, inLabel: String, inOnClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.border, RoundedCornerShape(6.dp))
            .clickable(onClick = inOnClick).padding(horizontal = 8.dp, vertical = 5.dp),
    ) { BtnContent(inIcon, inLabel, c.dim, 14.dp) }
}

/* Icon + label row used inside buttons. */
@Composable
internal fun BtnContent(inIcon: Int, inLabel: String, inColor: Color, inSize: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbolsOutlined(inIcon, tint = inColor, size = inSize)
        Text(inLabel, color = inColor, fontSize = 13.sp)
    }
}
