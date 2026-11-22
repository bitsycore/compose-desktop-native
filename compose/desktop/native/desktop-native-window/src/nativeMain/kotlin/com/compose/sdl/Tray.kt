@file:OptIn(
	androidx.compose.ui.InternalComposeUiApi::class,
	androidx.compose.runtime.InternalComposeApi::class,
	kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.compose.sdl

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cnames.structs.SDL_Tray
import cnames.structs.SDL_TrayEntry
import cnames.structs.SDL_TrayMenu
import kotlinx.cinterop.*
import platform.posix.memcpy
import sdl3.*

// ==================
// MARK: Composable MenuScope DSL
// ==================

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
annotation class MenuComposable

@MenuComposable
interface MenuScope {
	@Composable
	fun Item(
		text: String,
		icon: Painter? = null,
		enabled: Boolean = true,
		onClick: () -> Unit,
	)

	@Composable
	fun CheckboxItem(
		text: String,
		checked: Boolean,
		icon: Painter? = null,
		enabled: Boolean = true,
		onCheckedChange: (Boolean) -> Unit,
	)

	@Composable
	fun Separator()

	@Composable
	fun Menu(
		text: String,
		enabled: Boolean = true,
		content: @Composable @MenuComposable MenuScope.() -> Unit,
	)
}

internal object TrayMenuScopeImpl : MenuScope {
	@Composable
	override fun Item(
		text: String,
		icon: Painter?,
		enabled: Boolean,
		onClick: () -> Unit,
	) {
		ComposeNode<TrayItemNode, TrayApplier>(
			factory = { TrayItemNode(text, enabled, onClick) },
			update = {
				set(text) { updateText(it) }
				set(enabled) { updateEnabled(it) }
				set(onClick) { updateOnClick(it) }
			}
		)
	}

	@Composable
	override fun CheckboxItem(
		text: String,
		checked: Boolean,
		icon: Painter?,
		enabled: Boolean,
		onCheckedChange: (Boolean) -> Unit,
	) {
		ComposeNode<TrayCheckboxItemNode, TrayApplier>(
			factory = { TrayCheckboxItemNode(text, checked, enabled, onCheckedChange) },
			update = {
				set(text) { updateText(it) }
				set(checked) { updateChecked(it) }
				set(enabled) { updateEnabled(it) }
				set(onCheckedChange) { updateOnCheckedChange(it) }
			}
		)
	}

	@Composable
	override fun Separator() {
		ComposeNode<TraySeparatorNode, TrayApplier>(
			factory = { TraySeparatorNode() },
			update = {}
		)
	}

	@Composable
	override fun Menu(
		text: String,
		enabled: Boolean,
		content: @Composable @MenuComposable MenuScope.() -> Unit,
	) {
		ComposeNode<TraySubmenuNode, TrayApplier>(
			factory = { TraySubmenuNode(text, enabled) },
			update = {
				set(text) { updateText(it) }
				set(enabled) { updateEnabled(it) }
			},
			content = {
				content()
			}
		)
	}
}

// ==================
// MARK: Tray Node Tree & Applier
// ==================

/** One native tray entry mirrored by the menu composition. [attach] creates the native entry at
   `pos` in `parentMenu`; [detach] removes it but keeps the node (and its children) so a move can
   re-attach it; [destroy] is the final teardown. */
internal sealed class TrayNode {
	abstract fun attach(parentMenu: CPointer<SDL_TrayMenu>, pos: Int)
	abstract fun detach()
	open fun destroy() = detach()
}

internal sealed class TrayContainerNode : TrayNode() {
	abstract val menu: CPointer<SDL_TrayMenu>?
	val children = mutableListOf<TrayNode>()

	fun insertChild(index: Int, child: TrayNode) {
		children.add(index, child)
		menu?.let { child.attach(it, index) }
	}

	fun removeChildren(index: Int, count: Int) {
		for (i in (index + count - 1) downTo index) {
			if (i in children.indices) {
				val child = children.removeAt(i)
				child.destroy()
			}
		}
	}

	fun moveChildren(from: Int, to: Int, count: Int) {
		val moved = mutableListOf<TrayNode>()
		repeat(count) {
			moved.add(children.removeAt(from))
		}
		val targetIndex = if (to > from) to - count else to
		for ((idx, node) in moved.withIndex()) {
			children.add(targetIndex + idx, node)
		}
		// SDL entries can't be moved: detach every child natively, then re-attach in the new order.
		// detach keeps each node's own children, so submenus come back intact.
		val m = menu ?: return
		children.forEach { it.detach() }
		children.forEachIndexed { idx, child -> child.attach(m, idx) }
	}

	fun clearChildren() {
		for (child in children) {
			child.destroy()
		}
		children.clear()
	}

	override fun destroy() {
		clearChildren()
		detach()
	}
}

/** The tray's own root menu: owned by the tray, never inserted as an entry. */
internal class TrayRootNode(override val menu: CPointer<SDL_TrayMenu>?) : TrayContainerNode() {
	override fun attach(parentMenu: CPointer<SDL_TrayMenu>, pos: Int) = Unit
	override fun detach() = Unit
}

private class TrayCallbackHolder(var action: () -> Unit)

private val onTrayEntryClicked = staticCFunction<COpaquePointer?, CPointer<SDL_TrayEntry>?, Unit> { userdata, _ ->
	userdata?.asStableRef<TrayCallbackHolder>()?.get()?.action?.invoke()
}

internal class TrayItemNode(
	private var text: String,
	private var enabled: Boolean,
	private var onClick: () -> Unit,
) : TrayNode() {
	var entry: CPointer<SDL_TrayEntry>? = null
	private var callbackRef: StableRef<TrayCallbackHolder>? = null
	private val holder = TrayCallbackHolder { onClick() }

	override fun attach(parentMenu: CPointer<SDL_TrayMenu>, pos: Int) {
		var flags: SDL_TrayEntryFlags = SDL_TRAYENTRY_BUTTON
		if (!enabled) flags = flags or SDL_TRAYENTRY_DISABLED
		val e = SDL_InsertTrayEntryAt(parentMenu, pos, text, flags)
		entry = e
		if (e != null) {
			val ref = StableRef.create(holder)
			callbackRef = ref
			SDL_SetTrayEntryCallback(e, onTrayEntryClicked, ref.asCPointer())
		}
	}

	fun updateText(newText: String) {
		if (text != newText) {
			text = newText
			entry?.let { SDL_SetTrayEntryLabel(it, newText) }
		}
	}

	fun updateEnabled(newEnabled: Boolean) {
		if (enabled != newEnabled) {
			enabled = newEnabled
			entry?.let { SDL_SetTrayEntryEnabled(it, newEnabled) }
		}
	}

	fun updateOnClick(newOnClick: () -> Unit) {
		onClick = newOnClick
	}

	override fun detach() {
		callbackRef?.dispose()
		callbackRef = null
		entry?.let {
			SDL_RemoveTrayEntry(it)
			entry = null
		}
	}
}

internal class TrayCheckboxItemNode(
	private var text: String,
	private var checked: Boolean,
	private var enabled: Boolean,
	private var onCheckedChange: (Boolean) -> Unit,
) : TrayNode() {
	var entry: CPointer<SDL_TrayEntry>? = null
	private var callbackRef: StableRef<TrayCallbackHolder>? = null
	// SDL flips the native checkbox before calling back. Keep it controlled: restore the composed
	// value and request the flip - recomposition applies whatever the app decides.
	private val holder = TrayCallbackHolder {
		entry?.let { SDL_SetTrayEntryChecked(it, checked) }
		onCheckedChange(!checked)
	}

	override fun attach(parentMenu: CPointer<SDL_TrayMenu>, pos: Int) {
		var flags: SDL_TrayEntryFlags = SDL_TRAYENTRY_CHECKBOX
		if (checked) flags = flags or SDL_TRAYENTRY_CHECKED
		if (!enabled) flags = flags or SDL_TRAYENTRY_DISABLED
		val e = SDL_InsertTrayEntryAt(parentMenu, pos, text, flags)
		entry = e
		if (e != null) {
			val ref = StableRef.create(holder)
			callbackRef = ref
			SDL_SetTrayEntryCallback(e, onTrayEntryClicked, ref.asCPointer())
		}
	}

	fun updateText(newText: String) {
		if (text != newText) {
			text = newText
			entry?.let { SDL_SetTrayEntryLabel(it, newText) }
		}
	}

	fun updateChecked(newChecked: Boolean) {
		if (checked != newChecked) {
			checked = newChecked
			entry?.let { SDL_SetTrayEntryChecked(it, newChecked) }
		}
	}

	fun updateEnabled(newEnabled: Boolean) {
		if (enabled != newEnabled) {
			enabled = newEnabled
			entry?.let { SDL_SetTrayEntryEnabled(it, newEnabled) }
		}
	}

	fun updateOnCheckedChange(newOnCheckedChange: (Boolean) -> Unit) {
		onCheckedChange = newOnCheckedChange
	}

	override fun detach() {
		callbackRef?.dispose()
		callbackRef = null
		entry?.let {
			SDL_RemoveTrayEntry(it)
			entry = null
		}
	}
}

internal class TraySeparatorNode : TrayNode() {
	var entry: CPointer<SDL_TrayEntry>? = null

	override fun attach(parentMenu: CPointer<SDL_TrayMenu>, pos: Int) {
		entry = SDL_InsertTrayEntryAt(parentMenu, pos, null, 0u)
	}

	override fun detach() {
		entry?.let {
			SDL_RemoveTrayEntry(it)
			entry = null
		}
	}
}

internal class TraySubmenuNode(
	private var text: String,
	private var enabled: Boolean,
) : TrayContainerNode() {
	var entry: CPointer<SDL_TrayEntry>? = null
	private var mSubMenu: CPointer<SDL_TrayMenu>? = null
	override val menu: CPointer<SDL_TrayMenu>? get() = mSubMenu

	override fun attach(parentMenu: CPointer<SDL_TrayMenu>, pos: Int) {
		var flags: SDL_TrayEntryFlags = SDL_TRAYENTRY_SUBMENU
		if (!enabled) flags = flags or SDL_TRAYENTRY_DISABLED
		val e = SDL_InsertTrayEntryAt(parentMenu, pos, text, flags)
		entry = e
		if (e != null) {
			mSubMenu = SDL_CreateTraySubmenu(e)
			val sub = mSubMenu
			if (sub != null) {
				children.forEachIndexed { idx, child -> child.attach(sub, idx) }
			}
		}
	}

	fun updateText(newText: String) {
		if (text != newText) {
			text = newText
			entry?.let { SDL_SetTrayEntryLabel(it, newText) }
		}
	}

	fun updateEnabled(newEnabled: Boolean) {
		if (enabled != newEnabled) {
			enabled = newEnabled
			entry?.let { SDL_SetTrayEntryEnabled(it, newEnabled) }
		}
	}

	override fun detach() {
		// Children first: removing the submenu entry frees its native submenu and entries.
		children.forEach { it.detach() }
		mSubMenu = null
		entry?.let {
			SDL_RemoveTrayEntry(it)
			entry = null
		}
	}
}

internal class TrayApplier(val rootNode: TrayContainerNode) : AbstractApplier<TrayNode>(rootNode) {
	override fun onClear() {
		rootNode.clearChildren()
	}

	override fun insertBottomUp(index: Int, instance: TrayNode) {
		// Ignored - built top-down
	}

	override fun insertTopDown(index: Int, instance: TrayNode) {
		val container = current as? TrayContainerNode
			?: error("Cannot insert child into non-container node: $current")
		container.insertChild(index, instance)
	}

	override fun move(from: Int, to: Int, count: Int) {
		val container = current as? TrayContainerNode ?: return
		container.moveChildren(from, to, count)
	}

	override fun remove(index: Int, count: Int) {
		val container = current as? TrayContainerNode ?: return
		container.removeChildren(index, count)
	}

	override fun onEndChanges() {
		SDL_UpdateTrays()
	}
}

// ==================
// MARK: Tray Icon
// ==================

/**
 * Image representation for a system tray icon, holding RGBA pixel data.
 */
internal class AppTrayIcon(
	val width: Int,
	val height: Int,
	val rgbaPixels: ByteArray,
) {
	companion object {
		/**
		 * Decodes an .rgba blob (8-byte header [width u32-le][height u32-le] + straight-alpha RGBA).
		 */
		fun fromRgbaBlob(blob: ByteArray): AppTrayIcon? {
			if (blob.size < 8) return null
			val w = (blob[0].toInt() and 0xFF) or
				((blob[1].toInt() and 0xFF) shl 8) or
				((blob[2].toInt() and 0xFF) shl 16) or
				((blob[3].toInt() and 0xFF) shl 24)
			val h = (blob[4].toInt() and 0xFF) or
				((blob[5].toInt() and 0xFF) shl 8) or
				((blob[6].toInt() and 0xFF) shl 16) or
				((blob[7].toInt() and 0xFF) shl 24)
			if (w <= 0 || h <= 0 || blob.size < 8 + w * h * 4) return null
			val pixels = blob.copyOfRange(8, 8 + w * h * 4)
			return AppTrayIcon(w, h, pixels)
		}

		fun fromImageBitmap(bitmap: ImageBitmap): AppTrayIcon {
			val w = bitmap.width
			val h = bitmap.height
			val pixelMap = bitmap.toPixelMap()
			val bytes = ByteArray(w * h * 4)
			for (y in 0 until h) {
				for (x in 0 until w) {
					val color = pixelMap[x, y]
					val idx = (y * w + x) * 4
					bytes[idx] = (color.red * 255f).toInt().coerceIn(0, 255).toByte()
					bytes[idx + 1] = (color.green * 255f).toInt().coerceIn(0, 255).toByte()
					bytes[idx + 2] = (color.blue * 255f).toInt().coerceIn(0, 255).toByte()
					bytes[idx + 3] = (color.alpha * 255f).toInt().coerceIn(0, 255).toByte()
				}
			}
			return AppTrayIcon(w, h, bytes)
		}

		/**
		 * Rasterizes a Compose [Painter] onto a 32x32 [AppTrayIcon].
		 */
		fun fromPainter(
			painter: Painter,
			width: Int = 32,
			height: Int = 32,
			density: Density = Density(1f),
		): AppTrayIcon {
			val bitmap = ImageBitmap(width, height)
			val canvas = Canvas(bitmap)
			val drawScope = CanvasDrawScope()
			drawScope.draw(
				density = density,
				layoutDirection = LayoutDirection.Ltr,
				canvas = canvas,
				size = Size(width.toFloat(), height.toFloat()),
			) {
				with(painter) {
					draw(size)
				}
			}
			return fromImageBitmap(bitmap)
		}
	}
}

internal fun iconSurfaceFromPixels(width: Int, height: Int, rgbaPixels: ByteArray): CPointer<SDL_Surface>? {
	val surface = SDL_CreateSurface(width, height, SDL_PIXELFORMAT_RGBA32) ?: return null
	val pixels = surface.pointed.pixels?.reinterpret<ByteVar>() ?: run {
		SDL_DestroySurface(surface)
		return null
	}
	val dstPitch = surface.pointed.pitch
	val srcPitch = width * 4
	rgbaPixels.usePinned { pin ->
		for (row in 0 until height) {
			memcpy(
				pixels + (row.toLong() * dstPitch),
				pin.addressOf(row * srcPitch),
				srcPitch.convert(),
			)
		}
	}
	return surface
}

// ==================
// MARK: Native Tray Handle
// ==================

internal class NativeTrayController(
	initialTooltip: String?,
	initialIcon: AppTrayIcon?,
) {
	var tray: CPointer<SDL_Tray>? = null
		private set
	var rootMenu: CPointer<SDL_TrayMenu>? = null
		private set
	private var currentSurface: CPointer<SDL_Surface>? = null
	// Last values pushed to SDL: Tray() re-applies on every recomposition, but a new icon
	// surface per recomposition (and a tooltip round-trip) is wasted native work.
	private var mTooltip: String? = initialTooltip
	private var mIcon: AppTrayIcon? = initialIcon

	val isCreated: Boolean get() = tray != null

	init {
		currentSurface = resolveIconSurface(initialIcon)
		tray = SDL_CreateTray(currentSurface, initialTooltip)
		if (tray != null) {
			rootMenu = SDL_CreateTrayMenu(tray)
		} else {
			val err = SDL_GetError()?.toKString()
			println("SDL_CreateTray failed: $err")
		}
	}

	fun updateTooltip(tooltip: String?) {
		val t = tray ?: return
		if (tooltip == mTooltip) return
		mTooltip = tooltip
		SDL_SetTrayTooltip(t, tooltip)
	}

	fun updateIcon(icon: AppTrayIcon?) {
		val t = tray ?: return
		if (icon === mIcon) return
		mIcon = icon
		val newSurface = resolveIconSurface(icon)
		SDL_SetTrayIcon(t, newSurface)
		if (currentSurface != null && currentSurface != newSurface) {
			SDL_DestroySurface(currentSurface)
		}
		currentSurface = newSurface
	}

	fun destroy() {
		if (tray != null) {
			SDL_DestroyTray(tray)
			tray = null
			rootMenu = null
		}
		if (currentSurface != null) {
			SDL_DestroySurface(currentSurface)
			currentSurface = null
		}
	}

	private fun resolveIconSurface(icon: AppTrayIcon?): CPointer<SDL_Surface>? {
		if (icon == null) return null
		return iconSurfaceFromPixels(icon.width, icon.height, icon.rgbaPixels)
	}
}

// ==================
// MARK: Public Composable API
// ==================

/**
 * Declares a native system tray icon and context menu.
 *
 * Stays alive and keeps the application loop running as long as it is in the composition
 * and the underlying platform successfully created the native tray (`SDL_CreateTray`).
 * If all windows close while an active tray exists, the application remains running in
 * the background.
 *
 * Unlike Compose Desktop's `Tray`, there is no `onAction` and no `TrayState` (notifications) yet:
 * SDL 3.4 has no tray-icon click callback (a click opens the menu) and no notification API.
 *
 * @param icon The [Painter] to rasterize into the native tray icon surface (typically 32x32).
 * @param tooltip Optional hover tooltip string displayed by the OS for the tray icon.
 * @param menu The tray's context menu ([MenuScope]): [MenuScope.Item], [MenuScope.CheckboxItem],
 *   [MenuScope.Separator] and nested [MenuScope.Menu] entries. Empty by default.
 */
@Composable
fun ApplicationScope.Tray(
	icon: Painter,
	tooltip: String? = null,
	menu: @Composable @MenuComposable MenuScope.() -> Unit = {},
) {
	val vScope = this as? ApplicationScopeImpl
		?: error("Tray() must be called inside nativeComposeApp { ... }")

	val trayIcon = remember(icon) { AppTrayIcon.fromPainter(icon) }

	val controller = remember {
		NativeTrayController(tooltip, trayIcon)
	}

	DisposableEffect(controller) {
		val isCreated = controller.isCreated
		if (isCreated) {
			vScope.runtime.activeTrayCount++
		}
		onDispose {
			if (isCreated) {
				vScope.runtime.activeTrayCount--
			}
			controller.destroy()
		}
	}

	SideEffect {
		controller.updateTooltip(tooltip)
		controller.updateIcon(trayIcon)
	}

	val compositionContext = rememberCompositionContext()
	val currentMenu by rememberUpdatedState(menu)

	DisposableEffect(controller, compositionContext) {
		val rootMenu = controller.rootMenu
		if (!controller.isCreated || rootMenu == null) {
			return@DisposableEffect onDispose {}
		}
		val rootNode = TrayRootNode(rootMenu)
		val menuComposition = Composition(TrayApplier(rootNode), compositionContext)

		menuComposition.setContent {
			TrayMenuScopeImpl.currentMenu()
		}

		onDispose {
			menuComposition.dispose()
			rootNode.destroy()
		}
	}
}
