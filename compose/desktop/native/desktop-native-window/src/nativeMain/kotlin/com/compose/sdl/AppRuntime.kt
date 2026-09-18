@file:OptIn(
	androidx.compose.ui.InternalComposeUiApi::class,
	androidx.compose.runtime.InternalComposeApi::class,
)

package com.compose.sdl

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope

// ==================
// MARK: App runtime internals
// ==================

internal class ApplicationScopeImpl(val runtime: AppRuntime) : ApplicationScope {
	override fun exitApplication() { runtime.exitRequested = true }
}

/** Registry + lifecycle for the live windows. Windows are created by the app
   composition (Window()'s remember) and destroyed by the LOOP - DisposableEffect
   onDispose only schedules, because teardown disposes a composition and that
   must not run re-entrantly inside another composition's apply pass. */
internal class AppRuntime {
	lateinit var scope: CoroutineScope
	val windows = mutableListOf<WindowInstance>()
	private val destroyQueue = mutableListOf<WindowInstance>()
	var exitRequested = false
	var hadWindow = false

	fun windowFor(inId: UInt): WindowInstance? =
		if (inId == 0u) windows.firstOrNull()
		else windows.firstOrNull { it.backend.windowId == inId } ?: windows.firstOrNull()

	fun markAllNeedFrame() {
		for (vW in windows) vW.needsFrame = true
	}

	fun createWindow(
		inTitle: String,
		inWidth: Int,
		inHeight: Int,
		inGpu: GpuMode,
		inIcon: AppWindowIcon?,
		inAttrs: WindowAttributes,
		inOnFrame: ((RenderBackend, Int) -> Boolean)?,
		inPreviewKeyHandler: () -> ((androidx.compose.ui.input.key.KeyEvent) -> Boolean),
		inKeyHandler: () -> ((androidx.compose.ui.input.key.KeyEvent) -> Boolean),
		inContent: () -> (@Composable ComposeWindowScope.() -> Unit),
	): WindowInstance {
		val vWindow = WindowInstance(
			inTitle, inWidth, inHeight, inGpu, inIcon, inAttrs, inOnFrame,
			inPreviewKeyHandler, inKeyHandler, inContent,
		)
		// A Window() was declared either way - the "exit when the last window
		// is gone" rule must also fire when every declared window failed to
		// initialise (otherwise the loop would spin forever with none).
		hadWindow = true
		if (vWindow.init(scope)) {
			windows.add(vWindow)
		} else {
			println("nativeComposeApp: window '$inTitle' failed to initialise")
		}
		return vWindow
	}

	fun scheduleDestroy(inWindow: WindowInstance) {
		if (inWindow in windows && inWindow !in destroyQueue) destroyQueue.add(inWindow)
	}

	fun reapDestroyed() {
		while (destroyQueue.isNotEmpty()) {
			val vW = destroyQueue.removeLast()
			windows.remove(vW)
			vW.destroy()
		}
	}
}

// No-op applier for the app-level composition - it emits no UI nodes, only
// side effects (each Window() manages a native window).
internal class UnitApplier : AbstractApplier<Unit>(Unit) {
	override fun insertTopDown(index: Int, instance: Unit) {}
	override fun insertBottomUp(index: Int, instance: Unit) {}
	override fun remove(index: Int, count: Int) {}
	override fun move(from: Int, to: Int, count: Int) {}
	override fun onClear() {}
}
