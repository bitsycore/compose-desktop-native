import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// compose-renderer-sdl3 — pure-SDL3 renderer (SDL3 primitives + SDL3_ttf text
// + SDL3_image decode). Exposes createRenderBackend / rendererPreferredGpuMode
// in the com.compose.desktop.native package. Builds for every native target.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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
            }
            val sdl3_ttf by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3_ttf.def"))
                packageName("sdl3_ttf")
            }
            val sdl3_image by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3_image.def"))
                packageName("sdl3_image")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":compose-core"))
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
