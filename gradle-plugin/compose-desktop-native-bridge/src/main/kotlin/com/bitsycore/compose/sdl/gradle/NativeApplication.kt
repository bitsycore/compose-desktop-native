package com.bitsycore.compose.sdl.gradle

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

// ==================
// MARK: compose.desktop.native { } — the native counterpart of application { }
// ==================

/**
 * The native desktop analog of `compose.desktop { application { mainClass } }`:
 *
 * ```kotlin
 * compose.desktop {
 *     application { mainClass = "bubblewrap.MainJvmKt" }   // jvm (upstream)
 *     native { entryPoint = "bubblewrap.main" }            // this port
 * }
 * ```
 *
 * Declares an executable with the given entry point on every Kotlin/Native
 * DESKTOP target (mingwX64 / linuxX64 / linuxArm64 / macosArm64) — replacing
 * the hand-written `targets.withType<KotlinNativeTarget> { binaries.executable
 * { entryPoint = … } }` block. Targets that already declare an executable are
 * left untouched, so manual configuration (extra linker flags, custom build
 * types) still wins.
 */
abstract class ComposeDesktopNativeExtension {
	/** Entry point (`package.functionName`) for the native executables. */
	var entryPoint: String? = null
}

/* Grafts the `native` extension onto the Compose plugin's `desktop` extension
   (extensions are ExtensionAware, so this needs no compile-time dependency)
   and materialises the executables in afterEvaluate. All KGP access is
   reflective: applied from settings, this plugin's classloader is a PARENT of
   the project's buildscript loader and cannot see KGP types. */
internal fun installNativeApplicationDsl(project: Project) {
	project.pluginManager.withPlugin("org.jetbrains.compose") {
		project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			val composeExt = project.extensions.findByName("compose") as? ExtensionAware ?: return@withPlugin
			val desktopExt = composeExt.extensions.findByName("desktop") as? ExtensionAware ?: return@withPlugin
			if (desktopExt.extensions.findByName("native") != null) return@withPlugin
			val nativeExt = desktopExt.extensions.create("native", ComposeDesktopNativeExtension::class.java)
			project.afterEvaluate { configureNativeExecutables(it, nativeExt) }
		}
	}
}

private val desktopKonanFamilies = setOf("MINGW", "LINUX", "OSX")

private fun configureNativeExecutables(project: Project, ext: ComposeDesktopNativeExtension) {
	val entryPoint = ext.entryPoint ?: return
	val kotlinExt = project.extensions.findByName("kotlin") ?: return
	@Suppress("UNCHECKED_CAST")
	val targets = kotlinExt.javaClass.getMethod("getTargets").invoke(kotlinExt) as NamedDomainObjectCollection<Any>
	for (target in targets) {
		val platformType = target.javaClass.getMethod("getPlatformType").invoke(target)
		if (platformType.toString() != "native") continue
		val konanTarget = target.javaClass.getMethod("getKonanTarget").invoke(target)
		val family = konanTarget.javaClass.getMethod("getFamily").invoke(konanTarget)
		if ((family as Enum<*>).name !in desktopKonanFamilies) continue
		val binaries = target.javaClass.getMethod("getBinaries").invoke(target)
		// Respect manual configuration: skip targets that already have an
		// executable (matching by the binary class, not the name suffix).
		val existing = (binaries as Iterable<*>).any { it != null && it.javaClass.simpleName == "Executable" }
		if (existing) continue
		val executableMethod = binaries.javaClass.methods.first {
			it.name == "executable" && it.parameterCount == 1 && Action::class.java.isAssignableFrom(it.parameterTypes[0])
		}
		executableMethod.invoke(binaries, Action<Any> { binary ->
			binary.javaClass.getMethod("setEntryPoint", String::class.java).invoke(binary, entryPoint)
		})
	}
}
