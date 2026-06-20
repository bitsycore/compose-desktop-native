pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "ComposeNativeSDL3"
include(":compose-core")
include(":compose-renderer-sdl3")
include(":compose-renderer-skia")
include(":compose-desktop-native")
include(":demo")