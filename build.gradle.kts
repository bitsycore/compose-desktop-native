// Root build — declare the Kotlin/Compose plugins once (apply false) so every
// subproject shares a single plugin classloader. Applying them independently
// per-subproject can load Kotlin Gradle plugin build services under isolated
// classloaders, which fails with "X cannot be cast to X" once several modules
// apply the multiplatform plugin. Each subproject still applies the ones it needs.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    // Public-ABI dumps (api/<module>.klib.api). Used by tools/compose-fidelity-check.py
    // to diff our androidx.compose.* surface against the official Compose klib ABI.
    alias(libs.plugins.binary.compatibility.validator)
}

// Generate klib ABI dumps; the apps and icon-font modules carry no public API
// surface worth tracking, so skip them.
apiValidation {
    klib { enabled = true }
    ignoredProjects.addAll(listOf("demo", "apidemo"))
}

// Project coordinates. `group` is baked into every module's Kotlin/Native klib
// metadata as its module ID (e.g. `com.bitsycore.compose.sdl:ui`) — keep it
// stable, or a stale IC cache surfaces as "Unknown dependent library …" (see
// the pitfall note in CLAUDE.md).
allprojects {
    group = "com.bitsycore.compose.sdl"
    version = "0.0.0-SNAPSHOT"
}

// Whether the current host can build the mingwX64 target. Kotlin/Native can
// only cross-compile mingwX64 cinterops from a Windows host — the sdl3_ttf /
// sdl3_image cinterops need Windows SDL3 headers under libs/ (produced by
// tools/build-sdl/build-*.sh, which only runs on Windows). Declaring `mingwX64()` on
// a non-Windows host is safe for pure-Kotlin modules but blows up the moment
// a `depends = sdl3` cinterop tries to include SDL3_ttf/SDL_ttf.h.
// Override with `-PforceMingw=true` if you actually have the headers wired.
val vHostSupportsMingw = System.getProperty("os.name").startsWith("Windows") ||
    (findProperty("forceMingw") as? String)?.toBoolean() == true
extra["vHostSupportsMingw"] = vHostSupportsMingw
