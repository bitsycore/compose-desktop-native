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

kotlin {
    jvm()

    sourceSets {
        commonMain {
            // The portable Core + Material 3 screens + the sidebar shell + the
            // Screen/Category registry + the shim `expect`s all live in :demo's
            // commonMain. Compile the very same files here against upstream Compose.
            kotlin.srcDir(rootProject.file("demo/src/commonMain/kotlin"))
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.12.0-alpha03")
                implementation("org.jetbrains.compose.foundation:foundation:1.12.0-alpha03")
                implementation("org.jetbrains.compose.animation:animation:1.12.0-alpha03")
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
                implementation("org.jetbrains.compose.ui:ui:1.12.0-alpha03")
                // navigation3 content model (NavKey / entryProvider / NavEntry.Content()).
                // The `desktop` variant is a real JVM impl and pulls androidx.compose.runtime
                // 1.9.x — aligned with org.jetbrains.compose 1.12.0-alpha03 above.
                implementation("androidx.navigation3:navigation3-runtime:1.1.4")
            }
        }
        jvmMain {
            // Reuse the demo's bundled drawables / files as JVM classpath resources
            // (drawable/*.png|jpg|svg|xml, files/notice.txt) for the resource shim.
            resources.srcDir(rootProject.file("demo/src/nativeMain/composeResources"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
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

// Headless diagnostic: render the shared Navigation3Screen to nav3jvm.png (see RenderNav3.kt).
tasks.register<JavaExec>("renderNav3") {
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("RenderNav3Kt")
    workingDir = rootProject.projectDir
}
