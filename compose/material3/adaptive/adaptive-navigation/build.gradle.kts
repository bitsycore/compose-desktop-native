// :adaptive-navigation - androidx.compose.material3.adaptive.navigation.*
// (ThreePaneScaffoldNavigator + back-navigation behaviour), vendored VERBATIM from
// JetBrains/compose-multiplatform-core into src/vendor/. See :adaptive for why upstream
// ships no linux / mingw artifact. commonMain only - upstream's platform legs are
// android-specific.
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never hand-edit
// src/vendor - change the manifest + `python scripts/compose-fork/sync.py compose/material3/adaptive/adaptive-navigation`.
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
				api(project(":compose:material3:adaptive:adaptive-layout"))
				implementation(project(":compose:foundation:foundation"))
				implementation(project(":compose:ui:ui-util"))
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
			"-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
		)
	}
}
