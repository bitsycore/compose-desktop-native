// :ui-tooling-preview — androidx.compose.ui.tooling.preview.* (the common
// @Preview annotation + PreviewParameterProvider), vendored VERBATIM from
// upstream (compose/ui/ui-tooling-preview). The Maven artifact ships no
// mingwX64/linux klibs — apps declare the OFFICIAL coords in commonMain and
// the root bridge substitutes this module on native configurations. The
// annotations are IDE-only metadata: previews render through the app's JVM
// target against upstream Compose Desktop.
// Never hand-edit src/vendor/ — change the manifest + `python scripts/compose-fork/sync.py compose/ui/ui-tooling-preview`.
// Publication artifactId: ui-tooling-preview.
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
				api("org.jetbrains.compose.runtime:runtime:${libs.versions.composeRuntime.get()}")
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		nativeMain { kotlin.srcDir("src/vendor/native/kotlin") }
	}
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
		)
	}
}
