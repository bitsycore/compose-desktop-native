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
//   :material-symbols  (utils/material-symbols)                icon-font modules (outlined / rounded / sharp)
// androidx.collection comes from Maven.
// Module PATHS stay short (:ui, :foundation, …) so build files stay terse
// Our custom modules: :window in compose/sdl/, :material-symbols in utils/
include(":ui")
include(":ui-util")
include(":ui-geometry")
include(":ui-unit")
include(":ui-backhandler")
include(":animation-core")
include(":animation")
include(":animation-graphics")
include(":foundation")
include(":foundation-layout")
include(":material3")
include(":material-ripple")
include(":window")
include(":material-symbols")
include(":navigation3-ui")
project(":ui").projectDir = file("compose/ui/ui")
project(":ui-util").projectDir = file("compose/ui/ui-util")
project(":ui-geometry").projectDir = file("compose/ui/ui-geometry")
project(":ui-unit").projectDir = file("compose/ui/ui-unit")
project(":ui-backhandler").projectDir = file("compose/ui/ui-backhandler")
project(":animation-core").projectDir = file("compose/animation/animation-core")
project(":animation").projectDir = file("compose/animation/animation")
project(":animation-graphics").projectDir = file("compose/animation/animation-graphics")
project(":foundation").projectDir = file("compose/foundation/foundation")
project(":foundation-layout").projectDir = file("compose/foundation/foundation-layout")
project(":material3").projectDir = file("compose/material3/material3")
project(":material-ripple").projectDir = file("compose/material/material-ripple")
project(":window").projectDir = file("compose/sdl/window")
project(":material-symbols").projectDir = file("utils/material-symbols")
project(":navigation3-ui").projectDir = file("navigation3/navigation3-ui")

// Demo App testing foundation, animation, ui and material3
include(":demo")
// postman like in compose sdl
include(":apidemo")
