// :foundation-layout — androidx.compose.foundation.layout.*, vendored VERBATIM
// from upstream into src/vendor/. Split out of :foundation to mirror Compose
// Multiplatform's module packaging.
//
// Provenance = foundation-layout/compose-fork.txt + scripts/compose-fork/compose.properties.
// Never hand-edit files under src/vendor/ — change the manifest and re-run
// `python scripts/compose-fork/sync.py compose/foundation/foundation-layout`.
//
// Publication artifactId: desktop-foundation-layout.

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
				// foundation.layout.* only needs ui (+ runtime/collection transitively).
				api(project(":ui"))
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		nativeMain {
			kotlin.srcDir("src/vendor/native/kotlin")
		}
	}

	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xcollection-literals",
			"-Xexpect-actual-classes",
			"-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
			"-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
			"-opt-in=androidx.compose.ui.InternalComposeUiApi",
			"-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
		)
	}
}
