import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :window — the module apps depend on. Owns composeWindow() and selects the
// renderer per target by depending on exactly one renderer module:
//   mingwX64        → :renderer-sdl3 (always; Skiko has no Windows build)
//   macOS / Linux   → :renderer-skia, or :renderer-sdl3 under -Prenderer=sdl3
// composeWindow's makeRenderBackend/preferredGpuMode expects forward to that
// module's createRenderBackend / rendererPreferredGpuMode.
// Publication artifactId (when set up): compose-desktop-native.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// -Prenderer=sdl3 swaps macOS/Linux onto the SDL3 renderer (Skiko-free build).
val useSdl3Everywhere = (findProperty("renderer") as? String) == "sdl3"
val desktopRendererProject = if (useSdl3Everywhere) ":renderer-sdl3" else ":renderer-skia"

// See core/build.gradle.kts for the rationale on the host-side -I.
val vHostOs = System.getProperty("os.name")
val vHostSdlInclude: String? = when {
    vHostOs.startsWith("Mac")     -> "/opt/homebrew/include"
    vHostOs == "Linux"            -> "/usr/include"
    vHostOs.startsWith("Windows") -> "${rootDir.invariantSeparatorsPath}/libs/SDL3/include"
    else                          -> null
}

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()

    applyDefaultHierarchyTemplate()

    targets.withType<KotlinNativeTarget>().all {
        compilations["main"].cinterops {
            val sdl3 by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
                if (vHostSdlInclude != null) extraOpts("-compiler-options", "-I$vHostSdlInclude")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api so apps depending on :window also get the compose re-impl,
            // Res/resources, GpuMode, etc. from :core, and the Material
            // widget set (Button/Text/MaterialTheme/...) from :material.
            api(project(":core"))
            api(project(":material"))
            implementation(libs.kotlinx.coroutines.core)
            // setMain() / resetMain() are the only public APIs to swap the
            // process-global Dispatchers.Main. Despite the "test" naming,
            // they're safe at runtime — we install an SDL3-frame-driven
            // dispatcher at composeWindow startup so app code can
            // withContext(Dispatchers.Main) { ... } portably.
            implementation(libs.kotlinx.coroutines.test)
        }
        // Per-target renderer selection (the "include exactly one" mechanism).
        val mingwMain by getting {
            dependencies { implementation(project(":renderer-sdl3")) }
        }
        val macosMain by getting {
            dependencies { implementation(project(desktopRendererProject)) }
        }
        val linuxMain by getting {
            dependencies { implementation(project(desktopRendererProject)) }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
