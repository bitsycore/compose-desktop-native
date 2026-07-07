package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import demo.DropdownMenu
import demo.menuAnchor
import demo.rememberMenuAnchor

// Native actual: reuse the project's anchored DropdownMenu (UiCompat.kt). The
// anchor state is wired to the trigger via Modifier.menuAnchor; the popup lands
// just below it.
@Composable
actual fun DemoAnchoredMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: @Composable (Modifier) -> Unit,
    menu: @Composable () -> Unit,
) {
    val anchorState = rememberMenuAnchor()
    anchor(Modifier.menuAnchor(anchorState))
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        anchor = anchorState,
        offsetY = 4.dp,
        minWidth = 174.dp,
    ) { menu() }
}
