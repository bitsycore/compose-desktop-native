// :material-symbols:outlined — bundles the Material Symbols Outlined
// variable font and exposes a one-call install() that registers it with
// IconFont under the family name "material-symbols-outlined". The codepoint
// constants in :core (MaterialSymbols) resolve identically against all three
// styles, so picking a style is just a matter of which module you depend on.
//
// The font (Apache 2.0, Google Fonts) is downloaded into build/iconFont/ at
// build time and stitched into the consumer app's data.kres by the demo's
// Zip task — which auto-discovers every :material-symbols:* module and
// pulls its "iconFontFile" task output. No Compose resources runtime is
// involved (we have our own .kres pipeline), so this module deliberately
// doesn't apply the compose-multiplatform plugin.
// Publication artifactId (when set up): compose-desktop-material-symbols-outlined.

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // Compose compiler only — the multiplatform-resources plugin would scan
    // composeResources/ and fail because the .ttf is generated at build time,
    // not present at configuration time. We bundle the font through the
    // demo's own Zip task (see demo/build.gradle.kts), not via Compose's
    // resources runtime.
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
            // The MaterialSymbols* objects emit Icon(...) and read
            // IconDefaults/MaterialIconAxisDefaults from :material.
            api(project(":material3"))
            // Codepoint constants — depending on any style module
            // transparently pulls them in.
            api(project(":material-symbols"))
        }
    }
}

// ==================
// MARK: Font download
// ==================
// The font output is registered as a project property "iconFontFile" so the
// demo's Zip task can resolve it generically across every icon-font module
// without hard-coding paths.

val materialSymbolsFont = layout.buildDirectory.file("iconFont/MaterialSymbolsOutlined.ttf")

val downloadMaterialSymbolsOutlined = tasks.register("downloadMaterialSymbolsOutlined") {
    // Capture as plain local vals (String / File) so the configuration cache
    // can serialize the doLast body — Provider<RegularFile> referenced inside
    // doLast trips the script-object-references error otherwise.
    val vUrl = "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"
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
extra["iconFontDownloadTask"] = downloadMaterialSymbolsOutlined
