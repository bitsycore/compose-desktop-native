// :material — Material widgets re-implemented on top of :core's
// foundation+ui surface (Button, Text, MaterialTheme, Surface, TextField,
// Slider, Switch, Checkbox, Radio, Chip, Card, Dialog, DropdownMenu,
// SegmentedButton, Snackbar, Tooltip, ProgressIndicator, ...). Kept as a
// separate artifact so apps that only want the foundation+ui base
// without Material can skip pulling these in.
// Publication artifactId (when set up): compose-desktop-native-material.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}
