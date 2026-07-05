import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

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
    // Public-ABI dumps (api/<module>.klib.api). Used by scripts/compose-fidelity-check.sh
    // to diff our androidx.compose.* surface against the official Compose klib ABI.
    alias(libs.plugins.binary.compatibility.validator)
}

// Generate klib ABI dumps; the apps and icon-font modules carry no public API
// surface worth tracking, so skip them.
apiValidation {
    klib { enabled = true }
    ignoredProjects.addAll(listOf("demo", "apidemo", "outlined", "rounded", "sharp"))
}

// ==================
// MARK: Publishing — com.bitsycore.compose.native:desktop-<module>
// ==================
// Library modules publish under group `com.bitsycore.compose.native` with
// artifactId `desktop-<module>` (e.g. :core -> desktop-core,
// :material-symbols:outlined -> desktop-material-symbols-outlined). KMP adds the
// per-target suffix (…-linuxx64 etc.) and the root `kotlinMultiplatform` metadata.
//
// Version comes from -PreleaseVersion (CI sets it from the git tag).
// Destination is the Maven repo at $PUBLISH_URL (+ $PUBLISH_USERNAME/$PUBLISH_PASSWORD);
// with none set, `publish` writes to build/staging-repo so it never hard-fails.
// Artifacts are GPG-signed only when $SIGNING_KEY (ASCII-armored) is present.

allprojects {
    group = "com.bitsycore.compose.native"
    version = (findProperty("releaseVersion") as String?)?.takeIf { it.isNotBlank() } ?: "0.0.0-SNAPSHOT"
}

val publishModules = setOf(
    ":ui", ":foundation", ":animation-core", ":material3", ":window",
    ":material-symbols:outlined", ":material-symbols:rounded", ":material-symbols:sharp",
)

subprojects {
    if (path !in publishModules) return@subprojects

    pluginManager.apply("maven-publish")
    pluginManager.apply("signing")

    val baseName = "desktop-" + path.removePrefix(":").replace(":", "-")
    val repoUrl = (findProperty("repoUrl") as String?) ?: "https://github.com/BitsyCore/compose-desktop-native"

    extensions.configure(PublishingExtension::class.java) {
        publications.withType(MavenPublication::class.java).configureEach {
            pom {
                name.set(baseName)
                description.set("ComposeNativeSDL3 ($baseName) — a Kotlin/Native subset of Compose Desktop on SDL3/Skia.")
                url.set(repoUrl)
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("bitsycore")
                        name.set("BitsyCore")
                    }
                }
                scm {
                    url.set(repoUrl)
                    connection.set("scm:git:$repoUrl.git")
                    developerConnection.set("scm:git:$repoUrl.git")
                }
            }
        }
        repositories {
            maven {
                name = "Publish"
                url = uri(
                    System.getenv("PUBLISH_URL")
                        ?: layout.buildDirectory.dir("staging-repo").get().asFile.toURI().toString()
                )
                val user = System.getenv("PUBLISH_USERNAME")
                val pass = System.getenv("PUBLISH_PASSWORD")
                if (user != null && pass != null) credentials {
                    username = user
                    password = pass
                }
            }
        }
    }

    extensions.configure(SigningExtension::class.java) {
        val signingKey = System.getenv("SIGNING_KEY")
        val signingPassword = System.getenv("SIGNING_PASSWORD")
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(extensions.getByType(PublishingExtension::class.java).publications)
        }
    }

    // Gradle 8/9: publish tasks consume signature outputs — declare the dependency
    // so it doesn't fail with "uses output of :sign… without a dependency".
    tasks.withType(AbstractPublishToMaven::class.java).configureEach {
        mustRunAfter(tasks.withType(Sign::class.java))
    }
}

// Rename publication artifactIds to desktop-<module>[-<target>]. Done in
// gradle.projectsEvaluated — after every module AND the Kotlin Multiplatform
// plugin have fully configured — so even host-disabled target publications
// (e.g. macosArm64 / mingwX64 on a Linux runner) are renamed. This keeps the
// root `kotlinMultiplatform` module's available-at refs consistent with the
// per-target artifacts published from each OS in the publish matrix.
//   kotlinMultiplatform     -> desktop-<module>
//   mingwX64 / linuxX64 / …  -> desktop-<module>-<target lowercased>
gradle.projectsEvaluated {
    publishModules.forEach { modPath ->
        val proj = findProject(modPath) ?: return@forEach
        val base = "desktop-" + modPath.removePrefix(":").replace(":", "-")
        proj.extensions.findByType(PublishingExtension::class.java)
            ?.publications?.withType(MavenPublication::class.java)?.all {
                artifactId = if (name == "kotlinMultiplatform") base else "$base-${name.lowercase()}"
            }
    }
}
