@file:OptIn(
	androidx.compose.ui.InternalComposeUiApi::class,
	androidx.compose.runtime.InternalComposeApi::class,
)

package com.compose.sdl

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import sdl3.SDL_Delay
import sdl3.SDL_GetTicks
import sdl3.SDL_Quit
import sdl3.SDL_WaitEventTimeout

// ==================
// MARK: Application entry - nativeComposeApp + Window()
// ==================

/**
 Multi-window entry point, shaped like Compose Desktop's `application {}`:

     nativeComposeApp {
         var showTools by remember { mutableStateOf(false) }
         Window(onCloseRequest = ::exitApplication, title = "Main") { MainUi(onTools = { showTools = true }) }
         if (showTools) Window(onCloseRequest = { showTools = false }, title = "Tools") { ToolsUi() }
     }

 The app CONTENT is itself a composition (no UI tree - a Unit applier): each
 `Window(...)` call materialises an SDL window + renderer + root host + its own
 recomposer/composition, and leaving the composition (state flips to false)
 tears the window down. One main loop pumps the shared SDL event queue and
 routes events per SDL window id.

 Per-window pumping order matters: the render-bridge globals
 (currentImageLoader / viewport) are per-renderer, so the
 loop installs a window's globals before recomposing / laying out / drawing it.

 `nativeComposeWindow(...)` remains as the single-window wrapper (all demo
 probes and apidemo ride it unchanged).
*/

interface ApplicationScope {
	/** Requests the main loop to exit after the current iteration (all windows
	   are torn down on the way out). */
	fun exitApplication()
}

/** Declares one native window for as long as this composable stays in the app
   composition. `onCloseRequest` fires when the user asks the window to close
   (OS close button, or `window.close()` from content) - remove the state that
   composes this Window to actually close it, or call exitApplication(). */
@Composable
fun ApplicationScope.Window(
	onCloseRequest: () -> Unit,
	title: String = "ComposeNativeSDL3",
	width: Int = 800,
	height: Int = 600,
	gpu: GpuMode = GpuMode.Auto,
	icon: AppWindowIcon? = null,
	undecorated: Boolean = false,
	transparent: Boolean = false,
	resizable: Boolean = true,
	enabled: Boolean = true,
	focusable: Boolean = true,
	alwaysOnTop: Boolean = false,
	onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
	onKeyEvent: (KeyEvent) -> Boolean = { false },
	onFrame: ((backend: RenderBackend, frameIndex: Int) -> Boolean)? = null,
	content: @Composable ComposeWindowScope.() -> Unit,
) {
	val vScope = this as? ApplicationScopeImpl
		?: error("Window() must be called inside nativeComposeApp { ... }")
	val vContent = rememberUpdatedState(content)
	val vClose = rememberUpdatedState(onCloseRequest)
	val vPreviewKey = rememberUpdatedState(onPreviewKeyEvent)
	val vKey = rememberUpdatedState(onKeyEvent)
	val vAttrs = WindowAttributes(
		undecorated = undecorated,
		transparent = transparent,
		resizable = resizable,
		enabled = enabled,
		focusable = focusable,
		alwaysOnTop = alwaysOnTop,
	)
	val vWindow = remember {
		vScope.runtime.createWindow(
			inTitle = title,
			inWidth = width,
			inHeight = height,
			inGpu = gpu,
			inIcon = icon,
			inAttrs = vAttrs,
			inOnFrame = onFrame,
			// Read through holders so updated lambdas propagate without
			// recreating the window (same reason as the content holder below).
			inPreviewKeyHandler = { vPreviewKey.value },
			inKeyHandler = { vKey.value },
			// The window composition reads the holder each recomposition, so
			// updated content lambdas propagate without recreating the window.
			inContent = { vContent.value },
		).also { it.onCloseRequest = { vClose.value.invoke() } }
	}
	SideEffect {
		if (vWindow.facade.title != title) vWindow.facade.setTitle(title)
	}
	// Re-apply only on a real change; `transparent` is creation-only (see
	// WindowAttributes) so it is deliberately not re-applied here.
	DisposableEffect(vAttrs) {
		vWindow.applyAttributes(vAttrs)
		onDispose { }
	}
	DisposableEffect(Unit) {
		onDispose { vScope.runtime.scheduleDestroy(vWindow) }
	}
}

/** Boots SDL + the Compose runtime, runs `content` as the application
   composition, and drives every declared Window until exitApplication() or
   the last window closes. */
fun nativeComposeApp(content: @Composable ApplicationScope.() -> Unit) {
	// Install the Main dispatcher BEFORE any window/owner exists: ComposeOwner
	// captures Dispatchers.Main eagerly for its per-node coroutine scopes.
	val mainDispatcher = Sdl3MainDispatcher()
	@OptIn(ExperimentalCoroutinesApi::class)
	Dispatchers.setMain(mainDispatcher)

	val runtime = AppRuntime()

	runBlocking {
		runtime.scope = this

		// App-level composition: no UI tree - it only declares Window()s.
		val appClock = Sdl3FrameClock()
		val appRecomposer = Recomposer(coroutineContext + appClock)
		// The clock must be in the collector's context - runRecomposeAndApplyChanges
		// awaits parent frames through it.
		val appRecomposeJob = launch(appClock) { appRecomposer.runRecomposeAndApplyChanges() }
		val appComposition = Composition(UnitApplier(), appRecomposer)
		val appScope = ApplicationScopeImpl(runtime)

		val snapshotHandle = Snapshot.registerGlobalWriteObserver {
			// Only SCHEDULE a frame here; the apply is coalesced to once per loop
			// iteration (top of loop + before each pump + before layout in
			// renderFrame) instead of walking every snapshot observer on every
			// individual state write. Mirrors upstream GlobalSnapshotManager,
			// which schedules rather than applying inline.
			runtime.markAllNeedFrame()
		}

		appComposition.setContent { appScope.content() }
		// Compose the initial Window() declarations before entering the loop.
		appClock.sendFrame(frameClockNanos())
		yield()

		// ============
		//  Main loop
		var vGcLastTicks = SDL_GetTicks()
		var vRenderedSinceGc = false
		while (!runtime.exitRequested) {
			FrameProfiler.mark()
			// One virtual 16.6ms tick per loop iteration (no-op unless useVirtualFrameTime) -
			// all windows' clocks share the same timestamp this iteration.
			advanceVirtualFrame()
			Snapshot.sendApplyNotifications()

			// ============
			//  Events - one shared SDL queue, routed by window id.
			val vEvents = pollEvents()
			for (vEvent in vEvents) {
				when (vEvent) {
					is AppEvent.Quit -> {
						// Platform quit = close request on every window; any
						// veto (setOnCloseRequest returning false) keeps the
						// app alive. No windows at all → plain exit.
						if (runtime.windows.isEmpty()) runtime.exitRequested = true
						else for (vW in runtime.windows.toList()) vW.requestClose()
					}
					is AppEvent.WindowClose -> runtime.windowFor(vEvent.windowId)?.requestClose()
					is AppEvent.WindowResized -> runtime.windowFor(vEvent.windowId)?.onResizedEvent()
					is AppEvent.RedrawNeeded -> runtime.windowFor(vEvent.windowId)?.let { it.needsFrame = true }
					is AppEvent.SystemThemeChanged -> for (vW in runtime.windows) vW.onSystemThemeChanged()
					is AppEvent.WindowActivation -> runtime.windowFor(vEvent.windowId)?.onActivationEvent(vEvent)
					is AppEvent.Pointer -> runtime.windowFor(vEvent.windowId)?.onPointerEvent(vEvent)
					is AppEvent.PointerExit -> runtime.windowFor(vEvent.windowId)?.onPointerExit()
					is AppEvent.MouseWheel -> runtime.windowFor(vEvent.windowId)?.onWheelEvent(vEvent)
					is AppEvent.Key -> runtime.windowFor(vEvent.windowId)?.onKeyEvent(vEvent)
					is AppEvent.TextInput -> runtime.windowFor(vEvent.windowId)?.onTextInputEvent(vEvent)
					is AppEvent.TextEditing -> runtime.windowFor(vEvent.windowId)?.onTextEditingEvent(vEvent)
					is AppEvent.Drop -> runtime.windowFor(vEvent.windowId)?.onDropEvent(vEvent)
				}
			}

			mainDispatcher.drainPending()
			FrameProfiler.phase("events")

			// ============
			//  App composition pump - Window()s may appear / disappear here.
			Snapshot.sendApplyNotifications()
			appClock.sendFrame(frameClockNanos())
			yield()
			runtime.reapDestroyed()

			// Window content can flip window.close() / probes can end the app.
			for (vW in runtime.windows.toList()) {
				if (vW.facade.isCloseRequested && !vW.closeDispatched) {
					vW.closeDispatched = true
					vW.onCloseRequest()
				}
			}

			// Exit when the last window is gone (after at least one existed).
			if (runtime.hadWindow && runtime.windows.isEmpty()) runtime.exitRequested = true
			if (runtime.exitRequested) break
			FrameProfiler.phase("app")

			// ============
			//  Per-window pump + render.
			var vAnyRendered = false
			var vAllVsync = true
			// Non-vsync fallback cap: the shortest frame interval among the
			// rendered non-vsync windows' displays (60Hz default). Only used when
			// no window is vsync-paced (Software renderer / vsync unavailable).
			var vFallbackDelayMs = 16u
			val vAppPending = appRecomposer.hasPendingWork
			for (vW in runtime.windows.toList()) {
				vW.installGlobals()
				vW.host.sendAnimationFrame(animationClockNanos())
				mainDispatcher.drainPending()
				Snapshot.sendApplyNotifications()
				vW.frameClock.sendFrame(frameClockNanos())
				yield()
				FrameProfiler.phase("pump")
				if (vW.shouldRender()) {
					vW.renderFrame()
					vAnyRendered = true
					if (!vW.backend.vsyncEnabled) {
						vAllVsync = false
						vFallbackDelayMs = minOf(vFallbackDelayMs, displayFrameDelayMs(vW.backend.window))
					}
				}
				FrameProfiler.phase("render")
			}
			runtime.reapDestroyed()
			FrameProfiler.frameDone(vAnyRendered)

			// ============
			//  Drain deferred native-resource disposals on the MAIN thread -
			//  textures/surfaces whose owner closed them or whose Cleaner fired
			//  on a GC worker enqueue here (SDL calls aren't thread-safe). This
			//  is the ownership path that makes the GC nudge below a mere
			//  backstop (ROADMAP.md item 1).
			com.compose.sdl.graphics.NativeReleaseQueue.drain()

			// ============
			//  Pace / idle-skip.
			if (vAnyRendered) {
				SDL_Delay(if (vAllVsync) 1u else vFallbackDelayMs)
			} else if (!vAppPending) {
				// Nothing invalidated anywhere: block for events instead of
				// spinning. A real input/redraw event wakes this immediately; the
				// timeout only bounds how often async work (dispatcher/timers) is
				// re-checked while truly idle, so keep it coarse to cut wakeups.
				SDL_WaitEventTimeout(null, 100)
				for (vW in runtime.windows) vW.resetFpsWindow()
			} else {
				// App composition has pending work but nothing rendered this
				// iteration - yield briefly instead of spinning at full speed
				// until the composition settles into a renderable state.
				SDL_Delay(1u)
			}

			// ============
			//  Native-memory nudge. Renderer resources (Skia surfaces / images /
			//  fonts, SDL textures) are freed by Cleaners that only run when the
			//  Kotlin/Native GC collects - and a Compose app's Kotlin heap is
			//  small enough that the allocation-driven scheduler can starve them
			//  for minutes while the NATIVE heap balloons (issue #2: memory
			//  "never released" while navigating). Collect periodically, only
			//  after rendering activity, between frames; costs ~ms at these
			//  heap sizes and keeps RSS tracking real usage.
			if (vAnyRendered) vRenderedSinceGc = true
			val vNowTicks = SDL_GetTicks()
			if (vRenderedSinceGc && vNowTicks - vGcLastTicks >= 10_000uL) {
				vGcLastTicks = vNowTicks
				vRenderedSinceGc = false
				collectNativeGarbage()
			}
		}

		// ============
		//  Teardown
		snapshotHandle.dispose()
		for (vW in runtime.windows.toList()) runtime.scheduleDestroy(vW)
		runtime.reapDestroyed()
		com.compose.sdl.graphics.NativeReleaseQueue.drain()
		appComposition.dispose()
		appRecomposer.cancel()
		appRecomposeJob.cancelAndJoin()
	}

	mainDispatcher.close()
	@OptIn(ExperimentalCoroutinesApi::class)
	Dispatchers.resetMain()

	SDL_Quit()
}

/** Single-window compatibility wrapper - the pre-multi-window entry point.
   Closing the window exits the app, exactly as before. */
fun nativeComposeWindow(
	title: String = "ComposeNativeSDL3",
	width: Int = 800,
	height: Int = 600,
	gpu: GpuMode = GpuMode.Auto,
	icon: AppWindowIcon? = null,
	undecorated: Boolean = false,
	transparent: Boolean = false,
	resizable: Boolean = true,
	enabled: Boolean = true,
	focusable: Boolean = true,
	alwaysOnTop: Boolean = false,
	onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
	onKeyEvent: (KeyEvent) -> Boolean = { false },
	onFrame: ((backend: RenderBackend, frameIndex: Int) -> Boolean)? = null,
	content: @Composable ComposeWindowScope.() -> Unit,
) {
	nativeComposeApp {
		Window(
			onCloseRequest = { exitApplication() },
			title = title,
			width = width,
			height = height,
			gpu = gpu,
			icon = icon,
			undecorated = undecorated,
			transparent = transparent,
			resizable = resizable,
			enabled = enabled,
			focusable = focusable,
			alwaysOnTop = alwaysOnTop,
			onPreviewKeyEvent = onPreviewKeyEvent,
			onKeyEvent = onKeyEvent,
			onFrame = onFrame,
			content = content,
		)
	}
}
