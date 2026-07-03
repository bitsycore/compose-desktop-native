package com.compose.desktop.native.node

import androidx.compose.runtime.Applier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.ComposeOwner
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.compose.desktop.native.element.HorizontalScrollNode
import com.compose.desktop.native.element.MiddleClickNode
import androidx.compose.ui.node.requireLayoutNode
import com.compose.desktop.native.element.OnDragNode
import com.compose.desktop.native.element.OnPressedNode
import com.compose.desktop.native.element.OnTextInputNode
import com.compose.desktop.native.element.PressableNode
import com.compose.desktop.native.element.SecondaryClickNode
import com.compose.desktop.native.element.VerticalScrollNode

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
	}

	fun setConstraints(inWidth: Int, inHeight: Int) {
		fOwner.setRootConstraints(Constraints.fixed(inWidth, inHeight))
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
	// MARK: Key / text input (B6b) — route to the focused node via the FocusOwner
	// ==================

	/* Routes a key event to the focused node's KeyInput chain (upstream onPreKeyEvent /
	   onKeyEvent) — drives text-field editing keys (backspace / arrows / enter) and
	   clickable Enter/Space activation. Returns true if some node consumed it. */
	fun dispatchKeyEvent(inEvent: androidx.compose.ui.input.key.KeyEvent): Boolean =
		runCatching { fOwner.focusOwner.dispatchKeyEvent(inEvent) }.getOrDefault(false)

	/* Routes typed text (SDL TEXT_INPUT) to the focused node's project OnTextInputNode.
	   The focusable + onTextInput modifiers share one LayoutNode, so we take the active
	   focus target's LayoutNode and hand the text to its OnTextInputNode. */
	fun dispatchTextInput(inText: String) {
		val vFocused = fOwner.focusOwner.activeFocusTargetNode ?: return
		if (!vFocused.node.isAttached) return
		val vLayoutNode = runCatching { vFocused.requireLayoutNode() }.getOrNull() ?: return
		vLayoutNode.nodes.headToTail { if (it is OnTextInputNode) it.handler(inText) }
	}

	// ==================
	// MARK: Input (B6a) — hit-test + dispatch on the upstream tree
	// ==================

	private var fActivePress: PressableNode? = null
	private var fActivePressNode: LayoutNode? = null
	private var fDragNode: LayoutNode? = null
	private var fDrag: OnDragNode? = null

	// Deepest node containing (x,y), children-first in reverse z-order.
	private fun hitTest(inX: Float, inY: Float): LayoutNode? = hitNode(rootNode, inX, inY)

	private fun hitNode(inNode: LayoutNode, inX: Float, inY: Float): LayoutNode? {
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

	private fun absOf(inNode: LayoutNode): Offset = inNode.coordinates.positionInRoot()

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
					val dn = fDragNode!!; val vAp = absOf(dn)
					dm.onDrag((inX - vAp.x).toInt(), (inY - vAp.y).toInt())
				}
			}
			1 -> when (inButton) {
				1 -> findUp<SecondaryClickNode>(vHit)?.second?.onClick(inX.toInt(), inY.toInt())
				2 -> findUp<MiddleClickNode>(vHit)?.second?.onClick()
				else -> {
					cancelPress()
					// Focus-on-click: focus the nearest focusable under the cursor (text field / button /
					// any .focusable). Upstream clickable self-focuses, but plain focusables (text fields)
					// don't — this gives the intuitive click-to-focus behaviour and is what makes typing
					// into a clicked text field work (the focused node then receives key/text via B6b).
					findUp<androidx.compose.ui.focus.FocusTargetNode>(vHit)?.second?.requestFocus()
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
						fDragNode = node; fDrag = d; val vAp = absOf(node)
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

	fun onWheel(inX: Float, inY: Float, inDeltaX: Float, inDeltaY: Float) {
		val vHit = hitTest(inX, inY)
		val vVert = findUp<VerticalScrollNode>(vHit)?.second?.state
		val vHorz = findUp<HorizontalScrollNode>(vHit)?.second?.state
		if (vVert != null && inDeltaY != 0f) vVert.smoothScrollByPxInternal(-(inDeltaY * 50f).toInt())
		else if (vHorz != null && inDeltaY != 0f) vHorz.smoothScrollByPxInternal(-(inDeltaY * 50f).toInt())
		if (vHorz != null && inDeltaX != 0f) vHorz.smoothScrollByPxInternal(-(inDeltaX * 50f).toInt())
	}
}

// ==================
// MARK: pointer-event bridge (nativeMain builds the internal PointerInputEvent)
// ==================

/* The internal `expect PointerInputEvent` exposes no commonMain constructor, so the
   build+dispatch is a nativeMain actual. Constructs a single-pointer event and feeds it
   to the owner's PointerInputEventProcessor. inType: 0=Move 1=Press 2=Release. */
internal expect fun feedPointerToProcessor(
	inOwner: androidx.compose.ui.node.ComposeOwner,
	inType: Int,
	inButton: Int,
	inUptime: Long,
	inX: Float,
	inY: Float,
)
