// compose-desktop-native-bridge — the CONSUMER-side bridge, published as a Gradle plugin.
//
// Dependency-substitution rules cannot travel inside a Maven artifact (they
// are always configured in the consuming build), so the FULL-COMMONIZATION
// BRIDGE this repo uses internally is shipped to third-party apps as a
// settings plugin instead: apply `com.bitsycore.compose-desktop-native.bridge`
// once, declare the OFFICIAL org.jetbrains.compose coordinates in commonMain,
// and every native configuration (mingwX64 / linuxX64 / linuxArm64 /
// macosArm64) swaps them for the published com.bitsycore.compose.sdl klibs —
// the official artifacts keep serving every platform CMP already supports.
//
// This is an INCLUDED build (pluginManagement.includeBuild in the repo's
// settings.gradle.kts), not a subproject — :apidemo applies the plugin from
// source. The root build's allprojects/subprojects blocks don't reach it, so
// coordinates + publishing are declared here.
//
// Published by the WINDOWS publish job (host-independent jar):
//   :compose-desktop-native-bridge:publishAllPublicationsToGitHubPackagesRepository
// (composite task addressing — the leading path segment is the included
// build's name).
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

// Same coordinate scheme as the root build: version is driven by
// PUBLISH_VERSION (the git tag, `v1.2.3` → `1.2.3`); local dev = SNAPSHOT.
group = "com.bitsycore.compose.sdl"
version = (System.getenv("PUBLISH_VERSION") ?: "0.0.0-SNAPSHOT").removePrefix("v")

// Mirrors the root build's GitHub Packages publishing block (which no longer
// reaches this module now that it's an included build).
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            val vRepo = System.getenv("GITHUB_REPOSITORY") ?: "bitsycore/ComposeDesktopNative"
            url = uri("https://maven.pkg.github.com/$vRepo")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("ComposeDesktopNative ${project.name}")
            description.set("Compose Multiplatform on SDL3 (Kotlin/Native, no JVM) — ${project.name}")
            url.set("https://github.com/${System.getenv("GITHUB_REPOSITORY") ?: "bitsycore/ComposeDesktopNative"}")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
        }
    }
}


gradlePlugin {
    plugins {
        create("composeDesktopNativeBridge") {
            id = "com.bitsycore.compose-desktop-native.bridge"
            implementationClass = "com.bitsycore.compose.sdl.gradle.ComposeDesktopNativeBridgePlugin"
            displayName = "Compose Desktop Native Bridge"
            description = "Substitutes the official Compose Multiplatform coordinates for the " +
                "com.bitsycore.compose.sdl klibs on the Kotlin/Native desktop targets CMP does not ship " +
                "(mingwX64, linuxX64, linuxArm64) + macosArm64."
        }
    }
}

// The plugin defaults the substitution version to ITS OWN version (plugin and
// klibs ship from the same tag) and also ADVERTISES the official Compose
// Multiplatform versions the port tracks, so consumers declare the matching
// coords without reading compose.properties by hand. Both are read at runtime
// from this generated resource, sourced from the version catalog (single truth).
val vVersionDir = layout.buildDirectory.dir("generated/bridge-version")
val vGenerateVersion = tasks.register("generateBridgeVersionResource") {
    val vVersion = project.version.toString()
    val vCompose = libs.versions.compose.get()
    val vComposeMaterial3 = libs.versions.composeMaterial3.get()
    val vComposeRuntime = libs.versions.composeRuntime.get()
    val vOut = vVersionDir.get().file("com/bitsycore/compose/sdl/gradle/bridge-version.properties").asFile
    inputs.property("version", vVersion)
    inputs.property("compose", vCompose)
    inputs.property("composeMaterial3", vComposeMaterial3)
    inputs.property("composeRuntime", vComposeRuntime)
    outputs.dir(vVersionDir)
    doLast {
        vOut.parentFile.mkdirs()
        vOut.writeText(
            "version=$vVersion\n" +
                "compose=$vCompose\n" +
                "composeMaterial3=$vComposeMaterial3\n" +
                "composeRuntime=$vComposeRuntime\n"
        )
    }
}
sourceSets["main"].resources.srcDir(vVersionDir)
tasks.named("processResources") { dependsOn(vGenerateVersion) }
