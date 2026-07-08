import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.URI

// :core — the renderer-agnostic Compose base + both renderer pipelines.
//
// Source-set hierarchy:
//   commonMain
//     └── nativeMain                 (vendored .native.kt + project SDL3 wrappers)
//           ├── skikoRendererMain         (Skia drawing pipeline; Skiko on classpath)
//           │     ├── skikoRendererMacosMain     (macOS-only Skia actuals — Metal bridge)
//           │     └── skikoRendererLinuxMain     (Linux-only Skia actuals — OpenGL)
//           │       attached: macosArm64Main / linuxX64Main / linuxArm64Main
//           │       only when Skia path is active (default on macOS/Linux)
//           └── sdlRendererMain           (SDL3 drawing pipeline + TTF/IMG/FreeType)
//                 ├── sdlRendererMacosMain      (macOS-only SDL3 driver hint)
//                 ├── sdlRendererLinuxMain      (Linux-only SDL3 driver hint)
//                 └── sdlRendererMingwMain      (mingwX64-only SDL3 driver hint)
//                   attached: mingwX64Main always; macOS/Linux when -Prenderer=sdl3
//
// `-Prenderer=sdl3` flips macOS/Linux targets onto the SDL3 path. The
// `:renderer-skia` and `:renderer-sdl3` sibling modules are gone — their code
// lives here. `:window` depends only on `:core`; `createRenderBackend` comes
// from whichever of the renderer source sets is active for the target.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// -Prenderer=sdl3 flips macOS/Linux targets onto sdlRendererMain (Skiko-free build).
val useSdl3Everywhere = (findProperty("renderer") as? String) == "sdl3"

// Per-target host include rationale: cross-target cinterop indexing under
// kotlin.mpp.enableCInteropCommonization asks Gradle to cinterop every
// declared target so the shared nativeMain klib can be commonized. The .def
// files pin per-target SDL3 / FreeType / SDL3_ttf / SDL3_image include paths
// to the host's installed copies; we re-export the host's include as an
// extra -I so foreign-target cinterop indexing finds headers via the host.
// clang silently ignores -I dirs that don't exist, so this is safe.
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

// Renderer assignment per target. mingwX64 is always SDL3; macOS / Linux
// default to Skia, switch to SDL3 under -Prenderer=sdl3.
fun isSkiaTarget(targetName: String): Boolean = when (targetName) {
    "mingwX64" -> false
    "macosArm64", "linuxX64", "linuxArm64" -> !useSdl3Everywhere
    else -> false
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
        val vTargetName = name
        // Whether this target compiles the SDL3 drawing pipeline. mingwX64 is
        // always SDL3 (no Skiko on Windows); macOS / Linux switch to it only
        // under -Prenderer=sdl3. SDL3 on macOS/Linux is a DEBUG target we don't
        // ship in releases, so the default release build (Skia) skips the
        // sdl3_ttf / sdl3_image / freetype cinterops entirely — one fewer set
        // of system headers to install in CI, and a smaller klib footprint.
        val vSdlRenderer = vTargetName == "mingwX64" ||
            ((vTargetName == "macosArm64" || vTargetName == "linuxX64" || vTargetName == "linuxArm64") && useSdl3Everywhere)

        // Path to the sdl3 cinterop output klib for this target — used to
        // wire `depends = sdl3` in sdl3_ttf / sdl3_image / freetype below.
        // Gradle does NOT auto-add the sdl3 klib to the dependent cinterop
        // tasks' -library list, so cinterop generates its own SDL_Surface /
        // SDL_Color inside sdl3_image / sdl3_ttf instead of reusing the sdl3
        // ones. Passing the path explicitly via extraOpts forces the link.
        val vSdl3Klib = layout.buildDirectory.dir(
            "classes/kotlin/$vTargetName/main/cinterop/ui-cinterop-sdl3"
        ).get().asFile.absolutePath

        compilations["main"].cinterops {
            // sdl3 stays for every target — :window uses the SDL3 main-loop
            // types (SDL_Window / SDL_Event / SDL_GetBasePath / …) regardless
            // of the renderer choice.
            val sdl3 by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
                if (vHostSdlInclude != null) extraOpts("-compiler-options", "-I$vHostSdlInclude")
            }
            if (vSdlRenderer) {
                val sdl3_ttf by creating {
                    defFile(project.file("src/nativeInterop/cinterop/sdl3_ttf.def"))
                    packageName("sdl3_ttf")
                    extraOpts("-library", vSdl3Klib)
                    if (vHostSdlInclude != null) extraOpts("-compiler-options", "-I$vHostSdlInclude")
                    if (vHostTtfInclude != null) extraOpts("-compiler-options", "-I$vHostTtfInclude")
                }
                val sdl3_image by creating {
                    defFile(project.file("src/nativeInterop/cinterop/sdl3_image.def"))
                    packageName("sdl3_image")
                    extraOpts("-library", vSdl3Klib)
                    if (vHostSdlInclude != null) extraOpts("-compiler-options", "-I$vHostSdlInclude")
                    if (vHostImageInclude != null) extraOpts("-compiler-options", "-I$vHostImageInclude")
                }
                // FreeType powers variable-font axis rendering (FILL / wght /
                // GRAD / opsz) on Material Symbols icons in the SDL3 path.
                val freetype by creating {
                    defFile(project.file("src/nativeInterop/cinterop/freetype.def"))
                    packageName("freetype")
                    if (vHostFtInclude != null) extraOpts("-compiler-options", "-I$vHostFtInclude")
                }
            }
        }

        // Wire the task graph so cinteropSdl3_ttf/_image*Target run AFTER
        // cinteropSdl3*Target — the -library reference above only points at
        // the klib path; without a task dependency Gradle might run the
        // dependent cinterop first and the path wouldn't exist yet.
        if (vSdlRenderer) {
            val vT = vTargetName.replaceFirstChar { it.uppercase() }
            tasks.matching { it.name == "cinteropSdl3_ttf$vT" || it.name == "cinteropSdl3_image$vT" }
                .configureEach { dependsOn("cinteropSdl3$vT") }
        }
    }

    sourceSets {
        commonMain {
            // Files vendored VERBATIM from upstream Compose by
            // tools/compose-fork/sync.sh. Kept in their own folder so it's
            // obvious they are generated — never hand-edit; re-run sync instead.
            kotlin.srcDir("src/vendor/common/kotlin")
            dependencies {
                api("org.jetbrains.compose.runtime:runtime:1.11.1")
                api("org.jetbrains.compose.runtime:runtime-saveable:1.11.1")
                api("androidx.compose.runtime:runtime-retain:1.11.1")
                api("androidx.navigationevent:navigationevent-compose:1.1.2")
                api("androidx.savedstate:savedstate:1.5.0")
                api("androidx.savedstate:savedstate-compose:1.5.0")
                api("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
                api("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
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
        //  Renderer roots. Each is a child of nativeMain; per-platform
        //  intermediates below attach to one of these. Only the renderer
        //  source sets that will actually be attached are created, so Gradle
        //  doesn't warn about unused source sets when the build is asymmetric
        //  (e.g. -Prenderer=sdl3 wouldn't use any skikoRenderer* sets).

        val sdlRendererMain by creating {
            dependsOn(nativeMain.get())
            // src/vendor/sdlRenderer/kotlin holds files vendored verbatim
            // from upstream's skikoMain that are SDL3-friendly (no Skia refs)
            // or whose SDL3 actual we provide; same "never hand-edit" rule
            // as the other vendor srcDirs.
            kotlin.srcDir("src/vendor/sdlRenderer/kotlin")
            // SDL3_ttf / SDL3_image / freetype cinterop bindings come from
            // the per-target cinterop block above; no separate Gradle deps.
        }
        // mingwX64 is SDL3 always — attach its intermediate only when the
        // target itself was declared (host is Windows). Non-Windows hosts skip
        // both the target and its source-set wiring.
        if (vHostSupportsMingw) {
            val sdlRendererMingwMain by creating { dependsOn(sdlRendererMain) }
            mingwX64Main.get().dependsOn(sdlRendererMingwMain)
        }

        val macosArm64Main by getting
        val linuxX64Main by getting
        val linuxArm64Main by getting

        if (useSdl3Everywhere) {
            // macOS / Linux flip to SDL3 — create the sdl intermediates.
            val sdlRendererMacosMain by creating { dependsOn(sdlRendererMain) }
            val sdlRendererLinuxMain by creating { dependsOn(sdlRendererMain) }
            macosArm64Main.dependsOn(sdlRendererMacosMain)
            linuxX64Main.dependsOn(sdlRendererLinuxMain)
            linuxArm64Main.dependsOn(sdlRendererLinuxMain)
        } else {
            // Default: macOS / Linux use Skia. Create the skiko tree.
            val skikoRendererMain by creating {
                dependsOn(nativeMain.get())
                // src/vendor/skikoRenderer/kotlin holds upstream's `skikoMain`
                // files (Skia-tied actuals / helpers like BlendMode.skiko.kt)
                // vendored verbatim. Same "never hand-edit" rule.
                kotlin.srcDir("src/vendor/skikoRenderer/kotlin")
                dependencies {
                    implementation(libs.skiko)
                }
            }
            val skikoRendererMacosMain by creating { dependsOn(skikoRendererMain) }
            val skikoRendererLinuxMain by creating { dependsOn(skikoRendererMain) }
            macosArm64Main.dependsOn(skikoRendererMacosMain)
            linuxX64Main.dependsOn(skikoRendererLinuxMain)
            linuxArm64Main.dependsOn(skikoRendererLinuxMain)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            // Silence the expect/actual-classes Beta warning that vendored files
            // (e.g. ImageBitmap / Paint / Canvas / Path / ClipEntry) tripped. See
            // https://youtrack.jetbrains.com/issue/KT-61573.
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
            // Vendored code is upstream foundation/ui, which their build compiles with these
            // module-level opt-ins (e.g. lazy-layout prefetch uses ExperimentalFoundationApi).
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
}

// ==================
// MARK: Default + monospace fonts (Google Noto, downloaded at build time)
// ==================

val notoSansFont = layout.buildDirectory.file("fonts/NotoSans.ttf")
val notoSansMonoFont = layout.buildDirectory.file("fonts/NotoSansMono.ttf")

val downloadNotoFonts = tasks.register("downloadNotoFonts") {
    val vDownloads = listOf(
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth%2Cwght%5D.ttf"
            to notoSansFont.get().asFile,
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansmono/NotoSansMono%5Bwdth%2Cwght%5D.ttf"
            to notoSansMonoFont.get().asFile,
    )
    outputs.files(vDownloads.map { it.second })
    doLast {
        for ((vUrl, vOut) in vDownloads) {
            if (vOut.exists() && vOut.length() > 0) continue
            vOut.parentFile.mkdirs()
            println("Downloading $vUrl")
            URI(vUrl).toURL().openStream().use { vIn -> vOut.outputStream().use { vIn.copyTo(it) } }
            println("Saved ${vOut.length() / 1024} KiB to $vOut")
        }
    }
}
