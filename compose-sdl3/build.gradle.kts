import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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

// Renderer selector: -Prenderer=skia (default) uses Skiko on macOS / Linux
// and SDL3 on mingwX64; -Prenderer=sdl3 drops Skiko entirely and uses the
// SDL3 renderer on every target. Set in gradle.properties or pass on the
// command line: `./gradlew runDebugExecutableMacosArm64 -Prenderer=sdl3`.
val useSdl3Everywhere = (findProperty("renderer") as? String) == "sdl3"

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
    // SDL3_ttf is needed wherever the SDL3 renderer runs: always on
    // mingwX64, and on every target when -Prenderer=sdl3 is set.
    val needsSdl3Ttf: (KotlinNativeTarget) -> Boolean = {
        it.konanTarget.name == "mingw_x64" || useSdl3Everywhere
    }
    targets.withType<KotlinNativeTarget>().matching(needsSdl3Ttf).all {
        compilations["main"].cinterops {
            val sdl3_ttf by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3_ttf.def"))
                packageName("sdl3_ttf")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation(libs.kotlinx.coroutines.core)
        }

        // Source set holding the SDL3-only renderer + its actual
        // makeRenderBackend. Always built into mingwMain; pulled into
        // macosMain / linuxMain too when renderer=sdl3.
        val sdl3RendererMain by creating {
            dependsOn(nativeMain.get())
        }
        mingwMain.get().dependsOn(sdl3RendererMain)

        if (useSdl3Everywhere) {
            // SDL3-everywhere mode: no Skiko, no Skia source set.
            macosMain.get().dependsOn(sdl3RendererMain)
            linuxMain.get().dependsOn(sdl3RendererMain)

            // Per-platform preferredGpuMode actual returning SDL3.
            macosMain.get().kotlin.srcDir("src/macosSdl3Main/kotlin")
            linuxMain.get().kotlin.srcDir("src/linuxSdl3Main/kotlin")
        } else {
            // Default mode: macOS + Linux use Skia via Skiko.
            val skiaMain by creating {
                dependsOn(nativeMain.get())
                dependencies {
                    implementation(libs.skiko)
                }
            }
            macosMain.get().dependsOn(skiaMain)
            linuxMain.get().dependsOn(skiaMain)

            // Platform-specific Skia code (Metal bridge, Linux makeMetalBridge=null).
            macosMain.get().kotlin.srcDir("src/macosSkiaMain/kotlin")
            linuxMain.get().kotlin.srcDir("src/linuxSkiaMain/kotlin")
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
