// :components-resources — the official Compose Multiplatform resources runtime
// (org.jetbrains.compose.components:components-resources), vendored because the
// Maven artifact ships no mingwX64/linux klibs. Public API is byte-for-byte
// upstream (painterResource / stringResource / Font / qualifiers / Res codegen
// compatibility); the platform actuals are this port's: data.kres reading,
// image decode via :ui's Skia decoder, SDL locale/theme environment. Apps' JVM
// targets keep using the official Maven artifact — this module is native-only.

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.plugin.compose)
}

repositories {
	google()
	mavenCentral()
	maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

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
			// The Font / Image resource actuals — pure project code that delegates
			// to :ui's IconFont / NamedFont / decodeEncodedImageBitmap (the Skia
			// decoder). Renderer-agnostic and skiko-free, so a single native set
			// covers every target. (Dir name is historical — .sdl.kt.)
			kotlin.srcDir("src/sdlRendererMain/kotlin")
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
