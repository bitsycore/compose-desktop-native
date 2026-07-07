// :material-symbols — codepoint constants + all three Material Symbols style
// composables (Outlined / Rounded / Sharp) in one module.
//
// Previously each style lived in its own :material-symbols:outlined /
// :rounded / :sharp sub-module and apps opted in by listing the style
// dependencies they wanted. The three styles are 100% pure Kotlin
// (`object MaterialSymbolsOutlined { … }`) — no cinterops, no per-target
// actuals, no reason to be separate modules. Consolidated so apps just
// `implementation(project(":material-symbols"))` once and get all three;
// the app's data.kres Zip task decides which style FONT to actually
// bundle by scanning the app's Kotlin sources for MaterialSymbols<Style>
// call sites (see :apidemo / :demo build files).
//
// Publication artifactId (when set up): compose-desktop-material-symbols.

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // Compose compiler only — the multiplatform-resources plugin would scan
    // composeResources/ and fail because the .ttf is generated at build time,
    // not present at configuration time. We bundle the font(s) through each
    // consumer app's own Zip task (see apidemo/demo build.gradle.kts), not via
    // Compose's resources runtime.
    alias(libs.plugins.kotlin.plugin.compose)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw: Boolean by rootProject.extra

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    if (vHostSupportsMingw) mingwX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // The MaterialSymbols<Style> objects emit Icon(...) via
            // com.compose.desktop.native.icons.IconFontIcon and reference
            // MaterialIconAxisDefaults / IconDefaults — those live in
            // :foundation (com.compose.desktop.native.icons package), which
            // itself already api-depends on :ui.
            api(project(":foundation"))
            // Default tint follows upstream material3 Icon:
            // `tint: Color = LocalContentColor.current` — needs material3's
            // LocalContentColor (provided by Surface / button content / …).
            api(project(":material3"))
        }
    }
}

// ==================
// MARK: Font downloads (Outlined / Rounded / Sharp)
// ==================
// Each style's variable font is downloaded once at build time to build/iconFont/.
// The three files are advertised via project extras so consumer app builds can
// pull them (and their download tasks for correct task ordering) generically:
//     rootProject.project(":material-symbols").extra["iconFontFileOutlined"]
//     rootProject.project(":material-symbols").extra["iconFontFileRounded"]
//     rootProject.project(":material-symbols").extra["iconFontFileSharp"]

data class SymbolsStyle(val id: String, val fileName: String, val url: String)
val kStyles = listOf(
    SymbolsStyle("outlined", "MaterialSymbolsOutlined.ttf",
        "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"),
    SymbolsStyle("rounded", "MaterialSymbolsRounded.ttf",
        "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"),
    SymbolsStyle("sharp", "MaterialSymbolsSharp.ttf",
        "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsSharp%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"),
)

for (style in kStyles) {
    val vTitle = style.id.replaceFirstChar { it.uppercase() }
    val vOutProvider = layout.buildDirectory.file("iconFont/${style.fileName}")
    val vDownloadTask = tasks.register("downloadMaterialSymbols$vTitle") {
        // Capture local vals so the config-cache can serialize doLast.
        val vUrl = style.url
        val vOut = vOutProvider.get().asFile
        outputs.file(vOut)
        doLast {
            if (vOut.exists() && vOut.length() > 0) return@doLast
            vOut.parentFile.mkdirs()
            println("Downloading $vUrl")
            URI(vUrl).toURL().openStream().use { vIn -> vOut.outputStream().use { vIn.copyTo(it) } }
            println("Saved ${vOut.length() / 1024} KiB to $vOut")
        }
    }
    extra["iconFontFile$vTitle"] = vOutProvider
    extra["iconFontDownloadTask$vTitle"] = vDownloadTask
}
