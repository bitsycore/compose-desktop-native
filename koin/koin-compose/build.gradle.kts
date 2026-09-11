// :koin:koin-compose - org.koin.compose.*, vendored VERBATIM from InsertKoinIO/koin.
// Upstream publishes apple + android only; the sources (including the nativeMain
// KoinApplication actual) are platform-agnostic, so the port rebuilds them for its
// desktop targets. See koin/koin-core-viewmodel/build.gradle.kts for the coordinate
// policy; provenance = compose-fork.txt.
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
				api(libs.koin.core)
				api("org.jetbrains.compose.runtime:runtime:${libs.versions.composeRuntime.get()}")
				// Substituted to project(":compose:foundation:foundation") on every
				// native configuration by the root build's commonization bridge.
				api("org.jetbrains.compose.foundation:foundation:${libs.versions.compose.get()}")
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		nativeMain {
			kotlin.srcDir("src/vendor/native/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}
}
