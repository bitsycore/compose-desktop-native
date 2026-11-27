// :pulse:pulse-test - vendored VERBATIM from bitsycore/pulse-mvi into src/vendor.
// Host test kit (TestContainer, HostTester, container assertions) - new upstream in 0.4.0.
// Upstream targets android / jvm / ios / js / wasm but NO desktop native, so the
// port re-declares the targets and rebuilds the same commonMain sources.
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never
// hand-edit src/vendor - change the manifest + re-run
// `python scripts/compose-fork/sync.py pulse/pulse-test`.
plugins {
	alias(libs.plugins.kotlin.multiplatform)
}

val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
	linuxArm64(); linuxX64(); macosArm64(); if (vHostSupportsMingw) mingwX64()
	applyDefaultHierarchyTemplate()
	sourceSets {
		commonMain {
			dependencies {
				api(project(":pulse:pulse"))
				api(libs.kotlin.test)
				api(libs.kotlinx.coroutines.test)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}
}
