// :koin:koin-core-viewmodel - org.koin.viewmodel.* / org.koin.core.module.dsl.*,
// vendored VERBATIM from InsertKoinIO/koin into src/vendor.
//
// Upstream publishes this module for apple + android only, so there is no
// mingwX64 / linux klib on Maven; the sources are platform-agnostic, so the port
// simply rebuilds them for its desktop targets.
//
// koin-core itself DOES publish mingwX64 + linux and is consumed from Maven as-is.
// The lifecycle/viewmodel deps use the GOOGLE coordinates (androidx.lifecycle:*),
// matching the rest of the port - upstream koin uses the org.jetbrains.androidx.*
// mirrors, and mixing the two would put the same classes on the path twice.
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never
// hand-edit src/vendor - change the manifest + re-run
// `python scripts/compose-fork/sync.py koin/koin-core-viewmodel`.
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
				api(libs.koin.core)
				api(libs.androidx.lifecycle.viewmodel)
				api(libs.androidx.lifecycle.viewmodel.savedstate)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}
}
