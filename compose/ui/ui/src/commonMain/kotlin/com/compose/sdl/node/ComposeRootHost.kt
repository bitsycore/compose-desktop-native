package com.compose.sdl.node

import androidx.compose.runtime.Applier
import com.compose.sdl.node.impl.ComposeOwner
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// ==================
// MARK: ComposeRootHost
// ==================

/*
 Public facade the :window layer drives the upstream layout engine through.
 `LayoutNode` / `Owner` / `ComposeOwner` / `NodeApplier` are all internal to
 :core, so this hides them behind a public surface: [applier] (upcast to
 Applier<*> for the Composition), [attach], [setConstraints],
 [measureAndLayout], plus the input entry points ([onPointerRaw] / [onWheel])
 that feed the vendored PointerInputEventProcessor — the same upstream pipeline
 clickable / hoverable / detectDragGestures / awaitPointerEventScope all use.
 The internal [rootNode] is read by the renderer backend (also in :core) to paint.
*/
class ComposeRootHost(inDensity: Float = 1f) {

	internal val rootNode: LayoutNode = LayoutNode().apply {
		// The root measures its children against the incoming constraints and
		// places them at the origin — upstream's RootMeasurePolicy. Without this
		// the root keeps LayoutNode's ErrorMeasurePolicy ("Undefined measure").
		measurePolicy = androidx.compose.ui.layout.RootMeasurePolicy
	}
	private val fOwner = ComposeOwner(rootNode, Density(inDensity), LayoutDirection.Ltr)
	private val fApplier = NodeApplier(rootNode)

	// Upcast to the public supertype so the internal NodeApplier / LayoutNode
	// don't leak across the module boundary; Composition accepts Applier<*>.
	val applier: Applier<*> get() = fApplier

	fun attach() {
		// Install the focus root + drag-and-drop root on the root LayoutNode
		// (upstream AndroidComposeView does the same via
		// `focusOwner.modifier.then(dragAndDropManager.modifier)`). Without the
		// focus root the focus tree has no origin and requestFocus never
		// sticks; without the DnD root the root DragAndDropNode never receives
		// events from Sdl3DragAndDropOwner's dispatch calls.
		rootNode.modifier = fOwner.focusOwner.modifier.then(fOwner.dragAndDropManager.modifier)
		fOwner.attach()
		// Turn on snapshot observation so writes to `mutableStateOf` (ScrollState.value,
		// LazyList firstVisibleItemIndex, etc.) fire the appropriate invalidation
		// callbacks — requestRelayout / requestRemeasure — routing through the vendored
		// MeasureAndLayoutDelegate. Without this, `ScrollState.value = N` mutates the
		// state but the placement lambda inside ScrollNode.measure never re-runs, so
		// the child layer's move(IntOffset(0, -scroll)) never happens → scroll offset
		// is invisible even though the state is updating.
		fOwner.snapshotObserver.startObserving()
	}

	fun setConstraints(inWidth: Int, inHeight: Int) {
		fOwner.setRootConstraints(Constraints.fixed(inWidth, inHeight))
	}

	// Pump the owner's node-animation frame clock (scroll fling / animateScrollToItem / node
	// Animatables). Called once per frame by ComposeWindow with a monotonic nanos timestamp.
	fun sendAnimationFrame(inNanos: Long) {
		fOwner.animationFrameClock.sendFrame(inNanos)
	}

	fun measureAndLayout() {
		// Flush deferred end-of-apply work (focus invalidation etc.) before measuring, so
		// requestFocus() from composition OR from a pointer event this frame takes effect —
		// e.g. focus-on-click, which schedules a focus invalidation during event dispatch.
		fOwner.onEndApplyChanges()
		fOwner.measureAndLayout()
	}

	// The owner's FocusOwner IS a FocusManager — the window provides it as LocalFocusManager.
	val focusManager: androidx.compose.ui.focus.FocusManager get() = fOwner.focusOwner

	// ==================
	// MARK: Owner-backed values for the composition-locals seed
	// ==================
	// Upstream vendored `CompositionLocals.kt` defaults throw when read without a
	// Provider; ComposeWindow wraps setContent in a CompositionLocalProvider whose
	// values come from the ComposeOwner attached below. These accessors expose the
	// right fields without leaking the `internal Owner` interface across modules.
	val density: androidx.compose.ui.unit.Density get() = fOwner.density
	val layoutDirection: androidx.compose.ui.unit.LayoutDirection get() = fOwner.layoutDirection
	val viewConfiguration: androidx.compose.ui.platform.ViewConfiguration get() = fOwner.viewConfiguration
	val graphicsContext: androidx.compose.ui.graphics.GraphicsContext get() = fOwner.graphicsContext
	val inputModeManager: androidx.compose.ui.input.InputModeManager get() = fOwner.inputModeManager
	val hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback get() = fOwner.hapticFeedBack
	val textToolbar: androidx.compose.ui.platform.TextToolbar get() = fOwner.textToolbar
	val windowInfo: androidx.compose.ui.platform.WindowInfo get() = fOwner.windowInfo
	val softwareKeyboardController: androidx.compose.ui.platform.SoftwareKeyboardController
		get() = fOwner.softwareKeyboardController

	// ==================
	// MARK: Key input — route to the focused node via the FocusOwner
	// ==================

	/* Routes a key event to the focused node's KeyInput chain (upstream onPreKeyEvent /
	   onKeyEvent) — drives text-field editing keys (backspace / arrows / enter) and
	   clickable Enter/Space activation. Returns true if some node consumed it. */
	fun dispatchKeyEvent(inEvent: androidx.compose.ui.input.key.KeyEvent): Boolean =
		runCatching { fOwner.focusOwner.dispatchKeyEvent(inEvent) }.getOrDefault(false)

	// ==================
	// MARK: Input — feed the vendored PointerInputEventProcessor
	// ==================

	// Feed a raw pointer event to the vendored PointerInputEventProcessor (upstream
	// PointerInputModifierNode dispatch — hover / gestures / clickable). inType: 0=Move
	// 1=Press 2=Release; inButton: 0=primary 1=secondary 2=tertiary. The internal
	// `expect PointerInputEvent` has no commonMain constructor, so the actual build+dispatch
	// lives in nativeMain (feedPointerToProcessor).
	fun onPointerRaw(inX: Float, inY: Float, inType: Int, inButton: Int, inUptime: Long) {
		feedPointerToProcessor(fOwner, inType, inButton, inUptime, inX, inY)
	}

	// Mouse wheel — feed a scroll PointerInputEvent to the processor so the vendored
	// Modifier.scrollable (MouseWheelScrollingLogic) handles it, exactly like upstream.
	fun onWheel(inX: Float, inY: Float, inDeltaX: Float, inDeltaY: Float, inUptime: Long) {
		feedScrollToProcessor(fOwner, inX, inY, inDeltaX, inDeltaY, inUptime)
	}

	// ==================
	// MARK: Drag-and-drop — feed SDL_EVENT_DROP_* into the tree
	// ==================
	//
	// The :window loop maps SDL_EVENT_DROP_BEGIN / POSITION / FILE / TEXT /
	// COMPLETE to these methods; Sdl3DragAndDropOwner accumulates the drop
	// session and dispatches through the root DragAndDropNode on COMPLETE.

	fun onDropBegin() = fOwner.dragAndDropManager.dropBegin()
	fun onDropPosition(inX: Float, inY: Float) = fOwner.dragAndDropManager.dropPosition(inX, inY)
	fun onDropFile(inPath: String) = fOwner.dragAndDropManager.dropFile(inPath)
	fun onDropText(inText: String) = fOwner.dragAndDropManager.dropText(inText)
	fun onDropComplete() = fOwner.dragAndDropManager.dropComplete()
}

// ==================
// MARK: pointer-event bridge (nativeMain builds the internal PointerInputEvent)
// ==================

/* The internal `expect PointerInputEvent` exposes no commonMain constructor, so the
   build+dispatch is a nativeMain actual. Constructs a single-pointer event and feeds it
   to the owner's PointerInputEventProcessor. inType: 0=Move 1=Press 2=Release. */
internal expect fun feedPointerToProcessor(
	inOwner: com.compose.sdl.node.impl.ComposeOwner,
	inType: Int,
	inButton: Int,
	inUptime: Long,
	inX: Float,
	inY: Float,
)

/* Builds a Scroll-type PointerInputEvent (scrollDelta) and drives the processor, so the vendored
   Modifier.scrollable's MouseWheelScrollingLogic handles the wheel. nativeMain-only (ctor is native). */
internal expect fun feedScrollToProcessor(
	inOwner: com.compose.sdl.node.impl.ComposeOwner,
	inX: Float,
	inY: Float,
	inDeltaX: Float,
	inDeltaY: Float,
	inUptime: Long,
)
