@file:OptIn(ExperimentalMaterial3Api::class)

package screens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

@Composable
internal fun TextFieldScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "TextField",
            "BasicTextField + Material wrappers. Click to focus, type, drag to select, ⌘C/⌘V/⌘Z.",
        )

        var single by remember { mutableStateOf("") }
        // Pre-filled so the floating label (and the border cutout behind it —
        // the ClipOp.Difference path in the SDL canvas) is visible at rest.
        var outlined by remember { mutableStateOf("user@example.com") }
        var withError by remember { mutableStateOf("abc") }
        var multi by remember { mutableStateOf("Hello\nMulti-line text\nReturn to add a line\nUp / Down to navigate") }
        var raw by remember { mutableStateOf("BasicTextField (no chrome)") }

        Section("Material TextField (filled)", "label, placeholder, supportingText all wired") {
            TextField(
                value = single,
                onValueChange = { single = it },
                label = { Text("Name") },
                placeholder = { Text("Type your name…") },
                supportingText = { Text("Click, drag-select, ⌘C / ⌘V / ⌘Z") },
                modifier = Modifier.width(320.dp),
            )
        }

        Section("OutlinedTextField") {
            OutlinedTextField(
                value = outlined,
                onValueChange = { outlined = it },
                label = { Text("Email") },
                placeholder = { Text("user@example.com") },
                modifier = Modifier.width(320.dp),
            )
        }

        Section("Error state", "isError = true turns border, label, cursor, supporting text red") {
            TextField(
                value = withError,
                onValueChange = { withError = it },
                label = { Text("Password") },
                isError = true,
                supportingText = { Text("Too short") },
                modifier = Modifier.width(320.dp),
            )
        }

        Section("Multi-line", "Return inserts \\n, Up/Down navigates rows, field grows to fit") {
            OutlinedTextField(
                value = multi,
                onValueChange = { multi = it },
                label = { Text("Bio") },
                modifier = Modifier.width(420.dp),
            )
        }

        var soft by remember { mutableStateOf(
            "This text auto-wraps at the field width. Resize the field by changing its " +
            "Modifier.width and the wrap recalculates. Click anywhere to position the " +
            "cursor; Up / Down move between wrapped lines while preserving the preferred " +
            "x-column. Selection rectangles span all wrapped rows."
        ) }
        Section("Soft-wrap", "Long text wraps at word boundaries — field grows vertically to fit") {
            OutlinedTextField(
                value = soft,
                onValueChange = { soft = it },
                label = { Text("Long form") },
                modifier = Modifier.width(320.dp),
            )
        }

        var oneLine by remember { mutableStateOf("singleLine = true; Return does nothing, no wrap") }
        Section("singleLine = true", "Return is suppressed; wrap is disabled — text overflows past the field width") {
            TextField(
                value = oneLine,
                onValueChange = { oneLine = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.width(280.dp),
            )
        }

        Section("Raw BasicTextField", "No chrome — bare cursor + text") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                modifier = Modifier.width(320.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    BasicTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Section("SecureTextField / OutlinedSecureTextField", "Material 3 masked password fields (TextFieldState based)") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SecureTextField(
                    state = rememberTextFieldState(),
                    label = { Text("Password") },
                    modifier = Modifier.width(320.dp),
                )
                OutlinedSecureTextField(
                    state = rememberTextFieldState(),
                    label = { Text("Password (outlined)") },
                    modifier = Modifier.width(320.dp),
                )
            }
        }
    }
}
