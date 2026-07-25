package com.bitsycore.compose.sdl.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.Copy

// ==================
// MARK: Windows Skia leg — runtime DLL provisioning
// ==================
//
// When the mingwX64 target runs the Skia renderer (-PwindowsSkia=true), the app
// links against the bitsycore skiko fork, whose Kotlin/Native binding calls into
// a runtime skiko-windows-x64.dll. Unlike the JVM — where skiko-awt bundles its
// native lib inside the jar and a runtime loader extracts + LoadLibrary's it —
// Kotlin/Native has NO runtime native-lib loader: the DLL is imported by the PE
// and must physically sit next to the .exe at process start. So it cannot ride
// the `implementation` klib dependency to the output dir; this resolves the DLL
// from the fork's published `windows-x64` / `.dll` artifact and copies it there
// automatically for every mingwX64 executable.

private const val SKIKO_MINGW_VERSION_PROPERTY = "skikoMingwVersion"
private const val DEFAULT_SKIKO_MINGW_VERSION = "0.150.1-mingw.1"

/**
 * Provisions skiko-windows-x64.dll next to the mingwX64 executable(s) when the
 * Windows Skia leg is enabled (-PwindowsSkia=true). No-op otherwise. Version is
 * overridable via -PskikoMingwVersion. Called from installBridge at apply time;
 * the task hooks are lazy so targets that don't exist are simply not matched.
 */
internal fun installWindowsSkiaDll(inProject: Project) {
	if (inProject.providers.gradleProperty("windowsSkia").orNull != "true") return

	val vVersion = inProject.providers.gradleProperty(SKIKO_MINGW_VERSION_PROPERTY).orNull
		?: DEFAULT_SKIKO_MINGW_VERSION

	val vDllConfig = inProject.configurations.create("skikoWindowsRuntimeDll") {
		it.isCanBeConsumed = false
		it.isCanBeResolved = true
	}
	inProject.dependencies.add(
		vDllConfig.name,
		"org.jetbrains.skiko:skiko-mingwx64:$vVersion:windows-x64@dll"
	)

	listOf("Debug", "Release").forEach { vVariant ->
		val vProvision = inProject.tasks.register(
			"provisionSkikoDll${vVariant}MingwX64", Copy::class.java
		) { task ->
			task.description =
				"Copy skiko-windows-x64.dll next to the mingwX64 $vVariant executable (Windows Skia leg)."
			task.from(vDllConfig)
			task.into(
				inProject.layout.buildDirectory.dir(
					"bin/mingwX64/${vVariant.replaceFirstChar { it.lowercaseChar() }}Executable"
				)
			)
			task.rename { "skiko-windows-x64.dll" }
		}
		// finalizedBy the link (a plain build stages the DLL) and dependsOn from run
		// (the exe finds it before launch). finalizedBy — not dependsOn on link/
		// package — avoids the link<->package ordering cycle the bridge sets up for
		// data.kres.
		inProject.tasks.matching { it.name == "link${vVariant}ExecutableMingwX64" }
			.configureEach { it.finalizedBy(vProvision) }
		inProject.tasks.matching { it.name == "run${vVariant}ExecutableMingwX64" }
			.configureEach { it.dependsOn(vProvision) }
	}
}
