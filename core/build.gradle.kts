import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.net.URI

// :core — the renderer-agnostic base: the androidx.compose.* re-impl,
// RenderBackend interface, GpuMode, SDL3Backend + window/event/clipboard/IO,
// and the default bundled font. Owns the `sdl3` cinterop. Renderer modules
// and :window depend on this. No renderer code lives here.
// Publication artifactId (when set up): compose-desktop-native-core.

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

// The .def files pin per-target SDL3 include paths to host-specific
// directories (/opt/homebrew on macOS, /usr/include on Linux,
// C:/Dev/Libs/... on Windows). When kotlin.mpp.enableCInteropCommonization
// is on, IntelliJ asks Gradle to cinterop every declared target so the
// shared nativeMain klib can be commonized — which fails for foreign
// targets because their paths don't exist on this host.
// We add the HOST's installed SDL3 include path as an extra -I to every
// target's cinterop. clang silently ignores -I dirs that don't exist, so
// foreign-target cinterop indexing finds headers via the host's copy.
// Actual link-time still picks the per-target linkerOpts from the .def,
// but linking only runs for the host's target.
val vHostOs = System.getProperty("os.name")
val vHostSdlInclude: String? = when {
    vHostOs.startsWith("Mac")     -> "/opt/homebrew/include"
    vHostOs == "Linux"            -> "/usr/include"
    vHostOs.startsWith("Windows") -> "${rootDir.invariantSeparatorsPath}/libs/SDL3/include"
    else                          -> null
}

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()

    applyDefaultHierarchyTemplate()

    targets.withType<KotlinNativeTarget>().all {
        compilations["main"].cinterops {
            val sdl3 by creating {
                defFile(project.file("src/nativeInterop/cinterop/sdl3.def"))
                packageName("sdl3")
                if (vHostSdlInclude != null) extraOpts("-compiler-options", "-I$vHostSdlInclude")
            }
        }
    }

    sourceSets {
        commonMain {
            // Files vendored VERBATIM from upstream Compose by
            // tools/compose-fork/sync.sh. Kept in their own folder so it's
            // obvious they are generated — never hand-edit; re-run sync instead.
            kotlin.srcDir("src/vendor/common/kotlin")
            dependencies {
                api("org.jetbrains.compose.runtime:runtime:1.11.1")
                implementation(libs.kotlinx.coroutines.core)
                // Multiplatform file IO for the data.kres reader (ResourceIO.kt).
                // Replaces raw platform.posix (fseek/ftell/fread), whose long/off_t
                // bit-widths differ across LLP64 Windows vs LP64 Unix and break the
                // shared nativeMain metadata compilation used by Maven publishing.
                implementation(libs.okio)
                // Explicit atomicfu so vendored animation-core / foundation
                // AtomicReference / AtomicLong actuals resolve their
                // kotlinx.atomicfu.atomic call regardless of whether compose
                // runtime keeps it as a transitive dep in future releases.
                implementation("org.jetbrains.kotlinx:atomicfu:0.23.1")
            }
        }
        // Vendored platform `actual`s (e.g. ui.util InlineClassHelper.native.kt).
        nativeMain {
            kotlin.srcDir("src/vendor/native/kotlin")
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
// MARK: Default + monospace fonts (Google Noto, downloaded at build time)
// ==================
// The default UI font (Noto Sans) and the monospace family (Noto Sans Mono)
// are fetched from the Noto Fonts project at build time into build/fonts/ and
// stitched into each app's data.kres by the app's Zip task (see
// demo/apidemo build.gradle.kts), exactly like the Material Symbols fonts —
// no Compose resources runtime involved. The renderers load "font/
// NotoSans-Regular.ttf" as the default; apidemo registers
// "font/NotoSansMono-Regular.ttf" with IconFont under "noto-mono".

val notoSansFont = layout.buildDirectory.file("fonts/NotoSans.ttf")
val notoSansMonoFont = layout.buildDirectory.file("fonts/NotoSansMono.ttf")

val downloadNotoFonts = tasks.register("downloadNotoFonts") {
    // Variable fonts (wdth,wght axes) so the renderers' weight path
    // (SpanStyle.fontWeight → FontVariation.Weight → Skia makeClone / SDL3
    // FreeType) actually varies the weight rather than no-op'ing on a static
    // Regular. Plain local vals so the configuration cache can serialize doLast.
    val vDownloads = listOf(
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth%2Cwght%5D.ttf"
            to notoSansFont.get().asFile,
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansmono/NotoSansMono%5Bwdth%2Cwght%5D.ttf"
            to notoSansMonoFont.get().asFile,
    )
    outputs.files(vDownloads.map { it.second })
    doLast {
        for ((vUrl, vOut) in vDownloads) {
            if (vOut.exists() && vOut.length() > 0) continue
            vOut.parentFile.mkdirs()
            println("Downloading $vUrl")
            URI(vUrl).toURL().openStream().use { vIn -> vOut.outputStream().use { vIn.copyTo(it) } }
            println("Saved ${vOut.length() / 1024} KiB to $vOut")
        }
    }
}

// The app Zip tasks (demo / apidemo) reference these outputs by build-dir
// layout and depend on `:core:downloadNotoFonts` by task path — no cross-
// project "extra" handshake, so it doesn't matter whether :core is configured
// before the app project.
