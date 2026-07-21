package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.*

// ==================
// MARK: Desktop integration (SDL3 / platform)
// ==================

/** OS integration the SDL3 backend exposes and upstream Compose Multiplatform
   does not: open URLs / files with the default handler, reveal a folder in the
   platform file manager, resolve the per-user app-data directory, and register
   in-memory resources at runtime. */
@Composable
internal fun DesktopIntegrationScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Desktop integration", "openUrl · revealInFileManager · appDataDir · in-memory resources.")

		Section("openUrl", "Hands a URL / file:// URI to the OS default handler (browser, …)") {
			Button(onClick = { openUrl("https://github.com/bitsycore/compose-desktop-native") }) {
				Text("Open project page")
			}
		}

		val dataDir = remember { appDataDir("ComposeDesktopNative", "Demo") }
		Section("appDataDir", "Per-user writable directory (SDL_GetPrefPath)") {
			Text(dataDir ?: "(unavailable)", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
			if (dataDir != null) {
				OutlinedButton(onClick = { revealInFileManager(dataDir) }) {
					Text("Reveal in ${fileManagerName()}")
				}
			}
		}

		// In-memory resources: register bytes under a key, then read them back
		// through the same loader the bundled data.kres entries use.
		var memoryState by remember { mutableStateOf("not registered") }
		Section("registerMemoryResource / removeMemoryResource", "Runtime-registered resources, resolved by the resource loader") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Button(onClick = {
					registerMemoryResource(kMemoryKey, "hello from memory".encodeToByteArray())
					val loaded = loadComposeResourceBytes(kMemoryKey)?.decodeToString()
					memoryState = "registered (present=${hasComposeResource(kMemoryKey)}): \"$loaded\""
				}) { Text("Register") }
				OutlinedButton(onClick = {
					removeMemoryResource(kMemoryKey)
					memoryState = "removed (present=${hasComposeResource(kMemoryKey)})"
				}) { Text("Remove") }
			}
			Text(memoryState, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
		}
	}
}

private const val kMemoryKey = "files/memory-demo.txt"
