// :material-symbols — the codepoint constants for the Material Symbols
// icon set, kept separate from the style sub-modules so depending on any
// of :outlined / :rounded / :sharp transparently brings the codepoints
// in (each sub-module api()s this one). Apps that wire up their own
// IconFont registration and only need the constants can depend on this
// module directly without pulling a style font.
// Publication artifactId (when set up): compose-desktop-material-symbols.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
}
