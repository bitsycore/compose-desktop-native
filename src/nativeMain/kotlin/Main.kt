import androidx.compose.runtime.*
import compose.foundation.BasicText
import compose.foundation.layout.*
import compose.material.*
import compose.ui.*
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
    var counter by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32),
            verticalArrangement = Arrangement.CenterVertically,
            horizontalAlignment = HorizontalAlignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "ComposeNativeSDL3",
                color = MaterialTheme.colors.primary,
                fontSize = 32
            )

            Spacer(modifier = Modifier.height(24))

            // Counter display
            Text(
                text = "Counter: $counter",
                fontSize = 24
            )

            Spacer(modifier = Modifier.height(16))

            // Buttons row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = VerticalAlignment.CenterVertically
            ) {
                Button(onClick = { counter-- }) {
                    Text(text = "  -  ", color = MaterialTheme.colors.onPrimary)
                }

                Button(onClick = { counter = 0 }) {
                    Text(text = " Reset ", color = MaterialTheme.colors.onPrimary)
                }

                Button(onClick = { counter++ }) {
                    Text(text = "  +  ", color = MaterialTheme.colors.onPrimary)
                }
            }

            Spacer(modifier = Modifier.height(32))

            // Info
            Text(
                text = "Click the buttons above!",
                color = Color.Gray,
                fontSize = 14
            )
        }
    }
}
