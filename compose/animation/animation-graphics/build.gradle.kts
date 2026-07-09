// :animation-graphics — androidx.compose.animation.graphics.*, vendored VERBATIM
// from upstream into src/vendor/. Split out to mirror Compose Multiplatform's
// module packaging (compose/animation/animation-graphics).
//
// Provenance = animation-graphics/compose-fork.txt + scripts/compose-fork/compose-ref.txt.
// Never hand-edit files under src/vendor/ — change the manifest and re-run
// `python scripts/compose-fork/sync.py compose/animation/animation-graphics`.
//
// Publication artifactId: desktop-animation-graphics.

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
				// animation.graphics.* uses animation.* / animation.core.* and the vector
				// graphics from ui (ui-graphics.vector, ui-util).
				api(project(":animation"))
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
			"-opt-in=androidx.compose.ui.InternalComposeUiApi",
			"-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
		)
	}
}
