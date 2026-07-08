@file:OptIn(KotlinNativeCacheApi::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// ==================
// MARK: -PuseReleased=<version> — swap :window / :material3 for published artifacts
// ==================
// Default build (no property): :window + :material3 come from `project(...)`
// so a local `./gradlew :demo:run…` picks up your uncommitted changes.
// `-PuseReleased=<version>` swaps them for
//     com.bitsycore.compose.sdl:desktop-window-<target>:<version>
//     com.bitsycore.compose.sdl:desktop-material3-<target>:<version>
// pulled from GitHub Packages — good for reproducing a release build against
// the exact artifacts users will get. `:material-symbols` stays a project
// dep either way — the Zip task below hooks its per-style font download
// tasks (extra["iconFontDownloadTask<Style>"]), which don't exist on the
// Maven artifact.
//
// GitHub Packages requires auth even for public reads. Provide creds via:
//   -PgithubUser=<name> -PgithubToken=<pat>    or
//   env: GITHUB_ACTOR / GITHUB_TOKEN            (auto-set on CI runners)
val vReleased = (findProperty("useReleased") as String?)?.takeIf { it.isNotBlank() }

// Output dir for the generated Res.* accessor file — wired into nativeMain
// sources and produced by the generateComposeResAccessors task below.
val composeResGenDir = layout.buildDirectory.dir("generated/composeRes")

// Native deps live in a gitignored, in-repo folder populated by the scripts in
// tools/ (fetch-sdl3.sh, build-freetype.sh). Driven off rootDir so it works
// regardless of where the repo is cloned.
val vLibs = "${rootDir.invariantSeparatorsPath}/libs"

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw: Boolean by rootProject.extra

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64(){

    }
    if (vHostSupportsMingw) mingwX64()

    // Give us a real `nativeMain` intermediate source set (all targets are
    // native today). Screens that only touch androidx.compose.* live in
    // commonMain so a future jvm() target can compile them against upstream
    // Compose; project-only (com.compose.sdl.*) screens + the SDL3
    // entry point stay in nativeMain.
    applyDefaultHierarchyTemplate()

    targets.withType<KotlinNativeTarget>().all {
        val isMingw = name == "mingwX64"
        val isLinuxX64 = name == "linuxX64"
        binaries.executable {
            disableNativeCache(
                version = DisableCacheInKotlinVersion.`2_4_0`,
                reason = "Weird undef symbole on macos"
            )
            entryPoint = "main"
            // mingwX64 links SDL3 / SDL3_ttf / SDL3_image / FreeType + image
            // codecs all statically into the .exe (clean app.exe + data.kres,
            // no DLLs). macOS/Linux resolve via the .def's system -L paths.
            if (isMingw) linkerOpts(
                "-L$vLibs/SDL3/lib",
                "-L$vLibs/SDL3_ttf/lib",
                "-L$vLibs/SDL3_image/lib",
                "-L$vLibs/FreeType/lib",
                // --start-group resolves the circular static deps
                // (ttf<->freetype<->SDL3, image<->png/webp/zlib).
                "-Wl,--start-group",
                "-lSDL3_ttf", "-lSDL3_image", "-lSDL3", "-lfreetype",
                "-lpng16", "-lzlibstatic", "-lwebp", "-lwebpdemux", "-lwebpmux", "-lsharpyuv",
                "-Wl,--end-group",
                // Windows system libraries SDL3 pulls in when static.
                "-lm", "-lkernel32", "-luser32", "-lgdi32", "-lwinmm", "-limm32",
                "-lole32", "-loleaut32", "-lversion", "-luuid", "-ladvapi32",
                "-lsetupapi", "-lshell32", "-ldinput8",
                // Code-shrink: drop unused sections and strip symbols.
                "-Wl,--gc-sections", "-Wl,-s",
                // GUI subsystem so launching the .exe pops no console window.
                // Keep the standard C `main` entry — ld would otherwise default
                // a GUI-subsystem PE to WinMainCRTStartup (needs WinMain) and
                // fail to link, since Kotlin/Native emits `main`.
                "-Wl,--subsystem,windows", "-Wl,-e,mainCRTStartup",
            )
            // Linux (Skia/Skiko): Skiko's Skia is linked statically from the
            // klib, but it references the system graphics stack — fontconfig
            // (font matching), GL (Skia GL backend), X11 (windowing). Add them
            // here; SDL3 resolves via sdl3.def's linkerOpts.linux_x64.
            if (isLinuxX64) linkerOpts(
                "-lfontconfig", "-lGL", "-lX11",
            )
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                if (vReleased != null) {
                    implementation("com.bitsycore.compose.sdl:desktop-window:$vReleased")
                    implementation("com.bitsycore.compose.sdl:desktop-material3:$vReleased")
                    // Swap :material-symbols too — otherwise its transitive
                    // project deps (:foundation, :animation-core, :ui) collide
                    // with the same klibs pulled from Maven via desktop-window,
                    // and the K/N compiler fails with duplicate `unique_name`.
                    // The material-symbols module stays in settings.gradle
                    // regardless — the Zip task below still references its
                    // Gradle Project object via rootProject.project(...) to
                    // read the per-style font-download task extras.
                    implementation("com.bitsycore.compose.sdl:desktop-material-symbols:$vReleased")
                } else {
                    implementation(project(":window"))
                    implementation(project(":material3"))
                    implementation(project(":material-symbols"))
                }
            }
        }
        nativeMain {
            // Generated typed Res.* accessors (produced by generateComposeResAccessors).
            // They import the project's native Res API, so they belong to the native
            // source set — the resource shim's native actual is the only common-side
            // consumer. Keeping them off commonMain lets a future jvm() target swap in
            // org.jetbrains.compose.resources without seeing project accessors.
            kotlin.srcDir(composeResGenDir)
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

// Variants × targets used by the bundling Copy tasks below. One Copy task per
// (variant, target) keeps things configuration-cache friendly.
val variants = listOf("debug", "release")
val nativeTargets = listOf("macosArm64", "linuxX64", "linuxArm64", "mingwX64")

// On Windows everything (SDL3 / SDL3_ttf / SDL3_image / FreeType + image codecs)
// is linked statically into the .exe, so there are no runtime DLLs to bundle —
// the distributable is just <app>.exe + data.kres.

// ==================
// MARK: Generate typed Res accessors from composeResources/
// ==================
// Scans composeResources/drawable + /files and emits extension accessors on
// the library's Res object: Res.drawable.<name> (Painter) and Res.files.<name>
// (the relative path string for Res.readBytes). This is the project's own
// lightweight stand-in for the Compose Multiplatform resource codegen, which
// can't be used here (its generated code is bound to the official resources
// runtime + real Compose UI, which this repo deliberately re-implements).

val generateComposeResAccessors = tasks.register("generateComposeResAccessors") {
    val vSrcDir = composeResourcesDir.asFile
    val vOutDir = composeResGenDir.get().asFile
    inputs.dir(vSrcDir).withPropertyName("composeResources")
    outputs.dir(vOutDir).withPropertyName("generatedAccessors")
    doLast {
        // Local so the config-cache doesn't capture script / project state.
        fun idOf(inName: String): String {
            val vSb = StringBuilder()
            for (vCh in inName) vSb.append(if (vCh.isLetterOrDigit() || vCh == '_') vCh else '_')
            val vId = vSb.toString().ifEmpty { "_" }
            return if (vId.first().isDigit()) "_$vId" else vId
        }

        val vSb = StringBuilder()
        vSb.appendLine("// Generated by generateComposeResAccessors — do not edit.")
        vSb.appendLine("package composeresources.generated")
        vSb.appendLine()
        vSb.appendLine("import androidx.compose.ui.graphics.painter.Painter")
        vSb.appendLine("import com.compose.sdl.res.Res")
        vSb.appendLine("import com.compose.sdl.res.painterResource")
        vSb.appendLine()
        File(vSrcDir, "drawable").listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { vFile ->
            vSb.appendLine(
                "val Res.drawable.${idOf(vFile.nameWithoutExtension)}: Painter " +
                "get() = painterResource(\"drawable/${vFile.name}\")"
            )
        }
        vSb.appendLine()
        File(vSrcDir, "files").listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { vFile ->
            vSb.appendLine(
                "val Res.files.${idOf(vFile.nameWithoutExtension)}: String " +
                "get() = \"files/${vFile.name}\""
            )
        }
        vOutDir.mkdirs()
        File(vOutDir, "ComposeResAccessors.kt").writeText(vSb.toString())
    }
}

// Generated source must exist before any Kotlin/Native compilation.
tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn(generateComposeResAccessors)
}

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
// renderers then fall back to a system font. The generated Res accessors (see
// generateComposeResAccessors above) only scan the demo's resources.

val composeResourcesDir = layout.projectDirectory.dir("src/nativeMain/composeResources")
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
            entryCompression = if ((findProperty("compressResources") as? String)?.toBoolean() == true) org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED else org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
            from(composeResourcesDir)
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
                val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as org.gradle.api.provider.Provider<RegularFile>
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
