// :material3 — Material 3 widgets vendored VERBATIM from upstream
// androidx.compose.material3, into src/vendor/. Objective: eventually retire
// the hand-written :material module in favour of this one.
//
// Provenance = material3/compose-fork.txt + scripts/compose-fork/compose.properties.
// Never hand-edit files under material3/src/vendor/ — change the manifest and
// re-run `bash scripts/compose-fork/sync.sh :material3`.
//
// Publication artifactId (when set up): compose-desktop-native-material3.

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.plugin.compose)
}

// Skip mingwX64 on non-Windows hosts; see root build.gradle.kts.
val vHostSupportsMingw = rootProject.extra["vHostSupportsMingw"] as Boolean

kotlin {
	linuxArm64()
	linuxX64()
	macosArm64()
	if (vHostSupportsMingw) mingwX64()

	applyDefaultHierarchyTemplate()

	sourceSets {
		commonMain {
			dependencies {
				// :ui provides ui / ui-text / runtime; :foundation provides foundation.* +
				// (transitively) animation.* + foundation.layout.*; :animation-core provides
				// animation.core.*; :material-ripple provides material.ripple.*.
				api(project(":ui"))
				api(project(":foundation"))
				api(project(":animation-core"))
				api(project(":material-ripple"))
				// androidx.collection is used by material3 internals (MutableIntObjectMap, …).
				// Already on the classpath via :core's runtime; declare here for clarity.
				implementation("androidx.collection:collection:1.5.0")
				// androidx.graphics.shapes.* — MaterialShapes (RoundedPolygon /
				// Morph / CornerRounding) that WavyProgress / LoadingIndicator use.
				implementation("androidx.graphics:graphics-shapes:1.1.0")
				// kotlinx.datetime — DatePicker / TimePicker / CalendarModel.
				implementation(libs.kotlinx.datetime)
			}
			kotlin.srcDir("src/vendor/common/kotlin")
		}
		nativeMain {
			kotlin.srcDir("src/vendor/native/kotlin")
		}
	}

	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xcollection-literals",
			"-Xexpect-actual-classes",
			"-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
			"-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
			"-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
			"-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
			"-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
			"-opt-in=androidx.compose.foundation.text.ExperimentalTextApi",
		)
	}
}
