// :coil:coil-network-ktor3 - vendored VERBATIM from coil-kt/coil into src/vendor.
// Ktor 3 network fetcher, on the port's existing ktor pin.
// Upstream's non* source sets sit ABOVE nativeMain here (see coil-core's build file
// and compose-fork.txt for why). Provenance = compose-fork.txt; never hand-edit
// src/vendor - change the manifest + `python scripts/compose-fork/sync.py coil/coil-network-ktor3`.
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
				api(project(":coil:coil-core"))
				api(project(":coil:coil-network-core"))
				api(libs.ktor.client.core)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		val vNonAndroidMain = create("coilNonAndroidMain") {
			dependsOn(commonMain.get())
			kotlin.srcDir("src/vendor/nonAndroid/kotlin")
			dependencies { implementation(libs.kotlinx.io.okio) }
		}
		nativeMain {
			dependsOn(vNonAndroidMain)
			kotlin.srcDir("src/vendor/native/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
			"-opt-in=coil3.annotation.InternalCoilApi",
			"-opt-in=coil3.annotation.ExperimentalCoilApi",
			"-opt-in=coil3.annotation.DelicateCoilApi",
		)
	}
}
