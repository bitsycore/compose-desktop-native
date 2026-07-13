package com.bitsycore.compose.sdl.gradle

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.ZipEntryCompression

// ==================
// MARK: composeResources → data.kres packaging
// ==================

/**
 * Gives consumer apps the OFFICIAL composeResources experience on the port's
 * native desktop targets: files under `src/<sourceSet>/composeResources/` +
 * the generated `Res.*` accessors, with nothing hand-rolled.
 *
 * The official Compose plugin already handles accessor generation and every
 * other platform's packaging (jvm classpath, Android assets, …). What the
 * native desktop targets need is the port's runtime bundle: `data.kres`, a
 * STORED zip next to the executable that the runtime opens via
 * SDL_GetBasePath() and reads entry-by-entry (fseek+fread — hence no
 * compression). This registers one Zip task per native executable link task,
 * zipping the Compose plugin's PREPARED resources (values*.xml are converted
 * to .cvr there — zipping the raw source dir would break stringResource)
 * under the same `composeResources/<res-package>/` prefix the generated
 * accessors carry.
 *
 * IMPLEMENTATION NOTE — conventions, not KGP types: applied from settings,
 * this plugin lives in a classloader that is a PARENT of the project's
 * buildscript loader, so KGP / Compose plugin classes are structurally
 * invisible to it. Link tasks (`link<Variant>Executable<Target>`), binary
 * output dirs (`build/bin/<target>/<variant>Executable`), prepare tasks
 * (`prepareComposeResourcesTaskFor<SourceSet>`) and the prepared-resources
 * dir are all stable KGP / Compose-plugin naming conventions.
 */
internal fun installResourcePackaging(project: Project) {
	project.pluginManager.withPlugin("org.jetbrains.compose") {
		project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			// afterEvaluate: link tasks, prepare tasks and compose.resources {}
			// all materialise during project evaluation.
			project.afterEvaluate { registerDataKresTasks(it) }
		}
	}
}

/* The port's desktop targets and each one's default-hierarchy source sets,
   most specific first — a target-level resource overrides a commonMain one. */
private val desktopTargets = mapOf(
	"MingwX64" to listOf("mingwX64Main", "mingwMain", "nativeMain", "commonMain"),
	"LinuxX64" to listOf("linuxX64Main", "linuxMain", "nativeMain", "commonMain"),
	"LinuxArm64" to listOf("linuxArm64Main", "linuxMain", "nativeMain", "commonMain"),
	"MacosArm64" to listOf("macosArm64Main", "macosMain", "appleMain", "nativeMain", "commonMain"),
)

private fun registerDataKresTasks(project: Project) {
	val taskNames = project.tasks.names
	val resPackage = project.resolveResourcePackage()
	for ((target, sourceSets) in desktopTargets) {
		for (variant in listOf("Debug", "Release")) {
			val linkName = "link${variant}Executable$target"
			if (linkName !in taskNames) continue
			val zipName = "package${variant}ComposeResources$target"
			if (zipName in taskNames) continue
			val prepareNames = sourceSets
				.map { "prepareComposeResourcesTaskFor" + it.replaceFirstChar { c -> c.uppercase() } }
				.filter { it in taskNames }
			val zipTask = project.tasks.register(zipName, Zip::class.java) { task ->
				task.description = "Bundles composeResources into data.kres next to the $target ${variant.lowercase()} executable."
				task.archiveFileName.set("data.kres")
				task.destinationDirectory.set(
					project.layout.buildDirectory.dir(
						"bin/${target.replaceFirstChar { it.lowercase() }}/${variant.replaceFirstChar { it.lowercase() }}Executable"
					)
				)
				// STORED: the runtime's reader hands raw bytes straight to the
				// decoders — an entry is one fseek+fread, never inflated.
				task.entryCompression = ZipEntryCompression.STORED
				task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
				for (prepareName in prepareNames) {
					val sourceSet = prepareName.removePrefix("prepareComposeResourcesTaskFor")
						.replaceFirstChar { it.lowercase() }
					val prepared = project.layout.buildDirectory.dir(
						"generated/compose/resourceGenerator/preparedResources/$sourceSet/composeResources"
					)
					task.from(prepared) { spec -> spec.into("composeResources/$resPackage") }
					task.dependsOn(project.tasks.named(prepareName))
				}
			}
			project.tasks.named(linkName) { it.dependsOn(zipTask) }
		}
	}
}

/* The package the generated accessors carry in their resource paths —
   compose.resources.packageOfResClass (read REFLECTIVELY: the ResourcesExtension
   class lives in the project's buildscript loader, invisible from here), or
   the Compose plugin's documented default `{group}.{module}.generated.resources`
   (lowercased, '-' → '_', digit-leading segments prefixed with '_'). */
private fun Project.resolveResourcePackage(): String {
	val explicit = runCatching {
		val composeExt = extensions.findByName("compose") as? ExtensionAware
		val resourcesExt = composeExt?.extensions?.findByName("resources")
		resourcesExt?.javaClass?.getMethod("getPackageOfResClass")?.invoke(resourcesExt) as? String
	}.getOrNull().orEmpty()
	if (explicit.isNotEmpty()) return explicit
	val groupName = group.toString().lowercase().asUnderscoredIdentifier()
	val moduleName = name.lowercase().asUnderscoredIdentifier()
	val id = if (groupName.isNotEmpty()) "$groupName.$moduleName" else moduleName
	return "$id.generated.resources"
}

private fun String.asUnderscoredIdentifier(): String =
	replace('-', '_').let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }
