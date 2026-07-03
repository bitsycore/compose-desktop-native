package androidx.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.compose.desktop.native.window.LocalPopupHost

// ==================
// MARK: Popup — native actuals for the vendored expect
// ==================

/*
 Native actuals for the vendored upstream Popup.kt (expect class PopupProperties +
 expect fun Popup). This desktop/SDL renderer hosts overlay content at the composition
 root via the project's PopupHostState (no OS popup window). Behaviour flags on
 PopupProperties are accepted for source-compat; outside-click dismissal / modality are
 the caller's responsibility (Dialog draws a scrim, DropdownMenu installs a click-catcher).
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

/* Overlay at the window root, positioned by [alignment] + [offset] (logical points). */
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
	// Snapshot the CompositionLocals at the call site so the hosted content (rendered at
	// the composition root by PopupLayer) still sees MaterialTheme + app locals.
	val vLocals = currentCompositionLocalContext
	val vPositioned: @Composable () -> Unit =
		if (alignment == Alignment.TopStart && offset.x == 0 && offset.y == 0) {
			content
		} else {
			{
				Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
					Box(modifier = Modifier.offset(offset.x.dp, offset.y.dp)) { content() }
				}
			}
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

/* Position-provider overload — resolves an offset from the provider (best-effort: the
   anchor bounds aren't tracked in this reduced host) and delegates to the offset overload.
   Not exercised by the current widgets, which self-position via the alignment overload. */
@Composable
actual fun Popup(
	popupPositionProvider: PopupPositionProvider,
	onDismissRequest: (() -> Unit)?,
	properties: PopupProperties,
	content: @Composable () -> Unit,
) {
	val vOffset = popupPositionProvider.calculatePosition(
		anchorBounds = IntRect(IntOffset.Zero, IntSize.Zero),
		windowSize = IntSize(
			com.compose.desktop.native.text.currentViewportWidth,
			com.compose.desktop.native.text.currentViewportHeight,
		),
		layoutDirection = LayoutDirection.Ltr,
		popupContentSize = IntSize.Zero,
	)
	Popup(
		alignment = Alignment.TopStart,
		offset = vOffset,
		onDismissRequest = onDismissRequest,
		properties = properties,
		content = content,
	)
}
