package com.compose.sdl.renderer.skia

import com.compose.sdl.loadComposeResourceBytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

// ==================
// MARK: B6.3 spike — upstream skiko Paragraph on the mingw fork
// ==================

/** DE-RISK SPIKE (throwaway). Drives the upstream skiko paragraph stack
 *  (org.jetbrains.skia.paragraph.ParagraphBuilder / FontCollection /
 *  TypefaceFontProvider + HarfBuzz shaping) end-to-end, WITHOUT touching the
 *  port's hand-rolled SdlParagraph engine. The only unproven piece on the
 *  mingwX64 fork DLL — skiko Font/Typeface already work today. If this renders
 *  legible text, the full B6.3 migration is unblocked on Windows.
 *
 *  Registers the bundled data.kres NotoSans as the "Noto Sans" family (exactly
 *  the generic-family alias upstream's FontCache uses on the Linux platform
 *  mapping), so it also proves the family-resolution half of the bridge.
 *
 *  Returns a short diagnostic string; writes a PNG to [outPath]. */
@OptIn(ExperimentalForeignApi::class)
fun paragraphSpike(outPath: String): String {
	val width = 640
	val height = 200

	val fontBytes = loadComposeResourceBytes("font/NotoSans.ttf")
		?: return "paraspike: FAILED — could not load font/NotoSans.ttf from data.kres"

	// ============
	//  Font collection seeded with the bundled typeface under a family alias
	val fontMgr = FontMgr.default
	val typeface = fontMgr.makeFromData(Data.makeFromBytes(fontBytes), 0)
		?: return "paraspike: FAILED — makeFromData returned null"
	val provider = TypefaceFontProvider().apply { registerTypeface(typeface, "Noto Sans") }
	val fonts = FontCollection().apply {
		setDefaultFontManager(fontMgr)
		setAssetFontManager(provider)
	}

	// ============
	//  Build + lay out a paragraph via HarfBuzz shaping
	val textStyle = TextStyle().apply {
		color = Color.BLACK
		fontSize = 42f
		fontFamilies = arrayOf("Noto Sans")
	}
	val builder = ParagraphBuilder(ParagraphStyle(), fonts)
	builder.pushStyle(textStyle)
	builder.addText("Hello 123 Åé — wğ")
	builder.popStyle()
	val paragraph = builder.build()
	paragraph.layout(width.toFloat())

	// ============
	//  Rasterise to a white surface + snapshot PNG
	val surface = Surface.makeRasterN32Premul(width, height)
	surface.canvas.clear(Color.WHITE)
	paragraph.paint(surface.canvas, 12f, 12f)
	val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
		?: return "paraspike: FAILED — encodeToData(PNG) returned null"

	writeBytesToFile(outPath, png)
	return "paraspike: OK — font ${fontBytes.size}B, paragraph ${paragraph.maxWidth.toInt()}x${paragraph.height.toInt()} " +
		"(longestLine=${paragraph.longestLine.toInt()}, lines=${paragraph.lineNumber}), wrote ${png.size}B PNG -> $outPath"
}

/** Minimal binary file write via posix — the spike stands alone from the
 *  renderer's resource/screenshot plumbing. */
@OptIn(ExperimentalForeignApi::class)
private fun writeBytesToFile(path: String, bytes: ByteArray) {
	if (bytes.isEmpty()) return
	val handle = fopen(path, "wb") ?: return
	try {
		bytes.usePinned { pinned ->
			fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), handle)
		}
	} finally {
		fclose(handle)
	}
}
