package com.bitsycore.compose.sdl.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import java.util.Properties

// ==================
// MARK: Bridge table
// ==================

/**
 * Official Compose Multiplatform coordinates → com.bitsycore.compose.sdl
 * artifactIds. Mirrors the repo-internal FULL-COMMONIZATION BRIDGE (root
 * build.gradle.kts): ui-graphics / ui-text are merged INTO the port's ui;
 * the runtime is deliberately absent — the official
 * org.jetbrains.compose.runtime klibs serve every target, never substituted.
 */
private val bridgeTable = mapOf(
    "org.jetbrains.compose.ui:ui" to "ui",
    "org.jetbrains.compose.ui:ui-graphics" to "ui",
    "org.jetbrains.compose.ui:ui-text" to "ui",
    "org.jetbrains.compose.ui:ui-unit" to "ui-unit",
    "org.jetbrains.compose.ui:ui-geometry" to "ui-geometry",
    "org.jetbrains.compose.ui:ui-util" to "ui-util",
    "org.jetbrains.compose.ui:ui-backhandler" to "ui-backhandler",
    "org.jetbrains.compose.ui:ui-tooling-preview" to "ui-tooling-preview",
    "org.jetbrains.compose.foundation:foundation" to "foundation",
    "org.jetbrains.compose.foundation:foundation-layout" to "foundation-layout",
    "org.jetbrains.compose.animation:animation" to "animation",
    "org.jetbrains.compose.animation:animation-core" to "animation-core",
    "org.jetbrains.compose.animation:animation-graphics" to "animation-graphics",
    "org.jetbrains.compose.material3:material3" to "material3",
    "org.jetbrains.compose.material:material-ripple" to "material-ripple",
    "org.jetbrains.compose.components:components-resources" to "components-resources",
    "org.jetbrains.androidx.navigation3:navigation3-ui" to "navigation3-ui",
)

/** The K/N desktop targets the port serves; configuration names carry the token. */
private val nativeTargetTokens = listOf("mingwX64", "linuxX64", "linuxArm64", "macosArm64")

private const val portGroup = "com.bitsycore.compose.sdl"

/** Gradle property overriding the substitution version (default: the plugin's own). */
private const val versionProperty = "composeDesktopNative.version"

// ==================
// MARK: composeDesktopNative { } — version info exposed to the consumer
// ==================

/**
 * Read-only info the bridge exposes to the consuming build as the project
 * extension `composeDesktopNative`. Use it to declare the official Compose
 * Multiplatform coordinates that match the port's vendored sources, so
 * jvm/android targets stay aligned with what the native klibs were built from
 * (no more reading compose.properties at the release tag by hand):
 *
 * ```kotlin
 * commonMain.dependencies {
 *     implementation("org.jetbrains.compose.runtime:runtime:${composeDesktopNative.composeRuntime}")
 *     implementation("org.jetbrains.compose.ui:ui:${composeDesktopNative.compose}")
 *     implementation("org.jetbrains.compose.foundation:foundation:${composeDesktopNative.compose}")
 *     implementation("org.jetbrains.compose.material3:material3:${composeDesktopNative.composeMaterial3}")
 * }
 * ```
 *
 * The same values are mirrored into `project.extra` (keys `composeDesktopNative.compose`,
 * `.composeMaterial3`, `.composeRuntime`, `.version`) for builds that apply the
 * plugin from settings, where the type-safe accessor is not generated.
 *
 * The substituted PORT klib version is overridden with the
 * `composeDesktopNative.version` Gradle property (see [version]); this extension
 * only reports the effective value.
 */
open class ComposeDesktopNativeBridgeExtension(
	/** The port klib version substituted on native targets (after the `composeDesktopNative.version` override). */
	val version: String,
	/** Official Compose version for ui / foundation / animation / material. */
	val compose: String,
	/** Official material3 version (versioned separately upstream). */
	val composeMaterial3: String,
	/** Official Compose runtime version (never substituted; serves every target). */
	val composeRuntime: String,
)

// ==================
// MARK: Plugin
// ==================

/**
 * Installs the Compose Desktop Native bridge in a consuming build: every NATIVE
 * target configuration substitutes the official Compose Multiplatform
 * coordinates for the port's published klibs, so shared code declares ONE set
 * of official coords and resolves on all CMP platforms + the port's desktop
 * targets.
 *
 * Apply in settings.gradle.kts (covers every project) or in a single module's
 * build.gradle.kts. The substituted version defaults to the plugin's own
 * version — override with `composeDesktopNative.version` in gradle.properties.
 * The official Compose versions the port tracks are exposed via the
 * `composeDesktopNative` extension (see [ComposeDesktopNativeBridgeExtension]).
 */
class ComposeDesktopNativeBridgePlugin : Plugin<Any> {

	override fun apply(target: Any) {
		when (target) {
			is Settings -> target.gradle.lifecycle.beforeProject { project -> installBridge(project) }
			is Project -> installBridge(target)
			else -> throw GradleException(
				"com.bitsycore.compose-desktop-native.bridge must be applied to a Settings or Project, " +
					"got ${target::class.java.name}"
			)
		}
	}
}

// ==================
// MARK: Installation
// ==================

private fun installBridge(project: Project) {
	installResourcePackaging(project)
	installNativeApplicationDsl(project)

	// The effective port version: the override property, else the plugin's own.
	val version = project.providers.gradleProperty(versionProperty).orNull ?: bridgeProperty("version")

	// Advertise the Compose versions the port tracks so consumers align their
	// official coords without guessing. Extension for the common per-module apply
	// (type-safe accessor); extra properties for the settings apply (no accessor).
	if (project.extensions.findByName("composeDesktopNative") == null) {
		project.extensions.create(
			"composeDesktopNative",
			ComposeDesktopNativeBridgeExtension::class.java,
			version,
			bridgeProperty("compose"),
			bridgeProperty("composeMaterial3"),
			bridgeProperty("composeRuntime"),
		)
	}
	project.extensions.extraProperties.let { extra ->
		extra["composeDesktopNative.version"] = version
		extra["composeDesktopNative.compose"] = bridgeProperty("compose")
		extra["composeDesktopNative.composeMaterial3"] = bridgeProperty("composeMaterial3")
		extra["composeDesktopNative.composeRuntime"] = bridgeProperty("composeRuntime")
	}

	project.configurations.configureEach { configuration ->
		if (nativeTargetTokens.any { configuration.name.contains(it, ignoreCase = true) }) {
			configuration.resolutionStrategy.dependencySubstitution { substitutions ->
				for ((official, artifactId) in bridgeTable) {
					substitutions.substitute(substitutions.module(official))
						.using(substitutions.module("$portGroup:$artifactId:$version"))
						.because(
							"Compose Desktop Native bridge — the official artifact ships no " +
								"mingwX64/linux Kotlin/Native klibs"
						)
				}
			}
		}
	}
}

/* The plugin's own version + the Compose versions it tracks, stamped into a
   resource at build time (from the version catalog). `version` is the plugin's
   own release; the rest are the official CMP versions the port's vendored
   sources build against. */
private val bridgeProperties: Properties by lazy {
	val stream = ComposeDesktopNativeBridgePlugin::class.java
		.getResourceAsStream("bridge-version.properties")
		?: throw GradleException("com.bitsycore.compose-desktop-native.bridge: missing bridge-version.properties resource")
	Properties().apply { stream.use { load(it) } }
}

private fun bridgeProperty(key: String): String =
	bridgeProperties.getProperty(key)
		?: throw GradleException("com.bitsycore.compose-desktop-native.bridge: bridge-version.properties has no '$key'")
