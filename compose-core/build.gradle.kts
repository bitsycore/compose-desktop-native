import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// compose-core — the renderer-agnostic base: the androidx.compose.* re-impl,
// RenderBackend interface, GpuMode, SDL3Backend + window/event/clipboard/IO,
// and the default bundled font. Owns the `sdl3` cinterop. Renderer modules and
// compose-desktop-native depend on this. No renderer code lives here.

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
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation(libs.kotlinx.coroutines.core)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
