pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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
include(":window")
include(":material-symbols")
project(":ui").projectDir = file("compose/ui")
project(":animation-core").projectDir = file("compose/animation-core")
project(":foundation").projectDir = file("compose/foundation")
project(":material3").projectDir = file("compose/material3")
project(":window").projectDir = file("compose/native/window")
project(":material-symbols").projectDir = file("compose/material-symbols")

// App modules stay at the top level so `./gradlew :demo:run…` reads naturally
// as an app-facing command, distinct from library modules.
include(":demo")
include(":apidemo")
