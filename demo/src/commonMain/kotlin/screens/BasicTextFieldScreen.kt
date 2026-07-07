@file:OptIn(ExperimentalFoundationApi::class)

package screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Foundation — the undecorated editable text primitive below material3's
// TextField / OutlinedTextField, in both the value-based and the newer
// state-based (TextFieldState) forms, plus the masked secure variant.
@Composable
internal fun BasicTextFieldScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("BasicTextField", "BasicTextField (value + state), BasicSecureTextField.")

		Section("BasicTextField — value based", "The classic value / onValueChange form") {
			var value by remember { mutableStateOf("Type here…") }
			Field {
				BasicTextField(
					value = value,
					onValueChange = { value = it },
					textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
					modifier = Modifier.fillMaxWidth(),
				)
			}
			Text("value = \"$value\"", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
		}

		Section("BasicTextField — state based", "The newer TextFieldState form (rememberTextFieldState)") {
			val state = rememberTextFieldState("Edit me")
			Field {
				BasicTextField(
					state = state,
					textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}

		Section("BasicSecureTextField", "Foundation-level password field (masked input)") {
			Field {
				BasicSecureTextField(
					state = rememberTextFieldState(),
					textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}
	}
}

// A minimal surface so the undecorated fields are visible / clickable.
@Composable
private fun Field(content: @Composable () -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
			.padding(10.dp),
	) { content() }
}
