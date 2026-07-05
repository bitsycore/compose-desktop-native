package androidx.compose.ui.platform

import androidx.compose.ui.text.AnnotatedString
import kotlinx.cinterop.toKString
import sdl3.SDL_GetClipboardText
import sdl3.SDL_HasClipboardText
import sdl3.SDL_SetClipboardText
import sdl3.SDL_free

// ==================
// MARK: PlatformClipboard native actuals (SDL3-backed)
// ==================

/*
 * Native (linux / macOS / windows via mingwX64) actuals for vendored
 * `androidx.compose.ui.platform.ClipboardManager.kt`'s expect classes +
 * `Clipboard.kt`'s interface. Everything flows through SDL3's clipboard
 * APIs (SDL_GetClipboardText / SDL_SetClipboardText / SDL_HasClipboardText).
 *
 * The OS clipboard exchanges plain text, so styled `AnnotatedString` /
 * `ClipEntry` payloads are flattened to `text` on set and rehydrated as
 * unstyled `AnnotatedString` / `ClipEntry.withPlainText` on get.
 *
 * Mirrors upstream's macosMain / iosMain / desktopMain actuals. Combined
 * here in one nativeMain file because we have one source set for
 * macOS / Linux / Windows.
 */

// ============
//  `expect class NativeClipboard` — no native handle on linux/windows.
//  `Any` matches upstream's wasmMain / iosMain actuals shape (they
//  typealias to a platform-specific type — we typealias to Any since
//  there's no portable one).
actual typealias NativeClipboard = Any

private val nativeClipboardSentinel: NativeClipboard = Any()

// ============
//  `expect class ClipMetadata` — opaque metadata. Single shared instance
//  satisfies the type — we don't expose anything beyond presence.
actual class ClipMetadata internal constructor()

private val sharedClipMetadata = ClipMetadata()

// ============
//  `expect class ClipEntry` — plain-text bag.
//  Mirrors macos / desktop actuals: optional plainText + a
//  `withPlainText` factory.

actual class ClipEntry internal constructor() {
	actual val clipMetadata: ClipMetadata get() = sharedClipMetadata

	internal var plainText: String? = null

	/** Project access for `ClipEntry.getPlainText()` (matches upstream's
	 *  macOS / iOS / wasm actuals which expose the same accessor). */
	fun getPlainText(): String? = plainText

	companion object {
		fun withPlainText(text: String): ClipEntry = ClipEntry().apply { plainText = text }
	}
}

// ============
//  SDL3 read / write primitives

private fun sdlReadText(): String? {
	if (!SDL_HasClipboardText()) return null
	val vRaw = SDL_GetClipboardText() ?: return null
	val vText = vRaw.toKString()
	SDL_free(vRaw)
	return vText.ifEmpty { null }
}

private fun sdlWriteText(text: String) {
	SDL_SetClipboardText(text)
}

// ============
//  ClipboardManager actual (synchronous, AnnotatedString-typed)

@Suppress("DEPRECATION")
private class SDL3ClipboardManager : ClipboardManager {
	override fun setText(annotatedString: AnnotatedString) = sdlWriteText(annotatedString.text)

	override fun getText(): AnnotatedString? = sdlReadText()?.let { AnnotatedString(it) }

	override fun hasText(): Boolean = !sdlReadText().isNullOrEmpty()

	override fun getClip(): ClipEntry? = sdlReadText()?.let { ClipEntry.withPlainText(it) }

	override fun setClip(clipEntry: ClipEntry?) {
		sdlWriteText(clipEntry?.plainText.orEmpty())
	}

	override val nativeClipboard: NativeClipboard get() = nativeClipboardSentinel
}

// ============
//  Clipboard actual (suspend, ClipEntry-typed)

private class SDL3Clipboard : Clipboard {
	override suspend fun getClipEntry(): ClipEntry? =
		sdlReadText()?.let { ClipEntry.withPlainText(it) }

	override suspend fun setClipEntry(clipEntry: ClipEntry?) {
		sdlWriteText(clipEntry?.plainText.orEmpty())
	}

	override val nativeClipboard: NativeClipboard get() = nativeClipboardSentinel
}

// ============
//  Factories — declared in this file (no expect/actual since there's
//  only one native target; upstream uses these as expect-fns when
//  multiple platforms each provide their own factory).

@Suppress("DEPRECATION")
internal fun createPlatformClipboardManager(): ClipboardManager = SDL3ClipboardManager()

internal fun createPlatformClipboard(): Clipboard = SDL3Clipboard()

// ============
//  Project globals — replace the old `currentClipboard: Clipboard`
//  variable with module-level `ClipboardManager` + `Clipboard` accessors.
//  Inside composition prefer `LocalClipboardManager.current` /
//  `LocalClipboard.current`. The accessors below let non-suspend call
//  sites (event handlers, plain functions) reach the clipboard.

private val sharedClipboardManager: ClipboardManager = SDL3ClipboardManager()
private val sharedClipboard: Clipboard = SDL3Clipboard()

/** Non-composable access to the SDL3-backed [ClipboardManager]. */
val platformClipboardManager: ClipboardManager get() = sharedClipboardManager

/** Non-composable access to the SDL3-backed [Clipboard] (suspend API). */
val platformClipboard: Clipboard get() = sharedClipboard

// Non-composable defaults consumed by ComposeWindow's `CompositionLocalProvider`
// wrap (used to seed `LocalClipboardManager` / `LocalClipboard` — upstream's
// vendored CompositionLocals.kt defaults for both throw noLocalProvidedFor).
val defaultClipboardManager: ClipboardManager get() = sharedClipboardManager
val defaultClipboard: Clipboard get() = sharedClipboard

/** Project alias — non-composable event handlers / coroutines reach the SDL3
 *  clipboard through this. Inside composition prefer `LocalClipboardManager.current`. */
@Suppress("DEPRECATION")
val currentClipboard: ClipboardManager get() = sharedClipboardManager
