// :pulse:pulse - vendored VERBATIM from bitsycore/pulse-mvi into src/vendor.
// The MVI core (store, state, intents).
// Upstream targets android / jvm / ios / js / wasm but NO desktop native, so the
// port re-declares the targets and rebuilds the same commonMain sources.
//
// Lifecycle uses the GOOGLE coordinates (androidx.lifecycle:*) rather than
// upstream's org.jetbrains.androidx.* mirrors - the port already resolves the
// google ones, and mixing both puts the same classes on the path twice.
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never
// hand-edit src/vendor - change the manifest + re-run
// `python scripts/compose-fork/sync.py pulse/pulse`.
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
				implementation(libs.kotlinx.coroutines.core)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}
}
