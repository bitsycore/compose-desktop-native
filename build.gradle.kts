// Root build — declare the Kotlin/Compose plugins once (apply false) so every
// subproject shares a single plugin classloader. Applying them independently
// per-subproject can load Kotlin Gradle plugin build services under isolated
// classloaders, which fails with "X cannot be cast to X" once several modules
// apply the multiplatform plugin. Each subproject still applies the ones it needs.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
