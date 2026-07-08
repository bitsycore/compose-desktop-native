package com.compose.sdl.node

import androidx.compose.runtime.Applier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import com.compose.sdl.node.impl.ComposeOwner
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.compose.sdl.element.MiddleClickNode
import androidx.compose.ui.node.requireLayoutNode
import com.compose.sdl.element.OnDragNode
import com.compose.sdl.element.OnPressedNode
import com.compose.sdl.element.OnTextInputNode
import com.compose.sdl.element.PressableNode
import com.compose.sdl.element.SecondaryClickNode

// ==================
// MARK: ComposeRootHost
// ==================

/*
 Phase 9 B4/B6 — public facade the :window layer drives the upstream layout engine
 through. `LayoutNode` / `Owner` / `ComposeOwner` / `NodeApplier` are all internal
 to :core, so this hides them behind a public surface: [applier] (upcast to
 Applier<*> for the Composition), [attach], [setConstraints], [measureAndLayout],
 and the B6 input entry points ([onPointer] / [onWheel]) which hit-test the upstream
 tree + dispatch to the project input Modifier.Nodes. The internal [rootNode] is read
 by the renderer backend (also in :core) to paint.

 Toward the end-state (empty commonMain), the whole window/main-loop moves in here as
 the SDL3 windowing/event actual; for now :window still owns the loop and calls this.
 Focus / key / text input (text fields) are B6b — not yet wired here.
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
		// Install the focus root on the root LayoutNode (upstream AndroidComposeView does
		// `.then(focusOwner.modifier)`). Without it the focus tree has no root, so requestFocus()
		// can't establish a focus path (focus-on-click never sticks) and key dispatch crashes.
		rootNode.modifier = fOwner.focusOwner.modifier
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
	@Suppress("DEPRECATION")
	val textInputService: androidx.compose.ui.text.input.TextInputService get() = fOwner.textInputService
	@Suppress("DEPRECATION")
	val autofillTree: androidx.compose.ui.autofill.AutofillTree get() = fOwner.autofillTree

	// ==================
	// MARK: Key / text input (B6b) — route to the focused node via the FocusOwner
	// ==================

	/* Routes a key event to the focused node's KeyInput chain (upstream onPreKeyEvent /
	   onKeyEvent) — drives text-field editing keys (backspace / arrows / enter) and
	   clickable Enter/Space activation. Returns true if some node consumed it. */
	fun dispatchKeyEvent(inEvent: androidx.compose.ui.input.key.KeyEvent): Boolean =
		runCatching { fOwner.focusOwner.dispatchKeyEvent(inEvent) }.getOrDefault(false)

	/* Routes typed text (SDL TEXT_INPUT) to the focused node's project
	   OnTextInputNode (Modifier.onTextInput). Returns true if one consumed it.
	   Vendored text fields have no OnTextInputNode — the window loop turns
	   unconsumed text into synthetic TYPED KeyEvents (see ComposeWindow) so the
	   vendored text stacks (CoreTextField's isTypedEvent path and the
	   state-based field's key handler) commit it. SDL's committed text is the
	   only layout-correct source of characters — SDL key events carry
	   UNSHIFTED keycodes (no uppercase, no numpad digits, no dead keys). */
	fun dispatchTextInput(inText: String): Boolean {
		val vFocused = fOwner.focusOwner.activeFocusTargetNode ?: return false
		if (!vFocused.node.isAttached) return false
		val vLayoutNode = runCatching { vFocused.requireLayoutNode() }.getOrNull() ?: return false
		var vConsumed = false
		vLayoutNode.nodes.headToTail {
			if (it is OnTextInputNode) { it.handler(inText); vConsumed = true }
		}
		return vConsumed
	}

	// ==================
	// MARK: Input (B6a) — hit-test + dispatch on the upstream tree
	// ==================

	private var fActivePress: PressableNode? = null
	private var fActivePressNode: LayoutNode? = null
	private var fDragNode: LayoutNode? = null
	private var fDrag: OnDragNode? = null
	// Press-time anchor: absolute position of the drag receiver at the moment its
	// press fired. Held constant for the whole drag session so the reported
	// (relX, relY) reflect the pointer's OFFSET from where the drag started —
	// even if the receiver moves during the drag (typical apidemo pattern: apply
	// `graphicsLayer(translationX = vDragDx)` so the tab visually follows the
	// pointer). Without this we read positionInRoot every move, which INCLUDES
	// the layer translation → the reference oscillates against the delta and
	// the drag ghost trembles between two spots.
	private var fDragAnchorX: Float = 0f
	private var fDragAnchorY: Float = 0f

	// Deepest node containing (x,y), children-first in reverse z-order.
	private fun hitTest(inX: Float, inY: Float): LayoutNode? = hitNode(rootNode, inX, inY)

	private fun hitNode(inNode: LayoutNode, inX: Float, inY: Float): LayoutNode? {
		// A node can be mid-recycle (detached) while a pointer-move hit-test walks
		// the tree — e.g. fast LazyGrid/LazyColumn scrolling. positionInRoot() on a
		// detached coordinator throws ("LayoutCoordinate operations are only valid
		// when isAttached is true"); an unattached node can't be under the pointer
		// anyway, so skip it and its subtree.
		if (!inNode.coordinates.isAttached) return null
		val vPos = inNode.coordinates.positionInRoot()
		val vSize = inNode.coordinates.size
		if (inX < vPos.x || inY < vPos.y || inX >= vPos.x + vSize.width || inY >= vPos.y + vSize.height) return null
		val vKids = inNode.zSortedChildren
		for (i in vKids.size - 1 downTo 0) {
			val vHit = hitNode(vKids[i], inX, inY)
			if (vHit != null) return vHit
		}
		return inNode
	}

	private fun absOf(inNode: LayoutNode): Offset =
		if (inNode.coordinates.isAttached) inNode.coordinates.positionInRoot() else Offset.Zero

	private fun inside(inHit: LayoutNode?, inNode: LayoutNode): Boolean {
		var n = inHit
		while (n != null) { if (n === inNode) return true; n = n.parent }
		return false
	}

	// First node of type [T] on the self → root walk, with its owning LayoutNode.
	private inline fun <reified T> findUp(inStart: LayoutNode?): Pair<LayoutNode, T>? {
		var n = inStart
		while (n != null) {
			var vFound: T? = null
			n.nodes.headToTail { if (vFound == null && it is T) vFound = it }
			if (vFound != null) return n to (vFound as T)
			n = n.parent
		}
		return null
	}

	private fun cancelPress() {
		fActivePress?.onChange(false)
		fActivePress = null
		fActivePressNode = null
	}

	// inType: 0=Move 1=Press 2=Release ; inButton: 0=primary 1=secondary 2=tertiary
	fun onPointer(inX: Float, inY: Float, inType: Int, inButton: Int) {
		val vHit = hitTest(inX, inY)
		// clickable + hoverable are now vendored upstream and delivered through the
		// PointerInputEventProcessor (onPointerRaw). B6a only routes the remaining
		// project-only modifiers: pressable / onPressed / onDrag / secondary+middle click.

		when (inType) {
			0 -> {
				fActivePressNode?.let { if (!inside(vHit, it)) cancelPress() }
				fDrag?.let { dm ->
					// Use the press-time anchor, NOT the live positionInRoot — the
					// dragged node's abs pos may include a graphicsLayer(translationX = …)
					// that itself feeds off the value we compute here, which oscillates.
					dm.onDrag((inX - fDragAnchorX).toInt(), (inY - fDragAnchorY).toInt())
				}
			}
			1 -> when (inButton) {
				1 -> findUp<SecondaryClickNode>(vHit)?.second?.onClick(inX.toInt(), inY.toInt())
				2 -> findUp<MiddleClickNode>(vHit)?.second?.onClick()
				else -> {
					cancelPress()
					// Removed: legacy `findUp<FocusTargetNode>(vHit)?.second?.requestFocus()`. Upstream
					// clickable (dispatched via host.onPointerRaw) now handles focus-on-click, and
					// this legacy line was grabbing the nearest ancestor FocusTargetNode → producing
					// a SECOND focus request per click landing on an unrelated node. In a scrollable
					// list of clickables (apidemo sidebar) the ancestor was often a scrolled-off item
					// whose Focusable.onFocusChange → bringIntoView() then scrolled the list to
					// reveal *it* instead of leaving the just-clicked item in view.
					findUp<PressableNode>(vHit)?.let { (node, p) ->
						fActivePress = p; fActivePressNode = node; p.onChange(true)
					}
					var op = vHit
					while (op != null) {
						val vAp = absOf(op)
						op.nodes.headToTail { if (it is OnPressedNode) it.handler((inX - vAp.x).toInt(), (inY - vAp.y).toInt()) }
						op = op.parent
					}
					findUp<OnDragNode>(vHit)?.let { (node, d) ->
						fDragNode = node; fDrag = d
						val vAp = absOf(node)
						fDragAnchorX = vAp.x
						fDragAnchorY = vAp.y
						d.onStart((inX - vAp.x).toInt(), (inY - vAp.y).toInt())
					}
				}
			}
			2 -> {
				cancelPress()
				fDrag?.onEnd?.invoke(); fDragNode = null; fDrag = null
			}
		}
	}

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
