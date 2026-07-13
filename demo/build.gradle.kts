@file:OptIn(KotlinNativeCacheApi::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

// JVM parity target's Compose version — the dev build matching the pinned
// COMPOSE_CORE_REF (see scripts/compose-fork/compose.properties). material3
// rides its OWN release train: same +dev build number, different base
// version. Gradle orders "+dev" qualifiers BELOW the plain version, so
// currentOs's beta01 would win conflict resolution — force the core-repo
// groups on every jvm configuration. Umbrella-repo groups (desktop,
// components) are NOT forced: their dev numbering differs.
val vComposeJvmVersion = "1.12.0-beta01+dev4324"
val vComposeM3JvmVersion = "1.12.0-alpha03+dev4324"
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

    // Give us a real `nativeMain` intermediate source set (all targets are
    // native today). Screens that only touch androidx.compose.* live in
    // commonMain so a future jvm() target can compile them against upstream
    // Compose; project-only (com.compose.sdl.*) screens + the SDL3
    // entry point stay in nativeMain.
    applyDefaultHierarchyTemplate()

    targets.withType<KotlinNativeTarget>().all {
        val isMingw = name == "mingwX64"
        val isLinux = name.startsWith("linux")
        binaries.executable {
            disableNativeCache(
                version = DisableCacheInKotlinVersion.`2_4_0`,
                reason = "Weird undef symbole on macos"
            )
            entryPoint = "main"
            // SDL3 + SDL3_ttf + SDL3_image + image codecs + FreeType come from
            // :ui's cinterop klibs (staticLibraries directive in each .def), and
            // the system libs / frameworks each static archive needs are declared
            // in sdl3.def's linkerOpts.<target>. Nothing SDL-related is wired
            // here — what remains is shell-level flags only.
            if (isMingw) linkerOpts(
                // Code-shrink: drop unused sections and strip symbols.
                "-Wl,--gc-sections", "-Wl,-s",
                // GUI subsystem so launching the .exe pops no console window.
                // Keep the standard C `main` entry — ld would otherwise default
                // a GUI-subsystem PE to WinMainCRTStartup (needs WinMain) and
                // fail to link, since Kotlin/Native emits `main`.
                "-Wl,--subsystem,windows", "-Wl,-e,mainCRTStartup",
            )
            // Skia (default renderer) references the system graphics stack:
            // fontconfig (font matching), GL (Skia GL backend), X11 (windowing).
            // Under -Prenderer=sdl3 these go unused; the extra -l is harmless.
            // K/N's LLD sysroot on linuxX64 doesn't include the host's multi-arch
            // dir, so add the -L for both x86_64 and aarch64 (harmless if either
            // is absent — LLD skips missing dirs).
            if (isLinux) linkerOpts(
                "-L/usr/lib/x86_64-linux-gnu", "-L/usr/lib/aarch64-linux-gnu",
                "-lfontconfig", "-lGL", "-lX11",
            )
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // The OFFICIAL resources runtime, at the plugin's own version —
                // resolvable for jvm + metadata from Maven; the NATIVE targets
                // substitute it for the vendored :components-resources below
                // (the Maven artifact ships no mingwX64/linux klibs).
                implementation(compose.components.resources)
                // Common API on both stacks (its native/jvm actuals pick the
                // right rendering pipeline) — usable from shared screens.
                implementation(project(":material-symbols"))

                // Official Maven coords for everything the shared screens
                // touch, so commonMain metadata (and the IDE's common
                // analysis) resolve. Metadata + jvm resolve them from Maven;
                // the NATIVE configurations substitute each for its port
                // module (root build's FULL-COMMONIZATION BRIDGE). Every
                // artifact must be declared DIRECTLY: transitives of a
                // substituted module are invisible to the granular-metadata
                // visibility check. The runtime is the official klib
                // everywhere, never substituted (jvm configs force the +dev
                // build above).
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
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

                // nav3 + lifecycle: real KMP Maven artifacts on every target
                // (see "Known Compatible" in CLAUDE.md) — except navigation3-ui,
                // which the bridge substitutes for the vendored :navigation3-ui
                // on native.
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
                // The +dev builds matching scripts/compose-fork's pinned
                // COMPOSE_CORE_REF come from the version forcing above —
                // byte-exact parity with the vendored sources, and (unlike
                // the published beta01) desktop loadTypeface applies
                // Font.variationSettings, so the Material Symbols
                // variable-font axes work on JVM.
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

// The OFFICIAL resources pipeline runs as-is: assets at the convention
// location (src/commonMain/composeResources), Res generated into commonMain,
// JVM packaging handled by the plugin. The one port-specific piece is the
// substitution below.
compose.resources {
    packageOfResClass = "demo.generated.resources"
}

// FULL-COMMONIZATION BRIDGE: commonMain declares the official Maven
// components-resources (metadata + jvm resolve it), and every NATIVE target
// configuration swaps that module for the vendored :components-resources —
// same library version (the SET_REPO manifest pins the matching tag), so the
// plugin-generated Res compiles once in commonMain and links everywhere.
val vNativeTargetTokens = listOf("mingwX64", "linuxX64", "linuxArm64", "macosArm64")
configurations.configureEach {
    if (vNativeTargetTokens.any { name.contains(it, ignoreCase = true) }) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.jetbrains.compose.components:components-resources"))
                .using(project(":components-resources"))
        }
    }
}

// Variants × targets used by the bundling Copy tasks below. One Copy task per
// (variant, target) keeps things configuration-cache friendly.
val variants = listOf("debug", "release")
val nativeTargets = listOf("macosArm64", "linuxX64", "linuxArm64", "mingwX64")

// On Windows everything (SDL3 / SDL3_ttf / SDL3_image / FreeType + image codecs)
// is linked statically into the .exe, so there are no runtime DLLs to bundle —
// the distributable is just <app>.exe + data.kres.

// ==================
// MARK: Bundle composeResources next to each native executable
// ==================
// Two roots merge into a single archive <exe>/data.kres, loaded at
// runtime via SDL_GetBasePath() + "data.kres":
//   - the demo's own assets (drawable/, files/), and
//   - the library's default font (font/NotoSans.ttf, variable wght/wdth),
//     downloaded by :core, that the text renderers load at startup.
// Entries are STORED (no compression) so the ZIP reader in ResourceIO.kt can
// hand the raw bytes to SDL3_image / SDL3_ttf / Skia without inflating anything
// — readBytes is an fseek+fread per entry, not a whole-archive memory load.
// Pass -PbundleDefaultFont=false to ship without the bundled Noto Sans; the text
// renderers then fall back to a system font.

val composeResourcesDir = layout.projectDirectory.dir("src/commonMain/composeResources")
// :core downloads the variable Noto Sans default font into its build/fonts.
// Reference it by layout (a lazy provider, no evaluation of :core needed) and
// depend on the download task by path so ordering doesn't rely on :core being
// configured first.
val notoSansFile = rootProject.project(":ui").layout.buildDirectory.file("fonts/NotoSans.ttf")
val bundleDefaultFont = (findProperty("bundleDefaultFont") as? String)?.toBoolean() ?: true

// Which Material Symbols style(s) this app uses = which style call sites appear
// in its Kotlin sources. `MaterialSymbolsOutlined(...)` -> outlined font,
// `MaterialSymbolsRounded(...)` -> rounded, `MaterialSymbolsSharp(...)` -> sharp.
// The Zip task below downloads + bundles ONLY the fonts a style actually
// referenced — an app that only ever calls MaterialSymbolsOutlined never pays
// for Rounded/Sharp .ttf bytes (each is ~600KB uncompressed).
val kAllStyles = listOf(
    "Outlined" to Regex("\\bMaterialSymbolsOutlined\\b"),
    "Rounded"  to Regex("\\bMaterialSymbolsRounded\\b"),
    "Sharp"    to Regex("\\bMaterialSymbolsSharp\\b"),
)
fun detectUsedStyles(): List<String> {
    val vSrcRoot = layout.projectDirectory.dir("src").asFile
    val vUsed = mutableSetOf<String>()
    if (!vSrcRoot.exists()) return emptyList()
    vSrcRoot.walk().filter { it.isFile && it.extension == "kt" }.forEach { vFile ->
        val vText = vFile.readText()
        for ((vStyle, vRegex) in kAllStyles) {
            if (vStyle !in vUsed && vRegex.containsMatchIn(vText)) vUsed.add(vStyle)
        }
    }
    return vUsed.toList()
}
val vUsedStyles: List<String> = detectUsedStyles()

for (variant in variants) {
    for (target in nativeTargets) {
        val variantCap = variant.replaceFirstChar { it.uppercase() }
        val targetCap = target.replaceFirstChar { it.uppercase() }
        val copyTaskName = "copy${variantCap}ComposeResources${targetCap}"
        val outDir = layout.buildDirectory.dir("bin/$target/${variant}Executable")

        val copyTask = tasks.register<Zip>(copyTaskName) {
            archiveFileName.set("data.kres")
            destinationDirectory.set(outDir)
            entryCompression = if ((findProperty("compressResources") as? String)?.toBoolean() == true) ZipEntryCompression.DEFLATED else ZipEntryCompression.STORED
            // The official generated accessors carry the plugin's path scheme
            // (composeResources/<packageOfResClass>/drawable/x.png) — store the
            // data.kres entries under the same prefix so the vendored reader
            // resolves them 1:1. Fonts below stay at font/ (project pipeline).
            from(composeResourcesDir) { into("composeResources/demo.generated.resources") }
            // Default UI font (Noto Sans), downloaded by :core.
            if (bundleDefaultFont) {
                from(notoSansFile) { into("font") }
                dependsOn(":ui:downloadNotoFonts")
            }
            // Pull each USED style's downloaded .ttf into the font/ entry.
            // `:material-symbols` module exposes per-style extras named
            // iconFontFile<Style> / iconFontDownloadTask<Style>.
            val vSymbolsProject = rootProject.project(":material-symbols")
            for (vStyle in vUsedStyles) {
                @Suppress("UNCHECKED_CAST")
                val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as Provider<RegularFile>
                val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$vStyle"] as TaskProvider<*>
                from(vFontFile) { into("font") }
                dependsOn(vDownloadTask)
            }
        }

        listOf(
            "link${variantCap}Executable${targetCap}",
            "run${variantCap}Executable${targetCap}"
        ).forEach { taskName ->
            tasks.matching { it.name == taskName }.configureEach {
                dependsOn(copyTask)
            }
        }
    }
}

// ==================
// MARK: Stage Material Symbols fonts onto the JVM classpath
// ==================
// The :material-symbols JVM actual loads each style's font from the classpath
// at font/<Style>.ttf — the JVM analog of the data.kres font/ entries above.
// Same style detection: only the fonts the sources actually reference.
tasks.named<ProcessResources>("jvmProcessResources") {
    val vSymbolsProject = rootProject.project(":material-symbols")
    for (vStyle in vUsedStyles) {
        @Suppress("UNCHECKED_CAST")
        val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as Provider<RegularFile>
        val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$vStyle"] as TaskProvider<*>
        from(vFontFile) { into("font") }
        dependsOn(vDownloadTask)
    }
}
