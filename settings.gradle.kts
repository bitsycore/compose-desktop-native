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

// Library modules mirror upstream Compose Multiplatform's `compose/` tree — one
// Gradle module per upstream artifact (dir = upstream path, gradle path kept short):
//   :ui              (compose/ui)                     androidx.compose.ui.* + com.compose.desktop.native.*
//                                                     (cinterops + both renderer pipelines; still the merged
//                                                     ui mega-module — not split further yet).
//   :animation-core  (compose/animation/animation-core)      androidx.compose.animation.core.*
//   :animation       (compose/animation/animation)           androidx.compose.animation.* (non-core; incl. animation-graphics.*)
//   :foundation      (compose/foundation/foundation)         androidx.compose.foundation.*
//   :foundation-layout (compose/foundation/foundation-layout) androidx.compose.foundation.layout.*
//   :material3       (compose/material3/material3)            androidx.compose.material3.*
//   :material-ripple (compose/material/material-ripple)       androidx.compose.material.ripple.*
//   :material-symbols (compose/material-symbols)     icon-font modules (outlined / rounded / sharp)
// DAG (all `api`): foundation → animation, foundation-layout, animation-core, ui;
//   animation → animation-core, foundation-layout; material3 → foundation, material-ripple;
//   material-ripple → foundation, animation-core. androidx.collection comes from Maven.
// Module PATHS stay short (:ui, :foundation, …) so build files stay terse; each
// project's directory is redirected via projectDir below.
//
// :window is the exception — it owns nativeComposeWindow(), the SDL3 main
// loop, and the recomposer lifecycle. That's the SDL integration layer
// ("Compose SDL"), so it lives under compose/sdl/window/.
include(":ui")
include(":animation-core")
include(":animation")
include(":foundation")
include(":foundation-layout")
include(":material3")
include(":material-ripple")
include(":window")
include(":material-symbols")
project(":ui").projectDir = file("compose/ui")
project(":animation-core").projectDir = file("compose/animation/animation-core")
project(":animation").projectDir = file("compose/animation/animation")
project(":foundation").projectDir = file("compose/foundation/foundation")
project(":foundation-layout").projectDir = file("compose/foundation/foundation-layout")
project(":material3").projectDir = file("compose/material3/material3")
project(":material-ripple").projectDir = file("compose/material/material-ripple")
project(":window").projectDir = file("compose/sdl/window")
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
