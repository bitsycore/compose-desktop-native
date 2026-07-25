import com.bitsycore.compose.sdl.build.registerComposeFontBundling

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("com.bitsycore.compose-desktop-native.bridge")
}

val isHostMingwCompatible = rootProject.extra["vHostSupportsMingw"] as Boolean

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
                implementation("org.jetbrains.compose.ui:ui-tooling-preview:${vComposeJvmVersion}")
                implementation("org.jetbrains.compose.foundation:foundation:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.foundation:foundation-layout:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.animation:animation:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.animation:animation-core:$vComposeJvmVersion")
                implementation("org.jetbrains.compose.components:components-resources:${vComposeJvmVersion}")

                implementation("org.jetbrains.compose.material3:material3:$vComposeM3JvmVersion")

                // Common Material Symbols Utility
                implementation(project(":material-symbols"))

                // nav3 + lifecycle: real KMP Maven artifacts on every target
                implementation("androidx.navigation3:navigation3-runtime:1.1.4")
                implementation("org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0-alpha02")
                implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")
            }
        }
        nativeMain {
            dependencies {
                implementation(project(":window"))
            }
        }
        jvmMain {
            dependencies {
                implementation("androidx.navigation3:navigation3-runtime:1.2.0-alpha05")
                implementation(compose.desktop.currentOs)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}

compose.desktop {
    application {
        mainClass = "MainJvmKt"
    }
}

compose.desktop.native {
    entryPoint = "main"
}

compose.resources {
    packageOfResClass = "demo.generated.resources"
}

registerComposeFontBundling {
    bundleNotoSans = true
    autoDetectNotoSansMono = true
    bundleMaterialSymbols = true
}
// The mingwX64 skiko-windows-x64.dll is provisioned automatically by the bridge
// plugin (see installWindowsSkiaDll).
