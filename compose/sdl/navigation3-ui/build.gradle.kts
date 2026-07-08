// :navigation3-ui — a minimal NavDisplay for the SDL/native stack. Upstream
// androidx.navigation3:navigation3-ui has NO Kotlin/Native desktop artifact (its
// non-Android NavDisplay is `NotImplemented`), so — per the project's reimpl policy
// (same package/signature) — we provide NavDisplay here, rendering the top NavEntry
// of a navigation3-runtime back stack through this project's own AnimatedContent.
//
// navigation3-runtime + lifecycle-viewmodel-navigation3 ARE real Maven artifacts for
// all our targets; they come in via :ui (api). This module only adds the display.
//
// Publication artifactId: desktop-navigation3-ui.
plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.plugin.compose)
}
val vHostSupportsMingw: Boolean by rootProject.extra
kotlin {
	linuxArm64(); linuxX64(); macosArm64(); if (vHostSupportsMingw) mingwX64()
	applyDefaultHierarchyTemplate()
	sourceSets {
		commonMain {
			dependencies {
				// :animation → AnimatedContent + transition specs; also transitively brings
				// :ui (→ navigation3-runtime api: NavEntry / NavKey / NavBackStack).
				api(project(":animation"))
				api(project(":ui"))
			}
			kotlin.srcDir("src/commonMain/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
			"-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
			"-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
		)
	}
}
