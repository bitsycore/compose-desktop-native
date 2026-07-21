@file:Suppress("UNUSED", "DEPRECATION")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.compose.sdl.node.impl

import com.compose.sdl.node.createPlatformDragAndDropManager
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeDrawScope
import androidx.compose.ui.node.MeasureAndLayoutDelegate
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.node.OwnedLayerManager
import androidx.compose.ui.node.GraphicsLayerOwnerLayer
import androidx.compose.ui.node.OwnerScope
import androidx.compose.ui.node.OwnerSnapshotObserver
import androidx.compose.ui.node.RootForTest

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

/**
 The project [Owner] over an upstream [LayoutNode] root, driving the vendored
 [MeasureAndLayoutDelegate] for the measure/layout pass.

 Layout-driving members (root / measureAndLayout / onRequestMeasure /
 onRequestRelayout / onDetach / forceMeasureTheSubtree) forward to the delegate.
 Focus, pointer input, text input (IME), drag-and-drop, and the retained-layer
 bookkeeping (OwnedLayerManager) are wired below; semantics / autofill stay no-op.
 [createLayer] returns upstream's [GraphicsLayerOwnerLayer] over the per-renderer
 [GraphicsLayer] actual.

 Usage: construct with the root, call [attach] once, then per frame
 [setRootConstraints] + [measureAndLayout], then owner.root.draw(canvas).
*/
internal class ComposeOwner(
	override val root: LayoutNode,
	override val density: Density = Density(1f, 1f),
	override val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) : Owner, OwnedLayerManager {

	// The vendored measure/layout state machine, rooted at [root].
	private val fDelegate = MeasureAndLayoutDelegate(root)

	// The vendored pointer-input dispatcher. Routes a PointerInputEvent through
	// HitPathTracker to the tree's PointerInputModifierNodes (hover / gesture),
	// synthesizing Enter/Exit. Fed from ComposeWindow via ComposeRootHost.
	private val fPointerProcessor = androidx.compose.ui.input.pointer.PointerInputEventProcessor(root)

	// Window focus + container size backing [windowInfo], snapshot-backed so
	// focus-reactive UI and LocalWindowInfo.containerSize readers recompose. Fed by
	// the window: focus via setWindowFocused (SDL activation), size from setRootConstraints.
	private val fWindowFocused = androidx.compose.runtime.mutableStateOf(true)
	private val fContainerSize = androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)

	// Called by the window on SDL focus gain/loss.
	internal fun setWindowFocused(inFocused: Boolean) { fWindowFocused.value = inFocused }

	// Dispatch one pointer event to the upstream PointerInputModifierNode tree.
	// [this] is the PositionCalculator (Owner : PositionCalculator).
	internal fun processPointerInput(inEvent: androidx.compose.ui.input.pointer.PointerInputEvent): Boolean {
		val vResult = fPointerProcessor.process(inEvent, this)
		// Apply the cursor the hover pipeline just set for this event (deduped in SdlCursors).
		com.compose.sdl.SdlCursors.apply(fPointerIcon)
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
		// Report the container size (physical px, the port's layout coord space) to
		// windowInfo; updates on every resize since the window re-measures per frame.
		if (inConstraints.hasBoundedWidth && inConstraints.hasBoundedHeight) {
			fContainerSize.value =
				androidx.compose.ui.unit.IntSize(inConstraints.maxWidth, inConstraints.maxHeight)
		}
	}

	// ============
	//  Layout-driving members — forward to the delegate

	// affectsLookahead MUST reach the delegate's lookahead entry points —
	// dropping it starves the lookahead pass, and anything built on
	// LookaheadScope (SharedTransitionLayout, animateContentSize-style
	// intermediate layouts) dies with "LookaheadDelegate has not been
	// measured yet" the moment it needs a lookahead measure.
	override fun onRequestMeasure(
		layoutNode: LayoutNode,
		affectsLookahead: Boolean,
		forceRequest: Boolean,
		scheduleMeasureAndLayout: Boolean,
	) {
		if (affectsLookahead) fDelegate.requestLookaheadRemeasure(layoutNode, forceRequest)
		else fDelegate.requestRemeasure(layoutNode, forceRequest)
	}

	override fun onRequestRelayout(
		layoutNode: LayoutNode,
		affectsLookahead: Boolean,
		forceRequest: Boolean,
	) {
		if (affectsLookahead) fDelegate.requestLookaheadRelayout(layoutNode, forceRequest)
		else fDelegate.requestRelayout(layoutNode, forceRequest)
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
		// Drop the detached node's read-observation scopes, exactly as upstream
		// RootNodeOwner.onDetach does. Without this, every disposed node's measure/
		// layout/draw observation scopes accumulate in the snapshot observer forever
		// — the baseline composition-machinery leak the P2.2 soak caught.
		snapshotObserver.clear(node)
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
	): OwnedLayer = GraphicsLayerOwnerLayer(
		graphicsLayer = explicitLayer ?: graphicsContext.createGraphicsLayer(),
		context = if (explicitLayer != null) null else graphicsContext,
		layerManager = this,
		drawBlock = drawBlock,
		invalidateParentLayer = invalidateParentLayer,
	)

	// ============
	//  OwnedLayerManager — retained-layer bookkeeping (mirrors upstream
	//  OwnedLayerManagerImpl). dirtyLayers are re-recorded once per frame in
	//  [renderRoot] before the tree is drawn; clean layers replay their cached
	//  display list. invalidate() asks the window to schedule a frame.

	private val dirtyLayers = mutableListOf<OwnedLayer>()
	private var postponedDirtyLayers: MutableList<OwnedLayer>? = null
	private var isDrawingContent = false

	// Set by the window (via ComposeRootHost) to flag needsFrame; a layer whose
	// content changed (OwnedLayer.invalidate) schedules a frame even when nothing
	// else (recompose / relayout) is pending.
	internal var onInvalidate: (() -> Unit)? = null

	override fun invalidate() {
		onInvalidate?.invoke()
	}

	override fun notifyLayerIsDirty(layer: OwnedLayer, isDirty: Boolean) {
		// Mirrors upstream OwnedLayerManagerImpl exactly. The CRITICAL detail: when a
		// layer clears its dirty flag DURING drawing (updateDisplayList sets isDirty
		// = false in the renderRoot loop), we must NOT remove it from dirtyLayers here
		// — that would shrink the list mid-iteration (IndexOutOfBounds). renderRoot's
		// dirtyLayers.clear() after the loop handles it instead.
		if (!isDirty) {
			if (!isDrawingContent) {
				dirtyLayers.remove(layer)
				postponedDirtyLayers?.remove(layer)
			}
		} else if (!isDrawingContent) {
			dirtyLayers += layer
		} else {
			val postponed = postponedDirtyLayers
				?: mutableListOf<OwnedLayer>().also { postponedDirtyLayers = it }
			postponed += layer
		}
	}

	override fun recycle(layer: OwnedLayer): Boolean {
		dirtyLayers.remove(layer)
		postponedDirtyLayers?.remove(layer)
		return false
	}

	// Both Owner and OwnedLayerManager declare voteFrameRate with a default, so an
	// explicit override is required. We render at a fixed vsync cadence — no voting.
	override fun voteFrameRate(frameRate: Float) {}

	// Draw the tree: re-record dirty layers' display lists, then walk the root so
	// clean layers replay. Called once per frame by ComposeRootHost.drawRoot.
	internal fun renderRoot(canvas: Canvas) {
		isDrawingContent = true
		// Re-record dirty layers BEFORE drawing (unlike Android, our draw forms the
		// actual render-command sequence, so display lists must be current first).
		// notifyLayerIsDirty(false) is a no-op while drawing, so the list can't shrink
		// here; clear() below drops them all at once.
		if (dirtyLayers.isNotEmpty()) {
			for (i in 0 until dirtyLayers.size) {
				dirtyLayers[i].updateDisplayList()
			}
		}
		dirtyLayers.clear()
		root.draw(canvas, null)
		// Layers that invalidated themselves during draw were parked in postponed;
		// roll them into next frame.
		postponedDirtyLayers?.let {
			dirtyLayers.addAll(it)
			it.clear()
		}
		isDrawingContent = false
	}

	// ============
	//  No-op subsystems (focus / semantics / input / clipboard / text / …).
	//  Same reduced impls as StubOwner — refined in B5 (semantics/input) / B6.

	override val layoutNodes: IntObjectMap<LayoutNode> = mutableIntObjectMapOf()
	@Suppress("DEPRECATION")
	override val rootForTest: RootForTest = object : RootForTest {
		override val density: Density = this@ComposeOwner.density
		override val semanticsOwner = SemanticsOwner(this@ComposeOwner.root, androidx.compose.ui.semantics.EmptySemanticsModifier(), this@ComposeOwner.layoutNodes)
		override val textInputService = TextInputService(com.compose.sdl.text.input.NoOpPlatformTextInputService)
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
	// Created behind a per-renderer factory seam (B2 / P1.3): SDL uses the project
	// record/replay GraphicsContext; the Skia leg swaps in upstream's SkiaGraphicsContext
	// at P1.6. SharedTransitionLayout overlays and rememberGraphicsLayer() create layers
	// through this context. See com/compose/sdl/graphics/GraphicsContextFactory.kt.
	override val graphicsContext: GraphicsContext =
		com.compose.sdl.graphics.createGraphicsContext()
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
	override val textInputService: TextInputService = TextInputService(com.compose.sdl.text.input.NoOpPlatformTextInputService)
	override val softwareKeyboardController: SoftwareKeyboardController =
		object : SoftwareKeyboardController {
			override fun show() = Unit
			override fun hide() = Unit
		}
	// Desired hover cursor, set by the vendored HoverIconModifierNode
	// (Modifier.pointerHoverIcon) and applied via SDL in processPointerInput. null = default.
	private var fPointerIcon: androidx.compose.ui.input.pointer.PointerIcon =
		androidx.compose.ui.input.pointer.PointerIcon.Default
	override val pointerIconService: PointerIconService = object : PointerIconService {
		override fun getIcon(): androidx.compose.ui.input.pointer.PointerIcon = fPointerIcon
		override fun setIcon(value: androidx.compose.ui.input.pointer.PointerIcon?) {
			fPointerIcon = value ?: androidx.compose.ui.input.pointer.PointerIcon.Default
		}
		override fun getStylusHoverIcon(): androidx.compose.ui.input.pointer.PointerIcon? = null
		override fun setStylusHoverIcon(value: androidx.compose.ui.input.pointer.PointerIcon?) {}
	}
	override val semanticsOwner: SemanticsOwner = SemanticsOwner(this@ComposeOwner.root, androidx.compose.ui.semantics.EmptySemanticsModifier(), this@ComposeOwner.layoutNodes)
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
		override val isWindowFocused: Boolean get() = fWindowFocused.value
		override val containerSize: androidx.compose.ui.unit.IntSize get() = fContainerSize.value
		override val containerDpSize: androidx.compose.ui.unit.DpSize
			get() = with(density) {
				androidx.compose.ui.unit.DpSize(
					fContainerSize.value.width.toDp(),
					fContainerSize.value.height.toDp(),
				)
			}
	}
	override val retainedValuesStore: RetainedValuesStore = ForgetfulRetainedValuesStore
	@Suppress("DEPRECATION")
	override val fontLoader: Font.ResourceLoader = object : Font.ResourceLoader {
		@Deprecated("Replaced by FontFamily.Resolver, this method should not be called")
		override fun load(font: Font): Any = Unit
	}
	override val fontFamilyResolver: FontFamily.Resolver =
		com.compose.sdl.text.font.projectFontFamilyResolver
	override val localeList: LocaleList = LocaleList()
	override var showLayoutBounds: Boolean = false

	override fun calculatePositionInWindow(localPosition: Offset): Offset = localPosition
	override fun calculateLocalPosition(positionInWindow: Offset): Offset = positionInWindow
	override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen
	override fun localToScreen(localPosition: Offset): Offset = localPosition
	override fun requestAutofill(node: LayoutNode) = Unit
	override fun onInteropViewLayoutChange(view: InteropView) = Unit
	override val viewConfiguration: ViewConfiguration = com.compose.sdl.platform.DefaultViewConfiguration
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
	override val dragAndDropManager: DragAndDropManager = createPlatformDragAndDropManager()
	override suspend fun textInputSession(
		session: suspend PlatformTextInputSessionScope.() -> Nothing,
	): Nothing {
		// A focused text field runs its input here. startInputMethod registers the
		// request with ImeBridge so SDL text events (committed + composing) reach the
		// field's EditProcessor; cancellation (focus loss / dispose) clears it.
		kotlinx.coroutines.coroutineScope {
			val vScope = object :
				PlatformTextInputSessionScope,
				kotlinx.coroutines.CoroutineScope by this {
				@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
				override suspend fun startInputMethod(
					request: androidx.compose.ui.platform.PlatformTextInputMethodRequest,
				): Nothing {
					try {
						com.compose.sdl.text.input.ImeBridge.setRequest(request)
						kotlinx.coroutines.awaitCancellation()
					} finally {
						if (com.compose.sdl.text.input.ImeBridge.request === request) {
							com.compose.sdl.text.input.ImeBridge.setRequest(null)
						}
					}
				}
			}
			vScope.session()
		}
	}
}

