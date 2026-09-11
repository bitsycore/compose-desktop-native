// :koin:koin-compose-navigation3 - org.koin.compose.navigation3.*, vendored VERBATIM
// from InsertKoinIO/koin.
//
// This is the NAVIGATION 3 integration. Upstream's koin-compose-viewmodel-navigation
// targets nav2 (`navigation-compose`), which publishes no mingwX64 / linux klibs under
// either coordinate set, so it cannot reach this port's targets without vendoring nav2
// too; this port's navigation story is Navigation 3 (:navigation3:navigation3-ui).
// koin-compose-navigation3 needs only koin-compose + navigation3-runtime, both reachable.
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
				api(project(":koin:koin-compose"))
				api(libs.androidx.navigation3.runtime)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}
}
