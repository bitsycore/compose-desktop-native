package com.compose.desktop.native.node

import androidx.compose.runtime.Applier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.ComposeOwner
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.compose.desktop.native.element.ClickableNode
import com.compose.desktop.native.element.HorizontalScrollNode
import com.compose.desktop.native.element.MiddleClickNode
import com.compose.desktop.native.element.OnDragNode
import com.compose.desktop.native.element.OnPressedNode
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
		fOwner.attach()
	}

	fun setConstraints(inWidth: Int, inHeight: Int) {
		fOwner.setRootConstraints(Constraints.fixed(inWidth, inHeight))
	}

	fun measureAndLayout() {
		fOwner.measureAndLayout()
	}

	// ==================
	// MARK: Input (B6a) — hit-test + dispatch on the upstream tree
	// ==================

	private var fActivePress: PressableNode? = null
	private var fActivePressNode: LayoutNode? = null
	private var fArmedClickNode: LayoutNode? = null
	private var fArmedClick: (() -> Unit)? = null
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
		// Gesture (pointerInput) delivery is now handled by the vendored
		// PointerInputEventProcessor via onPointerRaw; B6a only routes the
		// project click/press/drag/scroll modifiers below.

		when (inType) {
			0 -> {
				fActivePressNode?.let { if (!inside(vHit, it)) cancelPress() }
				fArmedClickNode?.let { if (!inside(vHit, it)) { fArmedClickNode = null; fArmedClick = null } }
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
					findUp<PressableNode>(vHit)?.let { (node, p) ->
						fActivePress = p; fActivePressNode = node; p.onChange(true)
					}
					val vClick = findUp<ClickableNode>(vHit)
					fArmedClickNode = vClick?.first
					fArmedClick = vClick?.second?.onClick
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
				val vArmed = fArmedClickNode; val vCb = fArmedClick
				fArmedClickNode = null; fArmedClick = null
				if (vArmed != null && vCb != null && inside(vHit, vArmed)) vCb()
			}
		}
	}

	// Feed a raw pointer event to the vendored PointerInputEventProcessor (upstream
	// PointerInputModifierNode dispatch — hover / gestures). Runs alongside the B6a
	// project-node dispatch during the interaction migration. inType: 0=Move 1=Press 2=Release.
	// The internal `expect PointerInputEvent` has no commonMain constructor, so the actual
	// build+dispatch lives in nativeMain (feedPointerToProcessor).
	fun onPointerRaw(inX: Float, inY: Float, inType: Int, inUptime: Long) {
		feedPointerToProcessor(fOwner, inType, inUptime, inX, inY)
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
	inUptime: Long,
	inX: Float,
	inY: Float,
)
