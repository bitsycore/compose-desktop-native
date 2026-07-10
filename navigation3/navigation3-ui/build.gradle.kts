// :navigation3-ui — androidx.navigation3.ui.* (NavDisplay + scene machinery), vendored
// VERBATIM from JetBrains/compose-multiplatform-core (navigation3/navigation3-ui) into
// src/vendor/. Upstream androidx navigation3-ui has no Kotlin/Native desktop artifact, but
// the JetBrains fork does — so we sync it like the compose modules. The 3 default
// transition-spec `expect`s are actualised from the fork's macosMain (platform-agnostic).
//
// navigation3-runtime + lifecycle-viewmodel-navigation3 are Maven deps (api-exposed by :ui).
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never hand-edit
// src/vendor — change the manifest + `python scripts/compose-fork/sync.py navigation3/navigation3-ui`.
//
// Publication artifactId: desktop-navigation3-ui.
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
				api(project(":animation"))
				api(project(":ui"))
				api("androidx.navigation3:navigation3-runtime:1.2.0-alpha05")
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
			"-opt-in=androidx.compose.ui.InternalComposeUiApi",
			"-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
			"-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
		)
	}
}
