import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// compose-desktop-native — the module apps depend on. Owns composeWindow() and
// selects the renderer per target by depending on exactly one renderer module:
//   mingwX64        → compose-renderer-sdl3 (always; Skiko has no Windows build)
//   macOS / Linux   → compose-renderer-skia, or compose-renderer-sdl3 under -Prenderer=sdl3
// composeWindow's makeRenderBackend/preferredGpuMode expects forward to that
// module's createRenderBackend / rendererPreferredGpuMode.

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
val desktopRendererProject = if (useSdl3Everywhere) ":compose-renderer-sdl3" else ":compose-renderer-skia"

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
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api so apps depending on compose-desktop-native also get the
            // compose re-impl, Res/resources, GpuMode, etc. from compose-core.
            api(project(":compose-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        // Per-target renderer selection (the "include exactly one" mechanism).
        val mingwMain by getting {
            dependencies { implementation(project(":compose-renderer-sdl3")) }
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
