import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// :apidemo — a Postman-style REST API manager built on the compose-desktop-native
// stack. Uses Ktor (WinHttp on Windows, Darwin on macOS, Curl on Linux),
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
        // Per-target Ktor engine — WinHttp ships with Windows (no extra DLL),
        // Darwin uses NSURLSession, Curl needs libcurl on the Linux host.
        getByName("mingwX64Main").dependencies { implementation(libs.ktor.client.winhttp) }
        getByName("macosArm64Main").dependencies { implementation(libs.ktor.client.darwin) }
        getByName("linuxX64Main").dependencies { implementation(libs.ktor.client.curl) }
        getByName("linuxArm64Main").dependencies { implementation(libs.ktor.client.curl) }
    }

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
    }
}

// ==================
// MARK: Bundle SDL3 runtime DLLs next to the Windows executable (mingwX64)
// ==================
val variants = listOf("debug", "release")
val sdl3Dir = (findProperty("sdl3Dir") as? String) ?: "$vLibs/SDL3"
val sdl3TtfDir = (findProperty("sdl3TtfDir") as? String) ?: "$vLibs/SDL3_ttf"
val sdl3ImageDir = (findProperty("sdl3ImageDir") as? String) ?: "$vLibs/SDL3_image"

for (variant in variants) {
    val variantCap = variant.replaceFirstChar { it.uppercase() }
    val outDir = layout.buildDirectory.dir("bin/mingwX64/${variant}Executable")
    val copyTask = tasks.register<Copy>("copy${variantCap}DllsMingwX64") {
        from("$sdl3Dir/bin") { include("*.dll") }
        from("$sdl3TtfDir/bin") { include("*.dll") }
        from("$sdl3ImageDir/bin") { include("*.dll") }
        into(outDir)
    }
    listOf("link${variantCap}ExecutableMingwX64", "run${variantCap}ExecutableMingwX64").forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach { dependsOn(copyTask) }
    }
}

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
