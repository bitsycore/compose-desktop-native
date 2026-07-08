import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :window — the module apps depend on. Owns nativeComposeWindow() (main loop,
// recomposer lifecycle, event dispatch, Snapshot apply notifications).
// Renderer selection happens entirely inside :core via source-set wiring
// (skikoRendererMain vs sdlRendererMain) — this module just calls
// `createRenderBackend(...)` and `rendererPreferredGpuMode()` from :core and
// the right symbol resolves per target.
// Publication artifactId (when set up): compose-desktop-native.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// See core/build.gradle.kts for the rationale on the host-side -I.
val vHostOs = System.getProperty("os.name")
val vHostSdlInclude: String? = when {
    vHostOs.startsWith("Mac")     -> "/opt/homebrew/include"
    vHostOs == "Linux"            -> "/usr/include"
    vHostOs.startsWith("Windows") -> "${rootDir.invariantSeparatorsPath}/libs/SDL3/include"
    else                          -> null
}

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw: Boolean by rootProject.extra

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    if (vHostSupportsMingw) mingwX64()

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
            // api so apps depending on :window also get the compose re-impl,
            // Res/resources, GpuMode, and the renderer pipeline from :core, plus
            // the foundation / animation-core / animation modules that were split
            // out of :core (upstream Compose layout).
            // Material widgets used to be re-exported from :material here; the
            // module was retired when :apidemo and :demo migrated to :material3.
            // Apps that want Material 3 widgets pull `implementation(project(":material3"))`
            // themselves (:material3 doesn't need to be `api`-exposed — the
            // upstream vendored surface is stable and apps import it directly).
            api(project(":ui"))
            api(project(":foundation"))
            api(project(":animation-core"))
            implementation(libs.kotlinx.coroutines.core)
            // setMain() / resetMain() — see Sdl3MainDispatcher.kt for usage.
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
