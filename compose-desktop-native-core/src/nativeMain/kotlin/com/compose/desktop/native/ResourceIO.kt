package com.compose.desktop.native

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import sdl3.SDL_GetBasePath

// ==================
// MARK: composeResources archive IO
// ==================
// Resources are bundled in a single STORED zip "data.kres" next to
// the executable by the demo's Gradle Zip task (".kres" = a stored zip with a
// project-specific extension so the bundle reads as a single opaque blob next
// to the binary). At runtime we open the archive
// once via SDL_GetBasePath(), parse its central directory, then serve each
// resource by fseek+fread per entry — no whole-archive memory load, and no
// per-OS mmap shim (the OS page cache already keeps hot pages around).
//
// The reader supports STORED entries only (compression method 0). Non-stored
// entries return null. ZIP64 is not supported — the resource set is small
// enough that the standard 4 GB / 65535-entry limits don't apply.

@OptIn(ExperimentalForeignApi::class)
private class ComposeResourceArchive(private val fFile: CPointer<FILE>) {

	// Path → (offset of local file header, uncompressed size).
	private val fEntries: Map<String, LongArray>

	init {
		fEntries = readCentralDirectory()
	}

	fun has(inPath: String): Boolean = fEntries.containsKey(inPath)

	/* Reads an entry's raw bytes, or null if the entry is missing / not stored. */
	fun readBytes(inPath: String): ByteArray? {
		val vEntry = fEntries[inPath] ?: return null
		val vLocalOffset = vEntry[0]
		val vSize = vEntry[1]
		if (vSize <= 0L) return ByteArray(0)

		// Local file header layout: 30 fixed bytes, then filename_len + extra_len.
		// The CD's extra field can differ in size from the local extra field
		// (e.g. when a writer adds an extra to only one), so always read the local
		// header's lengths.
		val vHeader = ByteArray(30)
		if (!seekAndRead(vLocalOffset, vHeader)) return null
		if (le32(vHeader, 0) != 0x04034b50) return null
		val vNameLen = le16(vHeader, 26)
		val vExtraLen = le16(vHeader, 28)
		val vDataOffset = vLocalOffset + 30L + vNameLen + vExtraLen

		val vLen = vSize.toInt()
		val vBytes = ByteArray(vLen)
		if (!seekAndRead(vDataOffset, vBytes)) return null
		return vBytes
	}

	// ==================
	// MARK: Central directory parse
	// ==================

	private fun readCentralDirectory(): Map<String, LongArray> {
		// End of Central Directory record: 22 bytes minimum, with up to 65535
		// bytes of comment trailing. Read the last 64 KiB + 22 and scan backward
		// for the EOCD signature.
		fseek(fFile, 0.convert(), SEEK_END)
		val vFileLen: Long = ftell(fFile).convert()
		if (vFileLen < 22L) return emptyMap()
		val vTailLen = minOf(vFileLen, 65557L).toInt()
		val vTail = ByteArray(vTailLen)
		val vTailStart = vFileLen - vTailLen
		if (!seekAndRead(vTailStart, vTail)) return emptyMap()

		var vEocd = -1
		var i = vTailLen - 22
		while (i >= 0) {
			if (le32(vTail, i) == 0x06054b50) { vEocd = i; break }
			i--
		}
		if (vEocd < 0) return emptyMap()

		val vCdSize = le32(vTail, vEocd + 12).toLong() and 0xFFFFFFFFL
		val vCdOffset = le32(vTail, vEocd + 16).toLong() and 0xFFFFFFFFL
		if (vCdSize <= 0L || vCdOffset < 0L) return emptyMap()

		val vCd = ByteArray(vCdSize.toInt())
		if (!seekAndRead(vCdOffset, vCd)) return emptyMap()

		val vMap = HashMap<String, LongArray>()
		var vP = 0
		while (vP + 46 <= vCd.size) {
			if (le32(vCd, vP) != 0x02014b50) break
			val vMethod = le16(vCd, vP + 10)
			val vUncompressed = le32(vCd, vP + 24).toLong() and 0xFFFFFFFFL
			val vNameLen = le16(vCd, vP + 28)
			val vExtraLen = le16(vCd, vP + 30)
			val vCommentLen = le16(vCd, vP + 32)
			val vLocalOffset = le32(vCd, vP + 42).toLong() and 0xFFFFFFFFL

			if (vP + 46 + vNameLen > vCd.size) break
			val vName = vCd.decodeToString(vP + 46, vP + 46 + vNameLen)

			// Skip non-stored entries and directory placeholders (trailing '/').
			if (vMethod == 0 && !vName.endsWith("/")) {
				vMap[vName] = longArrayOf(vLocalOffset, vUncompressed)
			}
			vP += 46 + vNameLen + vExtraLen + vCommentLen
		}
		return vMap
	}

	// ==================
	// MARK: stdio helpers
	// ==================

	private fun seekAndRead(inOffset: Long, outBuf: ByteArray): Boolean {
		if (fseek(fFile, inOffset.convert(), SEEK_SET) != 0) return false
		if (outBuf.isEmpty()) return true
		return outBuf.usePinned { vPinned ->
			fread(vPinned.addressOf(0), 1.convert(), outBuf.size.convert(), fFile).toInt() == outBuf.size
		}
	}

	private fun le16(inBuf: ByteArray, inOff: Int): Int =
		(inBuf[inOff].toInt() and 0xFF) or
		((inBuf[inOff + 1].toInt() and 0xFF) shl 8)

	private fun le32(inBuf: ByteArray, inOff: Int): Int =
		(inBuf[inOff].toInt() and 0xFF) or
		((inBuf[inOff + 1].toInt() and 0xFF) shl 8) or
		((inBuf[inOff + 2].toInt() and 0xFF) shl 16) or
		((inBuf[inOff + 3].toInt() and 0xFF) shl 24)
}

// ==================
// MARK: Lazy singleton
// ==================

@OptIn(ExperimentalForeignApi::class)
private val kArchive: ComposeResourceArchive? by lazy {
	val vBase = SDL_GetBasePath()?.toKString() ?: return@lazy null
	if (vBase.isEmpty()) return@lazy null
	val vPath = vBase + "data.kres"
	val vFile = fopen(vPath, "rb") ?: run {
		println("ComposeResourceArchive: not found at $vPath")
		return@lazy null
	}
	ComposeResourceArchive(vFile)
}

// ==================
// MARK: Public API
// ==================

/* Reads a bundled resource's raw bytes by its path inside data.kres
   (e.g. "drawable/logo.png"). Returns null when the archive is missing or the
   entry isn't found. */
@OptIn(ExperimentalForeignApi::class)
fun loadComposeResourceBytes(inRelativePath: String): ByteArray? =
	kArchive?.readBytes(inRelativePath)

/* True when an entry exists in the bundled archive. Cheap (map lookup). */
@OptIn(ExperimentalForeignApi::class)
fun hasComposeResource(inRelativePath: String): Boolean =
	kArchive?.has(inRelativePath) == true
