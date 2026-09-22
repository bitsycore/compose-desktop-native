import kotlinx.validation.ExperimentalBCVApi

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    @OptIn(ExperimentalBCVApi::class)
    klib { enabled = true }
    // demo/apidemo: apps, no published API. sdl-core: naked SDL cinterop - its
    // "API" is the generated sdl3.* bindings (per-OS, can't infer macos), not our
    // surface. material-symbols: auto-generated icon codepoint maps + its jvm
    // dump trips BCV's ASM on newer JDKs (class file major 69). Track the
    // hand-authored Compose surface, not generated/binding code.
    ignoredProjects.addAll(listOf("demo", "apidemo", "sdl-core", "material-symbols"))
}

// Version is driven by PUBLISH_VERSION (set from the git tag in the publish
// workflow, `v1.2.3` → `1.2.3`). Local dev / demo runs default to SNAPSHOT.
val vPublishVersion = (System.getenv("PUBLISH_VERSION") ?: "0.0.0-SNAPSHOT").removePrefix("v")

// Published groups mirror the org.jetbrains.compose.<area> originals under the
// com.bitsycore fork name, so each module is the obvious 1:1 fork of its upstream
// coord (e.g. org.jetbrains.compose.ui:ui → com.bitsycore.compose.ui:ui). Modules
// with no upstream area (project-only: :sdl-core, :material-symbols) fall through
// to the default com.bitsycore.compose.sdl group; :desktop-native-window is the
// headline app-shell artifact, published under com.bitsycore.compose.
val kDefaultGroup = "com.bitsycore.compose.sdl"
val kAreaGroups = mapOf(
    ":compose:ui:ui" to "com.bitsycore.compose.ui",
    ":compose:ui:ui-graphics" to "com.bitsycore.compose.ui",
    ":compose:ui:ui-text" to "com.bitsycore.compose.ui",
    ":compose:ui:ui-unit" to "com.bitsycore.compose.ui",
    ":compose:ui:ui-geometry" to "com.bitsycore.compose.ui",
    ":compose:ui:ui-util" to "com.bitsycore.compose.ui",
    ":compose:ui:ui-backhandler" to "com.bitsycore.compose.ui",
    ":compose:ui:ui-tooling-preview" to "com.bitsycore.compose.ui",
    ":compose:foundation:foundation" to "com.bitsycore.compose.foundation",
    ":compose:foundation:foundation-layout" to "com.bitsycore.compose.foundation",
    ":compose:animation:animation" to "com.bitsycore.compose.animation",
    ":compose:animation:animation-core" to "com.bitsycore.compose.animation",
    ":compose:animation:animation-graphics" to "com.bitsycore.compose.animation",
    ":compose:material3:material3" to "com.bitsycore.compose.material3",
    ":compose:material3:adaptive:adaptive" to "com.bitsycore.compose.material3.adaptive",
    ":compose:material3:adaptive:adaptive-layout" to "com.bitsycore.compose.material3.adaptive",
    ":compose:material3:adaptive:adaptive-navigation" to "com.bitsycore.compose.material3.adaptive",
    ":compose:material3:adaptive:adaptive-navigation3" to "com.bitsycore.compose.material3.adaptive",
    ":compose:material:material-ripple" to "com.bitsycore.compose.material",
    ":components:resources:components-resources" to "com.bitsycore.compose.components",
    ":navigation3:navigation3-ui" to "com.bitsycore.navigation3",
    ":koin:koin-core-viewmodel" to "com.bitsycore.koin",
    ":koin:koin-compose" to "com.bitsycore.koin",
    ":koin:koin-compose-viewmodel" to "com.bitsycore.koin",
    ":koin:koin-compose-navigation3" to "com.bitsycore.koin",
    ":coil:coil-core" to "com.bitsycore.coil3",
    ":coil:coil" to "com.bitsycore.coil3",
    ":coil:coil-compose-core" to "com.bitsycore.coil3",
    ":coil:coil-compose" to "com.bitsycore.coil3",
    ":coil:coil-svg" to "com.bitsycore.coil3",
    ":coil:coil-network-core" to "com.bitsycore.coil3",
    ":coil:coil-network-ktor3" to "com.bitsycore.coil3",
    ":pulse:pulse" to "com.bitsycore.compose.desktop.native.pulse",
    ":pulse:pulse-viewmodel" to "com.bitsycore.compose.desktop.native.pulse",
    ":pulse:pulse-savedstate" to "com.bitsycore.compose.desktop.native.pulse",
    ":pulse:pulse-compose" to "com.bitsycore.compose.desktop.native.pulse",
    ":compose:desktop:native:desktop-native-window" to "com.bitsycore.compose",
)
fun groupFor(path: String): String = kAreaGroups[path] ?: kDefaultGroup

// The published artifactId is always the leaf project name - settings.gradle.kts
// keeps the leaf equal to the artifactId even where the directory differs.
fun artifactIdFor(path: String): String = path.substringAfterLast(':')

allprojects {
    group = groupFor(path)
    version = vPublishVersion
}

// ==================
// MARK: Publish to GitHub Packages
// ==================

// Every library module (everything except the two demo apps) auto-registers a
// MavenPublication via the kotlin-multiplatform plugin - one per target + one
// for the shared kotlinMultiplatform metadata. The CI publish workflow runs on
// three hosts (macOS / Linux / Windows) and each invokes only the publication
// tasks Gradle actually generated for its own targets, so the group of hosts
// together cover every K/N target + the JVM + the metadata module. Anything
// missing on a given host is silently skipped by Gradle's task lookup.

val kAppModules = setOf(":demo", ":apidemo")
val kPublishedLibs = setOf(
    ":sdl:sdl-core",
    ":compose:ui:ui", ":compose:ui:ui-util", ":compose:ui:ui-geometry",
    ":compose:ui:ui-graphics", ":compose:ui:ui-text",
    ":compose:ui:ui-unit", ":compose:ui:ui-backhandler", ":compose:ui:ui-tooling-preview",
    ":compose:animation:animation-core", ":compose:animation:animation",
    ":compose:animation:animation-graphics",
    ":compose:foundation:foundation", ":compose:foundation:foundation-layout",
    ":compose:material3:material3", ":compose:material:material-ripple",
    ":compose:material3:adaptive:adaptive", ":compose:material3:adaptive:adaptive-layout", ":compose:material3:adaptive:adaptive-navigation", ":compose:material3:adaptive:adaptive-navigation3",
    ":compose:desktop:native:desktop-native-window", ":utils:material-symbols",
    ":navigation3:navigation3-ui", ":components:resources:components-resources",
    ":koin:koin-core-viewmodel", ":koin:koin-compose",
    ":koin:koin-compose-viewmodel", ":koin:koin-compose-navigation3",
    ":coil:coil-core", ":coil:coil", ":coil:coil-compose-core", ":coil:coil-compose",
    ":coil:coil-svg", ":coil:coil-network-core", ":coil:coil-network-ktor3",
    ":pulse:pulse", ":pulse:pulse-viewmodel", ":pulse:pulse-savedstate", ":pulse:pulse-compose",
)

// -PuseGithubPackages=true swaps every `project(":<lib>")` reference the demo
// apps make for the published Maven coordinate. Library modules keep resolving
// each other as `project(...)` - the substitution only fires at the
// app→library boundary, so the swap validates end-to-end consumption of the
// published klibs without touching the source of `implementation(project(...))`.
// Version defaults to 0.1.0 (matches the git tag) but can be pinned via -PconsumeVersion=….
val kUseGhPackages = (findProperty("useGithubPackages") as? String)?.toBoolean() == true
val kConsumeVersion = (findProperty("consumeVersion") as? String) ?: "0.1.0"

// JVM-PARITY VERSION PIN (app modules): force the org.jetbrains.compose groups
// on every jvm configuration to the catalog pin matching COMPOSE_CORE_REF.
// Gradle orders "+dev" BELOW the plain version, so whenever the pin is a dev
// build the umbrella plugin's published artifacts would win conflict resolution
// and silently break byte-exact parity with the vendored sources.
val kComposeJvmForced = mapOf(
    "org.jetbrains.compose.runtime" to libs.versions.compose.get(),
    "org.jetbrains.compose.ui" to libs.versions.compose.get(),
    "org.jetbrains.compose.foundation" to libs.versions.compose.get(),
    "org.jetbrains.compose.animation" to libs.versions.compose.get(),
    "org.jetbrains.compose.material" to libs.versions.compose.get(),
    "org.jetbrains.compose.material3" to libs.versions.composeMaterial3.get(),
)

subprojects {
    if (kUseGhPackages && path in kAppModules) {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                kPublishedLibs.forEach { modulePath ->
                    val vArtifactId = artifactIdFor(modulePath)
                    substitute(project(modulePath))
                        .using(module("${groupFor(modulePath)}:$vArtifactId:$kConsumeVersion"))
                        .because("-PuseGithubPackages=true")
                }
            }
        }
    }
    if (path in kAppModules) {
        configurations.configureEach {
            if (name.startsWith("jvm")) {
                resolutionStrategy.eachDependency {
                    kComposeJvmForced[requested.group]?.let { useVersion(it) }
                }
            }
        }
        return@subprojects
    }
    // Nesting the paths under compose/ / sdl/ / utils/ … materialises container
    // projects (:compose, :compose:ui, …) that hold no code - skip them.
    if (path !in kPublishedLibs) return@subprojects
    plugins.apply("maven-publish")
    afterEvaluate {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    val vRepo = System.getenv("GITHUB_REPOSITORY") ?: "bitsycore/ComposeDesktopNative"
                    url = uri("https://maven.pkg.github.com/$vRepo")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
                maven {
                    name = "Bitsycore"
                    url = uri("https://maven.bitsycore.com/releases")
                    credentials {
                        username = System.getenv("BITSYCORE_MAVEN_USER")
                        password = System.getenv("BITSYCORE_MAVEN_TOKEN")
                    }
                }
            }
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("${rootProject.name} ${project.name}")
                    description.set("Compose Multiplatform on SDL3 (Kotlin/Native, no JVM) - ${project.name}")
                    url.set("https://github.com/${System.getenv("GITHUB_REPOSITORY") ?: "bitsycore/ComposeDesktopNative"}")
                    licenses {
                        license {
                            name.set("MIT")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                }
            }
        }
    }
}

// FULL-COMMONIZATION BRIDGE (repo-wide): a module may declare the OFFICIAL
// Maven Compose artifacts in its commonMain so metadata + jvm resolve them
// (e.g. :material-symbols' common API); every NATIVE target configuration
// swaps those modules for the port's project equivalents - the Maven
// artifacts ship no mingwX64/linux klibs. org.jetbrains.compose.runtime is
// deliberately NOT here: the port uses the official runtime klibs everywhere.
val vNativeTargetTokens = listOf("mingwX64", "linuxX64", "linuxArm64", "macosArm64")
allprojects {
    configurations.configureEach {
        if (vNativeTargetTokens.any { name.contains(it, ignoreCase = true) }) {
            resolutionStrategy.dependencySubstitution {
                substitute(module("org.jetbrains.compose.ui:ui")).using(project(":compose:ui:ui"))
                // ui-graphics / ui-text are their own modules (split out of :ui,
                // upstream layout). Declare each DIRECTLY so the app commonMain sees
                // them under the granular-metadata visibility rule.
                substitute(module("org.jetbrains.compose.ui:ui-graphics")).using(project(":compose:ui:ui-graphics"))
                substitute(module("org.jetbrains.compose.ui:ui-text")).using(project(":compose:ui:ui-text"))
                substitute(module("org.jetbrains.compose.ui:ui-unit")).using(project(":compose:ui:ui-unit"))
                substitute(module("org.jetbrains.compose.ui:ui-geometry")).using(project(":compose:ui:ui-geometry"))
                substitute(module("org.jetbrains.compose.ui:ui-util")).using(project(":compose:ui:ui-util"))
                substitute(module("org.jetbrains.compose.ui:ui-tooling-preview")).using(project(":compose:ui:ui-tooling-preview"))
                substitute(module("org.jetbrains.compose.foundation:foundation")).using(project(":compose:foundation:foundation"))
                substitute(module("org.jetbrains.compose.foundation:foundation-layout")).using(project(":compose:foundation:foundation-layout"))
                substitute(module("org.jetbrains.compose.animation:animation")).using(project(":compose:animation:animation"))
                substitute(module("org.jetbrains.compose.animation:animation-core")).using(project(":compose:animation:animation-core"))
                substitute(module("org.jetbrains.compose.material3:material3")).using(project(":compose:material3:material3"))
                // material3-adaptive: upstream publishes ios + macosArm64 only - no
                // linux, no mingw (issues/4). Vendored as :compose:material3:adaptive:*.
                substitute(module("org.jetbrains.compose.material3.adaptive:adaptive")).using(project(":compose:material3:adaptive:adaptive"))
                substitute(module("org.jetbrains.compose.material3.adaptive:adaptive-layout")).using(project(":compose:material3:adaptive:adaptive-layout"))
                substitute(module("org.jetbrains.compose.material3.adaptive:adaptive-navigation")).using(project(":compose:material3:adaptive:adaptive-navigation"))
                substitute(module("org.jetbrains.compose.material3.adaptive:adaptive-navigation3")).using(project(":compose:material3:adaptive:adaptive-navigation3"))
                // navigation3-ui: the JB Maven artifact has no K/N desktop
                // klibs - the port vendors it as :navigation3-ui.
                substitute(module("org.jetbrains.androidx.navigation3:navigation3-ui")).using(project(":navigation3:navigation3-ui"))
                // components-resources: the official resources runtime ships no
                // mingwX64/linux klibs - the port vendors it as :components-resources.
                substitute(module("org.jetbrains.compose.components:components-resources")).using(project(":components:resources:components-resources"))
                // Koin: koin-core itself publishes mingwX64 + linux and is used
                // from Maven; these four stop at apple+android upstream.
                substitute(module("io.insert-koin:koin-core-viewmodel")).using(project(":koin:koin-core-viewmodel"))
                substitute(module("io.insert-koin:koin-compose")).using(project(":koin:koin-compose"))
                substitute(module("io.insert-koin:koin-compose-viewmodel")).using(project(":koin:koin-compose-viewmodel"))
                substitute(module("io.insert-koin:koin-compose-navigation3")).using(project(":koin:koin-compose-navigation3"))
                // Coil 3: no mingwX64 upstream anywhere, and no linux for the
                // compose layer either.
                substitute(module("io.coil-kt.coil3:coil-core")).using(project(":coil:coil-core"))
                substitute(module("io.coil-kt.coil3:coil")).using(project(":coil:coil"))
                substitute(module("io.coil-kt.coil3:coil-compose-core")).using(project(":coil:coil-compose-core"))
                substitute(module("io.coil-kt.coil3:coil-compose")).using(project(":coil:coil-compose"))
                substitute(module("io.coil-kt.coil3:coil-svg")).using(project(":coil:coil-svg"))
                substitute(module("io.coil-kt.coil3:coil-network-core")).using(project(":coil:coil-network-core"))
                substitute(module("io.coil-kt.coil3:coil-network-ktor3")).using(project(":coil:coil-network-ktor3"))
                // Pulse MVI: upstream publishes com.bitsycore.lib:* with no desktop
                // native targets. Pulse is itself a com.bitsycore library, so the port's
                // republish is namespaced under THIS project rather than a bare
                // com.bitsycore.pulse, which would read like the upstream artifact. The
                // two coordinates never collide, and substitutes the official one here.
                substitute(module("com.bitsycore.lib:pulse")).using(project(":pulse:pulse"))
                substitute(module("com.bitsycore.lib:pulse-viewmodel")).using(project(":pulse:pulse-viewmodel"))
                substitute(module("com.bitsycore.lib:pulse-savedstate")).using(project(":pulse:pulse-savedstate"))
                substitute(module("com.bitsycore.lib:pulse-compose")).using(project(":pulse:pulse-compose"))
            }
        }
    }
}

// Whether the current host can build the mingwX64 target. Kotlin/Native can
// only cross-compile the mingwX64 sdl3 cinterop from a Windows host - it needs
// the Windows SDL3 headers under libs/ (produced by scripts/build-sdl/build-all.py
// run on a Windows host). Declaring `mingwX64()` on a non-Windows host is safe
// for pure-Kotlin modules but blows up the moment the sdl3 cinterop tries to
// include SDL3's headers.
// Override with `-PforceMingw=true` if you actually have the headers wired.
val vHostSupportsMingw = System.getProperty("os.name").startsWith("Windows") ||
    (findProperty("forceMingw") as? String)?.toBoolean() == true
extra["vHostSupportsMingw"] = vHostSupportsMingw
