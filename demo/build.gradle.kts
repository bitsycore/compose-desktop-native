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
        commonMain.dependencies {
            implementation(project(":compose-sdl3"))
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

for (variant in variants) {
    val variantCap = variant.replaceFirstChar { it.uppercase() }
    val copyTaskName = "copy${variantCap}DllsMingwX64"
    val outDir = layout.buildDirectory.dir("bin/mingwX64/${variant}Executable")

    val copyTask = tasks.register<Copy>(copyTaskName) {
        from("$sdl3Dir/bin/SDL3.dll")
        from("$sdl3TtfDir/bin/SDL3_ttf.dll")
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
