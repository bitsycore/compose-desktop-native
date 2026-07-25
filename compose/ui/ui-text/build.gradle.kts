// :ui-text — androidx.compose.ui.text.* split out of :ui (upstream layout).
// AnnotatedString / TextStyle / Paragraph / FontFamily + the B6.3 skiko text
// engine (SkiaParagraph / SkiaParagraphOps / SkiaFonts, IconFont). SDL-free —
// depends on :ui-graphics (Canvas), skiko, and the low ui primitives.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
    linuxArm64()
    linuxX64()
    macosArm64()
    if (vHostSupportsMingw) mingwX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            kotlin.srcDir("src/vendor/common/kotlin")
            dependencies {
                api(project(":ui-graphics"))
                api(project(":ui-unit"))
                api(project(":ui-util"))
                api("org.jetbrains.compose.runtime:runtime:${libs.versions.composeRuntime.get()}")
                api("org.jetbrains.compose.runtime:runtime-saveable:${libs.versions.composeRuntime.get()}")
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        nativeMain {
            kotlin.srcDir("src/vendor/native/kotlin")
        }

        val skikoRendererMain = create("skikoRendererMain") {
            dependsOn(nativeMain.get())
            kotlin.srcDir("src/vendor/skikoRenderer/kotlin")
            dependencies {
                implementation(libs.skiko)
            }
        }
        val skikoRendererMacosMain = create("skikoRendererMacosMain") { dependsOn(skikoRendererMain) }
        val skikoRendererLinuxMain = create("skikoRendererLinuxMain") { dependsOn(skikoRendererMain) }
        get("macosArm64Main").dependsOn(skikoRendererMacosMain)
        get("linuxX64Main").dependsOn(skikoRendererLinuxMain)
        get("linuxArm64Main").dependsOn(skikoRendererLinuxMain)

        if (vHostSupportsMingw) {
            val skikoRendererMingwSharedMain = create("skikoRendererMingwSharedMain") {
                dependsOn(nativeMain.get())
                kotlin.srcDir("src/skikoRendererMain/kotlin")
                kotlin.srcDir("src/vendor/skikoRenderer/kotlin")
                dependencies {
                    implementation("org.jetbrains.skiko:skiko:${providers.gradleProperty("skikoMingwVersion").getOrElse("0.150.1-mingw.1")}")
                }
            }
            val skikoRendererMingwMain = create("skikoRendererMingwMain") { dependsOn(skikoRendererMingwSharedMain) }
            get("mingwX64Main").dependsOn(skikoRendererMingwMain)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
}
