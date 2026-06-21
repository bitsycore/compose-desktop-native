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
include(":renderer-sdl3")
include(":renderer-skia")
include(":window")
include(":material-symbols:outlined")
include(":material-symbols:rounded")
include(":material-symbols:sharp")
include(":demo")
