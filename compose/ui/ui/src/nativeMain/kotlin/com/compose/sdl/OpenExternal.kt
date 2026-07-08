package com.compose.sdl

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import sdl3.SDL_OpenURL

// ==================
// MARK: Open external (URLs / file manager)
// ==================

/* OS handoffs run OFF the SDL main loop thread: SDL_OpenURL is synchronous —
   ShellExecuteEx + per-thread COM init on Windows (a cold Explorer start
   blocks for a second+), NSWorkspace on macOS, fork/exec of xdg-open on
   Linux. Called from a click handler on the main thread it froze the whole
   UI until the file manager was up. None of the three platform backends
   requires the main thread, so a small background scope launches them and
   (optionally) posts the result back to Main. */
private val fOpenExternalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/* Opens a URL or file:// URI with the OS's default handler (browser, file
   manager, …) via SDL_OpenURL — asynchronously, so the UI keeps pumping while
   the handler app starts. [inOnResult] (optional) is invoked on the main
   thread with SDL_OpenURL's success flag. */
@OptIn(ExperimentalForeignApi::class)
fun openUrl(inUrl: String, inOnResult: ((Boolean) -> Unit)? = null) {
	fOpenExternalScope.launch {
		val vOk = SDL_OpenURL(inUrl)
		if (inOnResult != null) withContext(Dispatchers.Main) { inOnResult(vOk) }
	}
}

/* Reveals a saved file in the OS file manager by opening its containing folder
   (Explorer on Windows, Finder on macOS, the default file manager on Linux).
   SDL has no "select the file" primitive, so we open the folder. Asynchronous
   like [openUrl]; [inOnResult] reports success on the main thread. */
fun revealInFileManager(inPath: String, inOnResult: ((Boolean) -> Unit)? = null) {
	val vNorm = inPath.trim().replace('\\', '/')
	val vDir = vNorm.substringBeforeLast('/', "")
	val vTarget = if (vDir.isEmpty()) vNorm else vDir
	openUrl("file:///$vTarget", inOnResult)
}

/* The OS file-manager's name, for menu labels ("Explorer" / "Finder" / …). */
@OptIn(ExperimentalNativeApi::class)
fun fileManagerName(): String = when (Platform.osFamily) {
	OsFamily.MACOSX -> "Finder"
	OsFamily.WINDOWS -> "Explorer"
	else -> "file manager"
}
