import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.unit.dp
import sdl3backend.composeWindow

fun main() {
    composeWindow(title = "ComposeNativeSDL3 Demo", width = 800, height = 600) {
        MaterialTheme(colors = darkColors()) {
            App()
        }
    }
}

// ==================
// MARK: Demo App
// ==================

@Composable
fun App() {
    RecompositionLogger("App")

    var counter by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ComposeNativeSDL3",
                color = MaterialTheme.colors.primary,
                fontSize = 32
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Counter: $counter",
                fontSize = 24
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { counter-- }, shape = CircleShape) {
                    Text(text = "-", color = MaterialTheme.colors.onPrimary, fontSize = 20)
                }

                OutlinedButton(onClick = { counter = 0 }) {
                    Text(text = "Reset", color = MaterialTheme.colors.primary)
                }

                Button(onClick = { counter++ }, shape = CircleShape) {
                    Text(text = "+", color = MaterialTheme.colors.onPrimary, fontSize = 20)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { counter += 10 },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "+10 (rounded 12dp)", color = MaterialTheme.colors.onPrimary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { counter -= 10 }) {
                Text(text = "-10 (text button)", color = MaterialTheme.colors.primary)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Click the buttons above!",
                color = Color.Gray,
                fontSize = 14
            )
        }
    }
}
