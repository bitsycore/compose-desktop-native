import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
        binaries.executable {
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
            )
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":window"))
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
// Reuses :core's bundled Roboto so the text renderers have a font at startup.
val nativeTargets = listOf("macosArm64", "linuxX64", "linuxArm64", "mingwX64")
val libComposeResourcesDir = rootProject.layout.projectDirectory.dir("core/src/nativeMain/composeResources")

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

for (variant in variants) {
    for (target in nativeTargets) {
        val variantCap = variant.replaceFirstChar { it.uppercase() }
        val targetCap = target.replaceFirstChar { it.uppercase() }
        val outDir = layout.buildDirectory.dir("bin/$target/${variant}Executable")
        val copyTask = tasks.register<Zip>("copy${variantCap}ComposeResources${targetCap}") {
            archiveFileName.set("data.kres")
            destinationDirectory.set(outDir)
            entryCompression = org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
            from(libComposeResourcesDir)
            iconFontModules.forEach { vP ->
                @Suppress("UNCHECKED_CAST")
                val vFontFile = vP.extra["iconFontFile"] as org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
                val vDownloadTask = vP.extra["iconFontDownloadTask"] as TaskProvider<*>
                from(vFontFile) { into("font") }
                dependsOn(vDownloadTask)
            }
        }
        listOf("link${variantCap}Executable${targetCap}", "run${variantCap}Executable${targetCap}").forEach { taskName ->
            tasks.matching { it.name == taskName }.configureEach { dependsOn(copyTask) }
        }
    }
}
