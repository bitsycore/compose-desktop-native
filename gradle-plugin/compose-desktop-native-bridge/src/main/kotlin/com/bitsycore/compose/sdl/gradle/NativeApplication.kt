package com.bitsycore.compose.sdl.gradle

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

// ==================
// MARK: compose.desktop.native { } — the native counterpart of application { }
// ==================

/**
 * The window / taskbar (and, on Windows, the .exe) icon for the app, declared
 * as PNG files. See [installAppIcon] for what the plugin does with them.
 *
 * ```kotlin
 * icon {
 *     light.from("icons/app-32.png", "icons/app-128.png")
 *     dark.from("icons/app-dark-32.png", "icons/app-dark-128.png") // optional
 *     // resourceDir.set("icon")          // data.kres subfolder (default)
 *     // embedWindowsIcon.set(true)       // embed the .exe icon (default)
 * }
 * ```
 *
 * At runtime the app selects the bundled blobs itself:
 * `nativeComposeWindow(icon = AppWindowIcon(light = listOf("icon/app-128.rgba",
 * "icon/app-32.rgba"), dark = listOf("icon/app-dark-128.rgba", …)))` — the paths
 * are `<resourceDir>/<pngBaseName>.rgba`.
 */
abstract class NativeIconSpec {
	/** PNG files for the light window / .exe icon (any sizes; largest = base). */
	abstract val light: ConfigurableFileCollection

	/** PNG files for the dark window icon (falls back to [light] when empty). */
	abstract val dark: ConfigurableFileCollection

	/** data.kres subfolder the `.rgba` blobs land in. Default `"icon"`. */
	abstract val resourceDir: Property<String>

	/** Embed the Windows `.exe` icon from [light] via windres. Default `true`. */
	abstract val embedWindowsIcon: Property<Boolean>
}

/**
 * The native desktop analog of `compose.desktop { application { mainClass } }`:
 *
 * ```kotlin
 * compose.desktop {
 *     application { mainClass = "bubblewrap.MainJvmKt" }   // jvm (upstream)
 *     native {                                             // this port
 *         entryPoint = "bubblewrap.main"
 *         icon { light.from("icons/app-128.png") }         // optional
 *     }
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

	/** App-icon config (see [NativeIconSpec]); no-op unless [NativeIconSpec.light] is set. */
	@get:Nested
	abstract val icon: NativeIconSpec

	/** Configure the app icon: `icon { light.from(...) }`. */
	fun icon(action: Action<NativeIconSpec>) {
		action.execute(icon)
	}
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
			project.afterEvaluate {
				configureNativeExecutables(it, nativeExt)
				installAppIcon(it, nativeExt)
			}
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
		// On mingw, also link the windres icon-resource object into the .exe (its
		// path is fixed; the object is produced by compileComposeNativeIconResource,
		// wired as a link dependency in installAppIcon).
		val isMingw = (family as Enum<*>).name == "MINGW"
		val iconObjPath = if (isMingw && wantsWindowsIconEmbed(ext)) appIconObjectFile(project).absolutePath else null
		executableMethod.invoke(binaries, Action<Any> { binary ->
			binary.javaClass.getMethod("setEntryPoint", String::class.java).invoke(binary, entryPoint)
			if (iconObjPath != null) {
				try {
					// linkerOpts(vararg String) compiles to linkerOpts(String[]).
					binary.javaClass.getMethod("linkerOpts", Array<String>::class.java)
						.invoke(binary, arrayOf(iconObjPath))
				} catch (t: Throwable) {
					project.logger.warn(
						"compose-desktop-native: couldn't add the Windows icon linker option " +
							"reflectively (${t.message}); the .exe icon won't be embedded.")
				}
			}
		})
	}
}
