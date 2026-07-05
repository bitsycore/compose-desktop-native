pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "ComposeNativeSDL3"

// Library modules live under `compose/native/` (mirrors upstream's `compose/`
// layout — keeps room to split the current mega-:core into per-package
// modules later without renaming top-level dirs). Module PATHS stay short
// (:core, :window, …) so build files across the repo stay terse; each project's
// directory is redirected via projectDir below.
include(":core")
include(":material3")
include(":window")
include(":material-symbols")
include(":material-symbols:outlined")
include(":material-symbols:rounded")
include(":material-symbols:sharp")
project(":core").projectDir = file("compose/native/core")
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
