// :ui-graphics — androidx.compose.ui.graphics.* split out of :ui (upstream layout).
// Canvas / Paint / Path / Brush / Color / ImageBitmap / Shader / GraphicsLayer +
// the Skia actuals (SkiaBackedCanvas, SkiaImageCache, …). SDL-free — image bytes
// come through the composeResourceReader seam, so it depends only on skiko +
// the low ui primitives, NOT on :sdl-core.

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
                api(project(":ui-geometry"))
                api(project(":ui-unit"))
                api(project(":ui-util"))
                api("org.jetbrains.compose.runtime:runtime:${libs.versions.composeRuntime.get()}")
                implementation(libs.kotlinx.coroutines.core)
                // androidx.collection comes transitively via :ui-util (api).
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
                    implementation("com.bitsycore.skiko:skiko:${providers.gradleProperty("skikoMingwVersion").getOrElse("0.150.1-mingw.1")}")
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
