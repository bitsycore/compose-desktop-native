// :ui-unit — androidx.compose.ui.unit.*, vendored from upstream (split of :ui, CMP layout).
// Publication artifactId: desktop-ui-unit.
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
				api(project(":ui-geometry"))
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
