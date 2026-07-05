pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "ComposeNativeSDL3"

// Library modules live under `compose/native/` (mirrors upstream Compose
// Multiplatform's compose/ tree). The current split:
//   :ui              — androidx.compose.ui.* + com.compose.desktop.native.*
//                      (cinterops + both renderer pipelines)
//   :animation-core  — androidx.compose.animation.core.*
//   :foundation      — androidx.compose.foundation.* + androidx.compose.animation.*
//                      (non-core; merged to sidestep the foundation-layout
//                      circular dep without splitting :foundation-layout too)
//   :window          — nativeComposeWindow() main loop
//   :material3       — androidx.compose.material3.*
//   :material-symbols — icon-font modules (outlined / rounded / sharp)
// Module PATHS stay short (:ui, :window, …) so build files across the repo
// stay terse; each project's directory is redirected via projectDir below.
include(":ui")
include(":animation-core")
include(":foundation")
include(":material3")
include(":window")
include(":material-symbols")
include(":material-symbols:outlined")
include(":material-symbols:rounded")
include(":material-symbols:sharp")
project(":ui").projectDir = file("compose/native/ui")
project(":animation-core").projectDir = file("compose/native/animation-core")
project(":foundation").projectDir = file("compose/native/foundation")
project(":material3").projectDir = file("compose/native/material3")
project(":window").projectDir = file("compose/native/window")
project(":material-symbols").projectDir = file("compose/native/material-symbols")
project(":material-symbols:outlined").projectDir = file("compose/native/material-symbols/outlined")
project(":material-symbols:rounded").projectDir = file("compose/native/material-symbols/rounded")
project(":material-symbols:sharp").projectDir = file("compose/native/material-symbols/sharp")

// App modules stay at the top level so `./gradlew :demo:run…` reads naturally
// as an app-facing command, distinct from library modules.
include(":demo")
include(":apidemo")
