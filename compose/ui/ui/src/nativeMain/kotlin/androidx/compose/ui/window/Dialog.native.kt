package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.SkikoComposeUiFlags
import androidx.compose.ui.animation.easeOutTimingFunction
import androidx.compose.ui.animation.withAnimationProgress
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.compose.sdl.window.LocalPopupExitHandle
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

// ==================
// MARK: Dialog — native actuals for vendored ui.window.Dialog expects
// ==================

/*
 Native actuals for the vendored upstream `ui.window.Dialog.kt`
 (`expect class DialogProperties` + `expect fun Dialog`). Renderer has no OS
 dialog window; we route Dialog through the project's Popup host (same
 mechanism DropdownMenu / material Dialog use) and draw a semi-transparent
 scrim behind the content for modality.

 dismissOnBackPress / dismissOnClickOutside: `dismissOnClickOutside` is
 wired — the scrim itself intercepts clicks and calls `onDismissRequest`.
 `dismissOnBackPress` registers a BackHandler on the window's
 NavigationEventDispatcher — an unconsumed ESC completes a back navigation
 (ComposeWindow's BackNavigationInput), which dismisses the topmost dialog.
 usePlatformDefaultWidth is accepted but has no effect (there is no platform
 default width on desktop).

 Appearance/disappearance animations mirror upstream `Dialog.skiko.kt`'s
 DialogAppearanceController: when `animateTransition` is on (default =
 SkikoComposeUiFlags.isDialogAnimationEnabled, true — same flag upstream
 desktop uses), the dialog content fades 0.2→1 alpha, scales 0.95→1 and
 slides up 10dp over a 0.2s ease-out, with the scrim alpha following the
 same curve. On dismissal the curve plays back over 0.1s (scaled by how far
 the appearance got). Upstream keeps its ComposeSceneLayer alive after the
 owner's composition leaves to animate out; here the equivalent is the popup
 host's exit deferral — the hosted content (which lives in the HOST
 composition, so it survives the caller disposing the Dialog) registers a
 PopupExitHandle, observes `isExiting`, animates, then finish()es the entry.

 Lives in :ui (not :foundation) — the whole ui.window pair is :ui-only, using
 Layout + drawBehind for the scrim instead of Box + Modifier.background.
*/

// Upstream Dialog.skiko.kt animation/scrim constants, kept verbatim so the
// native port matches the JVM (skiko) look and timing.
private const val kDefaultScrimOpacity = 0.6f
private val kDefaultScrimColor = Color.Black.copy(alpha = kDefaultScrimOpacity)
private const val kAnimatedLayerOffsetDp = 10f
private const val kAnimatedLayerInitialAlpha = 0.2f
private const val kAnimatedLayerScale = 0.05f
private const val kAppearanceDurationSeconds = 0.2
private const val kDisappearanceDurationSeconds = 0.1

/* Alpha ramp shared by the dialog content and the scrim — upstream's
   `contentAlpha(progress)`: starts at 0.2, ends at 1. */
private fun dialogContentAlpha(inProgress: Float): Float =
	kAnimatedLayerInitialAlpha + (1f - kAnimatedLayerInitialAlpha) * inProgress

// Skiko's DialogProperties carries extra fields (usePlatformInsets /
// useSoftwareKeyboardInset / scrimColor / animateTransition) that vendored
// material3 file `internal/BasicEdgeToEdgeDialog.skiko.kt` passes explicitly.
// scrimColor + animateTransition are honoured (scrim fill / appearance
// animation); the two insets flags are accept-and-ignore — no OS window
// insets, no soft keyboard on this desktop renderer.
//
// NB: NOT marked @ExperimentalComposeUiApi. The expect `DialogProperties` in
// commonMain isn't marked experimental (see ui/src/vendor/…/Dialog.kt), and
// annotating the actual with an experimental marker cascades opt-in to every
// widget that has `properties: DialogProperties = DialogProperties()` as a
// param default — notably m3's AlertDialog, whose default value on the expect
// signature transitively requires opt-in of ExperimentalComposeUiApi at every
// call site. Users would see "This API is experimental" pointed at their own
// `AlertDialog(...)` line even though upstream AlertDialog isn't experimental.
actual class DialogProperties actual constructor(
	actual val dismissOnBackPress: Boolean,
	actual val dismissOnClickOutside: Boolean,
	actual val usePlatformDefaultWidth: Boolean,
) {
	/* Scrim fill colour — upstream skiko default: 60% black. */
	var scrimColor: Color = kDefaultScrimColor
		private set

	/* Whether the dialog appearance animates — upstream default is the
	   SkikoComposeUiFlags.isDialogAnimationEnabled flag (true). */
	var animateTransition: Boolean = SkikoComposeUiFlags.isDialogAnimationEnabled
		private set

	@Suppress("unused")
	constructor(
		dismissOnBackPress: Boolean = true,
		dismissOnClickOutside: Boolean = true,
		usePlatformDefaultWidth: Boolean = true,
		usePlatformInsets: Boolean = true,
		useSoftwareKeyboardInset: Boolean = true,
		scrimColor: Color = kDefaultScrimColor,
		animateTransition: Boolean = SkikoComposeUiFlags.isDialogAnimationEnabled,
	) : this(dismissOnBackPress, dismissOnClickOutside, usePlatformDefaultWidth) {
		this.scrimColor = scrimColor
		this.animateTransition = animateTransition
	}
}

@Composable
actual fun Dialog(
	onDismissRequest: () -> Unit,
	properties: DialogProperties,
	content: @Composable () -> Unit,
) {
	// ESC (→ back navigation) dismisses the dialog. Registered here — not only
	// in Popup — because a dialog with dismissOnClickOutside=false passes a
	// null onDismissRequest to the popup but must still honour back-press.
	// Composition order makes the most recently opened dialog the topmost
	// enabled handler, so nested dialogs dismiss innermost-first.
	if (properties.dismissOnBackPress) {
		@Suppress("DEPRECATION")
		@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
		androidx.compose.ui.backhandler.BackHandler(enabled = true, onBack = onDismissRequest)
	}
	Popup(
		alignment = Alignment.Center,
		offset = IntOffset(0, 0),
		onDismissRequest = if (properties.dismissOnClickOutside) onDismissRequest else null,
		properties = PopupProperties(
			focusable = true,
			dismissOnBackPress = properties.dismissOnBackPress,
			dismissOnClickOutside = properties.dismissOnClickOutside,
			clippingEnabled = false,
			usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
		),
	) {
		// ============
		//  Appearance / disappearance animation
		// 0 → 1 over 0.2s ease-out on show, back → 0 over 0.1s on dismissal
		// (upstream DialogAppearanceController). Progress reads inside
		// graphicsLayer / drawBehind blocks only invalidate the layer /
		// redraw — no relayout per frame.
		var vProgress by remember {
			mutableFloatStateOf(if (properties.animateTransition) 0f else 1f)
		}
		// Exit deferral: this content composes in the popup HOST's composition,
		// so it survives the caller disposing the Dialog — the host flips
		// isExiting instead of removing the entry (see PopupExitHandle).
		val vExitHandle = LocalPopupExitHandle.current
		val vExiting = properties.animateTransition &&
			vExitHandle != null && vExitHandle.isExiting.value
		if (properties.animateTransition && vExitHandle != null) {
			SideEffect { vExitHandle.enableExitTransition() }
		}
		if (properties.animateTransition && !vExiting) {
			LaunchedEffect(Unit) {
				val vDurationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
				withAnimationProgress(
					duration = (vDurationScale * kAppearanceDurationSeconds).seconds,
					timingFunction = ::easeOutTimingFunction,
				) { inP -> vProgress = inP }
				vProgress = 1f
			}
		}
		if (vExiting) {
			LaunchedEffect(Unit) {
				val vDurationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
				// Play back from wherever the appearance got to — upstream scales
				// the 0.1s by the initial progress too.
				val vInitial = vProgress
				try {
					withAnimationProgress(
						duration = (vDurationScale * vInitial * kDisappearanceDurationSeconds).seconds,
						timingFunction = ::easeOutTimingFunction,
					) { inP -> vProgress = (1f - inP) * vInitial }
				} finally {
					// Guaranteed removal even if the animation is cancelled
					// (e.g. the whole window tears down mid-exit).
					vExitHandle!!.finish()
				}
			}
		}

		// Scrim + centering, expressed as a single Layout that fills the window
		// constraints, draws the scrim in the background, and centers the
		// content. Replaces the old Box(fillMaxSize().background()) — Box
		// and Modifier.background both live in :foundation, but ui.window has
		// no business pulling those in, so we do the same via ui-only
		// primitives (Layout + drawBehind).
		Layout(
			content = {
				// Inner wrapper so the animated layer applies to the dialog
				// content only — the scrim must not scale/slide with it.
				Layout(
					content = content,
					modifier = Modifier.graphicsLayer {
						val vReversed = 1f - vProgress
						alpha = dialogContentAlpha(vProgress)
						val vScale = 1f - vReversed * kAnimatedLayerScale
						scaleX = vScale
						scaleY = vScale
						translationY = kAnimatedLayerOffsetDp * vReversed * density
					},
				) { measurables, constraints ->
					val vChildren = measurables.map { it.measure(constraints) }
					val vW = vChildren.maxOfOrNull { it.width } ?: 0
					val vH = vChildren.maxOfOrNull { it.height } ?: 0
					layout(vW, vH) { vChildren.forEach { it.place(0, 0) } }
				}
			},
			modifier = Modifier.drawBehind {
				val vSize = size
				val vScrim = properties.scrimColor
				drawContext.canvas.drawRect(
					left = 0f, top = 0f, right = vSize.width, bottom = vSize.height,
					paint = Paint().apply {
						color = vScrim.copy(alpha = vScrim.alpha * dialogContentAlpha(vProgress))
					},
				)
			},
		) { measurables, constraints ->
			val vChildConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
			val vChildren = measurables.map { it.measure(vChildConstraints) }
			val vChildW = vChildren.maxOfOrNull { it.width } ?: 0
			val vChildH = vChildren.maxOfOrNull { it.height } ?: 0
			val vLayoutW = constraints.maxWidth
			val vLayoutH = constraints.maxHeight
			layout(vLayoutW, vLayoutH) {
				val vX = (vLayoutW - vChildW) / 2
				val vY = (vLayoutH - vChildH) / 2
				vChildren.forEach { it.place(vX, vY) }
			}
		}
	}
}

// Suppress unused-import warnings — these types (Rect, Size, IntSize) are
// referenced through drawContext.canvas.drawRect / size which use them
// implicitly at call sites the compiler picks up via receivers.
@Suppress("unused") private val vSuppressUnused: Any = listOf(Rect.Zero, Size.Zero, IntSize.Zero)
