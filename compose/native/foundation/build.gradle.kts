import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :foundation — androidx.compose.foundation.* AND androidx.compose.animation.*
// (non-core) vendored VERBATIM from upstream. Sits on :core (which still owns
// androidx.compose.ui.*, both renderers, cinterops) + :animation-core.
//
// Why the two upstream modules (`:animation` and `:foundation`) are combined
// here: upstream splits `foundation-layout` off from `foundation`, then has
// `:animation` `api`(`:foundation-layout`) and `:foundation` `api`(`:animation`).
// We don't split foundation-layout out, so a single Gradle module owning both
// packages is the smallest coherent unit that resolves without introducing a
// 5th module. Keeps the source-package boundary intact (androidx.compose.animation.*
// stays a distinct package from androidx.compose.foundation.*), only the Gradle
// module boundary is coalesced.
//
// Provenance = foundation/compose-fork.txt + tools/compose-fork/compose-ref.txt.
// Never hand-edit files under foundation/src/vendor/ — change the manifest and
// re-run `bash tools/compose-fork/sync.sh :foundation`.
//
// Note on DarkTheme: foundation declares `internal expect fun _isSystemInDarkTheme()`,
// and its actual lives here as a plain nativeMain kt file (see
// src/nativeMain/kotlin/androidx/compose/foundation/DarkTheme.native.kt) — a single
// stub returning false. The upstream skiko/sdl per-renderer actuals were dropped
// when :foundation was split from :core: expect/actual must live in the same
// module, and duplicating the skikoRenderer/sdlRenderer source-set hierarchy on
// :foundation just to read `LocalSystemTheme` on the Skia path was more build
// wiring than is worth it for one function no in-repo app calls.
//
// Publication artifactId (when set up): compose-desktop-native-foundation.

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
                api(project(":animation-core"))
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
