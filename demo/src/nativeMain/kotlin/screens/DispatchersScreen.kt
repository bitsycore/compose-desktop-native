package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DispatchersScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Dispatchers",
            "nativeComposeWindow installs an SDL3-driven Dispatchers.Main on startup. " +
                "withContext(Dispatchers.Main) posts back onto the frame loop, so app code can " +
                "background work via Default / IO and update UI state portably.",
        )

        Section(
            "Background fetch + Main hop",
            "Launches a coroutine on the screen's CoroutineScope, moves to Dispatchers.Default " +
                "to simulate I/O, then withContext(Dispatchers.Main) writes the result back. " +
                "The animated spinner only renders while loading is true.",
        ) {
            val vScope = rememberCoroutineScope()
            var vLoading by remember { mutableStateOf(false) }
            var vResult by remember { mutableStateOf("(no result yet)") }
            var vTickCount by remember { mutableStateOf(0) }

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        if (vLoading) return@Button
                        vLoading = true
                        vResult = "loading…"
                        vScope.launch(Dispatchers.Main) {
                            // We start on Main (the dispatcher this window installs).
                            val vData = withContext(Dispatchers.Default) {
                                // Now on a Default-pool worker. delay() suspends
                                // without blocking the main thread.
                                delay(1_500)
                                "fetched on ${threadHint()} after 1.5s"
                            }
                            // Back on Main here — state writes are visible to
                            // the next composition.
                            withContext(Dispatchers.Main) {
                                vResult = vData
                                vTickCount += 1
                                vLoading = false
                            }
                        }
                    },
                ) { Text("Load (simulated 1.5 s)", color = MaterialTheme.colorScheme.onPrimary) }

                if (vLoading) CircularProgressIndicator()

                Text("Result: $vResult", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(0.dp))
            Text("Loads completed: $vTickCount",
                 fontSize = 12.sp,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        Section(
            "Many concurrent jobs",
            "Spawn 5 coroutines at once, each delaying a different duration and posting back " +
                "to Main. UI stays interactive (the spinner keeps animating) because Main only " +
                "runs the post-back portions briefly, not the delays.",
        ) {
            val vScope = rememberCoroutineScope()
            var vCount by remember { mutableStateOf(0) }
            var vRunning by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = {
                        if (vRunning) return@OutlinedButton
                        vRunning = true
                        vCount = 0
                        repeat(5) { vIdx ->
                            vScope.launch(Dispatchers.Main) {
                                withContext(Dispatchers.Default) {
                                    delay(((vIdx + 1) * 300L))
                                }
                                vCount += 1
                                if (vCount == 5) vRunning = false
                            }
                        }
                    },
                ) { Text("Spawn 5", color = MaterialTheme.colorScheme.primary) }

                if (vRunning) CircularProgressIndicator()
                Text("Completed: $vCount / 5", fontSize = 13.sp)
            }
        }

        Section(
            "Animated CircularProgressIndicator (indeterminate)",
            "The no-progress overload spins forever via a LaunchedEffect that ticks every ~16 ms.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("default 36 dp", fontSize = 12.sp)
            }
        }
    }
}

/** Tiny helper to label which thread we're observing — K/N doesn't expose
   Thread.currentThread() in commonMain. We just attach the current
   monotonic timestamp so the printed string differs each call. */
private fun threadHint(): String {
    // No portable thread name on K/N commonMain; use a frame-time-ish label.
    return "Default worker @ ${kotlin.time.TimeSource.Monotonic.markNow()}"
}
