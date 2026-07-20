package com.bitsycore.compose.sdl.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.Inflater

// ==================
// MARK: App icon — packaging (runtime .rgba blobs) + Windows .exe embed
// ==================
//
// The consumer analog of the repo-internal apidemo icon setup. From the PNGs
// declared in `compose.desktop.native { icon { light = files(...); dark = ... } }`
// this:
//   - decodes each PNG to a raw straight-alpha RGBA blob and bundles it into
//     data.kres under the icon dir (default "icon/") → the RUNTIME theme-aware
//     window icon the app selects with `nativeComposeWindow(icon = AppWindowIcon(
//     light = listOf("icon/<name>.rgba", ...)))`;
//   - on the mingwX64 target, assembles a multi-size .ico, compiles it with
//     windres, and links the resource object into the .exe (Explorer / pinned
//     taskbar icon).
//
// Everything is self-contained Kotlin — no Python / Pillow dependency for
// consumers. The only external tool is `windres` (mingw-w64 binutils), needed
// solely for the optional Windows .exe embed.

/* Deterministic output paths so the executable's linkerOpts (added reflectively
   in NativeApplication) and the windres task agree without sharing state. */
internal fun appIconWorkDir(inProject: Project): File =
	inProject.layout.buildDirectory.dir("composeNativeAppIcon").get().asFile

internal fun appIconObjectFile(inProject: Project): File =
	File(appIconWorkDir(inProject), "app_icon.o")

/* True when the consumer configured a Windows .exe embed (icon supplied + not
   disabled). Read at afterEvaluate. */
internal fun wantsWindowsIconEmbed(inExt: ComposeDesktopNativeExtension): Boolean =
	!inExt.icon.light.isEmpty && inExt.icon.embedWindowsIcon.getOrElse(true)

/**
 * Wires icon packaging for a consumer project: the runtime .rgba blobs into
 * every data.kres zip, and (mingw) the .ico → windres → resource-object chain
 * the executable links. Call from afterEvaluate, after the extension is read.
 */
internal fun installAppIcon(inProject: Project, inExt: ComposeDesktopNativeExtension) {
	if (inExt.icon.light.isEmpty) return

	val vPrefix = inExt.icon.resourceDir.getOrElse("icon")
	val vRgbaDir = File(appIconWorkDir(inProject), "rgba")

	val vRgbaTask = inProject.tasks.register("generateComposeNativeIconBlobs", GenerateRgbaIconsTask::class.java) { task ->
		task.description = "Decode app-icon PNGs to .rgba blobs for the runtime window icon."
		task.pngs.from(inExt.icon.light, inExt.icon.dark)
		task.outputDir.set(vRgbaDir)
	}

	// Bundle the blobs into every consumer data.kres zip (ResourcePackaging's
	// package<Variant>ComposeResources<Target>). matching{}.configureEach keeps
	// this lazy — zips are registered for targets that may not exist.
	inProject.tasks.matching { it.name.startsWith("package") && it.name.contains("ComposeResources") }
		.configureEach { zip ->
			zip.dependsOn(vRgbaTask)
			(zip as org.gradle.api.tasks.bundling.Zip).from(vRgbaDir) { spec -> spec.into(vPrefix) }
		}

	// Windows .exe icon: .ico → windres → object linked into the executable.
	if (wantsWindowsIconEmbed(inExt)) {
		val vIco = File(appIconWorkDir(inProject), "app_icon.ico")
		val vIcoTask = inProject.tasks.register("generateComposeNativeIco", GenerateIcoTask::class.java) { task ->
			task.description = "Assemble the multi-size Windows .ico from the app-icon PNGs."
			// The .exe icon is theme-agnostic — use the light set.
			task.pngs.from(inExt.icon.light)
			task.icoFile.set(vIco)
		}
		val vObj = appIconObjectFile(inProject)
		val vWindresTask = inProject.tasks.register("compileComposeNativeIconResource", CompileWindowsIconResourceTask::class.java) { task ->
			task.description = "Compile the .ico to a COFF resource object (windres) for the .exe icon."
			task.icoFile.set(vIco)
			task.objectFile.set(vObj)
			task.dependsOn(vIcoTask)
		}
		// Link the object last: the mingw link tasks must run windres first (the
		// path is referenced via linkerOpts, added reflectively in NativeApplication).
		inProject.tasks.matching {
			it.name == "linkDebugExecutableMingwX64" || it.name == "linkReleaseExecutableMingwX64" ||
				it.name == "runDebugExecutableMingwX64" || it.name == "runReleaseExecutableMingwX64"
		}.configureEach { it.dependsOn(vWindresTask) }
	}
}

// ==================
// MARK: Tasks
// ==================

abstract class GenerateRgbaIconsTask : DefaultTask() {
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val pngs: ConfigurableFileCollection

	@get:OutputDirectory
	abstract val outputDir: DirectoryProperty

	@TaskAction
	fun run() {
		val vDir = outputDir.get().asFile
		vDir.mkdirs()
		for (vPng in pngs.files.distinct()) {
			val (vW, vH, vRgba) = IconCodec.decodePngToRgba(vPng.readBytes())
			val vOut = File(vDir, vPng.nameWithoutExtension + ".rgba")
			vOut.writeBytes(IconCodec.rgbaBlob(vW, vH, vRgba))
		}
	}
}

abstract class GenerateIcoTask : DefaultTask() {
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val pngs: ConfigurableFileCollection

	@get:OutputFile
	abstract val icoFile: RegularFileProperty

	@TaskAction
	fun run() {
		val vOut = icoFile.get().asFile
		vOut.parentFile.mkdirs()
		vOut.writeBytes(IconCodec.buildIco(pngs.files.distinct()))
	}
}

abstract class CompileWindowsIconResourceTask : DefaultTask() {
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val icoFile: RegularFileProperty

	@get:OutputFile
	abstract val objectFile: RegularFileProperty

	@TaskAction
	fun run() {
		val vIco = icoFile.get().asFile
		val vObj = objectFile.get().asFile
		vObj.parentFile.mkdirs()
		// windres reads the .ico via the .rc; a bare filename + -I keeps it portable.
		val vRc = File(vObj.parentFile, "app_icon.rc")
		vRc.writeText("1 ICON \"${vIco.name}\"\n")
		val vProc = ProcessBuilder(
			"windres", "-I", vIco.parentFile.absolutePath,
			"-O", "coff", vRc.absolutePath, vObj.absolutePath,
		).redirectErrorStream(true).start()
		val vOutput = vProc.inputStream.bufferedReader().readText()
		val vCode = vProc.waitFor()
		if (vCode != 0) throw org.gradle.api.GradleException(
			"windres failed (exit $vCode):\n$vOutput\n" +
				"Install mingw-w64 binutils (provides windres) or set " +
				"compose.desktop.native { icon { embedWindowsIcon.set(false) } }.")
	}
}

// ==================
// MARK: IconCodec — PNG decode + RGBA blob + .ico assembly (pure Kotlin)
// ==================

internal object IconCodec {
	private val kPngSignature = byteArrayOf(
		0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
	)

	/* Decodes an 8-bit non-interlaced RGBA (type 6) or RGB (type 2) PNG to
	   straight-alpha RGBA bytes. Returns (width, height, pixels). */
	fun decodePngToRgba(inBytes: ByteArray): Triple<Int, Int, ByteArray> {
		require(inBytes.size >= 8 && inBytes.copyOfRange(0, 8).contentEquals(kPngSignature)) {
			"not a PNG (bad signature)"
		}
		var vWidth = 0
		var vHeight = 0
		var vBitDepth = 0
		var vColorType = 0
		var vInterlace = 0
		val vIdat = java.io.ByteArrayOutputStream()
		var vPos = 8
		while (vPos + 8 <= inBytes.size) {
			val vLen = be32(inBytes, vPos)
			val vType = String(inBytes, vPos + 4, 4, Charsets.US_ASCII)
			val vDataStart = vPos + 8
			when (vType) {
				"IHDR" -> {
					vWidth = be32(inBytes, vDataStart)
					vHeight = be32(inBytes, vDataStart + 4)
					vBitDepth = inBytes[vDataStart + 8].toInt() and 0xFF
					vColorType = inBytes[vDataStart + 9].toInt() and 0xFF
					vInterlace = inBytes[vDataStart + 12].toInt() and 0xFF
				}
				"IDAT" -> vIdat.write(inBytes, vDataStart, vLen)
				"IEND" -> vPos = inBytes.size
			}
			if (vPos < inBytes.size) vPos = vDataStart + vLen + 4 // + CRC
		}
		require(vBitDepth == 8) { "unsupported bit depth $vBitDepth (need 8)" }
		require(vInterlace == 0) { "interlaced PNG not supported" }
		val vChannels = when (vColorType) {
			6 -> 4
			2 -> 3
			else -> throw IllegalArgumentException("unsupported color type $vColorType (need 2 or 6)")
		}

		val vRaw = inflate(vIdat.toByteArray())
		val vStride = vWidth * vChannels
		val vOut = ByteArray(vWidth * vHeight * 4)
		var vPrev = ByteArray(vStride)
		var vRp = 0
		for (vY in 0 until vHeight) {
			val vFilter = vRaw[vRp].toInt() and 0xFF
			vRp++
			val vLine = ByteArray(vStride)
			System.arraycopy(vRaw, vRp, vLine, 0, vStride)
			vRp += vStride
			for (vI in 0 until vStride) {
				val vA = if (vI >= vChannels) vLine[vI - vChannels].toInt() and 0xFF else 0
				val vB = vPrev[vI].toInt() and 0xFF
				val vC = if (vI >= vChannels) vPrev[vI - vChannels].toInt() and 0xFF else 0
				val vX = vLine[vI].toInt() and 0xFF
				val vVal = when (vFilter) {
					0 -> vX
					1 -> vX + vA
					2 -> vX + vB
					3 -> vX + ((vA + vB) shr 1)
					4 -> vX + paeth(vA, vB, vC)
					else -> throw IllegalArgumentException("bad filter type $vFilter")
				}
				vLine[vI] = (vVal and 0xFF).toByte()
			}
			vPrev = vLine
			val vDst = vY * vWidth * 4
			if (vChannels == 4) {
				System.arraycopy(vLine, 0, vOut, vDst, vStride)
			} else {
				for (vX in 0 until vWidth) {
					val vS = vX * 3
					val vD = vDst + vX * 4
					vOut[vD] = vLine[vS]
					vOut[vD + 1] = vLine[vS + 1]
					vOut[vD + 2] = vLine[vS + 2]
					vOut[vD + 3] = 255.toByte()
				}
			}
		}
		return Triple(vWidth, vHeight, vOut)
	}

	/* An 8-byte little-endian [width][height] header + straight-alpha RGBA — the
	   format the runtime (WindowIcon.kt / SDL3Backend) reads. */
	fun rgbaBlob(inW: Int, inH: Int, inRgba: ByteArray): ByteArray {
		val vOut = ByteArray(8 + inRgba.size)
		le32(vOut, 0, inW)
		le32(vOut, 4, inH)
		System.arraycopy(inRgba, 0, vOut, 8, inRgba.size)
		return vOut
	}

	/* A multi-resolution .ico embedding each PNG verbatim (PNG-payload entries —
	   read by every modern Windows shell). */
	fun buildIco(inPngFiles: List<File>): ByteArray {
		data class Img(val w: Int, val h: Int, val bytes: ByteArray)
		val vImages = inPngFiles.map { vFile ->
			val vBytes = vFile.readBytes()
			val (vW, vH) = pngSize(vBytes)
			Img(vW, vH, vBytes)
		}.sortedBy { it.w }
		val vCount = vImages.size
		val vOut = java.io.ByteArrayOutputStream()
		// ICONDIR
		vOut.write(le16(0)); vOut.write(le16(1)); vOut.write(le16(vCount))
		var vOffset = 6 + 16 * vCount
		// ICONDIRENTRY per image
		for (vImg in vImages) {
			vOut.write(if (vImg.w >= 256) 0 else vImg.w and 0xFF)
			vOut.write(if (vImg.h >= 256) 0 else vImg.h and 0xFF)
			vOut.write(0)            // palette count
			vOut.write(0)            // reserved
			vOut.write(le16(1))      // color planes
			vOut.write(le16(32))     // bits per pixel
			vOut.write(le32be(vImg.bytes.size)) // bytes in resource (LE)
			vOut.write(le32be(vOffset))         // offset (LE)
			vOffset += vImg.bytes.size
		}
		for (vImg in vImages) vOut.write(vImg.bytes)
		return vOut.toByteArray()
	}

	private fun pngSize(inBytes: ByteArray): Pair<Int, Int> {
		// IHDR is the first chunk after the signature: width/height at bytes 16/20.
		return Pair(be32(inBytes, 16), be32(inBytes, 20))
	}

	private fun inflate(inData: ByteArray): ByteArray {
		val vInflater = Inflater()
		vInflater.setInput(inData)
		val vOut = java.io.ByteArrayOutputStream(inData.size * 4)
		val vBuf = ByteArray(64 * 1024)
		while (!vInflater.finished()) {
			val vN = vInflater.inflate(vBuf)
			if (vN == 0 && vInflater.needsInput()) break
			vOut.write(vBuf, 0, vN)
		}
		vInflater.end()
		return vOut.toByteArray()
	}

	private fun paeth(inA: Int, inB: Int, inC: Int): Int {
		val vP = inA + inB - inC
		val vPa = kotlin.math.abs(vP - inA)
		val vPb = kotlin.math.abs(vP - inB)
		val vPc = kotlin.math.abs(vP - inC)
		return when {
			vPa <= vPb && vPa <= vPc -> inA
			vPb <= vPc -> inB
			else -> inC
		}
	}

	private fun be32(inBuf: ByteArray, inOff: Int): Int =
		((inBuf[inOff].toInt() and 0xFF) shl 24) or
			((inBuf[inOff + 1].toInt() and 0xFF) shl 16) or
			((inBuf[inOff + 2].toInt() and 0xFF) shl 8) or
			(inBuf[inOff + 3].toInt() and 0xFF)

	private fun le32(inBuf: ByteArray, inOff: Int, inValue: Int) {
		inBuf[inOff] = (inValue and 0xFF).toByte()
		inBuf[inOff + 1] = ((inValue ushr 8) and 0xFF).toByte()
		inBuf[inOff + 2] = ((inValue ushr 16) and 0xFF).toByte()
		inBuf[inOff + 3] = ((inValue ushr 24) and 0xFF).toByte()
	}

	// Little-endian byte arrays for the ByteArrayOutputStream .ico writer.
	private fun le16(inValue: Int): ByteArray =
		byteArrayOf((inValue and 0xFF).toByte(), ((inValue ushr 8) and 0xFF).toByte())

	private fun le32be(inValue: Int): ByteArray = ByteArray(4).also { le32(it, 0, inValue) }
}
