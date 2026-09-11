// :koin:koin-compose-viewmodel - org.koin.compose.viewmodel.*, vendored VERBATIM
// from InsertKoinIO/koin. Upstream publishes apple + android only.
// See koin/koin-core-viewmodel/build.gradle.kts for the coordinate policy.
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
				api(project(":koin:koin-core-viewmodel"))
				api(libs.androidx.lifecycle.viewmodel.compose)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
	}
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}
}
