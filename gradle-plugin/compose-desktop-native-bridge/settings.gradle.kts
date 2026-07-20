// Self-contained settings — this build is INCLUDED by the root build
// (pluginManagement.includeBuild in the repo's settings.gradle.kts) so
// :apidemo dogfoods the plugin from source. Being a separate build (not a
// subproject), it declares its own repositories and wires the repo's shared
// version catalog by path.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "compose-desktop-native-bridge"
