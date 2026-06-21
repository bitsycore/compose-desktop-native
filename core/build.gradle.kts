import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :core — the renderer-agnostic base: the androidx.compose.* re-impl,
// RenderBackend interface, GpuMode, SDL3Backend + window/event/clipboard/IO,
// and the default bundled font. Owns the `sdl3` cinterop. Renderer modules
// and :window depend on this. No renderer code lives here.
// Publication artifactId (when set up): compose-desktop-native-core.

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

// The .def files pin per-target SDL3 include paths to host-specific
// directories (/opt/homebrew on macOS, /usr/include on Linux,
// C:/Dev/Libs/... on Windows). When kotlin.mpp.enableCInteropCommonization
// is on, IntelliJ asks Gradle to cinterop every declared target so the
// shared nativeMain klib can be commonized — which fails for foreign
// targets because their paths don't exist on this host.
// We add the HOST's installed SDL3 include path as an extra -I to every
// target's cinterop. clang silently ignores -I dirs that don't exist, so
// foreign-target cinterop indexing finds headers via the host's copy.
// Actual link-time still picks the per-target linkerOpts from the .def,
// but linking only runs for the host's target.
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
