// :ui-util — androidx.compose.ui.util.* (+ Experimental/InternalComposeUiApi markers),
// vendored VERBATIM from upstream. Split out of :ui to mirror CMP (compose/ui/ui-util).
// Never hand-edit src/vendor/ — change the manifest + `python scripts/compose-fork/sync.py compose/ui/ui-util`.
// Publication artifactId: desktop-ui-util.
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
				api("org.jetbrains.compose.runtime:runtime:1.11.1")
				api("androidx.collection:collection:1.5.0")
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		nativeMain { kotlin.srcDir("src/vendor/native/kotlin") }
	}
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
			"-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
			"-opt-in=androidx.compose.ui.InternalComposeUiApi",
		)
	}
}
