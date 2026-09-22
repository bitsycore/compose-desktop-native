// :adaptive-layout - androidx.compose.material3.adaptive.layout.* (ListDetailPaneScaffold,
// SupportingPaneScaffold, ThreePaneScaffold + pane motion / expansion machinery), vendored
// VERBATIM from JetBrains/compose-multiplatform-core into src/vendor/. See :adaptive for why
// upstream ships no linux / mingw artifact.
//
// Upstream's `skikoMain` carries the l10n translation tables (~75 files) plus the Strings and
// drag-handle actuals; for this port every native target is a skiko target, so it lands in
// src/vendor/native/ alongside upstream's own nativeMain actuals. The two sets actualise
// disjoint expects, so they coexist in one source set.
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never hand-edit
// src/vendor - change the manifest + `python scripts/compose-fork/sync.py compose/material3/adaptive/adaptive-layout`.
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
				api(project(":compose:material3:adaptive:adaptive"))
				api(project(":compose:ui:ui"))
				api(project(":compose:animation:animation-core"))
				api(libs.androidx.collection)
				implementation(project(":compose:animation:animation"))
				implementation(project(":compose:foundation:foundation"))
				implementation(project(":compose:foundation:foundation-layout"))
				implementation(project(":compose:ui:ui-geometry"))
				implementation(libs.androidx.window.core)
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
			"-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
		)
	}
}
