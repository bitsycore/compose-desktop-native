package androidx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay

// ==================
// MARK: Snackbar
// ==================

/* Single-message transient toast. Pinned to the bottom-centre of the
   window; a single action slot lives at the trailing edge. Use `Snackbar`
   directly when you have your own visibility logic, or use SnackbarHost
   below for the standard show-then-auto-dismiss flow. */
@Composable
fun Snackbar(
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Popup(modal = false) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = modifier
                    .width(SnackbarDefaults.PreferredWidth)
                    .background(SnackbarDefaults.BackgroundColor, SnackbarDefaults.Shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier) { content() }
                    if (action != null) {
                        Box(modifier = Modifier.padding(start = 16.dp)) { action() }
                    }
                }
            }
        }
    }
}

// ==================
// MARK: SnackbarHostState + Host
// ==================

/* Lightweight controller exposing show(message, …) and a current
   snackbar that SnackbarHost renders. `durationMillis = 0` keeps the
   snackbar visible until the next show() / dismiss(). */
class SnackbarHostState {
    var current: SnackbarMessage? by mutableStateOf<SnackbarMessage?>(null)
        internal set

    fun show(message: String, actionLabel: String? = null, durationMillis: Long = 4000L) {
        current = SnackbarMessage(message, actionLabel, durationMillis)
    }
    fun dismiss() { current = null }
}

data class SnackbarMessage(
    val message: String,
    val actionLabel: String? = null,
    val durationMillis: Long = 4000L,
)

/* Renders the active snackbar (if any) from a SnackbarHostState and
   auto-dismisses it after durationMillis. Place once at the root of your
   app; call `state.show(...)` from button handlers. */
@Composable
fun SnackbarHost(
    hostState: SnackbarHostState,
    onActionClick: (() -> Unit)? = null,
) {
    val vCurrent = hostState.current ?: return

    // Auto-dismiss after the message's duration. Re-keyed on the message
    // value so each show() restarts the timer.
    LaunchedEffect(vCurrent) {
        if (vCurrent.durationMillis > 0) {
            delay(vCurrent.durationMillis)
            hostState.dismiss()
        }
    }

    Snackbar(
        action = if (vCurrent.actionLabel != null) {
            {
                TextButton(onClick = {
                    onActionClick?.invoke()
                    hostState.dismiss()
                }) {
                    Text(
                        text = vCurrent.actionLabel,
                        color = MaterialTheme.colors.secondary,
                    )
                }
            }
        } else null,
    ) {
        Text(text = vCurrent.message, color = SnackbarDefaults.ContentColor)
    }
}

object SnackbarDefaults {
    val PreferredWidth: Dp = 480.dp
    val BackgroundColor: Color = Color(0xFF323232L)
    val ContentColor: Color = Color.White
    val Shape = RoundedCornerShape(4.dp)
}
