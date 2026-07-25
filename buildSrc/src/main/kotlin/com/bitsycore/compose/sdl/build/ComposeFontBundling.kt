package com.bitsycore.compose.sdl.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import java.net.URI

// ==================
// MARK: Configuration
// ==================

/**
 * Per-app knobs for [registerComposeFontBundling]. EVERYTHING is opt-in — with no
 * flag set the call registers no tasks and stages nothing.
 */
class ComposeFontBundlingConfig {
	/**
	 * Bundle font/NotoSans.ttf, the default UI font the text renderers load at startup.
	 * -PbundleDefaultFont=false still skips it (system-font fallback).
	 */
	var bundleNotoSans: Boolean = false

	/**
	 * Always bundle font/NotoSansMono.ttf — for apps that load the mono font through
	 * their own seam (apidemo's body font, Fonts.kt), invisible to auto-detection.
	 */
	var bundleNotoSansMono: Boolean = false

	/**
	 * Bundle font/NotoSansMono.ttf only when `FontFamily.Monospace` appears in the
	 * app's Kotlin sources (it backs the generic monospace family at runtime).
	 */
	var autoDetectNotoSansMono: Boolean = false

	/**
	 * Bundle one Material Symbols font per style whose call sites appear in the app's
	 * Kotlin sources (MaterialSymbolsOutlined / Rounded / Sharp).
	 */
	var bundleMaterialSymbols: Boolean = false

	/**
	 * Subset each bundled Material Symbols font to the glyphs the sources reference
	 * (scripts/subset-material-symbols.py picks the codepoints; the subset task uses
	 * hb-subset when present, else fontTools via Python — auto-installed — and only
	 * bundles the full font if neither is available). Needs [bundleMaterialSymbols];
	 * still gated by -PsubsetIcons.
	 */
	var enableIconSubsetting: Boolean = false
}

// A style's font is bundled only when its call sites appear in the sources.
private val kAllStyles = listOf(
	"Outlined" to Regex("\\bMaterialSymbolsOutlined\\b"),
	"Rounded" to Regex("\\bMaterialSymbolsRounded\\b"),
	"Sharp" to Regex("\\bMaterialSymbolsSharp\\b"),
)

// FontFamily.Monospace call sites → NotoSansMono is worth bundling.
private val kMonospaceRegex = Regex("\\bFontFamily\\.Monospace\\b")

// ==================
// MARK: Entry point
// ==================

/**
 * Wires the app font pipeline shared by :demo and :apidemo. Everything is OPT-IN via
 * [ComposeFontBundlingConfig] — a bare call is a no-op.
 *
 * - Registers downloadNotoFonts (Noto Sans + Noto Sans Mono variable fonts →
 *   build/fonts/) when a Noto font is bundled.
 * - Adds the opted-in font/ entries to every data.kres Zip task — the bridge plugin's
 *   package<Variant>ComposeResources<Target> tasks or an app's own copy* tasks
 *   (matching{} is lazy: the plugin's tasks appear in its afterEvaluate). Material
 *   Symbols fonts are hb-subset to the used glyphs when subsetting is enabled.
 * - Stages the same fonts (always full, never subset) onto the JVM classpath via
 *   jvmProcessResources: the :material-symbols JVM actual loads font/<Style>.ttf, and
 *   the parity harness's JVM leg renders with the same NotoSans as the native leg
 *   (P0.3, RENDERER.md §8).
 */
fun Project.registerComposeFontBundling(configure: ComposeFontBundlingConfig.() -> Unit) {
	val vConfig = ComposeFontBundlingConfig().apply(configure)

	// ============
	//  What to bundle
	// ============
	val vBundleSans = vConfig.bundleNotoSans &&
			providers.gradleProperty("bundleDefaultFont").map(String::toBoolean).getOrElse(true)
	val vBundleMono = vConfig.bundleNotoSansMono ||
			(vConfig.autoDetectNotoSansMono && sourcesMatch(kMonospaceRegex))
	val vUsedStyles = if (vConfig.bundleMaterialSymbols) detectUsedStyles() else emptyList()
	if (!vBundleSans && !vBundleMono && vUsedStyles.isEmpty()) return

	val vSubsetIcons = vConfig.enableIconSubsetting && vUsedStyles.isNotEmpty() &&
			providers.gradleProperty("subsetIcons").map(String::toBoolean).getOrElse(false)
	// One subset task per used style, shared by all variant×target archives.
	val vSubsetTasksByStyle = if (vSubsetIcons) registerIconSubsetPipeline(vUsedStyles) else emptyMap()

	// ============
	//  Noto downloads
	// ============
	val vNotoSansFile = layout.buildDirectory.file("fonts/NotoSans.ttf")
	val vNotoMonoFile = layout.buildDirectory.file("fonts/NotoSansMono.ttf")
	val vNotoFiles = buildList {
		if (vBundleSans) add(vNotoSansFile)
		if (vBundleMono) add(vNotoMonoFile)
	}
	val vDownloadNotoFonts = if (vNotoFiles.isEmpty()) null else tasks.register("downloadNotoFonts") {
		description = "Download the Google Noto variable fonts (Sans + SansMono) to build/fonts/."
		val vDownloads = listOf(
			"https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth%2Cwght%5D.ttf"
					to vNotoSansFile.get().asFile,
			"https://raw.githubusercontent.com/google/fonts/main/ofl/notosansmono/NotoSansMono%5Bwdth%2Cwght%5D.ttf"
					to vNotoMonoFile.get().asFile,
		)
		outputs.files(vDownloads.map { it.second })
		doLast {
			for ((vUrl, vOut) in vDownloads) {
				if (vOut.exists() && vOut.length() > 0) continue
				vOut.parentFile.mkdirs()
				println("Downloading $vUrl")
				URI(vUrl).toURL().openStream().use { vIn -> vOut.outputStream().use { vIn.copyTo(it) } }
			}
		}
	}

	// ============
	//  data.kres font/ entries (native) + JVM classpath analog
	// ============
	tasks.withType<Zip>().matching {
		(it.name.startsWith("package") || it.name.startsWith("copy")) && it.name.contains("ComposeResources")
	}.configureEach {
		addFontEntries(this, vNotoFiles, vDownloadNotoFonts, vUsedStyles, vSubsetTasksByStyle)
	}
	tasks.withType<ProcessResources>().matching { it.name == "jvmProcessResources" }.configureEach {
		// Full fonts on JVM — Skiko applies the variable axes itself (Typeface.makeClone).
		addFontEntries(this, vNotoFiles, vDownloadNotoFonts, vUsedStyles, emptyMap())
	}
}

// ==================
// MARK: Source scanning
// ==================

/**
 * True when any Kotlin file under src/ matches inRegex.
 */
private fun Project.sourcesMatch(inRegex: Regex): Boolean {
	val vSrcRoot = layout.projectDirectory.dir("src").asFile
	return vSrcRoot.exists() && vSrcRoot.walk().any {
		it.isFile && it.extension == "kt" && inRegex.containsMatchIn(it.readText())
	}
}

/**
 * The Material Symbols styles whose call sites appear in the app's Kotlin sources.
 */
private fun Project.detectUsedStyles(): List<String> {
	val vSrcRoot = layout.projectDirectory.dir("src").asFile
	if (!vSrcRoot.exists()) return emptyList()
	val vUsed = mutableSetOf<String>()
	vSrcRoot.walk().filter { it.isFile && it.extension == "kt" }.forEach { vFile ->
		val vText = vFile.readText()
		for ((vStyle, vRegex) in kAllStyles) {
			if (vStyle !in vUsed && vRegex.containsMatchIn(vText)) vUsed.add(vStyle)
		}
	}
	return vUsed.toList()
}

// ==================
// MARK: Font staging
// ==================

/**
 * Adds the font/ entries (Notos + used Material Symbols styles) to inTask — a data.kres
 * Zip or jvmProcessResources. inSubsetTasksByStyle maps styles to hb-subset tasks whose
 * output replaces the full font; pass an empty map to stage the full fonts.
 */
private fun Project.addFontEntries(
	inTask: AbstractCopyTask,
	inNotoFiles: List<Provider<RegularFile>>,
	inDownloadNotoFonts: TaskProvider<*>?,
	inUsedStyles: List<String>,
	inSubsetTasksByStyle: Map<String, TaskProvider<*>>,
) {
	for (vNotoFile in inNotoFiles) {
		inTask.from(vNotoFile) { into("font") }
		inDownloadNotoFonts?.let { inTask.dependsOn(it) }
	}
	val vSymbolsProject = rootProject.project(":material-symbols")
	for (vStyle in inUsedStyles) {
		@Suppress("UNCHECKED_CAST")
		val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as Provider<RegularFile>
		val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$vStyle"] as TaskProvider<*>
		val vSubsetTask = inSubsetTasksByStyle[vStyle]
		if (vSubsetTask != null) {
			// Subset output keeps the original filename : runtime registration unchanged.
			val vOriginalName = vFontFile.get().asFile.name
			inTask.from(vSubsetTask.get().outputs.files) {
				into("font")
				rename { vOriginalName }
			}
			inTask.dependsOn(vSubsetTask)
		} else {
			inTask.from(vFontFile) { into("font") }
		}
		inTask.dependsOn(vDownloadTask)
	}
}

// ==================
// MARK: Icon subsetting
// ==================

/**
 * Registers the icon-subset pipeline: findMaterialSymbolsUsage scans src/ for
 * MaterialSymbols.<Name> usages → usage-codepoint.txt, then one subsetMaterialSymbols<Style>
 * task per used style hb-subsets that style's font to the referenced glyphs.
 */
private fun Project.registerIconSubsetPipeline(inUsedStyles: List<String>): Map<String, TaskProvider<*>> {
	val vIconsBuildDir = layout.buildDirectory.dir("icons")
	val vFindUsage = tasks.register<Exec>("findMaterialSymbolsUsage") {
		description = "Scan src/ for MaterialSymbols.<Name> usages → usage-codepoint.txt."
		val vScript = rootProject.layout.projectDirectory.file("scripts/subset-material-symbols.py").asFile
		val vConstants = rootProject.project(":material-symbols").layout.projectDirectory
			.file("src/commonMain/kotlin/com/compose/sdl/icons/MaterialSymbols.kt").asFile
		val vUsageFile = vIconsBuildDir.get().file("usage-codepoint.txt").asFile
		inputs.files(fileTree("src") { include("**/*.kt") })
		inputs.file(vConstants)
		outputs.file(vUsageFile)
		commandLine(
			"python3", vScript.absolutePath,
			"--src", layout.projectDirectory.dir("src").asFile.absolutePath,
			"--constants", vConstants.absolutePath,
			"--out", vUsageFile.absolutePath,
		)
	}
	return inUsedStyles.associateWith { vStyle -> registerSubsetTask(vStyle, vIconsBuildDir, vFindUsage) }
}

/**
 * Subset inStyle's downloaded TTF to the codepoints in usage-codepoint.txt, into this
 * app's build/icons/. Prefers hb-subset (harfbuzz); falls back to fontTools via Python
 * (variable-font-aware, auto-installed via pip) when hb-subset is absent; bundles the
 * full font only if neither subsetter is available.
 */
private fun Project.registerSubsetTask(
	inStyle: String,
	inIconsBuildDir: Provider<Directory>,
	inFindUsage: TaskProvider<*>,
): TaskProvider<*> {
	val vSymbolsProject = rootProject.project(":material-symbols")
	return tasks.register("subsetMaterialSymbols$inStyle") {
		description = "hb-subset the $inStyle Material Symbols font to icons actually used."
		@Suppress("UNCHECKED_CAST")
		val vInputProvider = vSymbolsProject.extra["iconFontFile$inStyle"] as Provider<RegularFile>
		val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$inStyle"] as TaskProvider<*>
		val vOut = inIconsBuildDir.get().file("MaterialSymbols$inStyle.subset.ttf").asFile
		val vUsage = inIconsBuildDir.get().file("usage-codepoint.txt").asFile
		inputs.file(vInputProvider)
		inputs.file(vUsage)
		outputs.file(vOut)
		dependsOn(inFindUsage)
		dependsOn(vDownloadTask)
		doLast {
			val vCodepoints = vUsage.readLines()
				.filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
				.map { it.substringAfter("=").trim().removePrefix("0x").removePrefix("0X") }
			if (vCodepoints.isEmpty()) throw GradleException(
				"usage-codepoint.txt has no entries : refusing to subset to an empty font."
			)
			val vUnicodes = vCodepoints.joinToString(",") { "U+$it" }
			vOut.parentFile.mkdirs()
			val vInputFile = vInputProvider.get().asFile
			val vBefore = vInputFile.length()
			// project.exec isn't usable inside doLast on Gradle 9, so shell out via
			// ProcessBuilder. Returns (exitedZero, combinedOutput); a missing binary
			// (IOException) yields (false, message) so callers can fall through.
			fun runCmd(vararg inCmd: String): Pair<Boolean, String> =
				try {
					val vProc = ProcessBuilder(*inCmd).redirectErrorStream(true).start()
					val vText = vProc.inputStream.bufferedReader().readText()
					(vProc.waitFor() == 0) to vText
				} catch (e: java.io.IOException) {
					false to (e.message ?: "not found")
				}

			fun logShrink(inTool: String) {
				val vAfter = vOut.length()
				val vPct = if (vBefore == 0L) 0 else (100 - 100 * vAfter / vBefore).coerceAtLeast(0)
				logger.lifecycle(
					"[subset $inStyle] ${vBefore / 1024}KB → ${vAfter / 1024}KB (-$vPct%) · " +
							"${vCodepoints.size} glyphs kept [$inTool]"
				)
			}

			// 1. hb-subset (harfbuzz) — fastest path when it is on PATH.
			if (runCmd("hb-subset", vInputFile.absolutePath, "-o", vOut.absolutePath, "--unicodes=$vUnicodes").first
				&& vOut.length() > 0L
			) {
				logShrink("hb-subset"); return@doLast
			}

			// 2. fontTools (pyftsubset) via Python — the portable fallback. Python is
			//    already a build prerequisite (SDL build + vendor sync) and fontTools is
			//    variable-font-aware, so the FILL/wght/GRAD/opsz axes survive. It is
			//    auto-installed if missing, so no manual harfbuzz install is ever required.
			val vPython = listOf("python3", "python", "py").firstOrNull { runCmd(it, "--version").first }
			if (vPython != null) {
				if (!runCmd(vPython, "-c", "import fontTools").first) {
					logger.lifecycle("[subset $inStyle] fontTools not present — installing it via pip…")
					// --user (no root) → --break-system-packages (PEP 668) → plain.
					listOf(listOf("--user"), listOf("--break-system-packages"), emptyList<String>())
						.firstOrNull { vExtra ->
							runCmd(
								vPython, "-m", "pip", "install", "-q", "--disable-pip-version-check",
								*vExtra.toTypedArray(), "fonttools"
							).first
						}
				}
				val (vFtOk, vFtOut) = runCmd(
					vPython, "-m", "fontTools.subset", vInputFile.absolutePath,
					"--unicodes=$vUnicodes", "--output-file=${vOut.absolutePath}"
				)
				if (vFtOk && vOut.length() > 0L) { logShrink("fontTools"); return@doLast }
				logger.warn("[subset $inStyle] fontTools subset failed :\n$vFtOut")
			}

			// 3. Last resort : the full font — correct, just larger.
			vInputFile.copyTo(vOut, overwrite = true)
			logger.warn(
				"[subset $inStyle] no working subsetter found (hb-subset or python3+fontTools) : " +
						"bundling the full font (${vBefore / 1024}KB). Install harfbuzz " +
						"(brew install harfbuzz / apt install harfbuzz-utils / " +
						"pacman -S mingw-w64-x86_64-harfbuzz) or put python3 on PATH."
			)
		}
	}
}
