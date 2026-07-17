[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE.md)

# Compose Desktop Native

Run [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)
as a native binary, with no JVM. Compose Desktop Native compiles to a single
executable for macOS (arm64), Linux (x64/arm64), and Windows (mingwX64), using
[SDL3](https://libsdl.org) for windowing and input.

<img src="screenshots/demo.png" width="100%" alt="Compose Desktop Native demo" />

## How it works

- **The runtime is the real one.** Composition, snapshots, and the recomposer
  are the official `org.jetbrains.compose.runtime` klibs from Maven, never
  reimplemented.
- **The UI layers are vendored upstream.** `androidx.compose.ui`, `foundation`,
  `animation`, and `material3` are copied from Compose Multiplatform verbatim
  wherever they compile as is, with project code filling in only the native glue.
- **Rendering is pluggable** behind one `RenderBackend`: Skia (via Skiko) on
  macOS and Linux, and a from-scratch SDL3 renderer (`SDL3_ttf` +
  `SDL_RenderGeometry`) on Windows, or anywhere with `-Prenderer=sdl3`.
- **The platform is SDL3.** Windowing, input, audio, filesystem, file dialogs,
  and clipboard all go through SDL3, so one code path covers every OS.
- **No runtime dependencies.** SDL3 and its codecs are built as static
  libraries and linked in. A distributable is just the executable plus a
  `data.kres` resource bundle.

| Platform | Gradle target | Default renderer |
|----------|---------------|------------------|
| macOS arm64 | `macosArm64` | Skia (Metal) |
| Linux x64 / arm64 | `linuxX64` / `linuxArm64` | Skia (OpenGL) |
| Windows | `mingwX64` | SDL3 |

## Quickstart

### A window

```kotlin
import androidx.compose.material3.Text
import com.compose.sdl.nativeComposeWindow

fun main() = nativeComposeWindow(title = "Hello") {
    Text("Hello from Compose Desktop Native")
}
```

The content lambda runs with a `ComposeWindowScope` receiver that exposes
`window: ComposeNativeWindow` (`setTitle`, `setSize`, `minimize`, `maximize`,
`setFullscreen`, `close`). The same handle is reachable from any nested
composable via `LocalComposeNativeWindow.current`.

For multiple windows, wrap them in `nativeComposeApp`, where the set of
`Window(...)` calls is state driven (Compose Desktop style):

```kotlin
fun main() = nativeComposeApp {
    Window(title = "Main", onCloseRequest = ::exitApplication) { /* ... */ }
    if (showInspector) {
        Window(title = "Inspector", onCloseRequest = { showInspector = false }) { /* ... */ }
    }
}
```

Each window gets its own `Lifecycle`, `ViewModelStore`, and `SavedStateRegistry`
owners, driven by real SDL focus and visibility events, so ViewModels and
saved state behave as they do on Android.

### Building in this repo

```kotlin
commonMain.dependencies {
    implementation(project(":window"))           // window shell + main loop
    implementation(project(":material3"))        // Material 3 widgets
    implementation(project(":material-symbols")) // icon-font composables (optional)
}
```

### Building from your own project: the bridge plugin

The klibs publish to GitHub Packages under `com.bitsycore.compose.sdl:*`. Apply
the bridge Gradle plugin once, declare the **official** Compose Multiplatform
coordinates, and the plugin swaps in the port's klibs on native desktop targets
while android, jvm, iOS, and wasm keep resolving the official artifacts.

```kotlin
// build.gradle.kts, official coordinates everywhere
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.bitsycore.compose-desktop-native.bridge") version "<release>"
}

commonMain.dependencies {
    // The plugin exposes the exact Compose versions the port tracks, so you
    // never hand-match them (material3 is versioned separately upstream).
    implementation("org.jetbrains.compose.runtime:runtime:${composeDesktopNative.composeRuntime}")
    implementation("org.jetbrains.compose.ui:ui:${composeDesktopNative.compose}")
    implementation("org.jetbrains.compose.foundation:foundation:${composeDesktopNative.compose}")
    implementation("org.jetbrains.compose.material3:material3:${composeDesktopNative.composeMaterial3}")
}
```

Repositories, credentials, and version pinning:
[gradle-plugin/compose-desktop-native-bridge/README.md](gradle-plugin/compose-desktop-native-bridge/README.md).

For a complete project that applies the bridge and builds one shared UI for
Android, JVM, and native desktop, see the example repo:
[bitsycore/compose-desktop-native-bridge-example](https://github.com/bitsycore/compose-desktop-native-bridge-example).

## Sample apps

Both live in `commonMain` and also build for stock JVM Compose Desktop, which
serves as the visual and behavioural reference: any difference against the
native build is a porting bug.

**`:demo`** is a tour of the re-implemented Compose and Material 3 surface,
30-plus screens covering text, layout, shapes, images, state, lazy lists,
dialogs, canvas, graphics layers, animation, and gestures.

```bash
./gradlew :demo:runDebugExecutableMacosArm64   # macOS (Skia / Metal)
./gradlew :demo:runDebugExecutableLinuxX64     # Linux (Skia / OpenGL)
gradlew.bat :demo:runDebugExecutableMingwX64   # Windows (SDL3)
./gradlew :demo:run                            # JVM Compose Desktop (reference)
```

**`:apidemo`** is a Postman-style REST client built entirely on the library:
request collections, a session inheritance ladder, syntax-highlighted body
editors, a response viewer with timing and TLS-chain inspection, and mTLS
client certificates.

<img src="screenshots/apidemo.png" width="100%" alt="Compose Desktop Native API Manager" />

```bash
./gradlew :apidemo:runDebugExecutableMacosArm64
./gradlew :apidemo:run                         # JVM Compose Desktop (reference)
```

Pass `-Prenderer=sdl3` on macOS or Linux to drop Skiko and use the pure SDL3
renderer everywhere.

## Modules

One Gradle module per upstream Compose artifact, mirroring the upstream
`compose/` tree. The renderer and cinterops live in `:ui`; `:window` is the SDL
integration layer. `:material-symbols`, `:components-resources`, and
`:navigation3-ui` are project or vendored modules that the official Maven
artifacts do not cover for Kotlin/Native desktop.

Most of the androidx architecture stack (lifecycle, viewmodel, navigation3,
savedstate, navigationevent) ships real Kotlin/Native desktop klibs and runs on
the port unmodified. The full module map, dependency graph, and the list of
compatible artifacts are in [CLAUDE.md](CLAUDE.md).

## Building

Build the native libraries once per machine, then build any app target:

```bash
python3 scripts/build-sdl/build-all.py         # SDL3, SDL3_ttf, SDL3_image, FreeType (static)
./gradlew :demo:runDebugExecutableMacosArm64
```

Every host needs `git`, `cmake`, and Python 3. Per host: macOS needs the Xcode
command line tools; Linux needs gcc/g++ and the X11 / Wayland / audio dev
headers; Windows needs a mingw-w64 g++ on PATH. See [TOOLING.md](TOOLING.md) for
the full build and verification workflow.

## Documentation

- [TOOLING.md](TOOLING.md): building the native libs, vendoring, and verification.
- [CLAUDE.md](CLAUDE.md): architecture, source-set hierarchy, vendoring rules,
  density flow, and a per-area file map.
- [gradle-plugin/compose-desktop-native-bridge](gradle-plugin/compose-desktop-native-bridge/README.md):
  consuming the port from another build.

## License

[MIT](LICENSE.md).
