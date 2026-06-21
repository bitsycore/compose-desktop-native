pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "ComposeNativeSDL3"
include(":compose-desktop-native-core")
include(":compose-desktop-native-renderer-sdl3")
include(":compose-desktop-native-renderer-skia")
include(":compose-desktop-native")
include(":compose-desktop-material-symbols:outlined")
include(":compose-desktop-material-symbols:rounded")
include(":compose-desktop-material-symbols:sharp")
include(":demo")