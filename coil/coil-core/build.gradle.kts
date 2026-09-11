// :coil:coil-core - coil3.* core, vendored VERBATIM from coil-kt/coil into src/vendor.
//
// Upstream publishes linux + apple klibs but NOT mingwX64, and the code is
// target-agnostic, so the port mostly just re-declares the targets.
//
// SOURCE-SET COLLAPSE: this module has no android, jvm or js target, so upstream's
// nonAndroidMain / nonJvmCommonMain / nonJsCommonMain / nativeMain all apply to every
// target here and are vendored into ONE tree (src/vendor/native). Only the
// apple-vs-rest split survives: appleMain is macosArm64, nonAppleMain is linux+mingw.
//
// SKIA: upstream's non-android decoder is skiko-based (SkiaImageDecoder), which is
// this port's renderer already. macOS/Linux take the official skiko; mingwX64 takes
// the bitsycore FORK, the same swap :compose:ui:ui-graphics makes.
//
// Provenance = compose-fork.txt + scripts/compose-fork/compose.properties. Never
// hand-edit src/vendor - change the manifest + re-run
// `python scripts/compose-fork/sync.py coil/coil-core`.
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
				api(libs.kotlinx.coroutines.core)
				api(libs.okio)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		// SKIKO BRANCHES. Upstream's nonAndroid files use org.jetbrains.skia.*, so the
		// intermediate source set that holds them needs skiko on its own compile
		// classpath (the shared-metadata compilation is a real compile). macOS/Linux
		// take the OFFICIAL skiko; mingwX64 takes the bitsycore FORK - and the two
		// cannot share an intermediate, so mingw gets a PARALLEL branch over the same
		// srcDirs. Same shape as :compose:ui:ui-graphics.
		//
		// Each branch is two levels deep because upstream's nonAndroid sets declare
		// `expect`s whose `actual`s live in nativeMain - siblings in one set is an error.
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

		val vOfficial = skikoBranch("coilOfficial", libs.skiko)
		val vAppleMain = create("coilAppleMain") {
			dependsOn(vOfficial)
			kotlin.srcDir("src/vendor/apple/kotlin")
		}
		val vLinuxMain = create("coilLinuxMain") {
			dependsOn(vOfficial)
			kotlin.srcDir("src/vendor/nonApple/kotlin")
		}
		get("macosArm64Main").dependsOn(vAppleMain)
		get("linuxX64Main").dependsOn(vLinuxMain)
		get("linuxArm64Main").dependsOn(vLinuxMain)

		if (vHostSupportsMingw) {
			val vFork = skikoBranch(
				"coilFork",
				"com.bitsycore.skiko:skiko:${providers.gradleProperty("skikoMingwVersion").getOrElse(libs.versions.skikoMingw.get())}",
			)
			val vMingwMain = create("coilMingwMain") {
				dependsOn(vFork)
				kotlin.srcDir("src/vendor/nonApple/kotlin")
			}
			get("mingwX64Main").dependsOn(vMingwMain)
		}
	}
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
			"-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
			"-opt-in=coil3.annotation.InternalCoilApi",
			"-opt-in=coil3.annotation.ExperimentalCoilApi",
			"-opt-in=coil3.annotation.DelicateCoilApi",
		)
	}
}
