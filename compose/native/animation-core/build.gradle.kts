import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :animation-core — androidx.compose.animation.core.* vendored VERBATIM from
// upstream. Renderer-agnostic (spring/tween specs, animatable, transition,
// vector math). Split out of :core so :core stays about renderer+ui glue only.
//
// Provenance = animation-core/compose-fork.txt + tools/compose-fork/compose-ref.txt.
// Never hand-edit files under animation-core/src/vendor/ — change the manifest and
// re-run `bash tools/compose-fork/sync.sh :animation-core`.
//
// Publication artifactId (when set up): compose-desktop-native-animation-core.

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

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            kotlin.srcDir("src/vendor/common/kotlin")
            dependencies {
                api(project(":core"))
            }
        }
        nativeMain {
            kotlin.srcDir("src/vendor/native/kotlin")
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}
