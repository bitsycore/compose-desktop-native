package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

// ==================
// MARK: Dialog
// ==================

/* Modal popup centered on screen. Clicking the scrim outside the dialog
   fires onDismissRequest (so apps can close the dialog by clicking out).
   Wrap your dialog content in a Surface for the rounded-corner look —
   Dialog itself is just the modal + centering wrapper, not opinionated
   about the content's chrome. */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Popup(onDismissRequest = onDismissRequest, modal = true) {
        // Stop scrim click-through: any click inside the dialog body itself
        // should be a no-op (it landed inside the content, not on the scrim).
        Box(
            modifier = Modifier
                .widthIn(min = DialogDefaults.MinWidth, max = DialogDefaults.MaxWidth)
                .background(MaterialTheme.colors.surface, DialogDefaults.Shape)
                .clickable { /* swallow */ },
        ) { content() }
    }
}

// ==================
// MARK: AlertDialog
// ==================

/* Material AlertDialog — title, message, confirm + dismiss buttons. The
   confirm/dismiss slots are TextButtons by default. Pass null to either
   slot to omit it (e.g. a confirm-only "OK" dialog). */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (title != null) title()
            if (text != null) text()
            if (confirmButton != null || dismissButton != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Box(modifier = Modifier.padding(start = 8.dp)) {}
                    }
                    if (confirmButton != null) confirmButton()
                }
            }
        }
    }
}

object DialogDefaults {
    val Shape = RoundedCornerShape(8.dp)
    val Padding: Dp = 24.dp
    val MinWidth: Dp = 280.dp
    val MaxWidth: Dp = 560.dp
}
