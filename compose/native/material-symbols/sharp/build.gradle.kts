// :material-symbols:sharp — Sharp style of Material Symbols. Mirrors the
// :outlined module but ships a different .ttf and registers under a
// different family name. See :outlined for architectural notes.
// Publication artifactId (when set up): compose-desktop-material-symbols-sharp.

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.URI

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
            api(project(":material3"))
            api(project(":material-symbols"))
        }
    }
}

val materialSymbolsFont = layout.buildDirectory.file("iconFont/MaterialSymbolsSharp.ttf")

val downloadMaterialSymbolsSharp = tasks.register("downloadMaterialSymbolsSharp") {
    val vUrl = "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsSharp%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"
    val vOut = materialSymbolsFont.get().asFile
    outputs.file(vOut)
    doLast {
        if (vOut.exists() && vOut.length() > 0) return@doLast
        vOut.parentFile.mkdirs()
        println("Downloading $vUrl")
        URI(vUrl).toURL().openStream().use { vIn ->
            vOut.outputStream().use { vIn.copyTo(it) }
        }
        println("Saved ${vOut.length() / 1024} KiB to $vOut")
    }
}

extra["iconFontFile"] = materialSymbolsFont
extra["iconFontDownloadTask"] = downloadMaterialSymbolsSharp
