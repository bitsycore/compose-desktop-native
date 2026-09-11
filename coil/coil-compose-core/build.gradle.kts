// :coil:coil-compose-core - AsyncImage / rememberAsyncImagePainter internals, vendored
// VERBATIM from coil-kt/coil. Upstream publishes NO desktop-native klibs for the
// compose layer at all (neither mingwX64 nor linux), so this whole module exists to
// give the port its targets. Compose coords substitute to the port's project modules
// on every native configuration (root build's commonization bridge).
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
				api(project(":coil:coil-core"))
				// Declared DIRECTLY, not leaned on transitively: these coords are
				// SUBSTITUTED to the port's project modules on native configurations,
				// and KGP's granular-metadata visibility rule then drops the substituted
				// module's transitives from the commonMain metadata classpath (CLAUDE.md
				// "Common pitfalls"). Every artifact the vendored common code touches
				// needs its own declaration + its own bridge rule.
				api("org.jetbrains.compose.runtime:runtime:${libs.versions.composeRuntime.get()}")
				api("org.jetbrains.compose.ui:ui:${libs.versions.compose.get()}")
				api("org.jetbrains.compose.ui:ui-graphics:${libs.versions.compose.get()}")
				api("org.jetbrains.compose.ui:ui-text:${libs.versions.compose.get()}")
				api("org.jetbrains.compose.ui:ui-unit:${libs.versions.compose.get()}")
				api("org.jetbrains.compose.ui:ui-geometry:${libs.versions.compose.get()}")
				api("org.jetbrains.compose.ui:ui-util:${libs.versions.compose.get()}")
				api("org.jetbrains.compose.foundation:foundation:${libs.versions.compose.get()}")
				api("org.jetbrains.compose.foundation:foundation-layout:${libs.versions.compose.get()}")
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		// Upstream's nonAndroid compose layer reaches straight into org.jetbrains.skia
		// (Canvas / Bitmap.asComposeImageBitmap), so it needs skiko on its own compile
		// classpath - official for macOS/Linux, the bitsycore FORK for mingwX64, on two
		// PARALLEL branches. See coil/coil-core/build.gradle.kts.
		fun nonAndroidBranch(inName: String, inSkiko: Any) = create("${inName}NonAndroidMain") {
			dependsOn(commonMain.get())
			kotlin.srcDir("src/vendor/nonAndroid/kotlin")
			dependencies { implementation(inSkiko) }
		}

		val vOfficial = nonAndroidBranch("coilComposeOfficial", libs.skiko)
		get("macosArm64Main").dependsOn(vOfficial)
		get("linuxX64Main").dependsOn(vOfficial)
		get("linuxArm64Main").dependsOn(vOfficial)

		if (vHostSupportsMingw) {
			val vFork = nonAndroidBranch(
				"coilComposeFork",
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
