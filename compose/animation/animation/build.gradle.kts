// :animation — androidx.compose.animation.* (non-core), vendored VERBATIM from
// upstream into src/vendor/. Split out of :foundation to mirror Compose
// Multiplatform's module packaging.
//
// Provenance = animation/compose-fork.txt + tools/compose-fork/compose-ref.txt.
// Never hand-edit files under src/vendor/ — change the manifest and re-run
// `python tools/compose-fork/sync.py compose/animation/animation`.
//
// Publication artifactId: desktop-animation.

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
				// animation.* imports animation.core.* and foundation.layout.* (Box/Row/Column);
				// ui / runtime arrive transitively.
				api(project(":animation-core"))
				api(project(":foundation-layout"))
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
