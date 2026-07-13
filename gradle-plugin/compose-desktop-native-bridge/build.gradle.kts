// :compose-desktop-native-bridge — the CONSUMER-side bridge, published as a Gradle plugin.
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
// Published by the macOS publish job (host-independent jar):
//   :compose-desktop-native-bridge:publishAllPublicationsToGitHubPackagesRepository
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
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
// klibs ship from the same tag), read at runtime from this generated resource.
val vVersionDir = layout.buildDirectory.dir("generated/bridge-version")
val vGenerateVersion = tasks.register("generateBridgeVersionResource") {
    val vVersion = project.version.toString()
    val vOut = vVersionDir.get().file("com/bitsycore/compose/sdl/gradle/bridge-version.properties").asFile
    inputs.property("version", vVersion)
    outputs.dir(vVersionDir)
    doLast {
        vOut.parentFile.mkdirs()
        vOut.writeText("version=$vVersion\n")
    }
}
sourceSets["main"].resources.srcDir(vVersionDir)
tasks.named("processResources") { dependsOn(vGenerateVersion) }
