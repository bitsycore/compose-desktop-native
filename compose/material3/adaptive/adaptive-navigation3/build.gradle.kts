// :adaptive-navigation3 - androidx.compose.material3.adaptive.navigation3.*
// (ListDetail / SupportingPane SceneStrategy for Navigation 3), vendored VERBATIM from
// JetBrains/compose-multiplatform-core into src/vendor/. See :adaptive for why upstream
// ships no linux / mingw artifact. commonMain only.
//
// navigation3-ui is the PROJECT module: upstream's org.jetbrains.androidx.navigation3:
// navigation3-ui has no K/N desktop artifact either, which is why :navigation3:navigation3-ui
// exists. navigationevent-compose is a plain Maven dep (api-exposed by :ui).
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never hand-edit
// src/vendor - change the manifest + `python scripts/compose-fork/sync.py compose/material3/adaptive/adaptive-navigation3`.
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
				api(project(":compose:material3:adaptive:adaptive-navigation"))
				api(project(":navigation3:navigation3-ui"))
				implementation(libs.androidx.collection)
				implementation(libs.androidx.navigationevent.compose)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
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
