package androidx.compose.ui.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import sdl3.SDL_GetClipboardData
import sdl3.SDL_GetClipboardText
import sdl3.SDL_HasClipboardData
import sdl3.SDL_HasClipboardText
import sdl3.SDL_SetClipboardData
import sdl3.SDL_SetClipboardText
import sdl3.SDL_free

// ==================
// MARK: SDL3-backed Clipboard actual
// ==================

/* Native actuals for the vendored ClipEntry / ClipMetadata / NativeClipboard
   expect classes plus a Clipboard implementation over SDL3's clipboard
   surface. Plain text flows through SDL_[GS]etClipboardText; PNG images
   through SDL_[GS]etClipboardData("image/png") with a callback that serves
   bytes on demand and a paired cleanup that frees them.

   Combined in one file because macOS / Linux / mingwX64 all share this
   source set — there's no meaningful per-target divergence. */

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
//  `expect class ClipEntry` — plain-text or image bag.
//  Mirrors macos / desktop actuals: optional plainText + a `withPlainText`
//  factory. Extended with `imageBytes` (PNG-encoded) + `withImage` / `getImage`
//  for the SDL3 SDL_SetClipboardData / SDL_GetClipboardData image path.

actual class ClipEntry internal constructor() {
	actual val clipMetadata: ClipMetadata get() = sharedClipMetadata

	internal var plainText: String? = null
	internal var imageBytes: ByteArray? = null

	/** Project access for `ClipEntry.getPlainText()` (matches upstream's
	 *  macOS / iOS / wasm actuals which expose the same accessor). */
	fun getPlainText(): String? = plainText

	/** PNG-encoded bytes if this entry carries an image, else null. */
	fun getImage(): ByteArray? = imageBytes

	companion object {
		fun withPlainText(text: String): ClipEntry = ClipEntry().apply { plainText = text }

		/** Wrap already-PNG-encoded image bytes for [Clipboard.setClipEntry].
		 *  The caller owns the encoding — Skia can produce these via
		 *  `Image.encodeToData(EncodedImageFormat.PNG)`, SDL3_image via
		 *  `IMG_SavePNG_IO`, or the bytes may come from an app resource. */
		fun withImage(pngBytes: ByteArray): ClipEntry = ClipEntry().apply { imageBytes = pngBytes }
	}
}

// ============
//  SDL3 read / write primitives — text via SDL_[GS]etClipboardText, image
//  via SDL_[GS]etClipboardData with MIME "image/png". SDL_SetClipboardData
//  is callback-based (the app hands SDL a serve function that returns bytes
//  when another app requests them), so the outbound image lives in a module-
//  level nativeHeap allocation the callback reads and the cleanup releases.

private const val kImageMime = "image/png"

// The bytes currently offered to the OS clipboard (from the last setClipEntry
// call with an image). Held on nativeHeap because SDL keeps the callback alive
// until the clipboard is cleared or another SDL_SetClipboardData replaces us —
// a Kotlin heap reference would break as soon as the caller's coroutine
// returned. Read by [fClipboardDataCallback], freed by [fClipboardCleanupCallback].
private var fOutgoingImagePtr: CPointer<ByteVar>? = null
private var fOutgoingImageSize: Int = 0

// SDL fires this on the main event thread when another app asks the clipboard
// for a MIME we advertised. We serve back the pointer + size we stashed at
// SDL_SetClipboardData time. Return NULL if the MIME doesn't match.
private val fClipboardDataCallback = staticCFunction {
		userdata: COpaquePointer?,
		mimeType: CPointer<ByteVar>?,
		sizePtr: CPointer<platform.posix.size_tVar>?,
	->
	userdata.hashCode()                                             // unused
	val vRequested = mimeType?.toKString() ?: return@staticCFunction null
	if (vRequested != kImageMime) return@staticCFunction null
	val vPtr = fOutgoingImagePtr ?: return@staticCFunction null
	sizePtr?.pointed?.value = fOutgoingImageSize.toULong()
	vPtr as COpaquePointer
}

// SDL calls this when the clipboard we set is cleared or overwritten (by us
// or another app). Frees the nativeHeap allocation.
private val fClipboardCleanupCallback = staticCFunction {
		userdata: COpaquePointer?,
	->
	userdata.hashCode()                                             // unused
	fOutgoingImagePtr?.let { nativeHeap.free(it.rawValue) }
	fOutgoingImagePtr = null
	fOutgoingImageSize = 0
	Unit
}

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

// Reads the current image/png clipboard entry into a fresh Kotlin ByteArray,
// or null when no image is available. SDL_GetClipboardData returns a fresh
// SDL_malloc'd buffer we must SDL_free; we memcpy into a Kotlin ByteArray so
// the returned bytes outlive the SDL allocation.
private fun sdlReadImage(): ByteArray? = memScoped {
	if (!SDL_HasClipboardData(kImageMime)) return@memScoped null
	val vSizeVar = alloc<platform.posix.size_tVar>()
	val vRaw = SDL_GetClipboardData(kImageMime, vSizeVar.ptr) ?: return@memScoped null
	val vSize = vSizeVar.value.toInt()
	if (vSize <= 0) { SDL_free(vRaw); return@memScoped null }
	val vBytes = ByteArray(vSize)
	val vSrc = vRaw.reinterpret<ByteVar>()
	for (i in 0 until vSize) vBytes[i] = vSrc[i]
	SDL_free(vRaw)
	vBytes
}

// Publishes [bytes] to the OS clipboard under MIME "image/png". Copies the
// bytes into a nativeHeap buffer so the callback can serve them even after
// the coroutine that called setClipEntry has returned. Frees the previous
// buffer (if any) — SDL will fire the cleanup callback for the previous
// registration once this new one supersedes it, keeping the invariant.
private fun sdlWriteImage(bytes: ByteArray) {
	// Free any previously-held buffer proactively (the cleanup callback also
	// fires from SDL, but doing it eagerly here handles same-thread re-set).
	fOutgoingImagePtr?.let { nativeHeap.free(it.rawValue) }

	val vPtr = nativeHeap.allocArray<ByteVar>(bytes.size)
	for (i in bytes.indices) vPtr[i] = bytes[i]
	fOutgoingImagePtr = vPtr
	fOutgoingImageSize = bytes.size

	memScoped {
		val vMimeArray = allocArray<CPointerVar<ByteVar>>(1)
		vMimeArray[0] = kImageMime.cstr.ptr
		SDL_SetClipboardData(
			fClipboardDataCallback,
			fClipboardCleanupCallback,
			null,
			vMimeArray,
			1.toULong(),
		)
	}
}

// Read whichever payload the OS clipboard currently holds, preferring an
// image if present. Returns null when the clipboard has nothing we can
// surface. Used by both actuals.
private fun sdlReadEntry(): ClipEntry? {
	val vImage = sdlReadImage()
	if (vImage != null) return ClipEntry.withImage(vImage)
	val vText = sdlReadText() ?: return null
	return ClipEntry.withPlainText(vText)
}

// Push whichever payload the entry carries. Image wins when both are set —
// text-and-image simultaneously has no portable OS-clipboard meaning.
private fun sdlWriteEntry(entry: ClipEntry?) {
	if (entry == null) { sdlWriteText(""); return }
	val vImage = entry.imageBytes
	if (vImage != null) sdlWriteImage(vImage) else sdlWriteText(entry.plainText.orEmpty())
}

// The SDL3-backed Clipboard implementation. Singleton because SDL's clipboard
// is process-global — allocating a new one per Window() would only waste
// memory. Consumed by :window when seeding LocalClipboard.
private object SDL3Clipboard : Clipboard {
	override suspend fun getClipEntry(): ClipEntry? = sdlReadEntry()
	override suspend fun setClipEntry(clipEntry: ClipEntry?) { sdlWriteEntry(clipEntry) }
	override val nativeClipboard: NativeClipboard get() = nativeClipboardSentinel
}

fun platformClipboard(): Clipboard = SDL3Clipboard
