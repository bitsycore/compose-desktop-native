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

kotlin {
    // mingwX64() disabled while Skia is the renderer — Skiko ships no
    // mingwX64 klib (their Windows native uses MSVC). Re-add when we have
    // a custom Skia binding for MinGW or a non-Skia fallback wired up.
    linuxArm64()
    linuxX64()
    macosArm64()

    targets.withType<KotlinNativeTarget>().all {
        compilations["main"].cinterops {
            val sdl3 by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
            }
            // sdl3_ttf cinterop removed — text now rendered by Skia.
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation(libs.kotlinx.coroutines.core)
        }
        nativeMain.dependencies {
            // Skiko ships native klibs for macosArm64/x64 + linuxX64/arm64 + ios* + android.
            // No mingwX64 variant — Windows would have to fall back to SDL3 software.
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
