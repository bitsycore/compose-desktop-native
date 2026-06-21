import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :renderer-sdl3 — pure-SDL3 renderer (SDL3 primitives + SDL3_ttf text +
// SDL3_image decode + FreeType for variable-font icon axes). Exposes
// createRenderBackend / rendererPreferredGpuMode in the
// com.compose.desktop.native package. Builds for every native target.
// Publication artifactId (when set up): compose-desktop-native-renderer-sdl3.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// See core/build.gradle.kts for the rationale; cross-target cinterop
// indexing needs an -I that exists on this host, which clang silently
// ignores for paths that don't exist (so per-.def per-target opts can
// remain unchanged).
val vHostOs = System.getProperty("os.name")
val vHostSdlInclude: String? = when {
    vHostOs.startsWith("Mac")     -> "/opt/homebrew/include"
    vHostOs == "Linux"            -> "/usr/include"
    vHostOs.startsWith("Windows") -> "${rootDir.invariantSeparatorsPath}/libs/SDL3/include"
    else                          -> null
}
val vHostFtInclude: String? = when {
    vHostOs.startsWith("Mac")     -> "/opt/homebrew/include/freetype2"
    vHostOs == "Linux"            -> "/usr/include/freetype2"
    vHostOs.startsWith("Windows") -> "${rootDir.invariantSeparatorsPath}/libs/FreeType/include/freetype2"
    else                          -> null
}

val vHostTtfInclude: String? =
    if (vHostOs.startsWith("Windows")) "${rootDir.invariantSeparatorsPath}/libs/SDL3_ttf/include" else null
val vHostImageInclude: String? =
    if (vHostOs.startsWith("Windows")) "${rootDir.invariantSeparatorsPath}/libs/SDL3_image/include" else null

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
            val sdl3_ttf by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3_ttf.def"))
                packageName("sdl3_ttf")
                if (vHostSdlInclude != null) extraOpts("-compiler-options", "-I$vHostSdlInclude")
                if (vHostTtfInclude != null) extraOpts("-compiler-options", "-I$vHostTtfInclude")
            }
            val sdl3_image by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3_image.def"))
                packageName("sdl3_image")
                if (vHostSdlInclude != null) extraOpts("-compiler-options", "-I$vHostSdlInclude")
                if (vHostImageInclude != null) extraOpts("-compiler-options", "-I$vHostImageInclude")
            }
            // FreeType powers variable-font axis rendering (FILL / wght / GRAD /
            // opsz) on Material Symbols icons — SDL3_ttf 3.2 has no axis-set
            // API, so the SDL3 renderer routes icon-font draws with non-default
            // axes through FreeType + a glyph-bitmap cache and stays on
            // SDL3_ttf for everything else.
            val freetype by creating {
                defFile(project.file("src/nativeInterop/cinterop/freetype.def"))
                packageName("freetype")
                if (vHostFtInclude != null) extraOpts("-compiler-options", "-I$vHostFtInclude")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
