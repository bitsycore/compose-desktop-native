@file:Suppress("UNUSED", "DEPRECATION")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.pointer.PointerIconService
import androidx.compose.ui.modifier.ModifierLocalManager
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillManager
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.draganddrop.DragAndDropManager
import androidx.compose.ui.focus.FocusOwner
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.spatial.RectManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.InteropView
import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.runtime.retain.RetainedValuesStore
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import androidx.collection.IntObjectMap
import androidx.collection.mutableIntObjectMapOf

// ==================
// MARK: ComposeOwner — real Owner driving the vendored layout engine
// ==================

/*
 Phase 9 B3 — a working [Owner] over an upstream [LayoutNode] root, driving the
 vendored [MeasureAndLayoutDelegate] for the measure/layout pass. This is the
 engine's heart for the Option-B swap; it replaces the no-op StubOwner once the
 composition builds upstream LayoutNode (B4).

 Layout-driving members (root / measureAndLayout / onRequestMeasure /
 onRequestRelayout / onDetach / forceMeasureTheSubtree) forward to the delegate.
 The remaining subsystems (focus / semantics / clipboard / text input / haptics /
 input mode / autofill) stay no-op — this owner exists to make layout + draw run,
 not accessibility or input, which land in later phases (B5/B6). createLayer just
 runs the draw block: nodes without a graphicsLayer draw through the translate
 path in NodeCoordinator.draw, so basic drawing works; graphicsLayer / alpha /
 clip via a transforming layer come in B6.

 Usage (B4): construct with the root, call [attach] once, then per frame
 [setRootConstraints] + [measureAndLayout], then owner.root.draw(canvas).
*/
internal class ComposeOwner(
	override val root: LayoutNode,
	override val density: Density = Density(1f, 1f),
	override val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) : Owner {

	// The vendored measure/layout state machine, rooted at [root].
	private val fDelegate = MeasureAndLayoutDelegate(root)

	// The vendored pointer-input dispatcher. Routes a PointerInputEvent through
	// HitPathTracker to the tree's PointerInputModifierNodes (hover / gesture),
	// synthesizing Enter/Exit. Fed from ComposeWindow via ComposeRootHost.
	private val fPointerProcessor = androidx.compose.ui.input.pointer.PointerInputEventProcessor(root)

	// Dispatch one pointer event to the upstream PointerInputModifierNode tree.
	// [this] is the PositionCalculator (Owner : PositionCalculator).
	internal fun processPointerInput(inEvent: androidx.compose.ui.input.pointer.PointerInputEvent): Boolean {
		val vResult = fPointerProcessor.process(inEvent, this)
		return vResult.dispatchedToAPointerInputModifier
	}

	// Attaches the root subtree to this owner. Call once after construction,
	// before the first measure pass — mirrors a platform Owner attaching when
	// its view enters the hierarchy.
	fun attach() {
		root.attach(this)
	}

	// Sets the constraints the root is measured against (window pixel size in
	// logical points). Call each frame before [measureAndLayout].
	fun setRootConstraints(inConstraints: Constraints) {
		fDelegate.updateRootConstraints(inConstraints)
	}

	// ============
	//  Layout-driving members — forward to the delegate

	override fun onRequestMeasure(
		layoutNode: LayoutNode,
		affectsLookahead: Boolean,
		forceRequest: Boolean,
		scheduleMeasureAndLayout: Boolean,
	) {
		fDelegate.requestRemeasure(layoutNode, forceRequest)
	}

	override fun onRequestRelayout(
		layoutNode: LayoutNode,
		affectsLookahead: Boolean,
		forceRequest: Boolean,
	) {
		fDelegate.requestRelayout(layoutNode, forceRequest)
	}

	override fun measureAndLayout(sendPointerUpdate: Boolean) {
		fDelegate.measureAndLayout()
		fDelegate.dispatchOnPositionedCallbacks()
	}

	override fun measureAndLayout(layoutNode: LayoutNode, constraints: Constraints) {
		fDelegate.measureAndLayout(layoutNode, constraints)
	}

	override fun forceMeasureTheSubtree(layoutNode: LayoutNode, affectsLookahead: Boolean) {
		fDelegate.forceMeasureTheSubtree(layoutNode, affectsLookahead)
	}

	override fun onDetach(node: LayoutNode) {
		fDelegate.onNodeDetached(node)
	}

	override fun registerOnLayoutCompletedListener(listener: Owner.OnLayoutCompletedListener) {
		fDelegate.registerOnLayoutCompletedListener(listener)
	}

	override fun requestOnPositionedCallback(layoutNode: LayoutNode) = Unit
	override fun onPreAttach(node: LayoutNode) = Unit
	override fun onPostAttach(node: LayoutNode) = Unit
	override fun onLayoutChange(layoutNode: LayoutNode) = Unit
	override fun onLayoutNodeDeactivated(layoutNode: LayoutNode) = Unit
	override fun onSemanticsChange() = Unit
	override val measureIteration: Long get() = 0L

	// ============
	//  Draw plumbing

	override val sharedDrawScope: LayoutNodeDrawScope = LayoutNodeDrawScope()
	override val snapshotObserver: OwnerSnapshotObserver = OwnerSnapshotObserver { it() }
	override val rectManager: RectManager = RectManager()

	override fun createLayer(
		drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
		invalidateParentLayer: () -> Unit,
		explicitLayer: GraphicsLayer?,
	): OwnedLayer = object : OwnedLayer {
		override fun updateLayerProperties(scope: androidx.compose.ui.graphics.ReusableGraphicsLayerScope) {}
		override fun isInLayer(position: Offset): Boolean = true
		override fun move(position: androidx.compose.ui.unit.IntOffset) {}
		override fun resize(size: androidx.compose.ui.unit.IntSize) {}
		override fun drawLayer(canvas: Canvas, parentLayer: GraphicsLayer?) { drawBlock(canvas, parentLayer) }
		override fun updateDisplayList() {}
		override fun invalidate() {}
		override fun destroy() {}
		override fun mapOffset(point: Offset, inverse: Boolean): Offset = point
		override fun mapBounds(rect: androidx.compose.ui.geometry.MutableRect, inverse: Boolean) {}
		override fun reuseLayer(drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit, invalidateParentLayer: () -> Unit) {}
		override fun transform(matrix: androidx.compose.ui.graphics.Matrix) {}
		override val underlyingMatrix: androidx.compose.ui.graphics.Matrix = androidx.compose.ui.graphics.Matrix()
		override fun inverseTransform(matrix: androidx.compose.ui.graphics.Matrix) {}
		override var frameRate: Float = 0f
		override var isFrameRateFromParent: Boolean = false
	}

	// ============
	//  No-op subsystems (focus / semantics / input / clipboard / text / …).
	//  Same reduced impls as StubOwner — refined in B5 (semantics/input) / B6.

	override val layoutNodes: IntObjectMap<LayoutNode> = mutableIntObjectMapOf()
	@Suppress("DEPRECATION")
	override val rootForTest: RootForTest = object : RootForTest {
		override val density: Density = this@ComposeOwner.density
		override val semanticsOwner = SemanticsOwner()
		override val textInputService = TextInputService(androidx.compose.ui.text.input.NoOpPlatformTextInputService)
		override fun sendKeyEvent(keyEvent: androidx.compose.ui.input.key.KeyEvent): Boolean = false
	}
	override val hapticFeedBack: HapticFeedback = object : HapticFeedback {
		override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) = Unit
	}
	override val inputModeManager: InputModeManager = object : InputModeManager {
		override val inputMode: InputMode = InputMode.Keyboard
		override fun requestInputMode(mode: InputMode): Boolean = false
	}
	@Suppress("DEPRECATION")
	override val clipboardManager: ClipboardManager = object : ClipboardManager {
		override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) = Unit
		override fun getText(): androidx.compose.ui.text.AnnotatedString? = null
	}
	override val clipboard: Clipboard = object : Clipboard {
		override suspend fun getClipEntry(): androidx.compose.ui.platform.ClipEntry? = null
		override suspend fun setClipEntry(clipEntry: androidx.compose.ui.platform.ClipEntry?) = Unit
		override val nativeClipboard: androidx.compose.ui.platform.NativeClipboard
			get() = throw NotImplementedError("No native clipboard on ComposeOwner")
	}
	override val accessibilityManager: AccessibilityManager = object : AccessibilityManager {
		override fun calculateRecommendedTimeoutMillis(
			originalTimeoutMillis: Long,
			containsIcons: Boolean,
			containsText: Boolean,
			containsControls: Boolean,
		): Long = originalTimeoutMillis
	}
	override val graphicsContext: GraphicsContext = object : GraphicsContext {
		override fun createGraphicsLayer(): GraphicsLayer =
			throw NotImplementedError("createGraphicsLayer not wired on ComposeOwner")
		override fun releaseGraphicsLayer(layer: GraphicsLayer) = Unit
	}
	override val textToolbar: TextToolbar = object : TextToolbar {
		override val status: androidx.compose.ui.platform.TextToolbarStatus
			get() = androidx.compose.ui.platform.TextToolbarStatus.Hidden
		override fun showMenu(
			rect: androidx.compose.ui.geometry.Rect,
			onCopyRequested: (() -> Unit)?,
			onPasteRequested: (() -> Unit)?,
			onCutRequested: (() -> Unit)?,
			onSelectAllRequested: (() -> Unit)?,
		) = Unit
		override fun hide() = Unit
	}
	override val autofillTree: AutofillTree = AutofillTree()
	override val autofill: Autofill? = null
	override val autofillManager: AutofillManager? = null
	@Suppress("DEPRECATION")
	override val textInputService: TextInputService = TextInputService(androidx.compose.ui.text.input.NoOpPlatformTextInputService)
	override val softwareKeyboardController: SoftwareKeyboardController =
		object : SoftwareKeyboardController {
			override fun show() = Unit
			override fun hide() = Unit
		}
	override val pointerIconService: PointerIconService = object : PointerIconService {
		override fun getIcon(): androidx.compose.ui.input.pointer.PointerIcon =
			androidx.compose.ui.input.pointer.PointerIcon.Default
		override fun setIcon(value: androidx.compose.ui.input.pointer.PointerIcon?) {}
		override fun getStylusHoverIcon(): androidx.compose.ui.input.pointer.PointerIcon? = null
		override fun setStylusHoverIcon(value: androidx.compose.ui.input.pointer.PointerIcon?) {}
	}
	override val semanticsOwner: SemanticsOwner = SemanticsOwner()
	override val focusOwner: FocusOwner = androidx.compose.ui.focus.FocusOwnerImpl(
		platformFocusOwner = object : androidx.compose.ui.focus.PlatformFocusOwner {
			// The SDL window always owns/accepts focus, so grant owner focus — returning false
			// here denies the very first focus request (performRequestFocus, previousActiveNode==null).
			override fun requestOwnerFocus(focusDirection: androidx.compose.ui.focus.FocusDirection?, previouslyFocusedRect: androidx.compose.ui.geometry.Rect?): Boolean = true
			override fun clearOwnerFocus() {}
			override fun moveFocusInChildren(focusDirection: androidx.compose.ui.focus.FocusDirection): Boolean = false
			override fun getEmbeddedViewFocusRect(): androidx.compose.ui.geometry.Rect? = null
		},
		owner = this,
	)
	override val windowInfo: WindowInfo = object : WindowInfo {
		override val isWindowFocused: Boolean get() = true
		override val containerSize: androidx.compose.ui.unit.IntSize
			get() = androidx.compose.ui.unit.IntSize.Zero
		override val containerDpSize: androidx.compose.ui.unit.DpSize
			get() = androidx.compose.ui.unit.DpSize.Zero
	}
	override val retainedValuesStore: RetainedValuesStore = ForgetfulRetainedValuesStore
	@Suppress("DEPRECATION")
	override val fontLoader: Font.ResourceLoader = object : Font.ResourceLoader {}
	override val fontFamilyResolver: FontFamily.Resolver = androidx.compose.ui.text.font.createFontFamilyResolver()
	override val localeList: LocaleList = LocaleList()
	override var showLayoutBounds: Boolean = false

	override fun calculatePositionInWindow(localPosition: Offset): Offset = localPosition
	override fun calculateLocalPosition(positionInWindow: Offset): Offset = positionInWindow
	override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen
	override fun localToScreen(localPosition: Offset): Offset = localPosition
	override fun requestAutofill(node: LayoutNode) = Unit
	override fun onInteropViewLayoutChange(view: InteropView) = Unit
	override val viewConfiguration: ViewConfiguration = androidx.compose.ui.platform.DefaultViewConfiguration
	override val modifierLocalManager: ModifierLocalManager = ModifierLocalManager(this)
	// Drives node-level animations (scroll fling, animateScrollToItem, Animatable inside
	// Modifier.Nodes). These call withFrameNanos on the MonotonicFrameClock in their coroutine
	// scope, which derives from this owner's coroutineContext — so it MUST carry a frame clock,
	// or every node animation hangs (e.g. mouse-wheel smooth scroll never applies). ComposeWindow
	// pumps it once per frame via ComposeRootHost.sendAnimationFrame.
	internal val animationFrameClock = androidx.compose.runtime.BroadcastFrameClock()

	// Modifier.Node coroutine scopes derive from this — hover's emitEnter/emitExit launch here,
	// and node animations await frames on animationFrameClock. Dispatcher is the SDL main
	// dispatcher (installed by ComposeWindow, drained each frame).
	override val coroutineContext: CoroutineContext =
		kotlinx.coroutines.Dispatchers.Main + animationFrameClock
	// End-of-apply-changes listeners — the focus system (FocusInvalidationManager) and other
	// engine parts register here to defer work until changes are applied; without this the
	// focus invalidation never flushes and requestFocus() (e.g. focus-on-click) never sticks.
	private val fEndApplyChangesListeners = mutableListOf<() -> Unit>()

	override fun registerOnEndApplyChangesListener(listener: () -> Unit) {
		if (listener !in fEndApplyChangesListeners) fEndApplyChangesListeners.add(listener)
	}

	override fun onEndApplyChanges() {
		// Drain in order; listeners may re-register, so loop until empty.
		while (fEndApplyChangesListeners.isNotEmpty()) {
			val vListener = fEndApplyChangesListeners.removeAt(0)
			vListener()
		}
	}
	override val dragAndDropManager: DragAndDropManager = object : DragAndDropManager {
		override val modifier = androidx.compose.ui.Modifier
		override val isRequestDragAndDropTransferRequired: Boolean = false
		override fun requestDragAndDropTransfer(
			node: androidx.compose.ui.draganddrop.DragAndDropNode,
			offset: androidx.compose.ui.geometry.Offset,
		) {}
		override fun registerTargetInterest(target: androidx.compose.ui.draganddrop.DragAndDropTarget) {}
		override fun isInterestedTarget(target: androidx.compose.ui.draganddrop.DragAndDropTarget): Boolean = false
	}
	override suspend fun textInputSession(
		session: suspend PlatformTextInputSessionScope.() -> Nothing,
	): Nothing = throw UnsupportedOperationException("No platform text input on desktop")
}
