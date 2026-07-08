@file:OptIn(
	androidx.compose.ui.InternalComposeUiApi::class,
	androidx.compose.runtime.InternalComposeApi::class,
)

package com.compose.desktop.native

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import com.compose.desktop.native.node.ComposeRootHost
import com.compose.desktop.native.res.currentImageLoader
import com.compose.desktop.native.text.currentTextMeasurer
import com.compose.desktop.native.window.LocalPopupHost
import com.compose.desktop.native.window.PopupLayer
import com.compose.desktop.native.window.createPopupHostState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.cinterop.reinterpret
import sdl3.SDL_Delay
import sdl3.SDL_GetTicks
import sdl3.SDL_Quit
import sdl3.SDL_SetWindowTitle
import sdl3.SDL_WaitEventTimeout

// ==================
// MARK: Application entry — nativeComposeApp + Window()
// ==================

/*
 Multi-window entry point, shaped like Compose Desktop's `application {}`:

     nativeComposeApp {
         var showTools by remember { mutableStateOf(false) }
         Window(onCloseRequest = ::exitApplication, title = "Main") { MainUi(onTools = { showTools = true }) }
         if (showTools) Window(onCloseRequest = { showTools = false }, title = "Tools") { ToolsUi() }
     }

 The app CONTENT is itself a composition (no UI tree — a Unit applier): each
 `Window(...)` call materialises an SDL window + renderer + root host + its own
 recomposer/composition, and leaving the composition (state flips to false)
 tears the window down. One main loop pumps the shared SDL event queue and
 routes events per SDL window id.

 Per-window pumping order matters: the render-bridge globals
 (currentTextMeasurer / currentImageLoader / viewport) are per-renderer, so the
 loop installs a window's globals before recomposing / laying out / drawing it.

 `nativeComposeWindow(...)` remains as the single-window wrapper (all demo
 probes and apidemo ride it unchanged).
*/

interface ApplicationScope {
	/* Requests the main loop to exit after the current iteration (all windows
	   are torn down on the way out). */
	fun exitApplication()
}

/* Declares one native window for as long as this composable stays in the app
   composition. `onCloseRequest` fires when the user asks the window to close
   (OS close button, or `window.close()` from content) — remove the state that
   composes this Window to actually close it, or call exitApplication(). */
@Composable
fun ApplicationScope.Window(
	onCloseRequest: () -> Unit,
	title: String = "ComposeNativeSDL3",
	width: Int = 800,
	height: Int = 600,
	gpu: GpuMode = GpuMode.Auto,
	onFrame: ((backend: RenderBackend, frameIndex: Int) -> Boolean)? = null,
	content: @Composable ComposeWindowScope.() -> Unit,
) {
	val vScope = this as? ApplicationScopeImpl
		?: error("Window() must be called inside nativeComposeApp { ... }")
	val vContent = rememberUpdatedState(content)
	val vClose = rememberUpdatedState(onCloseRequest)
	val vWindow = remember {
		vScope.runtime.createWindow(
			inTitle = title,
			inWidth = width,
			inHeight = height,
			inGpu = gpu,
			inOnFrame = onFrame,
			// The window composition reads the holder each recomposition, so
			// updated content lambdas propagate without recreating the window.
			inContent = { vContent.value },
		).also { it.onCloseRequest = { vClose.value.invoke() } }
	}
	SideEffect {
		if (vWindow.facade.title != title) vWindow.facade.setTitle(title)
	}
	DisposableEffect(Unit) {
		onDispose { vScope.runtime.scheduleDestroy(vWindow) }
	}
}

/* Boots SDL + the Compose runtime, runs `content` as the application
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

		// App-level composition: no UI tree — it only declares Window()s.
		val appClock = SDL3FrameClock()
		val appRecomposer = Recomposer(coroutineContext + appClock)
		// The clock must be in the collector's context — runRecomposeAndApplyChanges
		// awaits parent frames through it.
		val appRecomposeJob = launch(appClock) { appRecomposer.runRecomposeAndApplyChanges() }
		val appComposition = Composition(UnitApplier(), appRecomposer)
		val appScope = ApplicationScopeImpl(runtime)

		val snapshotHandle = Snapshot.registerGlobalWriteObserver {
			runtime.markAllNeedFrame()
			Snapshot.sendApplyNotifications()
		}

		appComposition.setContent { appScope.content() }
		// Compose the initial Window() declarations before entering the loop.
		appClock.sendFrame()
		yield()

		// ============
		//  Main loop
		while (!runtime.exitRequested) {
			Snapshot.sendApplyNotifications()

			// ============
			//  Events — one shared SDL queue, routed by window id.
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
					is AppEvent.Pointer -> runtime.windowFor(vEvent.windowId)?.onPointerEvent(vEvent)
					is AppEvent.MouseWheel -> runtime.windowFor(vEvent.windowId)?.onWheelEvent(vEvent)
					is AppEvent.Key -> runtime.windowFor(vEvent.windowId)?.onKeyEvent(vEvent)
					is AppEvent.TextInput -> runtime.windowFor(vEvent.windowId)?.onTextInputEvent(vEvent)
				}
			}

			mainDispatcher.drainPending()

			// ============
			//  App composition pump — Window()s may appear / disappear here.
			Snapshot.sendApplyNotifications()
			appClock.sendFrame()
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

			// ============
			//  Per-window pump + render.
			var vAnyRendered = false
			var vAllVsync = true
			val vAppPending = appRecomposer.hasPendingWork
			for (vW in runtime.windows.toList()) {
				vW.installGlobals()
				vW.host.sendAnimationFrame(SDL_GetTicks().toLong() * 1_000_000L)
				mainDispatcher.drainPending()
				Snapshot.sendApplyNotifications()
				vW.frameClock.sendFrame()
				yield()
				if (vW.shouldRender()) {
					vW.renderFrame()
					vAnyRendered = true
					if (!vW.backend.vsyncEnabled) vAllVsync = false
				}
			}
			runtime.reapDestroyed()

			// ============
			//  Pace / idle-skip.
			if (vAnyRendered) {
				SDL_Delay(if (vAllVsync) 1u else 16u)
			} else if (!vAppPending) {
				// Nothing invalidated anywhere: block for events instead of
				// spinning. Keep FPS windows anchored to active periods.
				SDL_WaitEventTimeout(null, 10)
				for (vW in runtime.windows) vW.resetFpsWindow()
			}
		}

		// ============
		//  Teardown
		snapshotHandle.dispose()
		for (vW in runtime.windows.toList()) runtime.scheduleDestroy(vW)
		runtime.reapDestroyed()
		appComposition.dispose()
		appRecomposer.cancel()
		appRecomposeJob.cancelAndJoin()
	}

	mainDispatcher.close()
	@OptIn(ExperimentalCoroutinesApi::class)
	Dispatchers.resetMain()

	SDL_Quit()
}

/* Single-window compatibility wrapper — the pre-multi-window entry point.
   Closing the window exits the app, exactly as before. */
fun nativeComposeWindow(
	title: String = "ComposeNativeSDL3",
	width: Int = 800,
	height: Int = 600,
	gpu: GpuMode = GpuMode.Auto,
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
			onFrame = onFrame,
			content = content,
		)
	}
}

// ==================
// MARK: App runtime internals
// ==================

internal class ApplicationScopeImpl(val runtime: AppRuntime) : ApplicationScope {
	override fun exitApplication() { runtime.exitRequested = true }
}

/* Registry + lifecycle for the live windows. Windows are created by the app
   composition (Window()'s remember) and destroyed by the LOOP — DisposableEffect
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
		inOnFrame: ((RenderBackend, Int) -> Boolean)?,
		inContent: () -> (@Composable ComposeWindowScope.() -> Unit),
	): WindowInstance {
		val vWindow = WindowInstance(inTitle, inWidth, inHeight, inGpu, inOnFrame, inContent)
		// A Window() was declared either way — the "exit when the last window
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

// No-op applier for the app-level composition — it emits no UI nodes, only
// side effects (each Window() manages a native window).
private class UnitApplier : AbstractApplier<Unit>(Unit) {
	override fun insertTopDown(index: Int, instance: Unit) {}
	override fun insertBottomUp(index: Int, instance: Unit) {}
	override fun remove(index: Int, count: Int) {}
	override fun move(from: Int, to: Int, count: Int) {}
	override fun onClear() {}
}

// ==================
// MARK: WindowInstance — one SDL window + renderer + composition
// ==================

internal class WindowInstance(
	private val initialTitle: String,
	inWidth: Int,
	inHeight: Int,
	inGpu: GpuMode,
	private val onFrame: ((RenderBackend, Int) -> Boolean)?,
	private val contentHolder: () -> (@Composable ComposeWindowScope.() -> Unit),
) {
	private val gpuMode = if (inGpu is GpuMode.Auto) rendererPreferredGpuMode() else inGpu
	val backend = SDL3Backend(initialTitle, inWidth, inHeight, gpuMode = gpuMode)
	private var renderBackend: RenderBackend? = null
	lateinit var host: ComposeRootHost
		private set
	lateinit var facade: ComposeNativeWindow
		private set
	private var popupHost: com.compose.desktop.native.window.PopupHostState? = null

	val frameClock = SDL3FrameClock()
	private var recomposer: Recomposer? = null
	private var recomposeJob: Job? = null
	private var composition: Composition? = null

	// Set by Window(); the compat wrapper points it at exitApplication().
	var onCloseRequest: () -> Unit = {}
	var closeDispatched = false

	// Render-on-demand flag (see the pre-multi-window loop): set by events,
	// state writes (global observer), resize, expose.
	var needsFrame = true

	// Hover refresh: last pointer position in PIXEL space, re-dispatched as a
	// synthetic Move after layout so items scrolled under the cursor hover.
	private var lastMouseX = -1f
	private var lastMouseY = -1f
	private var hasMousePos = false

	private var frameIndex = 0
	private var fpsFrames = 0
	private var fpsLastMs = SDL_GetTicks()

	// Escape → back (upstream desktop's BackNavigationEventInput): unconsumed
	// Escape completes a back navigation on THIS window's dispatcher.
	private val navigationEventOwner = object : androidx.navigationevent.NavigationEventDispatcherOwner {
		override val navigationEventDispatcher = androidx.navigationevent.NavigationEventDispatcher()
	}
	private val backNavigationInput = BackNavigationInput()

	fun init(inScope: CoroutineScope): Boolean {
		if (!backend.init()) return false
		backend.updateWindowSize()

		val vRender = createRenderBackend(backend, gpuMode)
		if (vRender == null || !vRender.ensureSize(backend.pixelWidth, backend.pixelHeight)) {
			println("Failed to init render backend for $gpuMode")
			backend.destroy(inQuitSdl = false)
			return false
		}
		renderBackend = vRender

		host = ComposeRootHost(inDensity = backend.pixelDensity)
		host.attach()
		facade = ComposeNativeWindow(backend, gpuMode, initialTitle)
		navigationEventOwner.navigationEventDispatcher.addInput(backNavigationInput)

		// The render-bridge globals must point at THIS window's renderer while
		// its content composes/measures (first composition happens inside
		// setContent below).
		installGlobals()

		val vRecomposer = Recomposer(inScope.coroutineContext + frameClock)
		recomposer = vRecomposer
		recomposeJob = inScope.launch(frameClock) { vRecomposer.runRecomposeAndApplyChanges() }
		val vComposition = Composition(host.applier, vRecomposer)
		composition = vComposition

		val vUriHandler = object : androidx.compose.ui.platform.UriHandler {
			override fun openUri(uri: String) { openUrl(uri) }
		}
		val vPopupHost = createPopupHostState()
		popupHost = vPopupHost
		val vWindowScope = object : ComposeWindowScope {
			override val window: ComposeNativeWindow = facade
		}

		vComposition.setContent {
			CompositionLocalProvider(
				LocalComposeNativeWindow provides facade,
				LocalPopupHost provides vPopupHost,
				// Upstream vendored CompositionLocals.kt declares each of these as
				// `staticCompositionLocalOf<T> { noLocalProvidedFor("…") }` — reading
				// one without a Provider throws. Seed them all from the ComposeOwner.
				androidx.compose.ui.platform.LocalDensity provides host.density,
				androidx.compose.ui.platform.LocalLayoutDirection provides host.layoutDirection,
				androidx.compose.ui.platform.LocalFocusManager provides host.focusManager,
				androidx.compose.ui.platform.LocalGraphicsContext provides host.graphicsContext,
				androidx.compose.ui.platform.LocalViewConfiguration provides host.viewConfiguration,
				androidx.compose.ui.platform.LocalInputModeManager provides host.inputModeManager,
				androidx.compose.ui.platform.LocalHapticFeedback provides host.hapticFeedback,
				androidx.compose.ui.platform.LocalTextToolbar provides host.textToolbar,
				androidx.compose.ui.platform.LocalWindowInfo provides host.windowInfo,
				androidx.compose.ui.platform.LocalSoftwareKeyboardController provides host.softwareKeyboardController,
				@Suppress("DEPRECATION")
				androidx.compose.ui.platform.LocalTextInputService provides host.textInputService,
				@Suppress("DEPRECATION")
				androidx.compose.ui.platform.LocalClipboardManager provides
					androidx.compose.ui.platform.defaultClipboardManager,
				androidx.compose.ui.platform.LocalClipboard provides
					androidx.compose.ui.platform.defaultClipboard,
				androidx.compose.ui.platform.LocalFontFamilyResolver provides
					com.compose.desktop.native.text.font.projectFontFamilyResolver,
				androidx.compose.ui.platform.LocalUriHandler provides vUriHandler,
				androidx.compose.ui.platform.LocalAutofillTree provides host.autofillTree,
				// Desktop windows have no system bars / notch / IME insets — the
				// interface's all-zero defaults are exactly right.
				androidx.compose.ui.platform.LocalPlatformWindowInsets provides
					object : androidx.compose.ui.platform.PlatformWindowInsets {},
				// Runtime-level host defaults — this window's navigation-event
				// owner (SearchBar / sheets back plumbing), null for keys this
				// port has no equivalent of (viewmodel store).
				androidx.compose.runtime.LocalHostDefaultProvider provides
					remember {
						object : androidx.compose.runtime.HostDefaultProvider {
							@Suppress("UNCHECKED_CAST")
							override fun <T> getHostDefault(key: androidx.compose.runtime.HostDefaultKey<T>): T = when (key) {
								androidx.navigationevent.compose.NavigationEventDispatcherOwnerHostDefaultKey -> navigationEventOwner
								else -> null
							} as T
						}
					},
			) {
				Box(modifier = Modifier.fillMaxSize()) {
					// Read through the holder so Window() recompositions with a
					// new content lambda propagate into this composition.
					val vContent = contentHolder()
					with(vWindowScope) { vContent() }
					PopupLayer(vPopupHost)
				}
			}
		}
		return true
	}

	/* Points the render-bridge globals (text measurer / image loader /
	   viewport) at this window's renderer. Must be called before composing,
	   measuring, or drawing this window's tree. */
	fun installGlobals() {
		val vRender = renderBackend ?: return
		currentTextMeasurer = vRender.textMeasurer
		currentImageLoader = vRender.imageLoader
		com.compose.desktop.native.text.currentViewportWidth = backend.pixelWidth
		com.compose.desktop.native.text.currentViewportHeight = backend.pixelHeight
	}

	// ============
	//  Event handling (loop-routed, per window)

	fun onPointerEvent(inEvent: AppEvent.Pointer) {
		needsFrame = true
		installGlobals()
		val vType = when (inEvent.event.type) {
			PointerEventType.Press -> 1
			PointerEventType.Release -> 2
			else -> 0
		}
		val vBtn = when (inEvent.event.button) {
			PointerButton.Secondary -> 1
			PointerButton.Tertiary -> 2
			else -> 0
		}
		// SDL3 delivers mouse coords in logical points on HiDPI — multiply by
		// DPR so hit-testing lands in the pixel space layout uses.
		val vDpr = backend.pixelDensity
		val vPx = inEvent.event.x.toFloat() * vDpr
		val vPy = inEvent.event.y.toFloat() * vDpr
		if (inEvent.event.type == PointerEventType.Press) {
			popupHost?.notifyOutsidePress(vPx.toInt(), vPy.toInt())
		}
		host.onPointer(vPx, vPy, vType, vBtn)
		host.onPointerRaw(vPx, vPy, vType, vBtn, SDL_GetTicks().toLong())
		lastMouseX = vPx
		lastMouseY = vPy
		hasMousePos = true
	}

	fun onWheelEvent(inEvent: AppEvent.MouseWheel) {
		needsFrame = true
		installGlobals()
		val vDpr = backend.pixelDensity
		host.onWheel(inEvent.x.toFloat() * vDpr, inEvent.y.toFloat() * vDpr, inEvent.deltaX, inEvent.deltaY, SDL_GetTicks().toLong())
	}

	fun onKeyEvent(inEvent: AppEvent.Key) {
		needsFrame = true
		installGlobals()
		// Focused chain → window-level shortcuts → back navigation (Escape).
		if (!host.dispatchKeyEvent(inEvent.event) && !facade.dispatchKeyShortcut(inEvent.event)) {
			backNavigationInput.onKeyEvent(inEvent.event)
		}
	}

	fun onTextInputEvent(inEvent: AppEvent.TextInput) {
		needsFrame = true
		installGlobals()
		// Project onTextInput fields take the raw string; otherwise synthesise
		// typed KeyEvents so the vendored text stacks commit SDL's committed
		// (shift/layout/numpad-correct) text.
		if (!host.dispatchTextInput(inEvent.text)) {
			dispatchTypedText(host, inEvent.text)
		}
	}

	fun onResizedEvent() {
		needsFrame = true
		backend.updateWindowSize()
		facade.onResized()
	}

	/* OS close button / app-level Quit: honour the veto handler, then hand the
	   decision to the Window() declaration. */
	fun requestClose() {
		if (closeDispatched) return
		if (facade.requestCloseFromUser()) {
			closeDispatched = true
			onCloseRequest()
		}
	}

	// ============
	//  Frame pump

	fun shouldRender(): Boolean =
		needsFrame || (recomposer?.hasPendingWork == true) || onFrame != null

	fun renderFrame() {
		val vRender = renderBackend ?: return
		needsFrame = false
		if (recomposer?.hasPendingWork == true) needsFrame = true

		backend.updateWindowSize()
		vRender.ensureSize(backend.pixelWidth, backend.pixelHeight)
		host.setConstraints(backend.pixelWidth, backend.pixelHeight)
		host.measureAndLayout()

		// Hover refresh after layout (upstream skiko: SyntheticEventSender).
		if (hasMousePos) {
			host.onPointerRaw(lastMouseX, lastMouseY, 0, 0, SDL_GetTicks().toLong())
		}

		vRender.beginFrame(1f)
		vRender.drawRoot(host)
		if (onFrame != null && !onFrame.invoke(vRender, frameIndex)) {
			// Probe consumers end the app when their scenario completes.
			facade.close()
		}
		vRender.endFrame()
		frameIndex++

		// FPS — refreshed ~once a second, per window.
		fpsFrames++
		val vNowMs = SDL_GetTicks()
		val vElapsed = (vNowMs - fpsLastMs).toInt()
		if (vElapsed >= 1000) {
			val vFps = fpsFrames * 1000 / vElapsed
			facade.updateFps(vFps)
			SDL_SetWindowTitle(backend.window?.reinterpret(), "${facade.title} · $vFps FPS")
			fpsFrames = 0
			fpsLastMs = vNowMs
		}
	}

	fun resetFpsWindow() {
		fpsFrames = 0
		fpsLastMs = SDL_GetTicks()
	}

	fun destroy() {
		composition?.dispose()
		composition = null
		recomposer?.cancel()
		recomposer = null
		recomposeJob?.cancel()
		recomposeJob = null
		renderBackend?.destroy()
		renderBackend = null
		backend.destroy(inQuitSdl = false)
	}
}

// ==================
// MARK: Input helpers
// ==================

/* Escape → back: port of upstream desktop's BackNavigationEventInput. An
   unconsumed Escape KeyDown completes a back navigation on the dispatcher
   this input is registered with (BackHandler / PredictiveBackHandler
   consumers: m3 SearchBar collapse, dialog dismissal, …). */
private class BackNavigationInput : androidx.navigationevent.NavigationEventInput() {
	fun onKeyEvent(inEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
		if (inEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
			inEvent.key == androidx.compose.ui.input.key.Key.Escape
		) {
			dispatchOnBackCompleted()
			return true
		}
		return false
	}
}

/* Re-dispatches committed text (SDL TEXT_INPUT) as one synthetic typed
   KeyDown per Unicode codepoint (surrogate pairs folded). Key.Unknown +
   codePoint + no modifiers matches both vendored text stacks' isTypedEvent
   criteria; the SDL key mapper leaves codePoint = 0 on real key events, so
   the physical KeyDown and the synthetic one can never double-insert. */
private fun dispatchTypedText(inHost: ComposeRootHost, inText: String) {
	var vI = 0
	while (vI < inText.length) {
		val vHigh = inText[vI]
		val vCodepoint: Int
		if (vHigh.isHighSurrogate() && vI + 1 < inText.length && inText[vI + 1].isLowSurrogate()) {
			vCodepoint = 0x10000 + ((vHigh.code - 0xD800) shl 10) + (inText[vI + 1].code - 0xDC00)
			vI += 2
		} else {
			vCodepoint = vHigh.code
			vI += 1
		}
		inHost.dispatchKeyEvent(
			androidx.compose.ui.input.key.KeyEvent(
				key = androidx.compose.ui.input.key.Key.Unknown,
				type = androidx.compose.ui.input.key.KeyEventType.KeyDown,
				codePoint = vCodepoint,
			),
		)
	}
}
