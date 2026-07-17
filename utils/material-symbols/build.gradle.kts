// :material-symbols — codepoint constants + all three Material Symbols style
// composables (Outlined / Rounded / Sharp) in one module, for BOTH stacks:
//
//   commonMain  — the MaterialSymbols codepoints + the PUBLIC API
//                 (MaterialSymbols<Style> objects, axis defaults) so
//                 consumers' shared code and IDE analysis resolve it.
//   nativeMain  — actual renderer over the port's IconFont pipeline
//                 (:foundation IconFontIcon; IconFont itself handles the
//                 SDL3 / Skia renderer split — this module never sees it).
//   jvmMain     — actual renderer over Skiko directly (Typeface.makeClone
//                 per axes combination + TextLine on the native canvas);
//                 the upstream Font(variationSettings) route is unusable —
//                 its FontCache drops the settings from the cache key.
//
// commonMain compiles against the OFFICIAL Maven Compose artifacts (metadata
// + jvm resolve them); the NATIVE target configurations substitute ui /
// material3 for the port's project modules — the FULL-COMMONIZATION BRIDGE
// declared once in the root build (the Maven artifacts ship no mingw/linux
// klibs). The runtime is the official klib everywhere, never substituted.
//
// The app owns the font files: the data.kres Zip task bundles the styles an
// app's sources actually reference (native), and the app's
// jvmProcessResources stages the same fonts onto the JVM classpath at
// font/<file>.ttf (see :demo's build file).
//
// Publication artifactId (when set up): compose-desktop-material-symbols.

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

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

// JVM stack version — the dev build matching the pinned COMPOSE_CORE_REF
// (scripts/compose-fork/compose.properties): byte-exact parity with the
// vendored sources, and (unlike published beta01) its desktop loadTypeface
// applies Font.variationSettings — required for the icon axes on JVM.
// material3 rides its own release train (same +dev build, different base).
// Gradle orders "+dev" BELOW the plain version, so force the core-repo
// groups on jvm configurations (mirrors :demo's forcing).
val vComposeJvmVersion = "1.12.0-beta02"
val vComposeM3JvmVersion = "1.12.0-alpha03"
val vComposeJvmForced = mapOf(
    "org.jetbrains.compose.runtime" to vComposeJvmVersion,
    "org.jetbrains.compose.ui" to vComposeJvmVersion,
    "org.jetbrains.compose.foundation" to vComposeJvmVersion,
    "org.jetbrains.compose.animation" to vComposeJvmVersion,
    "org.jetbrains.compose.material" to vComposeJvmVersion,
    "org.jetbrains.compose.material3" to vComposeM3JvmVersion,
)
configurations.configureEach {
    if (name.startsWith("jvm")) {
        resolutionStrategy.eachDependency {
            vComposeJvmForced[requested.group]?.let { useVersion(it) }
        }
    }
}

kotlin {
    jvm()

    linuxArm64()
    linuxX64()
    macosArm64()
    if (vHostSupportsMingw) mingwX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // The official runtime klibs — the SAME artifact/version the port
            // itself uses (:ui pins it); resolves for metadata, jvm AND native.
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            // Official Maven coordinates for the API surface (Modifier / Color
            // / Dp / material3's LocalContentColor). Metadata + jvm resolve
            // them from Maven; NATIVE configurations substitute them for
            // project(":ui") / project(":material3") (root build bridge).
            api("org.jetbrains.compose.ui:ui:$vComposeJvmVersion")
            // ui's transitives, declared EXPLICITLY: the native substitution
            // (ui → project(":ui")) hides them from the granular-metadata
            // visibility check, so compileCommonMainKotlinMetadata loses
            // Color / Dp without a direct declaration (+ bridge rules).
            api("org.jetbrains.compose.ui:ui-graphics:$vComposeJvmVersion")
            api("org.jetbrains.compose.ui:ui-unit:$vComposeJvmVersion")
            api("org.jetbrains.compose.material3:material3:$vComposeM3JvmVersion")
        }
        nativeMain.dependencies {
            // The native actual draws via com.compose.sdl.icons.IconFontIcon —
            // project code in :foundation (which api-depends on :ui).
            api(project(":foundation"))
        }
        jvmMain.dependencies {
            // BasicText for the JVM actual — same version as :demo's jvm
            // parity target.
            api("org.jetbrains.compose.foundation:foundation:$vComposeJvmVersion")
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

listOf(
    SymbolsStyle(
        "outlined",
        "MaterialSymbolsOutlined.ttf",
        "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"
    ),
    SymbolsStyle(
        "rounded",
        "MaterialSymbolsRounded.ttf",
        "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"
    ),
    SymbolsStyle(
        "sharp",
        "MaterialSymbolsSharp.ttf",
        "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsSharp%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf"
    ),
).forEach { style ->
    val vTitle = style.id.replaceFirstChar { it.uppercase() }
    val vOutProvider = layout.buildDirectory.file("iconFont/${style.fileName}")
    val vDownloadTask = tasks.register("downloadMaterialSymbols$vTitle") {
        description = "Download Material Symbols $vTitle variable font to build/iconFont/${style.fileName}"
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
