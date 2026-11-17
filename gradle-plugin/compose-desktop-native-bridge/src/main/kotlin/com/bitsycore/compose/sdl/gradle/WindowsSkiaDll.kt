package com.bitsycore.compose.sdl.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.util.zip.ZipFile

// ==================
// MARK: Windows Skia leg - runtime DLL + ICU data provisioning
// ==================
//
// The mingwX64 target renders through Skia (the SDL renderer was removed); the
// app links against the bitsycore skiko fork, whose Kotlin/Native binding calls into
// a runtime skiko-windows-x64.dll. Unlike the JVM - where skiko-awt bundles its
// native lib inside the jar and a runtime loader extracts + LoadLibrary's it -
// Kotlin/Native has NO runtime native-lib loader: the DLL is imported by the PE
// and must physically sit next to the .exe at process start. So it cannot ride
// the `implementation` klib dependency to the output dir; this resolves the DLL
// from the fork's published `windows-x64` / `.dll` artifact and copies it there
// automatically for every mingwX64 executable.
//
// Text rendering goes through Skia's skparagraph (HarfBuzz + skunicode). On
// Windows, Skia loads its ICU data (icudtl.dat) from a file next to the binary at
// runtime - without it ParagraphBuilder fatal-aborts (check(mUnicode)). Unlike
// macOS/Linux skiko, which bake the ICU data in, the Windows build ships it as a
// sidecar. The official skiko-awt-runtime-windows-x64 jar already contains a
// compatible icudtl.dat (same Skia base as the fork), so we source it from there
// rather than re-publishing it from the fork.

private const val SKIKO_MINGW_VERSION_PROPERTY = "skikoMingwVersion"
private const val DEFAULT_SKIKO_MINGW_VERSION = "0.150.1-mingw.2"

/**
 * Provisions skiko-windows-x64.dll AND icudtl.dat next to the mingwX64
 * executable(s). mingwX64 always renders through Skia (the SDL renderer was
 * removed), so this is unconditional - the task hooks are lazy and only fire for
 * mingw link/run, so projects without a mingwX64 target simply never resolve
 * either artifact. The DLL version is overridable via -PskikoMingwVersion; the
 * ICU data is taken from the matching official skiko runtime (version = the
 * mingw version with its `-mingw.N` suffix stripped). Called from installBridge
 * at apply time.
 */
internal fun installWindowsSkiaDll(inProject: Project) {
	val vVersion = inProject.providers.gradleProperty(SKIKO_MINGW_VERSION_PROPERTY).orNull
		?: DEFAULT_SKIKO_MINGW_VERSION
	// The official skiko runtime that ships the matching icudtl.dat: strip the
	// fork's `-mingw.N` suffix (e.g. 0.150.1-mingw.1 -> 0.150.1).
	val vSkikoVersion = vVersion.substringBefore("-mingw")

	val vDllConfig = inProject.configurations.create("skikoWindowsRuntimeDll") {
		it.isCanBeConsumed = false
		it.isCanBeResolved = true
	}
	inProject.dependencies.add(
		vDllConfig.name,
		"com.bitsycore.skiko:skiko-mingwx64:$vVersion:windows-x64@dll"
	)

	// icudtl.dat rides inside the official skiko-awt-runtime-windows-x64 jar; pull
	// just that jar (non-transitive - we don't want the whole skiko-awt graph) and
	// extract the one file below.
	val vIcuConfig = inProject.configurations.create("skikoWindowsIcuData") {
		it.isCanBeConsumed = false
		it.isCanBeResolved = true
	}
	inProject.dependencies.add(
		vIcuConfig.name,
		"org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$vSkikoVersion"
	).let { (it as? ModuleDependency)?.isTransitive = false }

	listOf("Debug", "Release").forEach { vVariant ->
		val vExeDir = inProject.layout.buildDirectory.dir(
			"bin/mingwX64/${vVariant.replaceFirstChar { it.lowercaseChar() }}Executable"
		)
		val vProvision = inProject.tasks.register(
			"provisionSkikoDll${vVariant}MingwX64", Copy::class.java
		) { task ->
			task.description =
				"Copy skiko-windows-x64.dll next to the mingwX64 $vVariant executable (Windows Skia leg)."
			task.from(vDllConfig)
			task.into(vExeDir)
			task.rename { "skiko-windows-x64.dll" }
		}
		val vProvisionIcu = inProject.tasks.register(
			"provisionSkikoIcu${vVariant}MingwX64", ExtractIcuDataTask::class.java
		) { task ->
			task.description =
				"Extract icudtl.dat next to the mingwX64 $vVariant executable (Skia ICU data for skparagraph)."
			// The configuration itself is the input: Gradle resolves it when the task
			// runs. (A `provider { zipTree(config.singleFile) }` source resolved it at
			// configuration time instead - flagged by Gradle, gradle/gradle#2298.)
			task.runtimeJar.from(vIcuConfig)
			task.icuFile.set(vExeDir.map { it.file("icudtl.dat") })
		}
		// finalizedBy the link (a plain build stages the sidecars) and dependsOn from
		// run (the exe finds them before launch). finalizedBy - not dependsOn on link/
		// package - avoids the link<->package ordering cycle the bridge sets up for
		// data.kres.
		inProject.tasks.matching { it.name == "link${vVariant}ExecutableMingwX64" }
			.configureEach { it.finalizedBy(vProvision, vProvisionIcu) }
		inProject.tasks.matching { it.name == "run${vVariant}ExecutableMingwX64" }
			.configureEach { it.dependsOn(vProvision, vProvisionIcu) }
	}
}

/** Extracts `icudtl.dat` from the official skiko Windows runtime jar. */
@DisableCachingByDefault(because = "a single small file copied out of a local jar")
abstract class ExtractIcuDataTask : DefaultTask() {
	/** The skiko-awt-runtime-windows-x64 jar (resolved lazily, at execution). */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val runtimeJar: ConfigurableFileCollection

	@get:OutputFile
	abstract val icuFile: RegularFileProperty

	@TaskAction
	fun run() {
		val vJar = runtimeJar.singleFile
		val vOut = icuFile.get().asFile
		vOut.parentFile.mkdirs()
		ZipFile(vJar).use { vZip ->
			val vEntry = vZip.getEntry("icudtl.dat")
				?: throw GradleException("icudtl.dat not found in ${vJar.name}")
			vZip.getInputStream(vEntry).use { vIn -> vOut.outputStream().use { vIn.copyTo(it) } }
		}
	}
}
