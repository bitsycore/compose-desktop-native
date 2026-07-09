// :components-resources — the official Compose Multiplatform resources runtime
// (org.jetbrains.compose.components:components-resources), vendored because the
// Maven artifact ships no mingwX64/linux klibs. Public API is byte-for-byte
// upstream (painterResource / stringResource / Font / qualifiers / Res codegen
// compatibility); the platform actuals are this port's: data.kres reading,
// SDL3_image decoding, SDL locale/theme environment. Apps' JVM targets keep
// using the official Maven artifact — this module is native-only.

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.plugin.compose)
}

repositories {
	google()
	mavenCentral()
	maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val useSdl3Everywhere = (findProperty("renderer") as? String) == "sdl3"
val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
	linuxArm64()
	linuxX64()
	macosArm64()
	if (vHostSupportsMingw) mingwX64()

	applyDefaultHierarchyTemplate()

	sourceSets {
		commonMain {
			kotlin.srcDir("src/vendor/common/kotlin")
			dependencies {
				// foundation (isSystemInDarkTheme in ResourceEnvironment) pulls the
				// whole ui surface + loadComposeResourceBytes + the sdl3 cinterop
				// types transitively (all edges are api).
				api(project(":foundation"))
				implementation(libs.kotlinx.coroutines.core)
			}
		}
		nativeMain {
			kotlin.srcDir("src/vendor/native/kotlin")
		}

		// ============
		//  Renderer roots — same conditional wiring as :ui: only the source
		//  sets that will actually be attached are created.
		val sdlRendererMain = create("sdlRendererMain") {
			dependsOn(nativeMain.get())
			kotlin.srcDir("src/vendor/sdlRenderer/kotlin")
		}
		if (vHostSupportsMingw) {
			val sdlRendererMingwMain = create("sdlRendererMingwMain") { dependsOn(sdlRendererMain) }
			get("mingwX64Main").dependsOn(sdlRendererMingwMain)
		}

		val macosArm64Main = get("macosArm64Main")
		val linuxX64Main = get("linuxX64Main")
		val linuxArm64Main = get("linuxArm64Main")

		if (useSdl3Everywhere) {
			val sdlRendererMacosMain = create("sdlRendererMacosMain") { dependsOn(sdlRendererMain) }
			val sdlRendererLinuxMain = create("sdlRendererLinuxMain") { dependsOn(sdlRendererMain) }
			macosArm64Main.dependsOn(sdlRendererMacosMain)
			linuxX64Main.dependsOn(sdlRendererLinuxMain)
			linuxArm64Main.dependsOn(sdlRendererLinuxMain)
		} else {
			val skikoRendererMain = create("skikoRendererMain") {
				dependsOn(nativeMain.get())
				kotlin.srcDir("src/vendor/skikoRenderer/kotlin")
				dependencies {
					implementation(libs.skiko)
				}
			}
			val skikoRendererMacosMain = create("skikoRendererMacosMain") { dependsOn(skikoRendererMain) }
			val skikoRendererLinuxMain = create("skikoRendererLinuxMain") { dependsOn(skikoRendererMain) }
			macosArm64Main.dependsOn(skikoRendererMacosMain)
			linuxX64Main.dependsOn(skikoRendererLinuxMain)
			linuxArm64Main.dependsOn(skikoRendererLinuxMain)
		}
	}

	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xexpect-actual-classes",
			// Upstream's library build opts the whole module into its own
			// annotations; the vendored files rely on that.
			"-opt-in=org.jetbrains.compose.resources.InternalResourceApi",
			"-opt-in=org.jetbrains.compose.resources.ExperimentalResourceApi",
		)
	}
}
