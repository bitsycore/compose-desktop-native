import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("com.bitsycore.compose-desktop-native.bridge")
}

val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

// ==================
// MARK: Targets
// ==================

kotlin {
    jvm()

    buildList {
        add(linuxArm64())
        add(linuxX64())
        add(macosArm64())
        if (vHostSupportsMingw) add(mingwX64())
    }.forEach {
        it.binaries {
            executable {
                when {
                    target.name == "mingwX64" -> linkerOpts(
                        // crypt32: client-cert (mTLS) import into the Windows cert store.
                        "-lcrypt32",
                        "-Wl,--gc-sections", "-Wl,-s",
                        // GUI subsystem (no console window), keeping the C `main` entry.
                        "-Wl,--subsystem,windows", "-Wl,-e,mainCRTStartup",
                    )

                    target.name.startsWith("linux") -> linkerOpts(
                        "-L/usr/lib/x86_64-linux-gnu", "-L/usr/lib/aarch64-linux-gnu",
                        "-lfontconfig", "-lGL", "-lX11",
                    )
                }
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                val vComposeJvmVersion = libs.versions.compose.get()
                val vComposeM3JvmVersion = libs.versions.composeMaterial3.get()

                // Official Maven coords for everything the shared UI touches; native
                // configurations substitute them for the port modules automatically
                // (root bridge in-repo, the bridge plugin for consumers).
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

                // Common Material Symbols Utility
                implementation(project(":material-symbols"))

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
                implementation(project(":window"))
                implementation(libs.ktor.client.curl)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
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

compose.desktop.native {
    entryPoint = "apidemo.main"
    icon {
        // Window/taskbar icon → icon/<name>.rgba in data.kres (paths in Main.kt).
        light.from(
            layout.projectDirectory.file("icons/voltic-icon-32.png"),
            layout.projectDirectory.file("icons/voltic-icon-128.png"),
        )
        dark.from(
            layout.projectDirectory.file("icons/voltic-icon-dark-32.png"),
            layout.projectDirectory.file("icons/voltic-icon-dark-128.png"),
        )
        // .exe (Explorer): the full branded icon with background.
        exeIcon.from(listOf(16, 32, 48, 64, 128, 256).map {
            layout.projectDirectory.file("icons/voltic-icon-$it.png")
        })
        embedWindowsIcon = providers.gradleProperty("embedWindowsIcon").map { it.toBoolean() }
    }
}

// ==================
// MARK: Fonts → data.kres + JVM classpath
// ==================

val notoSansFile = layout.buildDirectory.file("fonts/NotoSans.ttf")
val notoMonoFile = layout.buildDirectory.file("fonts/NotoSansMono.ttf")
val downloadNotoFonts = tasks.register("downloadNotoFonts") {
    description = "Download the Google Noto variable fonts (Sans + SansMono) to build/fonts/."
    val vDownloads = listOf(
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth%2Cwght%5D.ttf"
            to notoSansFile.get().asFile,
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansmono/NotoSansMono%5Bwdth%2Cwght%5D.ttf"
            to notoMonoFile.get().asFile,
    )
    outputs.files(vDownloads.map { it.second })
    doLast {
        for ((vUrl, vOut) in vDownloads) {
            if (vOut.exists() && vOut.length() > 0) continue
            vOut.parentFile.mkdirs()
            println("Downloading $vUrl")
            URI(vUrl).toURL().openStream().use { vIn -> vOut.outputStream().use { vIn.copyTo(it) } }
        }
    }
}

// A style's font is bundled only when its call sites appear in the sources.
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

/* hb-subset a style's downloaded TTF (owned by :material-symbols) to the
   codepoints in usage-codepoint.txt, into this app's build/icons/. */
fun registerSubsetTask(inStyle: String): TaskProvider<*> {
    val vSymbolsProject = rootProject.project(":material-symbols")
    return tasks.register("subsetMaterialSymbols$inStyle") {
        description = "hb-subset the $inStyle Material Symbols font to icons actually used."
        @Suppress("UNCHECKED_CAST")
        val vInputProvider = vSymbolsProject.extra["iconFontFile$inStyle"] as Provider<RegularFile>
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
            // ProcessBuilder: project.exec isn't usable inside doLast on Gradle 9.
            // hb-subset is optional — without it, bundle the full font.
            val vProc = try {
                ProcessBuilder(
                    "hb-subset",
                    vInputFile.absolutePath,
                    "-o", vOut.absolutePath,
                    "--unicodes=$vUnicodes",
                ).redirectErrorStream(true).start()
            } catch (_: java.io.IOException) {
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

// One subset task per used style, shared by all variant×target archives.
val subsetTasksByStyle: Map<String, TaskProvider<*>> =
    if (subsetIcons) vUsedStyles.associateWith { registerSubsetTask(it) } else emptyMap()

// Fonts ride in every data.kres zip the plugin registers (matching{} is lazy —
// the package* tasks appear in the plugin's afterEvaluate).
tasks.withType<Zip>().matching { it.name.startsWith("package") && it.name.contains("ComposeResources") }.configureEach {
    from(notoSansFile) { into("font") }
    from(notoMonoFile) { into("font") }
    dependsOn(downloadNotoFonts)
    val vSymbolsProject = rootProject.project(":material-symbols")
    for (vStyle in vUsedStyles) {
        @Suppress("UNCHECKED_CAST")
        val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as Provider<RegularFile>
        val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$vStyle"] as TaskProvider<*>
        val vSubsetTask = subsetTasksByStyle[vStyle]
        if (vSubsetTask != null) {
            // Subset output keeps the original filename — runtime registration unchanged.
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

// JVM analog of the data.kres font/ entries, loaded from the classpath.
tasks.named<ProcessResources>("jvmProcessResources") {
    from(notoMonoFile) { into("font") }
    dependsOn(downloadNotoFonts)
    // Parity-window icon (cosmetic — the harness compares the canvas, not chrome).
    from(layout.projectDirectory.file("icons/voltic-icon-256.png")) { into("icon") }
    val vSymbolsProject = rootProject.project(":material-symbols")
    for (vStyle in vUsedStyles) {
        @Suppress("UNCHECKED_CAST")
        val vFontFile = vSymbolsProject.extra["iconFontFile$vStyle"] as Provider<RegularFile>
        val vDownloadTask = vSymbolsProject.extra["iconFontDownloadTask$vStyle"] as TaskProvider<*>
        from(vFontFile) { into("font") }
        dependsOn(vDownloadTask)
    }
}
