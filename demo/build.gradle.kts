import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// Output dir for the generated Res.* accessor file — wired into nativeMain
// sources and produced by the generateComposeResAccessors task below.
val composeResGenDir = layout.buildDirectory.dir("generated/composeRes")

// Native deps live in a gitignored, in-repo folder populated by the scripts in
// tools/ (fetch-sdl3.sh, build-freetype.sh). Driven off rootDir so it works
// regardless of where the repo is cloned.
val vLibs = "${rootDir.invariantSeparatorsPath}/libs"

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()

    targets.withType<KotlinNativeTarget>().all {
        val isMingw = name == "mingwX64"
        binaries.executable {
            entryPoint = "main"
            // mingwX64 links against the in-repo import libs (SDL3 / ttf / image)
            // and the static FreeType. macOS/Linux resolve via the .def's
            // system -L paths, so no extra linker opts are needed there.
            if (isMingw) linkerOpts(
                "-L$vLibs/SDL3/lib",
                "-L$vLibs/SDL3_ttf/lib",
                "-L$vLibs/SDL3_image/lib",
                "-L$vLibs/FreeType/lib",
            )
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":window"))
                implementation(project(":material-symbols:outlined"))
                implementation(project(":material-symbols:rounded"))
                implementation(project(":material-symbols:sharp"))
            }
            // Generated typed Res.* accessors (produced by generateComposeResAccessors).
            kotlin.srcDir(composeResGenDir)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}

// Variants × targets used by the bundling Copy tasks below. One Copy task per
// (variant, target) keeps things configuration-cache friendly.
val variants = listOf("debug", "release")
val nativeTargets = listOf("macosArm64", "linuxX64", "linuxArm64", "mingwX64")

// ==================
// MARK: Bundle SDL3 runtime DLLs next to the Windows executable (mingwX64)
// ==================
// Windows resolves DLLs from the executable's own directory at launch, so we
// copy every DLL from each lib's bin/ next to the .exe — SDL3.dll, SDL3_ttf.dll,
// SDL3_image.dll, plus SDL3_image's format DLLs (libpng/jpeg/…). FreeType is
// linked statically, so it has no runtime DLL. Source roots default to the
// in-repo <repo>/libs/<lib>; override via -Psdl3Dir=... etc.

val sdl3Dir = (findProperty("sdl3Dir") as? String) ?: "$vLibs/SDL3"
val sdl3TtfDir = (findProperty("sdl3TtfDir") as? String) ?: "$vLibs/SDL3_ttf"
val sdl3ImageDir = (findProperty("sdl3ImageDir") as? String) ?: "$vLibs/SDL3_image"

for (variant in variants) {
    val variantCap = variant.replaceFirstChar { it.uppercase() }
    val copyTaskName = "copy${variantCap}DllsMingwX64"
    val outDir = layout.buildDirectory.dir("bin/mingwX64/${variant}Executable")

    val copyTask = tasks.register<Copy>(copyTaskName) {
        from("$sdl3Dir/bin") { include("*.dll") }
        from("$sdl3TtfDir/bin") { include("*.dll") }
        from("$sdl3ImageDir/bin") { include("*.dll") }
        into(outDir)
    }

    // Windows-only DLLs; the matching is a no-op on hosts without these tasks.
    listOf(
        "link${variantCap}ExecutableMingwX64",
        "run${variantCap}ExecutableMingwX64"
    ).forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach {
            dependsOn(copyTask)
        }
    }
}

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
        vSb.appendLine("import androidx.compose.ui.res.Res")
        vSb.appendLine("import androidx.compose.ui.res.painterResource")
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
//   - the library's default font (font/Roboto-Regular.ttf) that the text
//     renderers load at startup.
// Entries are STORED (no compression) so the ZIP reader in ResourceIO.kt can
// hand the raw bytes to SDL3_image / SDL3_ttf / Skia without inflating anything
// — readBytes is an fseek+fread per entry, not a whole-archive memory load.
// Pass -PbundleDefaultFont=false to ship without the bundled Roboto; the text
// renderers then fall back to a system font. The generated Res accessors (see
// generateComposeResAccessors above) only scan the demo's resources.

val composeResourcesDir = layout.projectDirectory.dir("src/nativeMain/composeResources")
val libComposeResourcesDir = rootProject.layout.projectDirectory.dir(
    "core/src/nativeMain/composeResources"
)
val bundleDefaultFont = (findProperty("bundleDefaultFont") as? String)?.toBoolean() ?: true

// Walk the demo's declared dependencies and pick out every :material-symbols:*
// project this module actually pulls in. Each such module exposes
// extra["iconFontFile"] (Provider<RegularFile> for the downloaded .ttf) and
// extra["iconFontDownloadTask"] (TaskProvider). The Zip task pulls the file
// into data.kres under font/ and depends on the download task — apps just
// declare the dependency on the style(s) they want, the bundling is
// automatic and only ships what's depended on.
fun collectIconFontModules(): List<Project> {
    val vConfigs = listOf(
        "commonMainImplementation", "commonMainApi",
        "nativeMainImplementation", "nativeMainApi",
    )
    val vSet = mutableSetOf<Project>()
    for (vName in vConfigs) {
        val vCfg = configurations.findByName(vName) ?: continue
        for (vDep in vCfg.dependencies) {
            if (vDep is org.gradle.api.artifacts.ProjectDependency) {
                val vPath = vDep.path
                if (vPath.startsWith(":material-symbols:")) {
                    rootProject.findProject(vPath)?.let { vSet.add(it) }
                }
            }
        }
    }
    return vSet.toList()
}
val iconFontModules: List<Project> = collectIconFontModules()

for (variant in variants) {
    for (target in nativeTargets) {
        val variantCap = variant.replaceFirstChar { it.uppercase() }
        val targetCap = target.replaceFirstChar { it.uppercase() }
        val copyTaskName = "copy${variantCap}ComposeResources${targetCap}"
        val outDir = layout.buildDirectory.dir("bin/$target/${variant}Executable")

        val copyTask = tasks.register<Zip>(copyTaskName) {
            archiveFileName.set("data.kres")
            destinationDirectory.set(outDir)
            entryCompression = org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
            from(composeResourcesDir)
            from(libComposeResourcesDir) {
                if (!bundleDefaultFont) exclude("font/Roboto-Regular.ttf")
            }
            // Pull each icon-font module's downloaded .ttf into the font/ entry.
            iconFontModules.forEach { vP ->
                @Suppress("UNCHECKED_CAST")
                val vFontFile = vP.extra["iconFontFile"] as org.gradle.api.provider.Provider<RegularFile>
                val vDownloadTask = vP.extra["iconFontDownloadTask"] as TaskProvider<*>
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
