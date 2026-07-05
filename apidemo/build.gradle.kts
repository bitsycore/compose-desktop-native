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

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// In-repo (gitignored) native deps; see tools/. Driven off rootDir for portability.
val vLibs = "${rootDir.invariantSeparatorsPath}/libs"

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()

    targets.withType<KotlinNativeTarget>().all {
        val isMingw = name == "mingwX64"
        val isLinuxX64 = name == "linuxX64"
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
            if (isMingw) linkerOpts(
                "-L$vLibs/SDL3/lib",
                "-L$vLibs/SDL3_ttf/lib",
                "-L$vLibs/SDL3_image/lib",
                "-L$vLibs/FreeType/lib",
                // SDL3 / SDL3_ttf / SDL3_image / FreeType + image codecs are
                // all linked statically into the .exe (clean app.exe + data.kres,
                // no DLLs). --start-group resolves the circular static deps
                // (ttf<->freetype<->SDL3, image<->png/webp/zlib).
                "-Wl,--start-group",
                "-lSDL3_ttf", "-lSDL3_image", "-lSDL3", "-lfreetype",
                "-lpng16", "-lzlibstatic", "-lwebp", "-lwebpdemux", "-lwebpmux", "-lsharpyuv",
                "-Wl,--end-group",
                // Windows system libraries SDL3 pulls in when static.
                "-lm", "-lkernel32", "-luser32", "-lgdi32", "-lwinmm", "-limm32",
                "-lole32", "-loleaut32", "-lversion", "-luuid", "-ladvapi32",
                "-lsetupapi", "-lshell32", "-ldinput8",
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
            // Linux (Skia/Skiko): system graphics stack Skia references.
            // libcurl + OpenSSL come bundled (static) inside the Ktor Curl klib.
            if (isLinuxX64) linkerOpts(
                "-lfontconfig", "-lGL", "-lX11",
            )
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":window"))
                implementation(project(":material3"))
                implementation(project(":material-symbols:outlined"))
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        // Single HTTP engine on every desktop target: Ktor's Curl engine. It
        // bundles a static libcurl per target (Schannel on Windows, OpenSSL on
        // macOS/Linux) embedded in its cinterop klib — no runtime DLL, one copy.
        // We use Curl everywhere (not WinHttp/Darwin) so that an upcoming
        // client-certificate (mTLS) path — which calls the same bundled libcurl
        // directly, since Ktor's engines expose no client-cert API — shares the
        // exact same TLS stack as ordinary requests.
        getByName("mingwX64Main").dependencies { implementation(libs.ktor.client.curl) }
        getByName("macosArm64Main").dependencies { implementation(libs.ktor.client.curl) }
        getByName("linuxX64Main").dependencies { implementation(libs.ktor.client.curl) }
        getByName("linuxArm64Main").dependencies { implementation(libs.ktor.client.curl) }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
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
val coreBuildDir = rootProject.project(":core").layout.buildDirectory
val notoSansFile = coreBuildDir.file("fonts/NotoSans.ttf")
val notoMonoFile = coreBuildDir.file("fonts/NotoSansMono.ttf")

// Material Symbols icon-font modules this app depends on — each exposes its
// downloaded .ttf via extra["iconFontFile"]; we pull it into data.kres/font/
// so the icons render at runtime (same scheme as :demo).
val iconFontModules: List<Project> = run {
    val vSet = mutableSetOf<Project>()
    for (vName in listOf("commonMainImplementation", "commonMainApi", "nativeMainImplementation", "nativeMainApi")) {
        val vCfg = configurations.findByName(vName) ?: continue
        for (vDep in vCfg.dependencies) {
            if (vDep is org.gradle.api.artifacts.ProjectDependency && vDep.path.startsWith(":material-symbols:")) {
                rootProject.findProject(vDep.path)?.let { vSet.add(it) }
            }
        }
    }
    vSet.toList()
}

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
        .file("src/commonMain/kotlin/com/compose/desktop/native/icons/MaterialSymbols.kt").asFile
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

/* Register `subsetMaterialSymbols<Style>` for a style module — hb-subsets
   its downloaded TTF to only the codepoints in usage-codepoint.txt and
   writes the output under this app's build/icons/ dir. */
fun registerSubsetTask(inStyleProject: Project): TaskProvider<*> {
    val vStyleName = inStyleProject.name.replaceFirstChar { it.uppercase() }
    return tasks.register("subsetMaterialSymbols$vStyleName") {
        description = "hb-subset the $vStyleName Material Symbols font to icons actually used."
        @Suppress("UNCHECKED_CAST")
        val vInputProvider = inStyleProject.extra["iconFontFile"] as org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
        val vDownloadTask = inStyleProject.extra["iconFontDownloadTask"] as TaskProvider<*>
        val vOut = iconsBuildDir.get().file("MaterialSymbols$vStyleName.subset.ttf").asFile
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
                logger.warn("[subset $vStyleName] hb-subset not found on PATH — bundling the full font " +
                    "(${vBefore / 1024}KB). Install harfbuzz to shrink it (brew install harfbuzz / " +
                    "pacman -S mingw-w64-x86_64-harfbuzz / apt install harfbuzz-utils).")
                return@doLast
            }
            val vOutput = vProc.inputStream.bufferedReader().readText()
            val vCode = vProc.waitFor()
            if (vCode != 0) throw GradleException("hb-subset failed (exit $vCode):\n$vOutput")
            val vAfter = vOut.length()
            val vPct = if (vBefore == 0L) 0 else ((100 - 100 * vAfter / vBefore)).coerceAtLeast(0)
            logger.lifecycle("[subset $vStyleName] ${vBefore / 1024}KB → ${vAfter / 1024}KB (-$vPct%) · ${vCodepoints.size} glyphs kept")
        }
    }
}

// Pre-create subset tasks per style module when enabled; the Zip below
// uses them as the font source. Built outside the variant×target loop
// so each style is subset once and reused across all bundle variants.
val subsetTasksByModule: Map<Project, TaskProvider<*>> =
    if (subsetIcons) iconFontModules.associateWith { registerSubsetTask(it) } else emptyMap()

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
            dependsOn(":core:downloadNotoFonts")
            iconFontModules.forEach { vP ->
                @Suppress("UNCHECKED_CAST")
                val vFontFile = vP.extra["iconFontFile"] as org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
                val vDownloadTask = vP.extra["iconFontDownloadTask"] as TaskProvider<*>
                val vSubsetTask = subsetTasksByModule[vP]
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
