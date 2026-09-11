// :coil:coil-svg - SVG decoding, vendored VERBATIM from coil-kt/coil into src/vendor.
// Upstream: linux + apple, no mingwX64. The non-android decoder goes through skiko's
// SVGDOM, so mingwX64 binds the bitsycore skiko FORK like :compose:ui:ui-graphics.
// Upstream's non* source sets sit ABOVE nativeMain here (see coil-core).
plugins {
	alias(libs.plugins.kotlin.multiplatform)
}

val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
	linuxArm64(); linuxX64(); macosArm64(); if (vHostSupportsMingw) mingwX64()
	applyDefaultHierarchyTemplate()
	sourceSets {
		commonMain {
			dependencies { api(project(":coil:coil-core")) }
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		// Two PARALLEL skiko branches (official for macOS/Linux, the bitsycore fork for
		// mingwX64), each two levels deep so nonAndroid `expect`s and nativeMain
		// `actual`s are not siblings. See coil/coil-core/build.gradle.kts for the why.
		fun skikoBranch(inName: String, inSkiko: Any): org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet {
			val vNonAndroid = create("${inName}NonAndroidMain") {
				dependsOn(commonMain.get())
				kotlin.srcDir("src/vendor/nonAndroid/kotlin")
				dependencies { implementation(inSkiko) }
			}
			return create("${inName}NativeMain") {
				dependsOn(vNonAndroid)
				kotlin.srcDir("src/vendor/native/kotlin")
			}
		}

		val vOfficial = skikoBranch("coilSvgOfficial", libs.skiko)
		get("macosArm64Main").dependsOn(vOfficial)
		get("linuxX64Main").dependsOn(vOfficial)
		get("linuxArm64Main").dependsOn(vOfficial)

		if (vHostSupportsMingw) {
			val vFork = skikoBranch(
				"coilSvgFork",
				"com.bitsycore.skiko:skiko:${providers.gradleProperty("skikoMingwVersion").getOrElse(libs.versions.skikoMingw.get())}",
			)
			get("mingwX64Main").dependsOn(vFork)
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
