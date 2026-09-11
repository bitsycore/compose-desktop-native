// :coil:coil-compose - the public compose entry points (AsyncImage, SubcomposeAsyncImage),
// vendored VERBATIM from coil-kt/coil. commonMain only. Upstream publishes no
// desktop-native klibs for the compose layer.
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
				api(project(":coil:coil"))
				api(project(":coil:coil-compose-core"))
			}
			kotlin.srcDir("src/vendor/common/kotlin")
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
