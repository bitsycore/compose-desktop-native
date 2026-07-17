@file:OptIn(
	androidx.compose.ui.InternalComposeUiApi::class,
	androidx.compose.runtime.InternalComposeApi::class,
)

package com.compose.sdl

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
import androidx.lifecycle.enableSavedStateHandles
import com.compose.sdl.node.ComposeRootHost
import com.compose.sdl.res.currentImageLoader
import com.compose.sdl.text.currentTextMeasurer
import com.compose.sdl.window.LocalPopupHost
import com.compose.sdl.window.PopupLayer
import com.compose.sdl.window.createPopupHostState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.cinterop.toKString
import kotlinx.cinterop.reinterpret
import sdl3.SDL_Delay
import sdl3.SDL_GetPerformanceCounter
import sdl3.SDL_GetPerformanceFrequency
import sdl3.SDL_GetTicks
import sdl3.SDL_GetTicksNS
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
		appClock.sendFrame(frameClockNanos())
		yield()

		// ============
		//  Main loop
		var vGcLastTicks = SDL_GetTicks()
		var vRenderedSinceGc = false
		while (!runtime.exitRequested) {
			FrameProfiler.mark()
			// One virtual 16.6ms tick per loop iteration (no-op unless useVirtualFrameTime) —
			// all windows' clocks share the same timestamp this iteration.
			advanceVirtualFrame()
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
					is AppEvent.WindowActivation -> runtime.windowFor(vEvent.windowId)?.onActivationEvent(vEvent)
					is AppEvent.Pointer -> runtime.windowFor(vEvent.windowId)?.onPointerEvent(vEvent)
					is AppEvent.MouseWheel -> runtime.windowFor(vEvent.windowId)?.onWheelEvent(vEvent)
					is AppEvent.Key -> runtime.windowFor(vEvent.windowId)?.onKeyEvent(vEvent)
					is AppEvent.TextInput -> runtime.windowFor(vEvent.windowId)?.onTextInputEvent(vEvent)
					is AppEvent.Drop -> runtime.windowFor(vEvent.windowId)?.onDropEvent(vEvent)
				}
			}

			mainDispatcher.drainPending()
			FrameProfiler.phase("events")

			// ============
			//  App composition pump — Window()s may appear / disappear here.
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
					if (!vW.backend.vsyncEnabled) vAllVsync = false
				}
				FrameProfiler.phase("render")
			}
			runtime.reapDestroyed()
			FrameProfiler.frameDone(vAnyRendered)

			// ============
			//  Drain deferred native-resource disposals on the MAIN thread —
			//  textures/surfaces whose owner closed them or whose Cleaner fired
			//  on a GC worker enqueue here (SDL calls aren't thread-safe). This
			//  is the ownership path that makes the GC nudge below a mere
			//  backstop (ROADMAP.md item 1).
			com.compose.sdl.graphics.NativeReleaseQueue.drain()

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

			// ============
			//  Native-memory nudge. Renderer resources (Skia surfaces / images /
			//  fonts, SDL textures) are freed by Cleaners that only run when the
			//  Kotlin/Native GC collects — and a Compose app's Kotlin heap is
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

/* CDN_PROFILE=1 — per-phase timings, printed every ~2s of rendered frames.
   A named-phase SINGLETON so both the main loop (events / app / pump / render)
   AND renderFrame's sub-steps (render.layout / render.draw / render.present)
   report into one line. `mark()` resets the stopwatch; `phase(name)` charges
   the elapsed since the last mark/phase to that name. Measure first, optimize
   second — see ROADMAP.md. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal object FrameProfiler {
	// null until first checked; then true/false for the run's lifetime.
	private var fEnabled: Boolean? = null
	// Output file — resolved once from CDN_PROFILE. Writing to a file (not
	// stdout) lets GUI-subsystem apps (the demo links --subsystem,windows, so
	// it has no console) be profiled too. CDN_PROFILE=1 → "cdn_profile.log" in
	// the cwd; CDN_PROFILE=<path> → that path.
	private var fPath: String = "cdn_profile.log"
	val enabled: Boolean
		get() = fEnabled ?: run {
			val vEnv = platform.posix.getenv("CDN_PROFILE")?.toKString()
			if (vEnv != null && vEnv != "1" && vEnv.isNotEmpty()) fPath = vEnv
			(vEnv != null).also { fEnabled = it }
		}

	private val fFreq = SDL_GetPerformanceFrequency().toDouble()
	// Insertion-ordered so the printed line follows the call order.
	private val fSum = LinkedHashMap<String, Double>()
	private val fMax = LinkedHashMap<String, Double>()
	private var fFrames = 0
	private var fLastPrintMs = SDL_GetTicks()
	private var fMark = 0uL

	fun mark() { if (enabled) fMark = SDL_GetPerformanceCounter() }

	fun phase(inName: String) {
		if (!enabled) return
		val vNow = SDL_GetPerformanceCounter()
		val vMs = (vNow - fMark).toDouble() * 1000.0 / fFreq
		fSum[inName] = (fSum[inName] ?: 0.0) + vMs
		if (vMs > (fMax[inName] ?: 0.0)) fMax[inName] = vMs
		fMark = vNow
	}

	fun frameDone(inRendered: Boolean) {
		if (!enabled) return
		if (inRendered) fFrames++
		val vNowMs = SDL_GetTicks()
		if (vNowMs - fLastPrintMs >= 2000u && fFrames > 0) {
			val vParts = fSum.keys.map { vName ->
				val vAvg = (fSum[vName] ?: 0.0) / fFrames
				"$vName=${(vAvg * 100).toInt() / 100.0}/${((fMax[vName] ?: 0.0) * 100).toInt() / 100.0}ms"
			}
			// Per-frame draw-work averages (see DrawStats): what's inside `draw`.
			val vStats = com.compose.sdl.graphics.DrawStats
			val vDraw = "geo=${vStats.geometrySubmits / fFrames} verts=${vStats.vertices / fFrames} " +
				"masks=${vStats.maskRealizations / fFrames} text=${vStats.textDraws / fFrames} img=${vStats.imageBlits / fFrames}"
			val vLine = "[profile] frames=$fFrames avg/max " + vParts.joinToString(" ") + " | per-frame " + vDraw + "\n"
			val vFile = platform.posix.fopen(fPath, "a")
			if (vFile != null) {
				platform.posix.fputs(vLine, vFile)
				platform.posix.fclose(vFile)
			}
			vStats.reset()
			fSum.clear(); fMax.clear()
			fFrames = 0
			fLastPrintMs = vNowMs
		}
	}
}

/* Trigger a Kotlin/Native GC so Cleaner-managed renderer resources release
   their native memory (see the main loop's native-memory nudge). */
private val kForceRender: Boolean = platform.posix.getenv("CDN_FORCERENDER") != null

// ==================
// MARK: Probe / screenshot support (render-to-quiescence)
// ==================

/* Set BEFORE nativeComposeApp/nativeComposeWindow: every window's composition then runs
   under an InfiniteAnimationPolicy that CANCELS infinite animations, so
   rememberInfiniteTransition & co. freeze at their initial value — the same mechanism
   upstream's test rules use. Screenshot/parity runs enable it so looping screens can
   reach quiescence and capture deterministically. */
var disableInfiniteAnimations: Boolean = false

/* The cancelling policy: a coroutine that ends in CancellationException counts as
   cancelled (not failed), so only the animation coroutine stops — nothing propagates. */
private object CancelInfiniteAnimationsPolicy : androidx.compose.ui.platform.InfiniteAnimationPolicy {
	override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R =
		throw CancellationException("infinite animations are disabled (disableInfiniteAnimations)")
}

/* Set BEFORE nativeComposeApp/nativeComposeWindow: the composition + animation frame
   clocks advance a VIRTUAL 16.6ms per main-loop iteration instead of reading SDL's
   real-time ticks — the native mirror of the JVM parity leg's render(nanos) stepping.
   Animations then progress by exact per-frame deltas, so anything time-raced (e.g. a
   bring-into-view scroll interrupted mid-flight) resolves identically on every run and
   screenshots become deterministic. Input timestamps and FPS stay on real time. */
var useVirtualFrameTime: Boolean = false

private var virtualFrameNanos = 0L

private fun advanceVirtualFrame() {
	if (useVirtualFrameTime) virtualFrameNanos += 16_666_667L
}

// Timestamp for the composition frame clocks (recomposer + withFrameNanos animations).
private fun frameClockNanos(): Long =
	if (useVirtualFrameTime) virtualFrameNanos else SDL_GetTicksNS().toLong()

// Timestamp for the owner's node-animation clock — real path keeps the pre-existing
// ms-resolution SDL_GetTicks base so non-screenshot behaviour is bit-for-bit unchanged.
private fun animationClockNanos(): Long =
	if (useVirtualFrameTime) virtualFrameNanos else SDL_GetTicks().toLong() * 1_000_000L

// The window currently inside renderFrame — the loop is single-threaded, so a plain
// var is enough for onFrame probes to address "the window I'm being called for".
private var renderingWindow: WindowInstance? = null

/* From inside an onFrame callback: true while the just-rendered window still has pending
   work (recomposition, layout/draw invalidation, or an animation awaiting the next frame).
   Screenshot probes capture once this stays false for a few consecutive frames instead of
   at a fixed frame count. Outside onFrame it answers false. */
fun windowHasInvalidations(): Boolean = renderingWindow?.hasInvalidations() ?: false

@OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
private fun collectNativeGarbage() = kotlin.native.runtime.GC.collect()

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
	private var popupHost: com.compose.sdl.window.PopupHostState? = null

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
	private var fpsEma = 0.0
	private var fpsLastFrameMs = SDL_GetTicks()
	private var fpsLastTitleMs = 0uL

	// Escape → back (upstream desktop's BackNavigationEventInput): unconsumed
	// Escape completes a back navigation on THIS window's dispatcher.
	private val navigationEventOwner = object : androidx.navigationevent.NavigationEventDispatcherOwner {
		override val navigationEventDispatcher = androidx.navigationevent.NavigationEventDispatcher()
	}
	private val backNavigationInput = BackNavigationInput()

	// WINDOW-scoped architecture-components owner (Lifecycle + ViewModelStore +
	// SavedStateRegistry) — the same trio upstream desktop's
	// DefaultArchitectureComponentsOwner supplies through the window
	// PlatformContext (compose/ui skikoMain PlatformOwnerProvider.skiko.kt).
	// viewModel(), SavedStateHandle plumbing and navigation3's
	// rememberViewModelStoreNavEntryDecorator all resolve their parent owners
	// from this; destroy() moves it to DESTROYED and clears the store.
	private val architectureOwner = WindowArchitectureOwner()

	// Window focus / visibility → Lifecycle.State, Compose Desktop's mapping:
	// focused → RESUMED, visible unfocused → STARTED, hidden/minimised →
	// CREATED. Starts focused+visible (SDL fires FOCUS_GAINED right after
	// creation anyway; headless probe runs simply stay RESUMED).
	private var windowFocused = true
	private var windowVisible = true

	fun onActivationEvent(inEvent: AppEvent.WindowActivation) {
		inEvent.focused?.let { windowFocused = it }
		inEvent.visible?.let { windowVisible = it }
		architectureOwner.setLifecycleState(
			when {
				!windowVisible -> androidx.lifecycle.Lifecycle.State.CREATED
				windowFocused -> androidx.lifecycle.Lifecycle.State.RESUMED
				else -> androidx.lifecycle.Lifecycle.State.STARTED
			}
		)
		// Shown / restored / focus-gained also invalidate the contents — keep
		// the pre-lifecycle RedrawNeeded behaviour of these SDL events.
		needsFrame = true
	}

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
		// A layer whose content changed (OwnedLayer.invalidate) schedules a frame
		// even when nothing else (recompose / relayout) is pending — retained layers
		// need this so a draw-only state change still repaints.
		host.setInvalidationCallback { needsFrame = true }
		facade = ComposeNativeWindow(backend, gpuMode, initialTitle)
		navigationEventOwner.navigationEventDispatcher.addInput(backNavigationInput)

		// The render-bridge globals must point at THIS window's renderer while
		// its content composes/measures (first composition happens inside
		// setContent below).
		installGlobals()

		// Effect context for this window's composition — the screenshot flag injects the
		// infinite-animation-cancelling policy (see disableInfiniteAnimations above).
		var vEffectContext = inScope.coroutineContext + frameClock
		if (disableInfiniteAnimations) vEffectContext += CancelInfiniteAnimationsPolicy
		val vRecomposer = Recomposer(vEffectContext)
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
				androidx.compose.ui.platform.LocalClipboard provides
					androidx.compose.ui.platform.platformClipboard(),
				androidx.compose.ui.platform.LocalFontFamilyResolver provides
					com.compose.sdl.text.font.projectFontFamilyResolver,
				androidx.compose.ui.platform.LocalUriHandler provides vUriHandler,
				// Desktop windows have no system bars / notch / IME insets — the
				// interface's all-zero defaults are exactly right.
				androidx.compose.ui.platform.LocalPlatformWindowInsets provides
					object : androidx.compose.ui.platform.PlatformWindowInsets {},
				// The window's architecture-components owner backs all three arch
				// locals, exactly like upstream desktop's window PlatformContext:
				// - LocalLifecycleOwner: RESUMED for the window's whole life;
				//   lifecycle-aware content (NavDisplay, rememberLifecycleOwner, …)
				//   errors without it.
				// - LocalViewModelStoreOwner: window-scoped ViewModels (google
				//   lifecycle-viewmodel-compose's local is a plain composition
				//   local; the JB HostDefault route doesn't exist in the google
				//   artifacts this port ships).
				// - LocalSavedStateRegistryOwner: SavedStateHandle / rememberSaveable
				//   registry parent (nav3's ViewModelStoreNavEntryDecorator requires it).
				androidx.lifecycle.compose.LocalLifecycleOwner provides architectureOwner,
				androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides architectureOwner,
				androidx.savedstate.compose.LocalSavedStateRegistryOwner provides architectureOwner,
				// Runtime-level host defaults — this window's navigation-event
				// owner (SearchBar / sheets back plumbing). The viewmodel store
				// is provided through the plain LocalViewModelStoreOwner above
				// instead: the google lifecycle artifacts this port ships have no
				// HostDefault key for it (that mechanism is JB-variant-only).
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
		// First composition done (setContent is synchronous) — promote the
		// window lifecycle from CREATED to the focus/visibility-derived state.
		// Composition itself runs at CREATED so enableSavedStateHandles()
		// callers see a legal state, mirroring upstream desktop's
		// compose-first-resume-after ordering.
		architectureOwner.setLifecycleState(
			when {
				!windowVisible -> androidx.lifecycle.Lifecycle.State.CREATED
				windowFocused -> androidx.lifecycle.Lifecycle.State.RESUMED
				else -> androidx.lifecycle.Lifecycle.State.STARTED
			}
		)
		return true
	}

	/* Points the render-bridge globals (text measurer / image loader /
	   viewport) at this window's renderer. Must be called before composing,
	   measuring, or drawing this window's tree. */
	fun installGlobals() {
		val vRender = renderBackend ?: return
		currentTextMeasurer = vRender.textMeasurer
		currentImageLoader = vRender.imageLoader
		com.compose.sdl.text.currentViewportWidth = backend.pixelWidth
		com.compose.sdl.text.currentViewportHeight = backend.pixelHeight
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
		// SDL's committed text is the only layout-correct source of characters —
		// SDL key events carry UNSHIFTED keycodes (no uppercase, no numpad digits,
		// no dead keys). Synthesise typed KeyEvents so the vendored text stacks
		// (CoreTextField's isTypedEvent path and the state-based field's key
		// handler) commit it.
		dispatchTypedText(host, inEvent.text)
	}

	fun onDropEvent(inEvent: AppEvent.Drop) {
		needsFrame = true
		installGlobals()
		// SDL fires drop coords in logical points at DPR-1; scale to the pixel
		// space layout runs in (Option-B density flow) so hit-testing lands
		// where the pointer is.
		val vDpr = backend.pixelDensity
		when (inEvent.phase) {
			AppEvent.DropPhase.BEGIN -> host.onDropBegin()
			AppEvent.DropPhase.POSITION -> host.onDropPosition(inEvent.x * vDpr, inEvent.y * vDpr)
			AppEvent.DropPhase.FILE -> inEvent.data?.let { host.onDropFile(it) }
			AppEvent.DropPhase.TEXT -> inEvent.data?.let { host.onDropText(it) }
			AppEvent.DropPhase.COMPLETE -> host.onDropComplete()
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
		needsFrame || (recomposer?.hasPendingWork == true) || onFrame != null || kForceRender

	/* True while this window still has pending work: a state/layout/draw invalidation
	   (needsFrame), recomposer work, or a composition/node animation awaiting the next
	   frame. Sampled by windowHasInvalidations() from onFrame probes — the quiescence
	   signal for render-to-settle screenshot capture. */
	fun hasInvalidations(): Boolean =
		needsFrame || recomposer?.hasPendingWork == true ||
			frameClock.hasAwaiters || host.hasAnimationAwaiters()

	// TEMP measurement: CDN_FORCERENDER=1 forces every frame to render so
	// sustained steady-state timings can be measured on otherwise-idle screens.

	fun renderFrame() {
		val vRender = renderBackend ?: return
		needsFrame = false
		if (recomposer?.hasPendingWork == true) needsFrame = true

		backend.updateWindowSize()
		vRender.ensureSize(backend.pixelWidth, backend.pixelHeight)
		host.setConstraints(backend.pixelWidth, backend.pixelHeight)
		host.measureAndLayout()
		FrameProfiler.phase("  layout")

		// Hover refresh after layout (upstream skiko: SyntheticEventSender).
		if (hasMousePos) {
			host.onPointerRaw(lastMouseX, lastMouseY, 0, 0, SDL_GetTicks().toLong())
		}

		vRender.beginFrame(1f)
		vRender.drawRoot { canvas -> host.drawRoot(canvas) }
		if (onFrame != null) {
			renderingWindow = this
			val vContinue = onFrame.invoke(vRender, frameIndex)
			renderingWindow = null
			// Probe consumers end the app when their scenario completes.
			if (!vContinue) facade.close()
		}
		FrameProfiler.phase("  draw")
		vRender.endFrame()
		FrameProfiler.phase("  present")
		frameIndex++

		// FPS — instantaneous inter-frame rate, EMA-smoothed, title refreshed
		// ~4x/sec. This shows within ~2 rendered frames of ANY activity rather
		// than waiting for a full second of unbroken rendering to accumulate
		// (the old fixed-window counter never got its first update: bursty
		// interaction kept idling before 1s elapsed, and the idle-skip reset the
		// window each time — so FPS only appeared during a long enough page
		// transition). A dt outside 1..100ms is the first frame or a resume from
		// idle (the gap isn't a real frame interval), so it's not sampled — the
		// title just holds the last active rate while idle.
		val vNowMs = SDL_GetTicks()
		val vDt = (vNowMs - fpsLastFrameMs).toInt()
		fpsLastFrameMs = vNowMs
		if (vDt in 1..100) {
			val vInst = 1000.0 / vDt
			fpsEma = if (fpsEma <= 0.0) vInst else fpsEma * 0.9 + vInst * 0.1
			if ((vNowMs - fpsLastTitleMs).toInt() >= 250) {
				val vFps = (fpsEma + 0.5).toInt()
				facade.updateFps(vFps)
				SDL_SetWindowTitle(backend.window?.reinterpret(), "${facade.title} · $vFps FPS")
				fpsLastTitleMs = vNowMs
			}
		}
	}

	// Idle-skip calls this: drop the frame timer so the resume-from-idle frame's
	// dt (the whole idle gap) isn't sampled as a real interval. The EMA is left
	// intact so the title keeps showing the last active rate while idle.
	fun resetFpsWindow() {
		fpsLastFrameMs = SDL_GetTicks()
	}

	fun destroy() {
		composition?.dispose()
		composition = null
		// Window gone → DESTROYED lifecycle + ViewModels cleared (onCleared
		// runs), matching upstream desktop's window-scoped owner lifetime.
		architectureOwner.destroy()
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
// MARK: WindowArchitectureOwner
// ==================

/* Per-window architecture-components owner, modeled on upstream desktop's
   DefaultArchitectureComponentsOwner (compose/ui skikoMain
   PlatformOwnerProvider.skiko.kt): one object implements LifecycleOwner +
   ViewModelStoreOwner + SavedStateRegistryOwner (+ the SavedState-aware
   default ViewModel factory), so viewModel(), SavedStateHandle and
   rememberSaveable-backed registries all resolve against the WINDOW scope.

   The lifecycle registry uses createUnsafe (no main-thread enforcement) —
   same as the root owner this replaces; the SDL loop is single-threaded
   anyway. RESUMED from construction; destroy() moves to DESTROYED and clears
   the ViewModelStore (onCleared runs). SavedState restores from nothing (no
   process-death persistence on desktop — upstream desktop passes null too). */
private class WindowArchitectureOwner :
	androidx.lifecycle.LifecycleOwner,
	androidx.lifecycle.ViewModelStoreOwner,
	androidx.lifecycle.HasDefaultViewModelProviderFactory,
	androidx.savedstate.SavedStateRegistryOwner {

	override val lifecycle = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
	override val viewModelStore = androidx.lifecycle.ViewModelStore()

	private val savedStateController = androidx.savedstate.SavedStateRegistryController.create(this)
	override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
		get() = savedStateController.savedStateRegistry

	override val defaultViewModelProviderFactory = androidx.lifecycle.SavedStateViewModelFactory()
	override val defaultViewModelCreationExtras: androidx.lifecycle.viewmodel.CreationExtras
		get() = androidx.lifecycle.viewmodel.MutableCreationExtras().also {
			it[androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY] = this
			it[androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY] = this
		}

	init {
		savedStateController.performAttach()
		savedStateController.performRestore(null)
		// SavedStateHandle support for WINDOW-scoped ViewModels — must run while
		// the lifecycle is still ≤ CREATED; upstream desktop's ComposeContainer
		// calls this at the same point. With it, `viewModel { ... }` against the
		// window owner (the activityViewModels() analog) can take a
		// SavedStateHandle instead of needing a saved-state-less child owner.
		enableSavedStateHandles()
		// CREATED (not RESUMED) until the first composition is done — code that
		// runs enableSavedStateHandles() during composition (nav3's decorators,
		// rememberViewModelStoreOwner, …) requires INITIALIZED/CREATED, and
		// upstream desktop windows likewise compose first and resume after.
		lifecycle.currentState = androidx.lifecycle.Lifecycle.State.CREATED
	}

	/* Focus/visibility-driven state (see WindowInstance.onActivationEvent).
	   Ignored once destroyed — a stray SDL event during teardown must not
	   resurrect the registry. */
	fun setLifecycleState(inState: androidx.lifecycle.Lifecycle.State) {
		if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED &&
			lifecycle.currentState != inState
		) {
			lifecycle.currentState = inState
		}
	}

	fun destroy() {
		lifecycle.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
		viewModelStore.clear()
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
