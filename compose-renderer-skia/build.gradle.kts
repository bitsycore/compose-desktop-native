import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// compose-renderer-skia — Skia (Skiko) renderer: CPU raster / OpenGL / Metal
// bridges on top of SDL3's window. Exposes createRenderBackend /
// rendererPreferredGpuMode in com.compose.desktop.native. No mingwX64 target —
// Skiko publishes no Windows/native artifact.

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
            implementation(project(":compose-core"))
            implementation(libs.skiko)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
