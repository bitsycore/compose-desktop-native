package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.compose.sdl.layout.intOffset
import com.compose.sdl.window.LocalPopupHost
import com.compose.sdl.window.PopupOutsideDismiss

// ==================
// MARK: Popup — native actuals for the vendored expect
// ==================

/*
 Native actuals for the vendored upstream Popup.kt (expect class PopupProperties +
 expect fun Popup). This desktop/SDL renderer hosts overlay content at the composition
 root via the project's PopupHostState (no OS popup window). Behaviour flags on
 PopupProperties are accepted for source-compat; outside-click dismissal / modality are
 the caller's responsibility (Dialog draws a scrim, DropdownMenu installs a click-catcher).

 Lives in :ui (not :foundation) — positioning goes through androidx.compose.ui.layout.Layout
 directly instead of Box + Modifier.fillMaxSize + Modifier.offset, so this pair stays
 in the module its package name suggests.
*/

actual class PopupProperties {

	actual val focusable: Boolean
	actual val dismissOnBackPress: Boolean
	actual val dismissOnClickOutside: Boolean
	actual val clippingEnabled: Boolean
	actual val usePlatformDefaultWidth: Boolean

	actual constructor(
		focusable: Boolean,
		dismissOnBackPress: Boolean,
		dismissOnClickOutside: Boolean,
		clippingEnabled: Boolean,
		usePlatformDefaultWidth: Boolean,
	) {
		this.focusable = focusable
		this.dismissOnBackPress = dismissOnBackPress
		this.dismissOnClickOutside = dismissOnClickOutside
		this.clippingEnabled = clippingEnabled
		this.usePlatformDefaultWidth = usePlatformDefaultWidth
	}

	@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
	actual constructor(
		focusable: Boolean,
		dismissOnBackPress: Boolean,
		dismissOnClickOutside: Boolean,
		clippingEnabled: Boolean,
	) : this(focusable, dismissOnBackPress, dismissOnClickOutside, clippingEnabled, usePlatformDefaultWidth = false)

	// Skiko-shape overload material3's Menu.skiko.kt / ModalBottomSheet.skiko.kt pass
	// (usePlatformInsets / useSoftwareKeyboardInset / scrimColor / onKeyEvent /
	// animateTransition / …). Accept-and-ignore — this desktop renderer has no
	// OS window insets, no soft keyboard, no scrim animation.
	@Suppress("unused")
	constructor(
		focusable: Boolean = false,
		dismissOnBackPress: Boolean = true,
		dismissOnClickOutside: Boolean = true,
		clippingEnabled: Boolean = true,
		usePlatformDefaultWidth: Boolean = false,
		usePlatformInsets: Boolean = true,
		useSoftwareKeyboardInset: Boolean = true,
		scrimColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
		animateTransition: Boolean = false,
	) : this(focusable, dismissOnBackPress, dismissOnClickOutside, clippingEnabled, usePlatformDefaultWidth)

	override fun equals(other: Any?): Boolean =
		other is PopupProperties &&
			focusable == other.focusable &&
			dismissOnBackPress == other.dismissOnBackPress &&
			dismissOnClickOutside == other.dismissOnClickOutside &&
			clippingEnabled == other.clippingEnabled &&
			usePlatformDefaultWidth == other.usePlatformDefaultWidth

	override fun hashCode(): Int {
		var result = focusable.hashCode()
		result = 31 * result + dismissOnBackPress.hashCode()
		result = 31 * result + dismissOnClickOutside.hashCode()
		result = 31 * result + clippingEnabled.hashCode()
		result = 31 * result + usePlatformDefaultWidth.hashCode()
		return result
	}
}

/* Overlay at the window root, positioned by [alignment] + [offset] (in PIXELS). */
@Composable
actual fun Popup(
	alignment: Alignment,
	offset: IntOffset,
	onDismissRequest: (() -> Unit)?,
	properties: PopupProperties,
	content: @Composable () -> Unit,
) {
	val vHost = LocalPopupHost.current
	val vId = remember { Any() }
	// ESC (→ back navigation) dismisses the popup, matching upstream desktop's
	// dismissOnBackPress behaviour (open DropdownMenus close on Escape).
	if (properties.dismissOnBackPress && onDismissRequest != null) {
		@Suppress("DEPRECATION")
		@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
		androidx.compose.ui.backhandler.BackHandler(enabled = true, onBack = onDismissRequest)
	}
	// Snapshot the CompositionLocals at the call site so the hosted content (rendered at
	// the composition root by PopupLayer) still sees MaterialTheme + app locals.
	val vLocals = currentCompositionLocalContext
	// alignment=TopStart + offset=(0,0) short-circuits to raw content; anything
	// else goes through the alignment-and-offset Layout below.
	val vPositioned: @Composable () -> Unit =
		if (alignment == Alignment.TopStart && offset.x == 0 && offset.y == 0) {
			content
		} else {
			{ AlignedOffsetLayout(alignment, offset, content) }
		}
	SideEffect {
		vHost.upsert(vId) {
			CompositionLocalProvider(vLocals) { vPositioned() }
		}
	}
	DisposableEffect(Unit) {
		onDispose { vHost.remove(vId) }
	}
}

/* Fills the window (constraint.max) and places `content` at `alignment` +
   pixel-space `offset`. Replaces the old `Box(Modifier.fillMaxSize()) { Box(offset {...}) { content } }`
   with a single Layout so no androidx.compose.foundation.layout deps are pulled. */
@Composable
private fun AlignedOffsetLayout(
	alignment: Alignment,
	offset: IntOffset,
	content: @Composable () -> Unit,
) {
	Layout(content) { measurables, constraints ->
		// Measure the child with the window's constraints relaxed on the min side
		// (Popup content shouldn't be forced to fill; it should be its intrinsic size).
		val vChildConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
		val vChildren = measurables.map { it.measure(vChildConstraints) }
		val vChildW = vChildren.maxOfOrNull { it.width } ?: 0
		val vChildH = vChildren.maxOfOrNull { it.height } ?: 0
		layout(constraints.maxWidth, constraints.maxHeight) {
			val vAlignOffset = alignment.align(
				IntSize(vChildW, vChildH),
				IntSize(constraints.maxWidth, constraints.maxHeight),
				layoutDirection,
			)
			val vX = vAlignOffset.x + offset.x
			val vY = vAlignOffset.y + offset.y
			vChildren.forEach { it.place(vX, vY) }
		}
	}
}

// Extra Popup overload with an `onKeyEvent` param, called by vendored
// `material3/SkikoMenu.skiko.kt` to intercept arrow keys inside a dropdown.
// Not part of official Compose common API; skiko-only. We ignore the handler
// (SDL keyboard focus flows are TBD).
@androidx.compose.runtime.Composable
fun Popup(
	popupPositionProvider: PopupPositionProvider,
	onDismissRequest: (() -> Unit)?,
	properties: PopupProperties = PopupProperties(),
	onKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
	content: @Composable () -> Unit,
) {
	Popup(popupPositionProvider, onDismissRequest, properties, content)
}

/* Position-provider overload — the path upstream Tooltip / Menu / ExposedDropdown
   components use. Positions the hosted content by asking [popupPositionProvider]
   for an offset, feeding it the REAL anchor bounds (captured by the zero-size probe
   below, whose parent layout is the popup's anchor) and the measured content size.
   Reading the anchor-bounds state inside the host's placement block re-positions the
   popup once the probe reports the anchor after the first layout pass. */
@Composable
actual fun Popup(
	popupPositionProvider: PopupPositionProvider,
	onDismissRequest: (() -> Unit)?,
	properties: PopupProperties,
	content: @Composable () -> Unit,
) {
	val vHost = LocalPopupHost.current
	val vId = remember { Any() }
	// ESC dismisses, matching the alignment overload / upstream dismissOnBackPress.
	if (properties.dismissOnBackPress && onDismissRequest != null) {
		@Suppress("DEPRECATION")
		@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
		androidx.compose.ui.backhandler.BackHandler(enabled = true, onBack = onDismissRequest)
	}
	val vLocals = currentCompositionLocalContext

	// The anchor's window-space rect. Empty until the probe fills it after the first
	// layout; the hosted content re-positions reactively when it does.
	val vAnchorBounds = remember { mutableStateOf(IntRect(IntOffset.Zero, IntSize.Zero)) }
	// The content's placed window rect, reported by the host layout — used to
	// dismiss the popup on a press OUTSIDE it (material3 DropdownMenu / menus rely
	// on the platform Popup honouring dismissOnClickOutside).
	val vPlaced = remember { mutableStateOf(IntRect(IntOffset.Zero, IntSize.Zero)) }

	// Invisible zero-size probe rendered at the call site: its PARENT layout node is
	// the popup's anchor (e.g. BasicTooltipBox's wrapper around the anchor content),
	// so parentLayoutCoordinates gives the anchor bounds in window pixels — the same
	// coordinate space PopupPositionProvider expects.
	Layout(
		content = {},
		modifier = Modifier.onGloballyPositioned { coords ->
			val vParent = coords.parentLayoutCoordinates
			val vBounds =
				if (vParent != null && vParent.isAttached) IntRect(vParent.intOffset, vParent.size)
				else IntRect(IntOffset.Zero, IntSize.Zero)
			if (vBounds != vAnchorBounds.value) vAnchorBounds.value = vBounds
		},
	) { _, _ -> layout(0, 0) {} }

	// Host the content at the composition root, positioned by the provider.
	SideEffect {
		vHost.upsert(vId) {
			CompositionLocalProvider(vLocals) {
				ProviderPositionedLayout(popupPositionProvider, vAnchorBounds, content) { vRect ->
					if (vRect != vPlaced.value) vPlaced.value = vRect
				}
				if (properties.dismissOnClickOutside && onDismissRequest != null) {
					val vRect = vPlaced.value
					PopupOutsideDismiss(vRect.left, vRect.top, vRect.width, vRect.height, onDismissRequest)
				}
			}
		}
	}
	DisposableEffect(Unit) {
		onDispose { vHost.remove(vId) }
	}
}

/* Fills the window and places the popup content at the offset [provider] computes
   from the live [anchorBounds], the window size, and the measured content size.
   Reading [anchorBounds] inside the placement block subscribes this layout to
   re-placement when the probe reports the anchor's bounds. */
@Composable
private fun ProviderPositionedLayout(
	provider: PopupPositionProvider,
	anchorBounds: State<IntRect>,
	content: @Composable () -> Unit,
	onPlaced: (IntRect) -> Unit,
) {
	Layout(content) { measurables, constraints ->
		val vChildConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
		val vChildren = measurables.map { it.measure(vChildConstraints) }
		val vChildW = vChildren.maxOfOrNull { it.width } ?: 0
		val vChildH = vChildren.maxOfOrNull { it.height } ?: 0
		layout(constraints.maxWidth, constraints.maxHeight) {
			val vOffset = provider.calculatePosition(
				anchorBounds = anchorBounds.value,
				windowSize = IntSize(constraints.maxWidth, constraints.maxHeight),
				layoutDirection = layoutDirection,
				popupContentSize = IntSize(vChildW, vChildH),
			)
			onPlaced(IntRect(vOffset, IntSize(vChildW, vChildH)))
			vChildren.forEach { it.place(vOffset.x, vOffset.y) }
		}
	}
}
