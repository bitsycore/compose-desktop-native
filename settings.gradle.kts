pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    // The consumer-side bridge plugin, built from source as an INCLUDED build
    includeBuild("gradle-plugin/compose-desktop-native-bridge")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Route 1a: the bitsycore/skiko fork's skiko-mingwx64 klib is published here.
        mavenLocal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/bitsycore/compose-desktop-native")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                    ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.token").orNull
                    ?: ""
            }
            // Neither skiko group lives here: the official skiko comes from Maven
            // Central, the bitsycore fork from GitHubPackagesSkiko below.
            content { excludeGroup("org.jetbrains.skiko"); excludeGroup("com.bitsycore.skiko") }
        }
        // Route 1a: the bitsycore/skiko fork's mingwX64 klib + runtime DLL.
        // PUBLIC and unauthenticated - no GitHub token needed to build the
        // Windows target. Scoped to com.bitsycore.skiko (the fork is
        // republished under the bitsycore name - see the fork repo).
        maven {
            name = "BitsycoreSkiko"
            url = uri("https://maven.bitsycore.com/releases")
            content { includeGroup("com.bitsycore.skiko") }
        }
    }
}

rootProject.name = "ComposeDesktopNative"

// Library modules mirror upstream Compose Multiplatform's `compose/` tree, and
// the Gradle path mirrors the directory 1:1 - a plain `include(...)` per module,
// no projectDir redirection anywhere.
//
// INVARIANT: the leaf directory IS the published artifactId. Kotlin
// Multiplatform derives each target publication's coordinate as
// `<project.name>-<target>`, and for a target DISABLED on the current host
// (every Apple target on Windows/Linux) there is no publication object to
// retarget after the fact - yet the root `kotlinMultiplatform` module metadata,
// which the WINDOWS publish job owns, still writes an `available-at` entry for
// it. So a mismatched leaf silently publishes a root module pointing at a
// nonexistent `<leaf>-macosarm64` and breaks macOS consumers; an artifactId
// override can't reach it. Two directories were renamed to hold the invariant:
// compose/desktop/native/window -> .../desktop-native-window, and
// components/resources/library -> components/resources/components-resources
// (upstream keeps `library` there and sets artifactId by hand instead).
include(":compose:ui:ui")
include(":compose:ui:ui-util")
include(":compose:ui:ui-geometry")
include(":compose:ui:ui-graphics")
include(":compose:ui:ui-text")
include(":compose:ui:ui-unit")
include(":compose:ui:ui-backhandler")
include(":compose:ui:ui-tooling-preview")
include(":compose:animation:animation-core")
include(":compose:animation:animation")
include(":compose:animation:animation-graphics")
include(":compose:foundation:foundation")
include(":compose:foundation:foundation-layout")
include(":compose:material3:material3")
include(":compose:material3:adaptive:adaptive")
include(":compose:material3:adaptive:adaptive-layout")
include(":compose:material3:adaptive:adaptive-navigation")
include(":compose:material3:adaptive:adaptive-navigation3")
include(":compose:material:material-ripple")
include(":compose:desktop:native:desktop-native-window")
include(":sdl:sdl-core")
include(":utils:material-symbols")
include(":navigation3:navigation3-ui")
// Koin DI - only the modules upstream stops publishing for desktop native.
include(":koin:koin-core-viewmodel")
include(":koin:koin-compose")
include(":koin:koin-compose-viewmodel")
include(":koin:koin-compose-navigation3")
// Coil 3 image loading - upstream ships linux but no mingwX64, and no desktop
// native at all for the compose layer.
include(":coil:coil-core")
include(":coil:coil")
include(":coil:coil-compose-core")
include(":coil:coil-compose")
include(":coil:coil-svg")
include(":coil:coil-network-core")
include(":coil:coil-network-ktor3")
// Pulse MVI (bitsycore/pulse-mvi) - upstream has no desktop-native target at all.
include(":pulse:pulse")
include(":pulse:pulse-viewmodel")
include(":pulse:pulse-savedstate")
include(":pulse:pulse-compose")
include(":components:resources:components-resources")

// Demo App testing foundation, animation, ui and material3
include(":demo")
// postman like in compose sdl
include(":apidemo")
