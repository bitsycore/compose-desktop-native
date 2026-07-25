import com.bitsycore.compose.sdl.build.registerComposeFontBundling

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("com.bitsycore.compose-desktop-native.bridge")
}

val isHostMingwCompatible = rootProject.extra["vHostSupportsMingw"] as Boolean

// ==================
// MARK: Targets
// ==================

kotlin {
    jvm()

    buildList {
        add(linuxArm64())
        add(linuxX64())
        add(macosArm64())
        if (isHostMingwCompatible) add(mingwX64())
    }.forEach {
        it.binaries {
            executable {
                when {
                    target.name == "mingwX64" -> linkerOpts(
                        // crypt32: client-cert (mTLS) import into the Windows cert store.
                        "-lcrypt32",
                        "-Wl,--gc-sections", "-Wl,-s",
                        // GUI subsystem (no console window), keeping the C `main` entry.
                        "-Wl,--subsystem,windows", "-Wl,-e,mainCRTStartup",
                    )

                    target.name.startsWith("linux") -> linkerOpts(
                        "-L/usr/lib/x86_64-linux-gnu", "-L/usr/lib/aarch64-linux-gnu",
                        "-lfontconfig", "-lGL", "-lX11",
                    )
                }
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                val vComposeJvmVersion = libs.versions.compose.get()
                val vComposeM3JvmVersion = libs.versions.composeMaterial3.get()

                // Official Maven coords for everything the shared UI touches; native
                // configurations substitute them for the port modules automatically
                // (root bridge in-repo, the bridge plugin for consumers).
                implementation("org.jetbrains.compose.runtime:runtime:${libs.versions.composeRuntime.get()}")
                implementation("org.jetbrains.compose.ui:ui:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.ui:ui-graphics:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.ui:ui-text:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.ui:ui-unit:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.ui:ui-geometry:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.ui:ui-util:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.foundation:foundation:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.foundation:foundation-layout:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.animation:animation:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.animation:animation-core:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.material3:material3:$vComposeM3JvmVersion")

                // Common Material Symbols Utility
                implementation(project(":material-symbols"))

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        nativeMain {
            dependencies {
                implementation(project(":desktop-native-window"))
                implementation(libs.ktor.client.curl)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
    }
}

compose.desktop {
    application {
        mainClass = "apidemo.MainJvmKt"
    }
}

compose.desktop.native {
    entryPoint = "apidemo.main"
    icon {
        light.from(
            layout.projectDirectory.file("icons/voltic-icon-32.png"),
            layout.projectDirectory.file("icons/voltic-icon-128.png"),
        )
        dark.from(
            layout.projectDirectory.file("icons/voltic-icon-dark-32.png"),
            layout.projectDirectory.file("icons/voltic-icon-dark-128.png"),
        )
        exeIcon.from(
            listOf(16, 32, 48, 64, 128, 256).map {
                layout.projectDirectory.file("icons/voltic-icon-$it.png")
            }
        )
        embedWindowsIcon = providers.gradleProperty("embedWindowsIcon").map { it.toBoolean() }
    }
}

// ==================
// MARK: Fonts (shared pipeline — buildSrc ComposeFontBundling.kt)
// ==================

registerComposeFontBundling {
    bundleNotoSans = true
    // The mono body font is loaded through the app's own seam (Fonts.kt), not via
    // FontFamily.Monospace call sites — auto-detection can't see it, bundle explicitly.
    bundleNotoSansMono = true
    bundleMaterialSymbols = true
    enableIconSubsetting = true
}

// Parity-window icon (cosmetic : the harness compares the canvas, not chrome).
tasks.named<ProcessResources>("jvmProcessResources") {
    from(layout.projectDirectory.file("icons/voltic-icon-256.png")) { into("icon") }
}