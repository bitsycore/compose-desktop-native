// :ui-backhandler — androidx.compose.ui.backhandler.*, vendored from upstream (split of :ui, CMP layout).
// Publication artifactId: desktop-ui-backhandler.
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
				api("androidx.navigationevent:navigationevent-compose:1.1.2")
				api(project(":ui-util"))
				implementation(libs.kotlinx.coroutines.core)
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
