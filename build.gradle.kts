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
    ignoredProjects.addAll(listOf("demo", "apidemo"))
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
    ":ui" to "com.bitsycore.compose.ui",
    ":ui-graphics" to "com.bitsycore.compose.ui",
    ":ui-text" to "com.bitsycore.compose.ui",
    ":ui-unit" to "com.bitsycore.compose.ui",
    ":ui-geometry" to "com.bitsycore.compose.ui",
    ":ui-util" to "com.bitsycore.compose.ui",
    ":ui-backhandler" to "com.bitsycore.compose.ui",
    ":ui-tooling-preview" to "com.bitsycore.compose.ui",
    ":foundation" to "com.bitsycore.compose.foundation",
    ":foundation-layout" to "com.bitsycore.compose.foundation",
    ":animation" to "com.bitsycore.compose.animation",
    ":animation-core" to "com.bitsycore.compose.animation",
    ":animation-graphics" to "com.bitsycore.compose.animation",
    ":material3" to "com.bitsycore.compose.material3",
    ":material-ripple" to "com.bitsycore.compose.material",
    ":components-resources" to "com.bitsycore.compose.components",
    ":navigation3-ui" to "com.bitsycore.navigation3",
    ":desktop-native-window" to "com.bitsycore.compose",
)
fun groupFor(path: String): String = kAreaGroups[path] ?: kDefaultGroup

allprojects {
    group = groupFor(path)
    version = vPublishVersion
}

// ==================
// MARK: Publish to GitHub Packages
// ==================

// Every library module (everything except the two demo apps) auto-registers a
// MavenPublication via the kotlin-multiplatform plugin — one per target + one
// for the shared kotlinMultiplatform metadata. The CI publish workflow runs on
// three hosts (macOS / Linux / Windows) and each invokes only the publication
// tasks Gradle actually generated for its own targets, so the group of hosts
// together cover every K/N target + the JVM + the metadata module. Anything
// missing on a given host is silently skipped by Gradle's task lookup.

val kAppModules = setOf(":demo", ":apidemo")
val kPublishedLibs = setOf(
    ":sdl-core",
    ":ui", ":ui-util", ":ui-geometry", ":ui-graphics", ":ui-text",
    ":ui-unit", ":ui-backhandler", ":ui-tooling-preview",
    ":animation-core", ":animation", ":animation-graphics",
    ":foundation", ":foundation-layout",
    ":material3", ":material-ripple",
    ":desktop-native-window", ":material-symbols",
    ":navigation3-ui", ":components-resources",
)

// -PuseGithubPackages=true swaps every `project(":<lib>")` reference the demo
// apps make for the published Maven coordinate. Library modules keep resolving
// each other as `project(...)` — the substitution only fires at the
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
                    val vArtifactId = modulePath.removePrefix(":")
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
            }
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("${rootProject.name} ${project.name}")
                    description.set("Compose Multiplatform on SDL3 (Kotlin/Native, no JVM) — ${project.name}")
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
// swaps those modules for the port's project equivalents — the Maven
// artifacts ship no mingwX64/linux klibs. org.jetbrains.compose.runtime is
// deliberately NOT here: the port uses the official runtime klibs everywhere.
val vNativeTargetTokens = listOf("mingwX64", "linuxX64", "linuxArm64", "macosArm64")
allprojects {
    configurations.configureEach {
        if (vNativeTargetTokens.any { name.contains(it, ignoreCase = true) }) {
            resolutionStrategy.dependencySubstitution {
                substitute(module("org.jetbrains.compose.ui:ui")).using(project(":ui"))
                // ui-graphics / ui-text are their own modules (split out of :ui,
                // upstream layout). Declare each DIRECTLY so the app commonMain sees
                // them under the granular-metadata visibility rule.
                substitute(module("org.jetbrains.compose.ui:ui-graphics")).using(project(":ui-graphics"))
                substitute(module("org.jetbrains.compose.ui:ui-text")).using(project(":ui-text"))
                substitute(module("org.jetbrains.compose.ui:ui-unit")).using(project(":ui-unit"))
                substitute(module("org.jetbrains.compose.ui:ui-geometry")).using(project(":ui-geometry"))
                substitute(module("org.jetbrains.compose.ui:ui-util")).using(project(":ui-util"))
                substitute(module("org.jetbrains.compose.ui:ui-tooling-preview")).using(project(":ui-tooling-preview"))
                substitute(module("org.jetbrains.compose.foundation:foundation")).using(project(":foundation"))
                substitute(module("org.jetbrains.compose.foundation:foundation-layout")).using(project(":foundation-layout"))
                substitute(module("org.jetbrains.compose.animation:animation")).using(project(":animation"))
                substitute(module("org.jetbrains.compose.animation:animation-core")).using(project(":animation-core"))
                substitute(module("org.jetbrains.compose.material3:material3")).using(project(":material3"))
                // navigation3-ui: the JB Maven artifact has no K/N desktop
                // klibs — the port vendors it as :navigation3-ui.
                substitute(module("org.jetbrains.androidx.navigation3:navigation3-ui")).using(project(":navigation3-ui"))
                // components-resources: the official resources runtime ships no
                // mingwX64/linux klibs — the port vendors it as :components-resources.
                substitute(module("org.jetbrains.compose.components:components-resources")).using(project(":components-resources"))
            }
        }
    }
}

// Whether the current host can build the mingwX64 target. Kotlin/Native can
// only cross-compile the mingwX64 sdl3 cinterop from a Windows host — it needs
// the Windows SDL3 headers under libs/ (produced by scripts/build-sdl/build-all.py
// run on a Windows host). Declaring `mingwX64()` on a non-Windows host is safe
// for pure-Kotlin modules but blows up the moment the sdl3 cinterop tries to
// include SDL3's headers.
// Override with `-PforceMingw=true` if you actually have the headers wired.
val vHostSupportsMingw = System.getProperty("os.name").startsWith("Windows") ||
    (findProperty("forceMingw") as? String)?.toBoolean() == true
extra["vHostSupportsMingw"] = vHostSupportsMingw
