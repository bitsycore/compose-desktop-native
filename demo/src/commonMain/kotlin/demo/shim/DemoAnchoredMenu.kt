package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ==================
// MARK: DemoAnchoredMenu — platform-neutral anchored dropdown
// ==================

/* An anchored dropdown popup used by the sidebar's category selector. The
   trigger is rendered via [anchor], which is handed a Modifier it MUST apply to
   the element the popup should attach to. [menu] is the popup body, shown while
   [expanded]. Native delegates to the project's anchor-based demo.DropdownMenu;
   a future jvm target uses a Popup-based equivalent. Menu rows are plain
   clickable composables supplied by the caller — no per-item shim needed. */
@Composable
expect fun DemoAnchoredMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: @Composable (Modifier) -> Unit,
    menu: @Composable () -> Unit,
)
