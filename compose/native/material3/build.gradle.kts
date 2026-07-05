// :material3 — Material 3 widgets vendored VERBATIM from upstream
// androidx.compose.material3, into src/vendor/. Objective: eventually retire
// the hand-written :material module in favour of this one.
//
// Provenance = material3/compose-fork.txt + tools/compose-fork/compose-ref.txt.
// Never hand-edit files under material3/src/vendor/ — change the manifest and
// re-run `bash tools/compose-fork/sync.sh :material3`.
//
// Publication artifactId (when set up): compose-desktop-native-material3.

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.plugin.compose)
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

	sourceSets {
		commonMain {
			dependencies {
				// :core provides ui / ui-text / runtime; :foundation provides
				// foundation.* + animation.* (non-core, merged in — see foundation/
				// build.gradle.kts for why); :animation-core provides animation.core.*.
				api(project(":core"))
				api(project(":foundation"))
				api(project(":animation-core"))
				// androidx.collection is used by material3 internals (MutableIntObjectMap, …).
				// Already on the classpath via :core's runtime; declare here for clarity.
				implementation("androidx.collection:collection:1.5.0")
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
