import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :renderer-skia — Skia (Skiko) renderer: CPU raster / OpenGL / Metal
// bridges on top of SDL3's window. Exposes createRenderBackend /
// rendererPreferredGpuMode in com.compose.desktop.native. No mingwX64 target —
// Skiko publishes no Windows/native artifact.
// Publication artifactId (when set up): compose-desktop-native-renderer-skia.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

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
            implementation(project(":core"))
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
