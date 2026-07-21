package androidx.compose.ui.draganddrop

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.ModifierNodeElement

// ==================
// MARK: Sdl3DragAndDropOwner — real DragAndDropManager over SDL_EVENT_DROP_*
// ==================

/** The per-window DragAndDropManager backing ComposeOwner. Holds a root
 * DragAndDropNode installed on the root LayoutNode via [modifier]; the SDL
 * event pump (see SDL3EventMapper + ComposeWindow) drives [handleDrop] which
 * accumulates BEGIN → FILE / TEXT / POSITION → COMPLETE into a single
 * DragAndDropEvent and dispatches through the node tree.
 *
 * Follows the shape of upstream skiko's DragAndDropOwner — same root
 * modifier element, same interested-targets set — but this port skips the
 * upstream PlatformDragAndDropSource "drag OUT of window" machinery: SDL3
 * has no portable "start OS-level drag" API. Dropping INTO the window (the
 * far more common desktop use case) is what's wired here.
 *
 * Cross-app drag OUT would need per-OS work (NSDraggingSession on macOS,
 * DoDragDrop on Windows, XDND on X11) that lives outside SDL. */
internal class Sdl3DragAndDropOwner : DragAndDropManager {

    private val rootNode = DragAndDropNode(onStartTransfer = null)
    private val interestedTargets = mutableSetOf<DragAndDropTarget>()

    override val modifier: Modifier = RootDragAndDropElement(rootNode)

    // Requesting drag-and-drop transfer (i.e. starting a drag OUT of the
    // window) isn't supported here. Modifier.dragAndDropSource still compiles
    // and its start-detector will call this, but the transfer is silently
    // dropped — SDL3 exposes no cross-platform "start OS drag" primitive.
    override val isRequestDragAndDropTransferRequired: Boolean get() = false

    override fun requestDragAndDropTransfer(node: DragAndDropNode, offset: Offset) {
        /* no-op — see class doc. */
    }

    override fun registerTargetInterest(target: DragAndDropTarget) {
        interestedTargets.add(target)
    }

    override fun isInterestedTarget(target: DragAndDropTarget): Boolean =
        interestedTargets.contains(target)

    // ============
    //  SDL event pump — accumulate DROP_* into a session, dispatch on COMPLETE.
    //  SDL's docs don't strictly ordering FILE/TEXT vs POSITION, so the state
    //  survives across arbitrary interleaving between BEGIN and COMPLETE.

    private var sessionActive = false
    private var lastPosition: Offset = Offset.Zero
    private val pendingFiles = mutableListOf<String>()
    private var pendingText: String? = null
    private var startDispatched = false

    // Fresh drop session — reset state, but don't dispatch onStarted yet
    // (we have no transfer payload / position before the first FILE/TEXT/
    // POSITION event).
    fun onDropBegin() {
        sessionActive = true
        startDispatched = false
        lastPosition = Offset.Zero
        pendingFiles.clear()
        pendingText = null
    }

    // The pointer moved while a drop is in progress (also fired between
    // BEGIN and COMPLETE for drag-hover). Once we have any content, this
    // is when onStarted / onEntered dispatch; subsequent POSITIONs become
    // onMoved.
    fun onDropPosition(x: Float, y: Float) {
        if (!sessionActive) return
        lastPosition = Offset(x, y)
        val vEvent = buildEvent()
        if (!startDispatched) {
            rootNode.acceptDragAndDropTransfer(vEvent)
            interestedTargets.forEach { it.onStarted(vEvent) }
            rootNode.onEntered(vEvent)
            startDispatched = true
        } else {
            rootNode.onMoved(vEvent)
        }
    }

    fun onDropFile(path: String) {
        if (!sessionActive) return
        pendingFiles.add(path)
    }

    fun onDropText(text: String) {
        if (!sessionActive) return
        // If multiple TEXT events arrive concatenate — SDL typically fires one.
        pendingText = (pendingText.orEmpty() + text).ifEmpty { null }
    }

    // Drop delivered — fire onDrop on the tree, then onEnded, then clear.
    fun onDropComplete() {
        if (!sessionActive) return
        val vEvent = buildEvent()
        if (!startDispatched) {
            // Some platforms fire COMPLETE without a preceding POSITION.
            rootNode.acceptDragAndDropTransfer(vEvent)
            interestedTargets.forEach { it.onStarted(vEvent) }
        }
        rootNode.onDrop(vEvent)
        rootNode.onEnded(vEvent)
        interestedTargets.clear()
        sessionActive = false
        startDispatched = false
        lastPosition = Offset.Zero
        pendingFiles.clear()
        pendingText = null
    }

    private fun buildEvent(): DragAndDropEvent =
        DragAndDropEvent(
            position = lastPosition,
            transferData = if (pendingFiles.isEmpty() && pendingText == null)
                DragAndDropTransferData.Empty
            else
                DragAndDropTransferData(pendingFiles.toList(), pendingText),
        )
}

// Wraps the root DragAndDropNode as a Modifier element attachable to the
// root LayoutNode. Mirrors upstream skiko's RootDragAndDropElement.
private class RootDragAndDropElement(
    private val dragAndDropNode: DragAndDropNode,
) : ModifierNodeElement<DragAndDropNode>() {
    override fun create() = dragAndDropNode
    override fun update(node: DragAndDropNode) = Unit
    override fun equals(other: Any?) = other === this
    override fun hashCode(): Int = dragAndDropNode.hashCode()
}
