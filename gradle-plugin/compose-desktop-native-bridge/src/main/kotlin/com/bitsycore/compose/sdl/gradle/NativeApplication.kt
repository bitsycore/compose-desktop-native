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
 *     // exeIcon.from("icons/app-16.png", …, "icons/app-256.png")  // optional
 *     // resourceDir.set("icon")          // data.kres subfolder (default)
 *     // embedWindowsIcon.set(true)       // embed the .exe icon (default)
 * }
 * ```
 *
 * At runtime the app selects the bundled blobs itself:
 * `nativeComposeWindow(icon = AppWindowIcon(light = listOf("icon/app-128.rgba",
 * "icon/app-32.rgba"), dark = listOf("icon/app-dark-128.rgba", …)))` — the paths
 * are `<resourceDir>/<pngBaseName>.rgba`.
 *
 * The runtime window icon and the Windows `.exe` icon can differ: point [light]
 * at a transparent mark (looks right in the taskbar) and [exeIcon] at the full
 * branded icon with a background (stays legible in Explorer).
 */
abstract class NativeIconSpec {
	/** PNG files for the light window icon (any sizes; largest = base). */
	abstract val light: ConfigurableFileCollection

	/** PNG files for the dark window icon (falls back to [light] when empty). */
	abstract val dark: ConfigurableFileCollection

	/** PNG files for the Windows `.exe` icon (Explorer / pinned taskbar). Falls
	    back to [light] when empty — set it to use a different icon there. */
	abstract val exeIcon: ConfigurableFileCollection

	/** data.kres subfolder the `.rgba` blobs land in. Default `"icon"`. */
	abstract val resourceDir: Property<String>

	/** Embed the Windows `.exe` icon via windres. Default `true`. */
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
 * { entryPoint = … } }` block. Hand-declared executables keep everything they
 * configure (linker flags, build types); [entryPoint] only fills in where the
 * executable didn't set one, so the DSL composes with a manual
 * `binaries.executable { }` block.
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
				injectWindowsIconLinkerOpts(it, nativeExt)
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
		val existing = (binaries as Iterable<*>).filterNotNull()
			.filter { it.javaClass.simpleName == "Executable" }
		if (existing.isEmpty()) {
			val executableMethod = binaries.javaClass.methods.first {
				it.name == "executable" && it.parameterCount == 1 && Action::class.java.isAssignableFrom(it.parameterTypes[0])
			}
			executableMethod.invoke(binaries, Action<Any> { binary ->
				binary.javaClass.getMethod("setEntryPoint", String::class.java).invoke(binary, entryPoint)
			})
		} else {
			// Hand-declared executables (e.g. a binaries.executable {} block kept
			// for custom linker flags): fill in the entry point where unset, keep
			// everything else as declared.
			for (binary in existing) {
				val current = binary.javaClass.getMethod("getEntryPoint").invoke(binary)
				if (current == null) {
					binary.javaClass.getMethod("setEntryPoint", String::class.java).invoke(binary, entryPoint)
				}
			}
		}
	}
}

/* Adds the windres icon-resource object (produced by
   compileComposeNativeIconResource, wired as a link dependency in
   installAppIcon) to the linker options of EVERY mingw executable — the ones
   `native { entryPoint }` just created AND hand-configured
   `binaries.executable { }` ones, so apps that declare their own executables
   for custom linker flags still get the .exe icon. Runs in afterEvaluate,
   after configureNativeExecutables materialised the plugin's executables. */
private fun injectWindowsIconLinkerOpts(project: Project, ext: ComposeDesktopNativeExtension) {
	if (!wantsWindowsIconEmbed(ext)) return
	val kotlinExt = project.extensions.findByName("kotlin") ?: return
	@Suppress("UNCHECKED_CAST")
	val targets = kotlinExt.javaClass.getMethod("getTargets").invoke(kotlinExt) as NamedDomainObjectCollection<Any>
	val iconObjPath = appIconObjectFile(project).absolutePath
	for (target in targets) {
		val platformType = target.javaClass.getMethod("getPlatformType").invoke(target)
		if (platformType.toString() != "native") continue
		val konanTarget = target.javaClass.getMethod("getKonanTarget").invoke(target)
		val family = konanTarget.javaClass.getMethod("getFamily").invoke(konanTarget)
		if ((family as Enum<*>).name != "MINGW") continue
		val binaries = target.javaClass.getMethod("getBinaries").invoke(target)
		for (binary in binaries as Iterable<*>) {
			if (binary == null || binary.javaClass.simpleName != "Executable") continue
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
	}
}
