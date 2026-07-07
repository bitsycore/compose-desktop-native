package screens
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

@Composable
internal fun CounterScreen() {
    var counter by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Counter", "The original +/- demo")
        Section("Click +/- to change the count") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Counter: $counter",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { counter-- },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "-",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(onClick = { counter = 0 }) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { counter++ },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "+",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
