@file:OptIn(
	androidx.compose.ui.InternalComposeUiApi::class,
	androidx.compose.runtime.InternalComposeApi::class,
	kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.compose.sdl

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import platform.posix.memcpy
import sdl3.*

// ==================
// MARK: Tray Menu Specification
// ==================

sealed interface TrayEntrySpec {
	data class Button(val label: String, val enabled: Boolean, val onClick: () -> Unit) : TrayEntrySpec
	data class Checkbox(val label: String, val checked: Boolean, val enabled: Boolean, val onCheckedChange: (Boolean) -> Unit) : TrayEntrySpec
	data object Separator : TrayEntrySpec
	data class Submenu(val label: String, val enabled: Boolean, val children: List<TrayEntrySpec>) : TrayEntrySpec
}

interface TrayMenuScope {
	fun Item(
		text: String,
		enabled: Boolean = true,
		onClick: () -> Unit,
	)

	fun Item(
		text: StringResource,
		enabled: Boolean = true,
		onClick: () -> Unit,
	) = Item(
		text = runBlocking { getString(text) },
		enabled = enabled,
		onClick = onClick,
	)

	fun CheckboxItem(
		text: String,
		checked: Boolean,
		enabled: Boolean = true,
		onCheckedChange: (Boolean) -> Unit,
	)

	fun CheckboxItem(
		text: StringResource,
		checked: Boolean,
		enabled: Boolean = true,
		onCheckedChange: (Boolean) -> Unit,
	) = CheckboxItem(
		text = runBlocking { getString(text) },
		checked = checked,
		enabled = enabled,
		onCheckedChange = onCheckedChange,
	)

	fun Divider()
	fun Separator() = Divider()

	fun Menu(
		text: String,
		enabled: Boolean = true,
		content: TrayMenuScope.() -> Unit,
	)

	fun Menu(
		text: StringResource,
		enabled: Boolean = true,
		content: TrayMenuScope.() -> Unit,
	) = Menu(
		text = runBlocking { getString(text) },
		enabled = enabled,
		content = content,
	)
}

internal class TrayMenuBuilder : TrayMenuScope {
	val entries = mutableListOf<TrayEntrySpec>()

	override fun Item(text: String, enabled: Boolean, onClick: () -> Unit) {
		entries.add(TrayEntrySpec.Button(text, enabled, onClick))
	}

	override fun CheckboxItem(text: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
		entries.add(TrayEntrySpec.Checkbox(text, checked, enabled, onCheckedChange))
	}

	override fun Divider() {
		entries.add(TrayEntrySpec.Separator)
	}

	override fun Menu(text: String, enabled: Boolean, content: TrayMenuScope.() -> Unit) {
		val sub = TrayMenuBuilder()
		sub.content()
		entries.add(TrayEntrySpec.Submenu(text, enabled, sub.entries))
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
		 * Rasterizes a Compose [Painter] onto a 32x32 (or custom [size]) [AppTrayIcon].
		 */
		fun fromPainter(
			painter: Painter,
			width: Int = 32,
			height: Int = 32,
			density: Density = Density(1f),
		): AppTrayIcon {
			if (com.compose.sdl.res.composeResourceReader == null) {
				com.compose.sdl.res.composeResourceReader = ::loadComposeResourceBytes
			}
			val prevDrawer = com.compose.sdl.renderer.skia.skiaLeafDrawer
			if (prevDrawer == null) {
				com.compose.sdl.renderer.skia.skiaLeafDrawer = com.compose.sdl.renderer.skia.SkiaLeafDrawer(
					com.compose.sdl.renderer.skia.SkiaImageCache()
				)
			}
			try {
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
			} finally {
				if (prevDrawer == null) {
					com.compose.sdl.renderer.skia.skiaLeafDrawer = null
				}
			}
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

private class TrayCallbackHolder(var action: () -> Unit)

private val onTrayEntryClicked = staticCFunction<COpaquePointer?, CPointer<SDL_TrayEntry>?, Unit> { userdata, _ ->
	userdata?.asStableRef<TrayCallbackHolder>()?.get()?.action?.invoke()
}

internal class NativeTrayController(
	initialTooltip: String?,
	initialIcon: AppTrayIcon?,
) {
	private var tray: CPointer<SDL_Tray>? = null
	private var rootMenu: CPointer<SDL_TrayMenu>? = null
	private var currentSurface: CPointer<SDL_Surface>? = null
	private val activeCallbacks = mutableListOf<StableRef<TrayCallbackHolder>>()

	init {
		currentSurface = resolveIconSurface(initialIcon)
		tray = SDL_CreateTray(currentSurface, initialTooltip)
		if (tray != null) {
			rootMenu = SDL_CreateTrayMenu(tray)
		}
	}

	fun updateTooltip(tooltip: String?) {
		if (tray != null) {
			SDL_SetTrayTooltip(tray, tooltip)
		}
	}

	fun updateIcon(icon: AppTrayIcon?) {
		if (tray == null) return
		val newSurface = resolveIconSurface(icon)
		SDL_SetTrayIcon(tray, newSurface)
		if (currentSurface != null && currentSurface != newSurface) {
			SDL_DestroySurface(currentSurface)
		}
		currentSurface = newSurface
	}

	fun updateMenu(specs: List<TrayEntrySpec>) {
		if (tray == null) return

		// Clear previous callbacks
		for (ref in activeCallbacks) {
			ref.dispose()
		}
		activeCallbacks.clear()

		if (rootMenu != null) {
			// Clear old entries
			var count = 0
			memScoped {
				val countVar = alloc<IntVar>()
				val entriesPtr = SDL_GetTrayEntries(rootMenu, countVar.ptr)
				if (entriesPtr != null) {
					count = countVar.value
					for (i in (count - 1) downTo 0) {
						val entry = entriesPtr[i]
						if (entry != null) {
							SDL_RemoveTrayEntry(entry)
						}
					}
				}
			}
		} else {
			rootMenu = SDL_CreateTrayMenu(tray)
		}

		val menu = rootMenu ?: return
		populateMenu(menu, specs)
		SDL_UpdateTrays()
	}

	private fun populateMenu(menu: CPointer<SDL_TrayMenu>, specs: List<TrayEntrySpec>) {
		for (spec in specs) {
			when (spec) {
				is TrayEntrySpec.Button -> {
					var flags: SDL_TrayEntryFlags = SDL_TRAYENTRY_BUTTON
					if (!spec.enabled) flags = flags or SDL_TRAYENTRY_DISABLED
					val entry = SDL_InsertTrayEntryAt(menu, -1, spec.label, flags)
					if (entry != null) {
						val holder = TrayCallbackHolder(spec.onClick)
						val ref = StableRef.create(holder)
						activeCallbacks.add(ref)
						SDL_SetTrayEntryCallback(entry, onTrayEntryClicked, ref.asCPointer())
					}
				}
				is TrayEntrySpec.Checkbox -> {
					var flags: SDL_TrayEntryFlags = SDL_TRAYENTRY_CHECKBOX
					if (spec.checked) flags = flags or SDL_TRAYENTRY_CHECKED
					if (!spec.enabled) flags = flags or SDL_TRAYENTRY_DISABLED
					val entry = SDL_InsertTrayEntryAt(menu, -1, spec.label, flags)
					if (entry != null) {
						val holder = TrayCallbackHolder {
							val isCheckedNow = SDL_GetTrayEntryChecked(entry)
							spec.onCheckedChange(isCheckedNow)
						}
						val ref = StableRef.create(holder)
						activeCallbacks.add(ref)
						SDL_SetTrayEntryCallback(entry, onTrayEntryClicked, ref.asCPointer())
					}
				}
				is TrayEntrySpec.Separator -> {
					SDL_InsertTrayEntryAt(menu, -1, null, 0u)
				}
				is TrayEntrySpec.Submenu -> {
					var flags: SDL_TrayEntryFlags = SDL_TRAYENTRY_SUBMENU
					if (!spec.enabled) flags = flags or SDL_TRAYENTRY_DISABLED
					val entry = SDL_InsertTrayEntryAt(menu, -1, spec.label, flags)
					if (entry != null) {
						val subMenu = SDL_CreateTraySubmenu(entry)
						if (subMenu != null) {
							populateMenu(subMenu, spec.children)
						}
					}
				}
			}
		}
	}

	fun destroy() {
		for (ref in activeCallbacks) {
			ref.dispose()
		}
		activeCallbacks.clear()

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
 * Stays alive and keeps the application loop running as long as it is in the composition.
 */
@Composable
fun ApplicationScope.Tray(
	icon: Painter,
	tooltip: String? = null,
	menu: (TrayMenuScope.() -> Unit)? = null,
) {
	val trayIcon = remember(icon) { AppTrayIcon.fromPainter(icon) }
	InternalTray(
		icon = trayIcon,
		tooltip = tooltip,
		menu = menu,
	)
}

@Composable
internal fun ApplicationScope.InternalTray(
	icon: AppTrayIcon? = null,
	tooltip: String? = null,
	menu: (TrayMenuScope.() -> Unit)? = null,
) {
	val vScope = this as? ApplicationScopeImpl
		?: error("Tray() must be called inside nativeComposeApp { ... }")

	val controller = remember {
		NativeTrayController(tooltip, icon)
	}

	DisposableEffect(Unit) {
		vScope.runtime.activeTrayCount++
		onDispose {
			vScope.runtime.activeTrayCount--
			controller.destroy()
		}
	}

	SideEffect {
		controller.updateTooltip(tooltip)
		controller.updateIcon(icon)
	}

	if (menu != null) {
		val builder = remember { TrayMenuBuilder() }
		builder.entries.clear()
		builder.menu()
		val specs = builder.entries.toList()
		DisposableEffect(specs) {
			controller.updateMenu(specs)
			onDispose { }
		}
	}
}

