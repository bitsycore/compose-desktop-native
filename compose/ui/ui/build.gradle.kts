import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :ui — the Compose base + the Skia rendering pipeline (all native targets).
//
// Source-set hierarchy:
//   commonMain
//     └── nativeMain                        (vendored .native.kt + project SDL3 wrappers)
//           ├── skikoRendererMain           (Skia pipeline; OFFICIAL Skiko — macOS/Linux)
//           │     ├── skikoRendererMacosMain    (macOS Metal bridge)  → macosArm64
//           │     └── skikoRendererLinuxMain    (Linux OpenGL)        → linuxX64/Arm64
//           └── skikoRendererMingwSharedMain (Skia pipeline; the bitsycore skiko FORK —
//                 └── skikoRendererMingwMain    mingwX64 has no official Skiko klib) → mingwX64
//
// SDL3 stays as the windowing / input / platform layer (the single `sdl3`
// cinterop). The from-scratch SDL renderer and its SDL3_ttf / SDL3_image /
// FreeType cinterops were removed — every target now renders through Skia.
// `:window` depends only on this module and calls createRenderBackend() /
// rendererPreferredGpuMode(), which resolve to the Skia actuals.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// SDL3 headers/libs from the in-repo static build tree at <repo>/libs
// (scripts/build-sdl/build-all.py). The .def file is pathless and relies on
// these -I / -libraryPath injections.
val vLibs = "${rootDir.invariantSeparatorsPath}/libs"
val vHostSdlInclude: String = "$vLibs/SDL3/include"
val vSdlLibDir: String = "$vLibs/SDL3/lib"

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    if (vHostSupportsMingw) mingwX64()

    applyDefaultHierarchyTemplate()

    targets.withType<KotlinNativeTarget>().all {
        compilations["main"].cinterops {
            // The one SDL3 cinterop — windowing / input / SDL_GetBasePath / … —
            // used by every target regardless of GPU path.
            create("sdl3") {
                defFile(project.file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
                extraOpts("-compiler-options", "-I$vHostSdlInclude")
                extraOpts("-libraryPath", vSdlLibDir)
            }
        }
    }

    sourceSets {
        commonMain {
            // Files vendored VERBATIM from upstream Compose by
            // scripts/compose-fork/sync.sh. Never hand-edit; re-run sync.
            kotlin.srcDir("src/vendor/common/kotlin")
            dependencies {
                api(project(":ui-util"))
                api(project(":ui-geometry"))
                api(project(":ui-unit"))
                api(project(":ui-backhandler"))
                api("org.jetbrains.compose.runtime:runtime:${libs.versions.composeRuntime.get()}")
                api("org.jetbrains.compose.runtime:runtime-saveable:${libs.versions.composeRuntime.get()}")
                api("androidx.compose.runtime:runtime-retain:${libs.versions.composeRuntime.get()}")
                api("androidx.navigationevent:navigationevent-compose:1.1.2")
                api("androidx.savedstate:savedstate:1.5.0")
                api("androidx.savedstate:savedstate-compose:1.5.0")
                api("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
                api("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
                api("androidx.navigation3:navigation3-runtime:1.1.4")
                api("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okio)
                implementation("org.jetbrains.kotlinx:atomicfu:0.23.1")
            }
        }
        // Vendored platform `actual`s + project SDL3 wrappers / Compose native code.
        nativeMain {
            kotlin.srcDir("src/vendor/native/kotlin")
        }

        // ============
        //  Skia renderer. macOS/Linux use the OFFICIAL Skiko; mingwX64 uses the
        //  bitsycore skiko FORK (no official mingw klib), in a separate tree so
        //  only mingw gets the fork coord.

        val skikoRendererMain = create("skikoRendererMain") {
            dependsOn(nativeMain.get())
            // src/vendor/skikoRenderer/kotlin — upstream `skikoMain` files
            // (Skia-tied actuals like BlendMode.skiko.kt) vendored verbatim.
            kotlin.srcDir("src/vendor/skikoRenderer/kotlin")
            dependencies {
                implementation(libs.skiko)
            }
        }
        val skikoRendererMacosMain = create("skikoRendererMacosMain") { dependsOn(skikoRendererMain) }
        val skikoRendererLinuxMain = create("skikoRendererLinuxMain") { dependsOn(skikoRendererMain) }
        get("macosArm64Main").dependsOn(skikoRendererMacosMain)
        get("linuxX64Main").dependsOn(skikoRendererLinuxMain)
        get("linuxArm64Main").dependsOn(skikoRendererLinuxMain)

        if (vHostSupportsMingw) {
            // Route 1a: mingwX64 Skia leg on the fork. Two levels so the shared
            // PlatformGpu expect (Shared) has its mingw actual (Mingw), mirroring
            // the macos/linux split. Depends on the fork ROOT coord (not the
            // platform artifact) so KMP variant-resolution exposes api-elements.
            val skikoRendererMingwSharedMain = create("skikoRendererMingwSharedMain") {
                dependsOn(nativeMain.get())
                kotlin.srcDir("src/skikoRendererMain/kotlin")
                kotlin.srcDir("src/vendor/skikoRenderer/kotlin")
                dependencies {
                    // Published by the fork's CI to GitHub Packages; version
                    // overridable via -PskikoMingwVersion=.
                    implementation("org.jetbrains.skiko:skiko:${providers.gradleProperty("skikoMingwVersion").getOrElse("0.150.1-mingw.1")}")
                }
            }
            val skikoRendererMingwMain = create("skikoRendererMingwMain") { dependsOn(skikoRendererMingwSharedMain) }
            get("mingwX64Main").dependsOn(skikoRendererMingwMain)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            // Silence the expect/actual-classes Beta warning vendored files trip.
            // https://youtrack.jetbrains.com/issue/KT-61573.
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
}
