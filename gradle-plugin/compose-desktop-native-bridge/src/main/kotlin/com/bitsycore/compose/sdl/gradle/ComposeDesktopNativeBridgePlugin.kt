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
 * version — override with `composeSdl.version` in gradle.properties.
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
	val version = project.providers.gradleProperty(versionProperty).orNull ?: pluginVersion
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

/** The plugin's own version, stamped into a resource at build time. */
private val pluginVersion: String by lazy {
	val stream = ComposeDesktopNativeBridgePlugin::class.java
		.getResourceAsStream("bridge-version.properties")
		?: throw GradleException("com.bitsycore.compose-desktop-native.bridge: missing bridge-version.properties resource")
	val properties = Properties().apply { stream.use { load(it) } }
	properties.getProperty("version")
		?: throw GradleException("com.bitsycore.compose-desktop-native.bridge: bridge-version.properties has no 'version'")
}
