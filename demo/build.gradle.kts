import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

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

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()

    targets.withType<KotlinNativeTarget>().all {
        binaries.executable {
            entryPoint = "main"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":compose-sdl3"))
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

// ==================
// MARK: Bundle fonts next to each native executable
// ==================
// Skia's matchFamilyStyle returns macOS CoreText typefaces (SkTypeface_Mac)
// which crash inside onCharsToGlyphs in Skiko 0.144.6. Loading a typeface
// from a file bypasses that path, so we ship Roboto-Regular.ttf next to
// the binary and resolve it at runtime via SDL_GetBasePath().

val fontsSourceDir = rootProject.layout.projectDirectory.dir(
    "compose-sdl3/src/nativeMain/resources/fonts"
)

// One Copy task per (variant, target) so it's config-cache friendly.
val variants = listOf("debug", "release")
val nativeTargets = listOf("macosArm64", "linuxX64", "linuxArm64", "mingwX64")

for (variant in variants) {
    for (target in nativeTargets) {
        val variantCap = variant.replaceFirstChar { it.uppercase() }
        val targetCap = target.replaceFirstChar { it.uppercase() }
        val copyTaskName = "copy${variantCap}Fonts${targetCap}"
        val outDir = layout.buildDirectory.dir("bin/$target/${variant}Executable/fonts")

        val copyTask = tasks.register<Copy>(copyTaskName) {
            from(fontsSourceDir)
            into(outDir)
        }

        // Wire into both link and run for the matching (variant, target).
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
// MARK: Bundle SDL3 runtime DLLs next to the Windows executable (mingwX64)
// ==================
// Windows resolves DLLs from the executable's own directory at launch, so we
// copy SDL3.dll + SDL3_ttf.dll next to the .exe — the same trick used for the
// font above. Source roots default to the layout documented in CLAUDE.md
// (C:\SDL3, C:\SDL3_ttf); override with -Psdl3Dir=... / -Psdl3TtfDir=... or in
// ~/.gradle/gradle.properties if your SDL3 lives elsewhere.

val sdl3Dir = (findProperty("sdl3Dir") as? String) ?: "C:/SDL3"
val sdl3TtfDir = (findProperty("sdl3TtfDir") as? String) ?: "C:/SDL3_ttf"
val sdl3ImageDir = (findProperty("sdl3ImageDir") as? String) ?: "C:/SDL3_image"

for (variant in variants) {
    val variantCap = variant.replaceFirstChar { it.uppercase() }
    val copyTaskName = "copy${variantCap}DllsMingwX64"
    val outDir = layout.buildDirectory.dir("bin/mingwX64/${variant}Executable")

    val copyTask = tasks.register<Copy>(copyTaskName) {
        from("$sdl3Dir/bin/SDL3.dll")
        from("$sdl3TtfDir/bin/SDL3_ttf.dll")
        from("$sdl3ImageDir/bin/SDL3_image.dll")
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
// Mirrors the font copy: resources live under demo/src/nativeMain/composeResources
// (drawable/, files/, …) and are copied next to the binary so the runtime can
// load them via SDL_GetBasePath() + "composeResources/<path>". The generated
// Res accessors (see generateComposeResAccessors below) reference the same
// relative paths.

val composeResourcesDir = layout.projectDirectory.dir("src/nativeMain/composeResources")

for (variant in variants) {
    for (target in nativeTargets) {
        val variantCap = variant.replaceFirstChar { it.uppercase() }
        val targetCap = target.replaceFirstChar { it.uppercase() }
        val copyTaskName = "copy${variantCap}ComposeResources${targetCap}"
        val outDir = layout.buildDirectory.dir("bin/$target/${variant}Executable/composeResources")

        val copyTask = tasks.register<Copy>(copyTaskName) {
            from(composeResourcesDir)
            into(outDir)
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
