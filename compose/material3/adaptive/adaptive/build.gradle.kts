// :adaptive - androidx.compose.material3.adaptive.* (WindowAdaptiveInfo / Posture /
// window size class helpers), vendored VERBATIM from JetBrains/compose-multiplatform-core
// (compose/material3/adaptive/adaptive) into src/vendor/.
//
// Upstream org.jetbrains.compose.material3.adaptive:* publishes only ios / macosArm64 -
// no linux, no mingw (github.com/bitsycore/compose-desktop-native/issues/4). Nothing in
// the sources blocks those targets: the whole port is vendor-only, every actual the
// native targets need already exists upstream, and the one real dependency
// (androidx.window:window-core) ALREADY publishes linux + mingw + macos. The artifacts
// are missing purely because upstream did not enable the targets.
//
// Upstream's `nonAndroidMain` lands in src/vendor/native/ - for this port native IS the
// only non-android leg, same treatment coil's nonAndroid sets get.
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never hand-edit
// src/vendor - change the manifest + `python scripts/compose-fork/sync.py compose/material3/adaptive/adaptive`.
plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.plugin.compose)
}

val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
	linuxArm64(); linuxX64(); macosArm64(); if (vHostSupportsMingw) mingwX64()
	applyDefaultHierarchyTemplate()
	sourceSets {
		commonMain {
			dependencies {
				api(project(":compose:ui:ui"))
				api(libs.androidx.window.core)
				implementation(project(":compose:foundation:foundation"))
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		nativeMain {
			kotlin.srcDir("src/vendor/native/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
			"-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
			"-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
		)
	}
}
