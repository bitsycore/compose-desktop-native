pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "ComposeNativeSDL3"
include(":core")
include(":material")
include(":material3")
include(":window")
include(":material-symbols")
include(":material-symbols:outlined")
include(":material-symbols:rounded")
include(":material-symbols:sharp")
include(":demo")
include(":apidemo")
