// :ui-geometry — androidx.compose.ui.geometry.*, vendored from upstream (split of :ui, CMP layout).
// Publication artifactId: desktop-ui-geometry.
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
				api(project(":ui-util"))
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
