@file:Suppress("UNUSED", "DEPRECATION")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.compose.sdl.node.impl

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeDrawScope
import androidx.compose.ui.node.MeasureAndLayoutDelegate
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.OwnedLayer
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
	): OwnedLayer = ProjectOwnedLayer(drawBlock)

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
	// GraphicsLayer here is the project record/replay actual (see
	// GraphicsLayer.native.kt) — SharedTransitionLayout overlays and
	// rememberGraphicsLayer() create layers through this context. The
	// expect class hides its constructor/release from commonMain, hence
	// the project factory hops.
	override val graphicsContext: GraphicsContext = object : GraphicsContext {
		override fun createGraphicsLayer(): GraphicsLayer =
			com.compose.sdl.graphics.createProjectGraphicsLayer()
		override fun releaseGraphicsLayer(layer: GraphicsLayer) =
			com.compose.sdl.graphics.releaseProjectGraphicsLayer(layer)
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
	override val textInputService: TextInputService = TextInputService(com.compose.sdl.text.input.NoOpPlatformTextInputService)
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
		override val isWindowFocused: Boolean get() = true
		override val containerSize: androidx.compose.ui.unit.IntSize
			get() = androidx.compose.ui.unit.IntSize.Zero
		override val containerDpSize: androidx.compose.ui.unit.DpSize
			get() = androidx.compose.ui.unit.DpSize.Zero
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
	): Nothing {
		// SDL IME wire-up isn't done yet; suspend forever so upstream's
		// LegacyAdaptingPlatformTextInputModifierNode has something to await
		// instead of throwing on every TextField click. Cancellation of the
		// launching coroutine (focus loss, dispose) resumes cleanly.
		kotlinx.coroutines.awaitCancellation()
	}
}

// ==================
// MARK: ProjectOwnedLayer — real OwnedLayer implementing translate + clip
// ==================

/*
 The [OwnedLayer] returned by [ComposeOwner.createLayer]. Each modifier that
 introduces a graphics layer (`Modifier.graphicsLayer`, `Modifier.clip`, or
 `Placeable.placeRelativeWithLayer` for scroll offsets) drives one of these.

 A no-op stub is enough to make composition run but produces broken visuals:
 scroll offsets never move children (the "scroll doesn't scroll" bug) and
 `Modifier.clip` never clips overflowing content. Here we honour the two most
 load-bearing state fields the vendored `NodeCoordinator` writes:

  * `move(position)` — the child's placement offset (used by
    `placeRelativeWithLayer` for `verticalScroll`'s scroll translation).
  * `updateLayerProperties(scope)` — the `graphicsLayer { … }` field bag:
    `translationX/Y`, `scaleX/Y`, `alpha`, `clip`. Enough for scroll clip
    (`clipToBounds()` sets `clip = true`) and app-level graphicsLayer usage.

 `drawLayer` wraps the drawBlock in `canvas.save() / translate / scale / clipRect
 / drawBlock / restore` so those fields actually reach the canvas. Rotation +
 shadow + colorFilter + renderEffect + explicit `outline` shape are left as TODO
 (falling back to a rectangular clip when `clip = true`) — matches what the
 project renderer's Sdl3Canvas / SkiaCanvas currently handle.
*/
private class ProjectOwnedLayer(
	private var drawBlock: (Canvas, GraphicsLayer?) -> Unit,
) : OwnedLayer {
	private var fPosition: androidx.compose.ui.unit.IntOffset = androidx.compose.ui.unit.IntOffset.Zero
	private var fSize: androidx.compose.ui.unit.IntSize = androidx.compose.ui.unit.IntSize.Zero

	private var fTranslationX: Float = 0f
	private var fTranslationY: Float = 0f
	private var fScaleX: Float = 1f
	private var fScaleY: Float = 1f
	private var fRotationZ: Float = 0f
	// transformOrigin pivot as a fraction of the layer size (Center = 0.5, 0.5).
	private var fPivotFractionX: Float = 0.5f
	private var fPivotFractionY: Float = 0.5f
	private var fAlpha: Float = 1f
	private var fClip: Boolean = false
	// Shape used when fClip=true. RectangleShape / null → rect clip, anything else
	// (RoundedCornerShape / CircleShape / GenericShape) → path clip via the shape's
	// Outline. Without this the `Modifier.clip(RoundedCornerShape(6.dp))` on a
	// small icon-button clipped subsequent draws (background, indication) to a
	// plain rectangle — the button's rounded hover fill wasn't clipped to its
	// rounded shape and looked square.
	private var fShape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape
	private var fLayoutDirection: androidx.compose.ui.unit.LayoutDirection =
		androidx.compose.ui.unit.LayoutDirection.Ltr
	private var fDensity: androidx.compose.ui.unit.Density =
		androidx.compose.ui.unit.Density(1f)
	// Drop shadow (Modifier.shadow / m3 Surface shadowElevation) — painted by
	// the renderer canvas via NativeShadowCanvas before content + clip.
	private var fShadowElevation: Float = 0f
	private var fAmbientColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black
	private var fSpotColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black

	override fun isInLayer(position: Offset): Boolean {
		if (!fClip) return true
		// `position` is already the layer-LOCAL coord (NodeCoordinator.fromParentPosition
		// has already stripped this.position and applied mapOffset). Just check the
		// layer-local size box.
		return position.x in 0f..fSize.width.toFloat() && position.y in 0f..fSize.height.toFloat()
	}

	override fun move(position: androidx.compose.ui.unit.IntOffset) {
		fPosition = position
	}
	override fun updateLayerProperties(scope: androidx.compose.ui.graphics.ReusableGraphicsLayerScope) {
		fTranslationX = scope.translationX
		fTranslationY = scope.translationY
		fScaleX = scope.scaleX
		fScaleY = scope.scaleY
		fRotationZ = scope.rotationZ
		fPivotFractionX = scope.transformOrigin.pivotFractionX
		fPivotFractionY = scope.transformOrigin.pivotFractionY
		fAlpha = scope.alpha
		fClip = scope.clip
		fShape = scope.shape
		fShadowElevation = scope.shadowElevation
		fAmbientColor = scope.ambientShadowColor
		fSpotColor = scope.spotShadowColor
		// Density + layoutDirection are `internal` on ReusableGraphicsLayerScope but
		// visible here (same :ui module). shape.createOutline needs them to convert
		// `RoundedCornerShape(6.dp)` corner-radius from Dp to px against the current
		// scale — without this the rounded corners would be sized against Density(1f)
		// and look pixel-sharp on Retina but under-radiused everywhere else.
		fDensity = androidx.compose.ui.unit.Density(scope.density, scope.fontScale)
		fLayoutDirection = scope.layoutDirection
	}

	override fun resize(size: androidx.compose.ui.unit.IntSize) {
		fSize = size
	}

	override fun drawLayer(canvas: Canvas, parentLayer: GraphicsLayer?) {
		val vNeedsAlphaLayer = fAlpha < 1f
		if (vNeedsAlphaLayer && fSize.width > 0 && fSize.height > 0) {
			// Offscreen layer with alpha so overlapping shapes composite correctly.
			// Bounds are the layer's LOCAL rect BEFORE the translate below (saveLayer
			// captures the current canvas state's transform, then we translate for
			// child painting). Restore() pops the layer + composites at alpha.
			val vPaint = androidx.compose.ui.graphics.Paint().apply { alpha = fAlpha }
			canvas.saveLayer(
				androidx.compose.ui.geometry.Rect(
					fPosition.x + fTranslationX,
					fPosition.y + fTranslationY,
					fPosition.x + fTranslationX + fSize.width,
					fPosition.y + fTranslationY + fSize.height,
				),
				vPaint,
			)
		} else {
			canvas.save()
		}
		canvas.translate(fPosition.x + fTranslationX, fPosition.y + fTranslationY)
		// scale + rotationZ apply about the transformOrigin pivot (Center by
		// default) — translate to the pivot, transform, translate back. Modifier
		// .scale / .rotate rely on this; scaling about (0,0) had shifted + resized
		// the wrong way (and rotation was ignored entirely).
		val vHasScale = fScaleX != 1f || fScaleY != 1f
		val vHasRotation = fRotationZ != 0f
		if ((vHasScale || vHasRotation) && fSize.width > 0 && fSize.height > 0) {
			val vPivotX = fSize.width * fPivotFractionX
			val vPivotY = fSize.height * fPivotFractionY
			canvas.translate(vPivotX, vPivotY)
			if (vHasRotation) canvas.rotate(fRotationZ)
			if (vHasScale) canvas.scale(fScaleX, fScaleY)
			canvas.translate(-vPivotX, -vPivotY)
		}
		// Drop shadow — painted in layer-local coords BEFORE the clip (the
		// shadow lives outside the bounds; Modifier.shadow layers ship
		// clip=false, but ordering here keeps clip=true layers correct too).
		if (fShadowElevation > 0f && fSize.width > 0 && fSize.height > 0) {
			val vShadowCanvas = canvas as? com.compose.sdl.graphics.NativeShadowCanvas
			if (vShadowCanvas != null) {
				val vOutline = fShape.createOutline(
					androidx.compose.ui.geometry.Size(fSize.width.toFloat(), fSize.height.toFloat()),
					fLayoutDirection,
					fDensity,
				)
				vShadowCanvas.drawDropShadow(vOutline, fShadowElevation, fAmbientColor, fSpotColor)
			}
		}
		if (fClip && fSize.width > 0 && fSize.height > 0) {
			// Route the clip through the layer's shape so `Modifier.clip(RoundedCornerShape(6.dp))`
			// actually rounds — was `canvas.clipRect(0, 0, w, h)` which clipped to a plain
			// rectangle regardless of fShape and left rounded-button hover fills looking
			// square. RectangleShape shortcut avoids the shape.createOutline allocation for
			// the common case.
			if (fShape === androidx.compose.ui.graphics.RectangleShape) {
				canvas.clipRect(0f, 0f, fSize.width.toFloat(), fSize.height.toFloat())
			} else {
				val vOutline = fShape.createOutline(
					androidx.compose.ui.geometry.Size(fSize.width.toFloat(), fSize.height.toFloat()),
					fLayoutDirection,
					fDensity,
				)
				when (vOutline) {
					is androidx.compose.ui.graphics.Outline.Rectangle -> canvas.clipRect(
						vOutline.rect.left, vOutline.rect.top, vOutline.rect.right, vOutline.rect.bottom,
					)
					is androidx.compose.ui.graphics.Outline.Rounded -> {
						// Skia's clipPath rounds natively, so we build the rounded rect as a
						// Path and clip with it there. The SDL3 canvas has no path-clip
						// primitive, so it implements NativeShapeClipCanvas and clips the
						// rounded OUTLINE via an offscreen masked target — preferred over
						// clipPath (which on SDL falls back to the bounding rect and left
						// rounded-button state layers looking square).
						val vShapeClip = canvas as? com.compose.sdl.graphics.NativeShapeClipCanvas
						if (vShapeClip != null) {
							vShapeClip.clipRoundRect(vOutline.roundRect)
						} else {
							val vPath = androidx.compose.ui.graphics.Path()
							vPath.addRoundRect(vOutline.roundRect)
							canvas.clipPath(vPath)
						}
					}
					is androidx.compose.ui.graphics.Outline.Generic -> canvas.clipPath(vOutline.path)
				}
			}
		}
		drawBlock(canvas, parentLayer)
		canvas.restore()
	}

	override fun updateDisplayList() {}
	override fun invalidate() {}
	override fun destroy() {}
	// mapOffset / mapBounds apply ONLY the layer-block transforms (translationX/Y —
	// scale + rotation TODO), NOT `fPosition`. `fPosition` is the layer's `move(…)`
	// value, which NodeCoordinator.fromParentPosition/toParentPosition already
	// subtracts/adds via `this.position` before/after calling mapOffset — including
	// it here would double-count and land hit-tests at 2× the visible offset (this
	// showed up as popups drawing correctly but their menu items not receiving
	// clicks).
	override fun mapOffset(point: Offset, inverse: Boolean): Offset {
		if (fTranslationX == 0f && fTranslationY == 0f) return point
		return if (inverse) Offset(point.x - fTranslationX, point.y - fTranslationY)
		else Offset(point.x + fTranslationX, point.y + fTranslationY)
	}
	override fun mapBounds(rect: androidx.compose.ui.geometry.MutableRect, inverse: Boolean) {
		if (fTranslationX == 0f && fTranslationY == 0f) return
		val vSx = if (inverse) -1 else 1
		rect.left += vSx * fTranslationX
		rect.right += vSx * fTranslationX
		rect.top += vSx * fTranslationY
		rect.bottom += vSx * fTranslationY
	}
	override fun reuseLayer(
		drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
		invalidateParentLayer: () -> Unit,
	) {
		this.drawBlock = drawBlock
		// Reset layer state so a reused layer for a different node starts clean.
		fPosition = androidx.compose.ui.unit.IntOffset.Zero
		fSize = androidx.compose.ui.unit.IntSize.Zero
		fTranslationX = 0f; fTranslationY = 0f
		fScaleX = 1f; fScaleY = 1f
		fRotationZ = 0f
		fPivotFractionX = 0.5f; fPivotFractionY = 0.5f
		fAlpha = 1f
		fClip = false
	}
	override fun transform(matrix: androidx.compose.ui.graphics.Matrix) {}
	override val underlyingMatrix: androidx.compose.ui.graphics.Matrix = androidx.compose.ui.graphics.Matrix()
	override fun inverseTransform(matrix: androidx.compose.ui.graphics.Matrix) {}
	override var frameRate: Float = 0f
	override var isFrameRateFromParent: Boolean = false
}
