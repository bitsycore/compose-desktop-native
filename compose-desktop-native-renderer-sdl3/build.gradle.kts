import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// compose-desktop-native-renderer-sdl3 — pure-SDL3 renderer (SDL3 primitives + SDL3_ttf text
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
            // FreeType powers variable-font axis rendering (FILL / wght / GRAD /
            // opsz) on Material Symbols icons — SDL3_ttf 3.2 has no axis-set
            // API, so the SDL3 renderer routes icon-font draws with non-default
            // axes through FreeType + a glyph-bitmap cache and stays on
            // SDL3_ttf for everything else.
            val freetype by creating {
                defFile(project.file("src/nativeInterop/cinterop/freetype.def"))
                packageName("freetype")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":compose-desktop-native-core"))
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
