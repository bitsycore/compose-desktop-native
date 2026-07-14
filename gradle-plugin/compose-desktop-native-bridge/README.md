# Compose Desktop Native Bridge

`com.bitsycore.compose-desktop-native.bridge` lets an app declare the **official Compose
Multiplatform coordinates once in `commonMain`** and run on every platform CMP
supports **plus** the port's Kotlin/Native desktop targets (mingwX64,
linuxX64, linuxArm64, macosArm64 — no JVM).

Gradle dependency-substitution rules cannot ship inside a Maven artifact, so
this plugin carries them instead: on every configuration belonging to a native
desktop target it substitutes the official `org.jetbrains.compose.*`
coordinates for the published `com.bitsycore.compose.sdl:*` klibs. All other
targets (android / jvm / iOS / wasm) keep resolving the official artifacts
untouched. `org.jetbrains.compose.runtime` is never substituted — the official
runtime klibs serve every target.

## Usage

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        // GitHub Packages requires authentication even for public packages.
        maven("https://maven.pkg.github.com/bitsycore/compose-desktop-native") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.bitsycore.compose-desktop-native.bridge") version "<release-version>"
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.github.com/bitsycore/compose-desktop-native") { credentials { /* same */ } }
        google()
        mavenCentral()
    }
}
```

```kotlin
// module build.gradle.kts — official coords, everywhere
kotlin {
    macosArm64(); linuxX64(); mingwX64()   // + jvm()/android()/ios if you like
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation("org.jetbrains.compose.ui:ui:<cmp-version>")
            implementation("org.jetbrains.compose.foundation:foundation:<cmp-version>")
            implementation("org.jetbrains.compose.material3:material3:<cmp-m3-version>")
        }
    }
}
```

### Settings-wide or per-module

The plugin applies at either level:

```kotlin
// settings.gradle.kts — every module of the build
plugins { id("com.bitsycore.compose-desktop-native.bridge") version "<v>" }
```

```kotlin
// build.gradle.kts — this module only (compose-plugin style)
plugins { id("com.bitsycore.compose-desktop-native.bridge") }
```

Note the difference between the two settings blocks: a
`pluginManagement { plugins { id(...) version ... } }` entry only PINS the
version (so module-level applications can omit it); the top-level
`plugins { }` block in settings is what actually applies it build-wide.
Rule of thumb: single app module → apply in the module; multi-module builds →
apply once in settings.

## compose.desktop.native — the application block for native

The native counterpart of `compose.desktop { application { mainClass } }`:

```kotlin
compose.desktop {
    application { mainClass = "app.MainJvmKt" }   // upstream jvm
    native { entryPoint = "app.main" }            // compose-desktop-native
}
```

Declares an executable with that entry point on every Kotlin/Native desktop
target — no `targets.withType<KotlinNativeTarget> { binaries.executable { … } }`
boilerplate. Targets that already declare an executable are left untouched,
so manual configuration (extra linker flags, custom build types) still wins.

## composeResources — zero setup

If the module also applies the official `org.jetbrains.compose` plugin, the
bridge completes the resources story on the native desktop targets: it
registers a `package<Variant>ComposeResources<Target>` task per native
executable that bundles the Compose plugin's prepared resources into
`data.kres` next to the binary (a STORED zip the port's runtime reads via
SDL_GetBasePath). Files under `src/commonMain/composeResources/` + the
generated `Res.*` accessors then work exactly like on every other platform —
drawables, strings (`values/*.xml`), fonts, raw files:

```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.compose.components:components-resources:<cmp-version>")
}
```

`compose.resources { packageOfResClass = … }` is honoured; source-set
overrides follow the default hierarchy (a `mingwX64Main` resource beats a
`commonMain` one).

## Notes

- The substituted klib version defaults to the plugin's own version (both ship
  from the same tag). Override with `composeDesktopNative.version=<x>` in
  `gradle.properties` when mixing releases.
- Match `<cmp-version>` to the CMP release the port's vendored sources track
  (see `scripts/compose-fork/compose.properties` at the release tag) —
  substitution replaces whatever version you request on native, but your
  jvm/android targets resolve the official artifacts at the version you write.
- Requires Gradle 8.8+ when applied in settings (`gradle.lifecycle.beforeProject`).
- App windowing/main-loop (`com.bitsycore.compose.sdl:window`) and the icon
  font module (`material-symbols`) are the port's own APIs — depend on them
  directly; no substitution involved.
