import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.compose.sdl.nativeComposeWindow
import utils.encodeBmpBgra32
import utils.writeFile

// ==================
// MARK: Animation probes - shared transitions, AnimatedVisibility, dialog entry.
// ==================
//
// Split out of MainNative.kt, which held the entry point plus 20 scenarios in
// 1240 lines. Behaviour is unchanged; each probe is `internal` so main()'s
// argument dispatch still reaches it.

/** Drives a SharedTransitionLayout shared-element morph both directions from
   the frame counter (no clicks) - it requires the LOOKAHEAD pass, which dies
   with "LookaheadDelegate has not been measured yet" if the owner drops
   affectsLookahead measure/relayout requests. Reaching the end frame = PASS. */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
internal fun runSharedTest() {
    val vBig = mutableStateOf(false)
    nativeComposeWindow(
        title = "sharedtest",
        width = 300,
        height = 300,
        onFrame = { _, vFrame ->
            when (vFrame) {
                20 -> { vBig.value = true; true }    // small → big morph
                70 -> { vBig.value = false; true }   // big → small morph
                130 -> {
                    println("sharedtest: PASS (shared-element morph ran both directions)")
                    false
                }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.animation.SharedTransitionLayout {
                Column {
                    androidx.compose.animation.AnimatedVisibility(visible = !vBig.value) {
                        Box(
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "box"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                )
                                .size(48.dp)
                                .background(Color(0xFF7C4DFF), RoundedCornerShape(8.dp)),
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = vBig.value) {
                        Box(
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "box"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                )
                                .size(160.dp)
                                .background(Color(0xFF26A69A), RoundedCornerShape(24.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** Frame-by-frame trace of AnimatedVisibility(fadeIn+expandVertically / fadeOut+
   shrinkVertically): logs the AV container's animated size and the window-Y of a
   marker Box below it every frame, toggling visibility three times. Diagnoses
   (a) a size snap between just-before-end and end of the animation and (b)
   instant (non-animated) transitions on subsequent toggles.

   Expected healthy trace: ~24 smooth frames per toggle ending exactly at 0/60,
   PLUS one 16px marker jump when the fully-shrunk node unmounts (exit end) or
   mounts (enter start) - that's the parent's spacedBy(16) collapsing, inherent
   upstream behaviour (spacing applies to zero-height children too), NOT a bug. */
internal fun runAnimVisTest() {
    val vShown = mutableStateOf(true)
    var vAvSize = androidx.compose.ui.unit.IntSize(-1, -1)
    var vMarkerY = -1f
    var vLastLog = ""
    nativeComposeWindow(
        title = "animvistest",
        width = 600,
        height = 500,
        onFrame = { _, vFrame ->
            // Log only when something moved - keeps the trace readable.
            val vLine = "size=$vAvSize markerY=$vMarkerY shown=${vShown.value}"
            if (vLine != vLastLog) {
                println("animvistest: f=$vFrame $vLine")
                vLastLog = vLine
            }
            when (vFrame) {
                60 -> { println("animvistest: === HIDE 1 ==="); vShown.value = false; true }
                140 -> { println("animvistest: === SHOW 2 ==="); vShown.value = true; true }
                220 -> { println("animvistest: === HIDE 3 ==="); vShown.value = false; true }
                300 -> false
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            // spacedBy mirrors the demo's Section column - the end-of-exit jump
            // reported on the FoundationExtra screen involves the spacing around
            // the AnimatedVisibility node collapsing when the node unmounts.
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(30.dp).background(androidx.compose.ui.graphics.Color(0xFFC07040)))
                androidx.compose.animation.AnimatedVisibility(
                    visible = vShown.value,
                    modifier = Modifier.onSizeChanged { vAvSize = it },
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFF7040C0)),
                    ) {
                        Text("Animated content", modifier = Modifier.padding(14.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF40C070))
                        .onGloballyPositioned { vMarkerY = it.positionInRoot().y },
                )
            }
        }
    }
}

/** Boots a window whose full-size surface opens an m3 AlertDialog on click (the real
   material3 → ui.window.Dialog path, default DialogProperties ⇒ animateTransition on),
   injects the click, then dumps screenshots during the 0.2s appearance animation and
   after it settles; then injects Escape and dumps mid-disappearance (0.1s reverse,
   via the popup host's exit deferral) + after. Visual check: mid shots must show the
   dialog semi-transparent, slightly scaled-down and shifted down vs the settled shot,
   and exit_end must show no dialog at all - skiko-parity animation both ways. */
internal fun runDialogAnimTest() {
    fun snap(inBridge: com.compose.sdl.RenderBackend, inName: String) {
        val vSnap = inBridge.snapshotBgra() ?: return
        val (vW, vH, vBgra) = vSnap
        writeFile(inName, encodeBmpBgra32(vW, vH, vBgra))
        println("dialoganimtest: wrote $inName")
    }
    nativeComposeWindow(
        title = "dialoganimtest",
        width = 1000,
        height = 700,
        onFrame = { vBridge, vFrame ->
            when (vFrame) {
                30 -> { com.compose.sdl.injectMouseEvent(1, 500f, 350f); true }
                32 -> { com.compose.sdl.injectMouseEvent(2, 500f, 350f); true }
                36 -> { snap(vBridge, "dialog_mid1.bmp"); true }
                40 -> { snap(vBridge, "dialog_mid2.bmp"); true }
                90 -> { snap(vBridge, "dialog_end.bmp"); true }
                92 -> {
                    com.compose.sdl.injectKey(41, true)   // Escape → dismiss
                    com.compose.sdl.injectKey(41, false)
                    true
                }
                97 -> { snap(vBridge, "dialog_exit_mid.bmp"); true }
                140 -> { snap(vBridge, "dialog_exit_end.bmp"); false }
                else -> true
            }
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            var vShow by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { vShow = true },
            )
            if (vShow) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { vShow = false },
                    title = { Text("Animated dialog") },
                    text = { Text("Appearance animation parity with the JVM (skiko) Dialog.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { vShow = false }) { Text("OK") }
                    },
                )
            }
        }
    }
}
