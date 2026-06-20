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
    // mingwX64() disabled — see compose-sdl3/build.gradle.kts.
    linuxArm64()
    linuxX64()
    macosArm64()

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
val nativeTargets = listOf("macosArm64", "linuxX64", "linuxArm64")

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
