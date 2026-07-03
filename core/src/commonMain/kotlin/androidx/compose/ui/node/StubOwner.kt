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
import androidx.compose.runtime.retain.RetainedValuesStore
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.PlacementScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import androidx.collection.IntObjectMap
import androidx.collection.mutableIntObjectMapOf

// ==================
// MARK: StubOwner — no-op Owner singleton
// ==================

/**
 * No-op [Owner] singleton attached to every LayoutNode so vendored
 * DelegatableNode helpers (`requireOwner()`, `observeReads { … }`, etc.)
 * don't crash on a null owner.
 *
 * Every property returns a bare no-op instance / marker; every method
 * is a no-op. Replaced by a real Owner implementation when the layout
 * state machine + renderer wire-through lands (Phase 9 step E).
 */
internal object StubOwner : Owner {
	override val root: LayoutNode get() = throw IllegalStateException("StubOwner has no root")
	override val layoutNodes: IntObjectMap<LayoutNode> = mutableIntObjectMapOf()
	override val sharedDrawScope: LayoutNodeDrawScope = LayoutNodeDrawScope()
	@Suppress("DEPRECATION")
	override val rootForTest: RootForTest = object : RootForTest {
		override val density: Density = Density(1f, 1f)
		override val semanticsOwner = androidx.compose.ui.semantics.SemanticsOwner()
		override val textInputService = androidx.compose.ui.text.input.TextInputService(
			object : androidx.compose.ui.text.input.PlatformTextInputService {})
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
			get() = throw NotImplementedError("No native clipboard on StubOwner")
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
			throw NotImplementedError("createGraphicsLayer not wired on StubOwner")
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
	override val density: Density = Density(1f, 1f)
	@Suppress("DEPRECATION")
	override val textInputService: TextInputService =
		TextInputService(object : PlatformTextInputService {})
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
	override val retainedValuesStore: RetainedValuesStore = object : RetainedValuesStore {}
	override val rectManager: RectManager = RectManager()
	@Suppress("DEPRECATION")
	override val fontLoader: Font.ResourceLoader = object : Font.ResourceLoader {}
	override val fontFamilyResolver: FontFamily.Resolver = androidx.compose.ui.text.font.createFontFamilyResolver()
	override val layoutDirection: LayoutDirection = LayoutDirection.Ltr
	override val localeList: LocaleList = LocaleList()
	override var showLayoutBounds: Boolean = false

	override fun onRequestMeasure(layoutNode: LayoutNode, affectsLookahead: Boolean, forceRequest: Boolean, scheduleMeasureAndLayout: Boolean) = Unit
	override fun onRequestRelayout(layoutNode: LayoutNode, affectsLookahead: Boolean, forceRequest: Boolean) = Unit
	override fun requestOnPositionedCallback(layoutNode: LayoutNode) = Unit
	override fun onPreAttach(node: LayoutNode) = Unit
	override fun onPostAttach(node: LayoutNode) = Unit
	override fun onDetach(node: LayoutNode) = Unit
	override fun calculatePositionInWindow(localPosition: Offset): Offset = localPosition
	override fun calculateLocalPosition(positionInWindow: Offset): Offset = positionInWindow
	override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen
	override fun localToScreen(localPosition: Offset): Offset = localPosition
	override fun requestAutofill(node: LayoutNode) = Unit
	override fun measureAndLayout(sendPointerUpdate: Boolean) = Unit
	override fun measureAndLayout(layoutNode: LayoutNode, constraints: Constraints) = Unit
	override fun forceMeasureTheSubtree(layoutNode: LayoutNode, affectsLookahead: Boolean) = Unit
	override fun createLayer(
		drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
		invalidateParentLayer: () -> Unit,
		explicitLayer: GraphicsLayer?,
	): OwnedLayer = object : OwnedLayer {
		override fun updateLayerProperties(scope: androidx.compose.ui.graphics.ReusableGraphicsLayerScope) {}
		override fun isInLayer(position: androidx.compose.ui.geometry.Offset): Boolean = true
		override fun move(position: androidx.compose.ui.unit.IntOffset) {}
		override fun resize(size: androidx.compose.ui.unit.IntSize) {}
		override fun drawLayer(canvas: androidx.compose.ui.graphics.Canvas, parentLayer: androidx.compose.ui.graphics.layer.GraphicsLayer?) {}
		override fun updateDisplayList() {}
		override fun invalidate() {}
		override fun destroy() {}
		override fun mapOffset(point: androidx.compose.ui.geometry.Offset, inverse: Boolean): androidx.compose.ui.geometry.Offset = point
		override fun mapBounds(rect: androidx.compose.ui.geometry.MutableRect, inverse: Boolean) {}
		override fun reuseLayer(drawBlock: (canvas: androidx.compose.ui.graphics.Canvas, parentLayer: androidx.compose.ui.graphics.layer.GraphicsLayer?) -> Unit, invalidateParentLayer: () -> Unit) {}
		override fun transform(matrix: androidx.compose.ui.graphics.Matrix) {}
		override val underlyingMatrix: androidx.compose.ui.graphics.Matrix = androidx.compose.ui.graphics.Matrix()
		override fun inverseTransform(matrix: androidx.compose.ui.graphics.Matrix) {}
		override var frameRate: Float = 0f
		override var isFrameRateFromParent: Boolean = false
	}
	override fun onSemanticsChange() = Unit
	override fun onLayoutChange(layoutNode: LayoutNode) = Unit
	override fun onLayoutNodeDeactivated(layoutNode: LayoutNode) = Unit
	override fun onInteropViewLayoutChange(view: InteropView) = Unit
	override val measureIteration: Long = 0L
	override val viewConfiguration: ViewConfiguration = androidx.compose.ui.platform.DefaultViewConfiguration
	override val snapshotObserver: OwnerSnapshotObserver = OwnerSnapshotObserver { it() }
	override val modifierLocalManager: ModifierLocalManager = ModifierLocalManager(this)
	override val coroutineContext: CoroutineContext = EmptyCoroutineContext
	override fun registerOnEndApplyChangesListener(listener: () -> Unit) = Unit
	override fun onEndApplyChanges() = Unit
	override fun registerOnLayoutCompletedListener(listener: Owner.OnLayoutCompletedListener) = Unit
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
