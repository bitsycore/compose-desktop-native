pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // -PuseReleased=<version> swaps :window / :material3 / :material-symbols for the
        // published artifacts (see demo/apidemo build.gradle.kts). Only added when set.
        val useReleased = providers.gradleProperty("useReleased").orNull?.takeIf { it.isNotBlank() }
        if (useReleased != null) {
            maven {
                name = "GitHubPackages"
                setUrl("https://maven.pkg.github.com/bitsycore/ComposeDesktopNative")
                credentials {
                    username = providers.gradleProperty("githubUser").orNull ?: System.getenv("GITHUB_ACTOR") ?: ""
                    password = providers.gradleProperty("githubToken").orNull ?: System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}

rootProject.name = "ComposeSDL"

// Library modules mirror upstream Compose Multiplatform's `compose/` tree — one
// Gradle module per upstream artifact (dir = upstream path, gradle path kept short):
//   :ui                (compose/ui)                            androidx.compose.ui.* + com.compose.sdl.* + SDL cinterops
//   :animation-core    (compose/animation/animation-core)      androidx.compose.animation.core.*
//   :animation         (compose/animation/animation)           androidx.compose.animation.* )
//   :foundation        (compose/foundation/foundation)         androidx.compose.foundation.*
//   :foundation-layout (compose/foundation/foundation-layout)  androidx.compose.foundation.layout.*
//   :material3         (compose/material3/material3)           androidx.compose.material3.*
//   :material-ripple   (compose/material/material-ripple)      androidx.compose.material.ripple.*
//   :material-symbols  (compose/sdl/material-symbols)          icon-font modules (outlined / rounded / sharp)
// androidx.collection comes from Maven.
// Module PATHS stay short (:ui, :foundation, …) so build files stay terse
// Our customs modules are in compose/sdl/ like window
include(":ui")
include(":animation-core")
include(":animation")
include(":animation-graphics")
include(":foundation")
include(":foundation-layout")
include(":material3")
include(":material-ripple")
include(":window")
include(":material-symbols")
project(":ui").projectDir = file("compose/ui/ui")
project(":animation-core").projectDir = file("compose/animation/animation-core")
project(":animation").projectDir = file("compose/animation/animation")
project(":animation-graphics").projectDir = file("compose/animation/animation-graphics")
project(":foundation").projectDir = file("compose/foundation/foundation")
project(":foundation-layout").projectDir = file("compose/foundation/foundation-layout")
project(":material3").projectDir = file("compose/material3/material3")
project(":material-ripple").projectDir = file("compose/material/material-ripple")
project(":window").projectDir = file("compose/sdl/window")
project(":material-symbols").projectDir = file("compose/sdl/material-symbols")

// Demo App testing foundation, animation, ui and material3
include(":demo")
// JVM version running demo directly on the jvm and upstream compose
include(":demojvm")
// postman like in compose sdl
include(":apidemo")
