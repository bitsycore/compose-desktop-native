// :material-ripple — androidx.compose.material.ripple, vendored VERBATIM from
// upstream into src/vendor/. Split out of :material3 to mirror Compose
// Multiplatform's module packaging.
//
// Provenance = material-ripple/compose-fork.txt + tools/compose-fork/compose-ref.txt.
// Never hand-edit files under src/vendor/ — change the manifest and re-run
// `python tools/compose-fork/sync.py compose/material/material-ripple`.
//
// Publication artifactId: desktop-material-ripple.

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.plugin.compose)
}

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw: Boolean by rootProject.extra

kotlin {
	linuxArm64()
	linuxX64()
	macosArm64()
	if (vHostSupportsMingw) mingwX64()

	applyDefaultHierarchyTemplate()

	sourceSets {
		commonMain {
			dependencies {
				// ripple imports androidx.compose.foundation.* (Indication) and
				// androidx.compose.animation.core.* (RippleAnimation); ui/runtime arrive transitively.
				api(project(":foundation"))
				api(project(":animation-core"))
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
			"-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
			"-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
			"-opt-in=androidx.compose.ui.InternalComposeUiApi",
		)
	}
}
