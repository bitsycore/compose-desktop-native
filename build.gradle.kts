// Root build — declare the Kotlin/Compose plugins once (apply false) so every
// subproject shares a single plugin classloader. Applying them independently
// per-subproject can load Kotlin Gradle plugin build services under isolated
// classloaders, which fails with "X cannot be cast to X" once several modules
// apply the multiplatform plugin. Each subproject still applies the ones it needs.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    // Public-ABI dumps (api/<module>.klib.api). Used by scripts/compose-fidelity-check.py
    // to diff our androidx.compose.* surface against the official Compose klib ABI.
    alias(libs.plugins.binary.compatibility.validator)
}

// Generate klib ABI dumps; the apps and icon-font modules carry no public API
// surface worth tracking, so skip them.
apiValidation {
    klib { enabled = true }
    ignoredProjects.addAll(listOf("demo", "apidemo"))
}

// Project coordinates. `group` is baked into every module's Kotlin/Native klib
// metadata as its module ID (e.g. `com.bitsycore.compose.sdl:ui`) — keep it
// stable, or a stale IC cache surfaces as "Unknown dependent library …" (see
// the pitfall note in CLAUDE.md).
//
// Version is driven by PUBLISH_VERSION (set from the git tag in the publish
// workflow, `v1.2.3` → `1.2.3`). Local dev / demo runs default to SNAPSHOT.
val vPublishVersion = (System.getenv("PUBLISH_VERSION") ?: "0.0.0-SNAPSHOT").removePrefix("v")
allprojects {
    group = "com.bitsycore.compose.sdl"
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
    ":ui", ":ui-util", ":ui-geometry", ":ui-unit", ":ui-backhandler",
    ":animation-core", ":animation", ":animation-graphics",
    ":foundation", ":foundation-layout",
    ":material3", ":material-ripple",
    ":window", ":material-symbols",
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

subprojects {
    if (kUseGhPackages && path in kAppModules) {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                kPublishedLibs.forEach { modulePath ->
                    val vArtifactId = modulePath.removePrefix(":")
                    substitute(project(modulePath))
                        .using(module("com.bitsycore.compose.sdl:$vArtifactId:$kConsumeVersion"))
                        .because("-PuseGithubPackages=true")
                }
            }
        }
    }
    if (path in kAppModules) return@subprojects
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

// Whether the current host can build the mingwX64 target. Kotlin/Native can
// only cross-compile mingwX64 cinterops from a Windows host — the sdl3_ttf /
// sdl3_image cinterops need Windows SDL3 headers under libs/ (produced by
// scripts/build-sdl/build-*.sh, which only runs on Windows). Declaring `mingwX64()` on
// a non-Windows host is safe for pure-Kotlin modules but blows up the moment
// a `depends = sdl3` cinterop tries to include SDL3_ttf/SDL_ttf.h.
// Override with `-PforceMingw=true` if you actually have the headers wired.
val vHostSupportsMingw = System.getProperty("os.name").startsWith("Windows") ||
    (findProperty("forceMingw") as? String)?.toBoolean() == true
extra["vHostSupportsMingw"] = vHostSupportsMingw
