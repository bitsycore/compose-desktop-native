package com.compose.sdl

import kotlinx.cinterop.toKString
import sdl3.SDL_GetPerformanceCounter
import sdl3.SDL_GetPerformanceFrequency
import sdl3.SDL_GetTicks

// ==================
// MARK: FrameProfiler
// ==================

/** CDN_PROFILE=1 - per-phase timings, printed every ~2s of rendered frames.
   A named-phase SINGLETON so both the main loop (events / app / pump / render)
   AND renderFrame's sub-steps (render.layout / render.draw / render.present)
   report into one line. `mark()` resets the stopwatch; `phase(name)` charges
   the elapsed since the last mark/phase to that name. Measure first, optimize
   second - see ROADMAP.md. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal object FrameProfiler {
	// null until first checked; then true/false for the run's lifetime.
	private var mEnabled: Boolean? = null
	// Output file - resolved once from CDN_PROFILE. Writing to a file (not
	// stdout) lets GUI-subsystem apps (the demo links --subsystem,windows, so
	// it has no console) be profiled too. CDN_PROFILE=1 → "cdn_profile.log" in
	// the cwd; CDN_PROFILE=<path> → that path.
	private var mPath: String = "cdn_profile.log"
	val enabled: Boolean
		get() = mEnabled ?: run {
			val vEnv = platform.posix.getenv("CDN_PROFILE")?.toKString()
			if (vEnv != null && vEnv != "1" && vEnv.isNotEmpty()) mPath = vEnv
			(vEnv != null).also { mEnabled = it }
		}

	private val mFreq = SDL_GetPerformanceFrequency().toDouble()
	// Insertion-ordered so the printed line follows the call order.
	private val mSum = LinkedHashMap<String, Double>()
	private val mMax = LinkedHashMap<String, Double>()
	private var mFrames = 0
	private var mLastPrintMs = SDL_GetTicks()
	private var mMark = 0uL

	fun mark() { if (enabled) mMark = SDL_GetPerformanceCounter() }

	fun phase(inName: String) {
		if (!enabled) return
		val vNow = SDL_GetPerformanceCounter()
		val vMs = (vNow - mMark).toDouble() * 1000.0 / mFreq
		mSum[inName] = (mSum[inName] ?: 0.0) + vMs
		if (vMs > (mMax[inName] ?: 0.0)) mMax[inName] = vMs
		mMark = vNow
	}

	fun frameDone(inRendered: Boolean) {
		if (!enabled) return
		if (inRendered) mFrames++
		val vNowMs = SDL_GetTicks()
		if (vNowMs - mLastPrintMs >= 2000u && mFrames > 0) {
			val vParts = mSum.keys.map { vName ->
				val vAvg = (mSum[vName] ?: 0.0) / mFrames
				"$vName=${(vAvg * 100).toInt() / 100.0}/${((mMax[vName] ?: 0.0) * 100).toInt() / 100.0}ms"
			}
			val vLine = "[profile] frames=$mFrames avg/max " + vParts.joinToString(" ") + "\n"
			val vFile = platform.posix.fopen(mPath, "a")
			if (vFile != null) {
				platform.posix.fputs(vLine, vFile)
				platform.posix.fclose(vFile)
			}
			mSum.clear(); mMax.clear()
			mFrames = 0
			mLastPrintMs = vNowMs
		}
	}
}

/** CDN_FORCERENDER=1 forces every frame to render so sustained steady-state
   timings can be measured on otherwise-idle screens (see TOOLING.md, frame
   profiler). */
internal val kForceRender: Boolean = platform.posix.getenv("CDN_FORCERENDER") != null
