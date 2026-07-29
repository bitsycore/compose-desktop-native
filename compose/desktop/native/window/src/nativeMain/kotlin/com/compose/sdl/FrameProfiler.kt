package com.compose.sdl

import kotlinx.cinterop.toKString
import sdl3.SDL_GetPerformanceCounter
import sdl3.SDL_GetPerformanceFrequency
import sdl3.SDL_GetTicks

// ==================
// MARK: FrameProfiler
// ==================

/** CDN_PROFILE=1 — per-phase timings, printed every ~2s of rendered frames.
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
			val vLine = "[profile] frames=$fFrames avg/max " + vParts.joinToString(" ") + "\n"
			val vFile = platform.posix.fopen(fPath, "a")
			if (vFile != null) {
				platform.posix.fputs(vLine, vFile)
				platform.posix.fclose(vFile)
			}
			fSum.clear(); fMax.clear()
			fFrames = 0
			fLastPrintMs = vNowMs
		}
	}
}

/** CDN_FORCERENDER=1 forces every frame to render so sustained steady-state
   timings can be measured on otherwise-idle screens (see TOOLING.md, frame
   profiler). */
internal val kForceRender: Boolean = platform.posix.getenv("CDN_FORCERENDER") != null
