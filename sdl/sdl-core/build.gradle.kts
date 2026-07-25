import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :sdl-core — the SDL3 platform layer. Step 1: it owns the single `sdl3` cinterop
// (SDL_Window / SDL_Event / SDL_GetBasePath / clipboard / dialogs / GL+Metal
// context / SDL_Renderer). Kept as its own module so :ui (and, later, the SDL
// platform + Skia renderer moving here) stop bundling the cinterop, which is the
// step toward splitting :ui into ui / ui-graphics / ui-text like upstream.
//
// This module has NO dependency on :ui, so :ui -> :sdl-core (for the cinterop)
// introduces no cycle. When the platform + renderer code moves here next, the
// direction flips to :sdl-core -> :ui and :ui becomes SDL-free.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
            create("sdl3") {
                defFile(project.file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
                extraOpts("-compiler-options", "-I$vHostSdlInclude")
                extraOpts("-libraryPath", vSdlLibDir)
            }
        }
    }
}
