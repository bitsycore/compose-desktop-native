import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.compose.sdl.ComposeNativeWindow
import com.compose.sdl.nativeComposeWindow
import screens.AdaptiveScreen
import screens.gAdaptiveTwoPane

/**
 `--adaptivetest`: proves material3-adaptive reacts to a LIVE window resize.

 The `--screen=Adaptive --width=…` screenshots only ever prove the fold is right
 for the size the window OPENED at - each run is a fresh process. This drives one
 window through wide → narrow → wide with `setSize`, and asserts the scaffold
 folds and unfolds along the way. That exercises the whole chain: SDL resize →
 the owner's snapshot-backed containerSize → recomposition → `BoxWithConstraints`
 re-measure → a new `PaneScaffoldDirective` on the navigator → a new scaffold value.

 The screen is hosted in the same `verticalScroll` + padding wrapper the demo
 shell uses for its content pane, so the constraints match the real thing
 (notably the infinite max height).
 */
internal fun runAdaptiveTest() {
	var vFrames = 0
	var vFacade: ComposeNativeWindow? = null
	var vWide: Boolean? = null
	var vFolded: Boolean? = null
	var vUnfolded: Boolean? = null

	// Frames to settle between each resize - SDL delivers the size change as an
	// event, so the new constraints only reach layout on a later frame.
	val kSettle = 6

	nativeComposeWindow(
		title = "adaptive probe",
		width = 1400,
		height = 760,
		onFrame = { _, _ ->
			vFrames++
			when (vFrames) {
				kSettle -> {
					vWide = gAdaptiveTwoPane
					vFacade?.setSize(760, 760)
					true
				}
				kSettle * 2 -> {
					vFolded = gAdaptiveTwoPane
					vFacade?.setSize(1400, 760)
					true
				}
				kSettle * 3 -> {
					vUnfolded = gAdaptiveTwoPane
					val vPass = vWide == true && vFolded == false && vUnfolded == true
					println("adaptivetest: wide=$vWide folded=$vFolded unfolded=$vUnfolded")
					println(
						if (vPass) {
							"adaptivetest: PASS (scaffold folded on resize to 760dp " +
								"and unfolded again at 1400dp)"
						} else {
							"adaptivetest: FAIL (expected wide=true folded=false " +
								"unfolded=true)"
						}
					)
					false
				}
				else -> true
			}
		},
	) {
		SideEffect { vFacade = window }
		MaterialTheme(colorScheme = darkColorScheme()) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background)
					.verticalScroll(rememberScrollState())
					.padding(24.dp),
			) {
				AdaptiveScreen()
			}
		}
	}
}
