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
            content { excludeGroup("org.jetbrains.skiko") }
        }
        // Route 1a: the bitsycore/skiko fork's mingwX64 klib + runtime DLL,
        // published by its CI to GitHub Packages. Scoped to org.jetbrains.skiko.
        maven {
            name = "GitHubPackagesSkiko"
            url = uri("https://maven.pkg.github.com/bitsycore/skiko")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                    ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.token").orNull
                    ?: ""
            }
            content { includeGroup("org.jetbrains.skiko") }
        }
    }
}

rootProject.name = "ComposeDesktopNative"

// Library modules mirror upstream Compose Multiplatform's `compose/` tree
include(":ui")
include(":ui-util")
include(":ui-geometry")
include(":ui-graphics")
include(":ui-unit")
include(":ui-backhandler")
include(":ui-tooling-preview")
include(":animation-core")
include(":animation")
include(":animation-graphics")
include(":foundation")
include(":foundation-layout")
include(":material3")
include(":material-ripple")
include(":window")
include(":sdl-core")
include(":material-symbols")
include(":navigation3-ui")
include(":components-resources")
project(":ui").projectDir = file("compose/ui/ui")
project(":ui-util").projectDir = file("compose/ui/ui-util")
project(":ui-geometry").projectDir = file("compose/ui/ui-geometry")
project(":ui-graphics").projectDir = file("compose/ui/ui-graphics")
project(":ui-unit").projectDir = file("compose/ui/ui-unit")
project(":ui-backhandler").projectDir = file("compose/ui/ui-backhandler")
project(":ui-tooling-preview").projectDir = file("compose/ui/ui-tooling-preview")
project(":animation-core").projectDir = file("compose/animation/animation-core")
project(":animation").projectDir = file("compose/animation/animation")
project(":animation-graphics").projectDir = file("compose/animation/animation-graphics")
project(":foundation").projectDir = file("compose/foundation/foundation")
project(":foundation-layout").projectDir = file("compose/foundation/foundation-layout")
project(":material3").projectDir = file("compose/material3/material3")
project(":material-ripple").projectDir = file("compose/material/material-ripple")
project(":window").projectDir = file("compose/sdl/window")
project(":sdl-core").projectDir = file("sdl/sdl-core")
project(":material-symbols").projectDir = file("utils/material-symbols")
project(":navigation3-ui").projectDir = file("navigation3/navigation3-ui")
project(":components-resources").projectDir = file("components/resources/library")

// Demo App testing foundation, animation, ui and material3
include(":demo")
// postman like in compose sdl
include(":apidemo")
