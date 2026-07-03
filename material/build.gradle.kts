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
            // Material widgets reference MaterialSymbols codepoints in
            // their public defaults (e.g. Icon's default icon param).
            api(project(":material-symbols"))
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
