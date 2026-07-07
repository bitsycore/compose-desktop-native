plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// ==================
// MARK: :demojvm — the JVM comparison app
// ==================
// A Compose Desktop (JVM) app that compiles the SAME shared screens as :demo —
// its commonMain srcDir points at demo/src/commonMain/kotlin — but resolves
// androidx.compose.* against UPSTREAM org.jetbrains.compose rather than this
// project's native reimplementation. The shared screens use only androidx.compose
// .foundation/.ui/.animation/.material3/.runtime, so if they compile + run here
// unchanged, the commonization held. Project-only seams (icons, resources, the
// pressable modifier, platform categories) are `expect` in the shared code and
// get UPSTREAM actuals in jvmMain below.
//
// It's a separate module (not a jvm() target on :demo) because :demo's commonMain
// depends on the native-only project modules (:window / :material3 / …), which a
// JVM target can't resolve — so the two apps share source, not a source set.

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            // The portable Core + Material 3 screens + the sidebar shell + the
            // Screen/Category registry + the shim `expect`s all live in :demo's
            // commonMain. Compile the very same files here against upstream Compose.
            kotlin.srcDir(rootProject.file("demo/src/commonMain/kotlin"))
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.animation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        jvmMain {
            // Reuse the demo's bundled drawables / files as JVM classpath resources
            // (drawable/*.png|jpg|svg|xml, files/notice.txt) for the resource shim.
            resources.srcDir(rootProject.file("demo/src/nativeMain/composeResources"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.materialIconsExtended)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
}

compose.desktop {
    application {
        mainClass = "MainJvmKt"
    }
}
