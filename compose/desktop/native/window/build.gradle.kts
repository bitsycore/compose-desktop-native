// :desktop-native-window — the module apps depend on. Owns nativeComposeWindow() (main loop,
// recomposer lifecycle, event dispatch, Snapshot apply notifications).
// Renderer selection happens entirely inside :ui via source-set wiring
// (skikoRendererMain — Skia-only) — this module just calls
// `createRenderBackend(...)` and `rendererPreferredGpuMode()` from :ui and
// the right symbol resolves per target.
// Publication artifactId (when set up): compose-desktop-native.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    if (vHostSupportsMingw) mingwX64()

    applyDefaultHierarchyTemplate()

    // sdl3.* types are api-exposed via :ui → :sdl-core's cinterop klib — no
    // separate sdl3 cinterop here.

    sourceSets {
        commonMain.dependencies {
            // api so apps depending on :desktop-native-window also get the compose re-impl,
            // Res/resources, GpuMode, and the renderer pipeline from :ui, plus
            // the foundation / animation-core / animation modules that were split
            // out of :ui (upstream Compose layout).
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
