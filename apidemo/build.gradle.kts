import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

// :apidemo — a Postman-style REST API manager built on the compose-desktop-native
// stack. Uses Ktor with the Curl engine on every desktop target (bundled
// libcurl: Schannel on Windows, OpenSSL on macOS/Linux),
// kotlinx.serialization for the savable "packs", and okio for file I/O.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw: Boolean by rootProject.extra

// JVM parity target's Compose version — the dev build matching the pinned
// COMPOSE_CORE_REF (see scripts/compose-fork/compose.properties); mirrors
// :demo's forcing (Gradle orders "+dev" BELOW the plain version, so the
// plugin's beta01 would win conflict resolution without it).
val vComposeJvmVersion = libs.versions.compose.get()
val vComposeM3JvmVersion = libs.versions.composeMaterial3.get()
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

    targets.withType<KotlinNativeTarget>().all {
        val isMingw = name == "mingwX64"
        val isLinux = name.startsWith("linux")
        val isApple = name.startsWith("macos") || name.startsWith("ios")
        binaries.executable {
            if (buildType == NativeBuildType.RELEASE) {
                // --gc-sections is a GNU ld/lld flag; Apple's ld64 rejects it
                // (it dead-strips at -O anyway). Only pass it on GNU-ld targets.
                if (!isApple) linkerOpts("-Wl,--gc-sections")
                compilerOptions {
                    freeCompilerArgs.add("-opt")
                }
            }

            entryPoint = "apidemo.main"
            // SDL3 + SDL3_ttf + SDL3_image + FreeType + image codecs come from
            // :ui's cinterop klibs (staticLibraries directive in each .def); the
            // system libs they need are in sdl3.def's linkerOpts.<target>. Only
            // shell-level flags and app-specific extras (crypt32 for mTLS) live
            // here. Ktor's Curl engine bundles its own libcurl + TLS stack.
            if (isMingw) linkerOpts(
                // crypt32: client-cert (mTLS) import into the Windows cert store
                // (CertOpenStore / PFXImportCertStore / CertAddCertificateContextToStore…).
                "-lcrypt32",
                // Code-shrink: drop unused sections and strip symbols.
                "-Wl,--gc-sections", "-Wl,-s",
                // GUI subsystem so launching the .exe pops no console window.
                // Keep the standard C `main` entry — ld would otherwise default
                // a GUI-subsystem PE to WinMainCRTStartup (needs WinMain) and
                // fail to link, since Kotlin/Native emits `main`.
                "-Wl,--subsystem,windows", "-Wl,-e,mainCRTStartup",
            )
            // Skia (default renderer) references the system graphics stack.
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
                // Common API on both stacks — usable from shared code.
                implementation(project(":material-symbols"))

                // Official Maven coords for everything the shared UI touches,
                // so commonMain metadata (and the IDE's common analysis)
                // resolve. Metadata + jvm resolve them from Maven; the NATIVE
                // configurations substitute each for its port module (root
                // build's FULL-COMMONIZATION BRIDGE). Every artifact must be
                // declared DIRECTLY: transitives of a substituted module are
                // invisible to the granular-metadata visibility check. The
                // runtime is the official klib everywhere, never substituted
                // (jvm configs force the +dev build above).
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
                // The SDL window shell + main loop (native-only).
                implementation(project(":window"))
                // Single HTTP engine on every native target: Ktor's Curl
                // engine. It bundles a static libcurl per target (Schannel on
                // Windows, OpenSSL on macOS/Linux) embedded in its cinterop
                // klib — no runtime DLL, one copy. Curl everywhere (not
                // WinHttp/Darwin) so the client-certificate (mTLS) path —
                // which calls the same bundled libcurl directly, since Ktor's
                // engines expose no client-cert API — shares the exact same
                // TLS stack as ordinary requests.
                implementation(libs.ktor.client.curl)
            }
        }
        jvmMain {
            dependencies {
                // Upstream Compose Desktop — the parity stack.
                implementation(compose.desktop.currentOs)
                // Coroutine-based engine on JVM; native targets keep Curl.
                implementation(libs.ktor.client.cio)
                // Dispatchers.Main provider on desktop JVM (must match
                // kotlinx-coroutines-core's version — the catalog pins both).
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

// SDL3 / SDL3_ttf / SDL3_image / FreeType are linked statically into the
// executable (see the mingw linkerOpts above), so there are no runtime DLLs to
// bundle — the distributable is just <app>.exe + data.kres.
val variants = listOf("debug", "release")

// ==================
// MARK: Bundle the default font (data.kres) next to each native executable
// ==================
// Default UI font (Noto Sans) + monospace body font (Noto Sans Mono) are
// downloaded by :core and exposed through these generic "extra" handles (the
// same handshake the :material-symbols:* modules use). The renderers load
// NotoSans (variable) as the default; apidemo registers NotoSansMono (variable)
// with IconFont under "noto-mono" for the body editor.
val nativeTargets = listOf("macosArm64", "linuxX64", "linuxArm64", "mingwX64")
// :core downloads the variable Noto fonts into its build/fonts. Reference them
// by layout (lazy providers, no evaluation of :core needed) and depend on the
// download task by path so ordering doesn't rely on :core being configured first.
val coreBuildDir = rootProject.project(":ui").layout.buildDirectory
val notoSansFile = coreBuildDir.file("fonts/NotoSans.ttf")
val notoMonoFile = coreBuildDir.file("fonts/NotoSansMono.ttf")

// Which Material Symbols style(s) this app uses = which style call sites appear
// in its Kotlin sources. `MaterialSymbolsOutlined(...)` → outlined font,
// `MaterialSymbolsRounded(...)` → rounded, `MaterialSymbolsSharp(...)` → sharp.
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

// ==================
// MARK: Material Symbols subsetting (-PsubsetIcons=true)
// ==================
// scripts/subset-material-symbols.py scans this module's Kotlin sources for
// MaterialSymbols.<Name> references and writes build/icons/usage-codepoint.txt.
// When -PsubsetIcons=true is passed, each icon-font module's downloaded TTF
// is hb-subset'd against that file and the Zip below bundles the trimmed
// font instead of the full one — typically 80-95% smaller.
//
// Needs: python3 + hb-subset on PATH (brew install harfbuzz /
// apt install harfbuzz-utils).
val subsetIcons = (findProperty("subsetIcons") as? String)?.toBoolean() ?: false
val iconsBuildDir = layout.buildDirectory.dir("icons")

val findMaterialSymbolsUsage = tasks.register<Exec>("findMaterialSymbolsUsage") {
    description = "Scan src/ for MaterialSymbols.<Name> usages → usage-codepoint.txt."
    val vScript = rootProject.layout.projectDirectory.file("scripts/subset-material-symbols.py").asFile
    val vConstants = rootProject.project(":material-symbols").layout.projectDirectory
        .file("src/commonMain/kotlin/com/compose/sdl/icons/MaterialSymbols.kt").asFile
    val vUsageFile = iconsBuildDir.get().file("usage-codepoint.txt").asFile
    inputs.files(fileTree("src") { include("**/*.kt") })
    inputs.file(vConstants)
    outputs.file(vUsageFile)
    commandLine(
        "python3", vScript.absolutePath,
        "--src", layout.projectDirectory.dir("src").asFile.absolutePath,
        "--constants", vConstants.absolutePath,
        "--out", vUsageFile.absolutePath,
    )
}

/* Register `subsetMaterialSymbols<Style>` for a style — hb-subsets the style's
   downloaded TTF (owned by :material-symbols) to only the codepoints in
   usage-codepoint.txt and writes the output under this app's build/icons/ dir. */
fun registerSubsetTask(inStyle: String): TaskProvider<*> {
    val vSymbolsProject = rootProject.project(":material-symbols")
    return tasks.register("subsetMaterialSymbols$inStyle") {
        description = "hb-subset the $inStyle Material Symbols font to icons actually used."
        @Suppress("UNCHECKED_CAST")
        val vInputProvider = vSymbolsProject.extra["iconFontFile$inStyle"] as org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
        val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$inStyle"] as TaskProvider<*>
        val vOut = iconsBuildDir.get().file("MaterialSymbols$inStyle.subset.ttf").asFile
        val vUsage = iconsBuildDir.get().file("usage-codepoint.txt").asFile
        inputs.file(vInputProvider)
        inputs.file(vUsage)
        outputs.file(vOut)
        dependsOn(findMaterialSymbolsUsage)
        dependsOn(vDownloadTask)
        doLast {
            val vCodepoints = vUsage.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
                .map { it.substringAfter("=").trim().removePrefix("0x").removePrefix("0X") }
            if (vCodepoints.isEmpty()) throw GradleException(
                "usage-codepoint.txt has no entries — refusing to subset to an empty font.")
            val vUnicodes = vCodepoints.joinToString(",") { "U+$it" }
            vOut.parentFile.mkdirs()
            val vInputFile = vInputProvider.get().asFile
            val vBefore = vInputFile.length()
            // ProcessBuilder rather than project.exec — the latter isn't
            // available inside doLast on Gradle 9 (only ExecOperations via
            // an injected service is, which would mean a buildSrc plugin).
            // hb-subset (HarfBuzz) is optional: if it isn't on PATH — common on
            // Windows — bundle the full font instead of failing the build.
            val vProc = try {
                ProcessBuilder(
                    "hb-subset",
                    vInputFile.absolutePath,
                    "-o", vOut.absolutePath,
                    "--unicodes=$vUnicodes",
                ).redirectErrorStream(true).start()
            } catch (e: java.io.IOException) {
                vInputFile.copyTo(vOut, overwrite = true)
                logger.warn("[subset $inStyle] hb-subset not found on PATH — bundling the full font " +
                    "(${vBefore / 1024}KB). Install harfbuzz to shrink it (brew install harfbuzz / " +
                    "pacman -S mingw-w64-x86_64-harfbuzz / apt install harfbuzz-utils).")
                return@doLast
            }
            val vOutput = vProc.inputStream.bufferedReader().readText()
            val vCode = vProc.waitFor()
            if (vCode != 0) throw GradleException("hb-subset failed (exit $vCode):\n$vOutput")
            val vAfter = vOut.length()
            val vPct = if (vBefore == 0L) 0 else ((100 - 100 * vAfter / vBefore)).coerceAtLeast(0)
            logger.lifecycle("[subset $inStyle] ${vBefore / 1024}KB → ${vAfter / 1024}KB (-$vPct%) · ${vCodepoints.size} glyphs kept")
        }
    }
}

// Pre-create subset tasks per USED style when enabled; the Zip below uses
// them as the font source. Built outside the variant×target loop so each
// style is subset once and reused across all bundle variants.
val subsetTasksByStyle: Map<String, TaskProvider<*>> =
    if (subsetIcons) vUsedStyles.associateWith { registerSubsetTask(it) } else emptyMap()

for (variant in variants) {
    for (target in nativeTargets) {
        val variantCap = variant.replaceFirstChar { it.uppercase() }
        val targetCap = target.replaceFirstChar { it.uppercase() }
        val outDir = layout.buildDirectory.dir("bin/$target/${variant}Executable")
        val copyTask = tasks.register<Zip>("copy${variantCap}ComposeResources${targetCap}") {
            archiveFileName.set("data.kres")
            destinationDirectory.set(outDir)
            entryCompression = if ((findProperty("compressResources") as? String)?.toBoolean() == true) org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED else org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
            from(notoSansFile) { into("font") }
            from(notoMonoFile) { into("font") }
            dependsOn(":ui:downloadNotoFonts")
            val vSymbolsProject = rootProject.project(":material-symbols")
            for (vStyle in vUsedStyles) {
                @Suppress("UNCHECKED_CAST")
                val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
                val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$vStyle"] as TaskProvider<*>
                val vSubsetTask = subsetTasksByStyle[vStyle]
                if (vSubsetTask != null) {
                    // Trimmed font: depend on the subset task and bundle its output
                    // with the original filename so the runtime IconFont registration
                    // doesn't need to know the bundle was subset.
                    val vOriginalName = vFontFile.get().asFile.name
                    from(vSubsetTask.get().outputs.files) {
                        into("font")
                        rename { vOriginalName }
                    }
                    dependsOn(vSubsetTask)
                } else {
                    from(vFontFile) { into("font") }
                }
                dependsOn(vDownloadTask)
            }
        }
        listOf("link${variantCap}Executable${targetCap}", "run${variantCap}Executable${targetCap}").forEach { taskName ->
            tasks.matching { it.name == taskName }.configureEach { dependsOn(copyTask) }
        }
    }
}

// ==================
// MARK: Stage fonts onto the JVM classpath
// ==================
// The JVM analog of the data.kres font/ entries: :material-symbols' JVM actual
// loads each style's font from the classpath at font/<Style>.ttf, and the
// jvm Fonts actual loads the mono body font at font/NotoSansMono.ttf.
tasks.named<ProcessResources>("jvmProcessResources") {
    from(notoMonoFile) { into("font") }
    dependsOn(":ui:downloadNotoFonts")
    val vSymbolsProject = rootProject.project(":material-symbols")
    for (vStyle in vUsedStyles) {
        @Suppress("UNCHECKED_CAST")
        val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
        val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$vStyle"] as TaskProvider<*>
        from(vFontFile) { into("font") }
        dependsOn(vDownloadTask)
    }
}
