pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Single source of dependency repositories for every module (was duplicated in each
// module's build.gradle.kts). PREFER_SETTINGS: these win; a stray project-level
// repositories{} is ignored (with a warning) rather than failing the build.
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

rootProject.name = "ComposeNativeSDL3"

// Library modules mirror upstream Compose Multiplatform's `compose/` tree
// (ui, foundation, animation-core, material3, material-symbols at the top
// level). The current split:
//   :ui              — androidx.compose.ui.* + com.compose.desktop.native.*
//                      (cinterops + both renderer pipelines). Package-source-wise
//                      cross-platform-friendly; renderer bindings + cinterops
//                      are what pin it to native today.
//   :animation-core  — androidx.compose.animation.core.*
//   :foundation      — androidx.compose.foundation.* + androidx.compose.animation.*
//                      (non-core; merged to sidestep the foundation-layout
//                      circular dep without splitting :foundation-layout too)
//   :material3       — androidx.compose.material3.*
//   :material-symbols — icon-font modules (outlined / rounded / sharp)
// Module PATHS stay short (:ui, :foundation, …) so build files across the
// repo stay terse; each project's directory is redirected via projectDir below.
//
// :window is the exception — it owns nativeComposeWindow(), the SDL3 main
// loop, and the recomposer lifecycle. That's inherently native, so it
// stays under compose/native/window/. Everything else lives directly
// under compose/.
include(":ui")
include(":animation-core")
include(":foundation")
include(":material3")
include(":material-ripple")
include(":window")
include(":material-symbols")
project(":ui").projectDir = file("compose/ui")
project(":animation-core").projectDir = file("compose/animation-core")
project(":foundation").projectDir = file("compose/foundation")
project(":material3").projectDir = file("compose/material3")
project(":material-ripple").projectDir = file("compose/material/material-ripple")
project(":window").projectDir = file("compose/native/window")
project(":material-symbols").projectDir = file("compose/material-symbols")

// App modules stay at the top level so `./gradlew :demo:run…` reads naturally
// as an app-facing command, distinct from library modules.
include(":demo")
include(":apidemo")

// The JVM comparison app: a Compose Desktop (JVM) target that compiles the SAME
// shared screens as :demo (via kotlin.srcDir on demo/src/commonMain) but against
// UPSTREAM org.jetbrains.compose instead of this project's native reimplementation.
// Running both side-by-side is how we verify the commonization holds.
include(":demojvm")
