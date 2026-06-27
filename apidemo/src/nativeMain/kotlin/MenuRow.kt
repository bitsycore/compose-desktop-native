package apidemo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined

/* Icon + label row used inside nearly every DropdownMenuItem. */
@Composable
internal fun MenuRow(inIcon: Int, inLabel: String, inColor: Color? = null) {
    val c = LocalAppColors.current
    val vCol = inColor ?: c.text
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbolsOutlined(inIcon, tint = vCol, size = 16.dp)
        Text(inLabel, color = vCol, fontSize = 13.sp)
    }
}
