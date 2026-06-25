[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)

# ComposeNativeSDL3

A subset of **Compose Desktop** running on **Kotlin/Native** with **SDL3** for
windowing and input — **no JVM**. Compiles to native binaries for macOS
(arm64), Linux (x64/arm64) and Windows (mingwX64).

Rendering is pluggable behind one `RenderBackend`:

- **Skia** (via Skiko) on macOS + Linux — Metal / OpenGL / CPU raster
- **SDL3** (`SDL3_ttf` + `SDL_RenderGeometry`) on Windows, and on macOS/Linux
  with `-Prenderer=sdl3`

## demo — widget & feature showcase

<img src="screenshots/demo.png" width="100%" alt="ComposeNativeSDL3 demo" />

`:demo` is a full tour of the re-implemented Compose + Material surface — 30+
sidebar screens covering buttons, text fields, layout, modifiers, shapes,
images, state & recomposition, scrolling & lazy lists, dialogs, icons, canvas,
graphics layers, custom layout, animation and gestures.

```bash
./gradlew :demo:runDebugExecutableMacosArm64      # macOS  (Skia / Metal)
./gradlew :demo:runDebugExecutableLinuxX64        # Linux  (Skia / OpenGL)
gradlew.bat :demo:runDebugExecutableMingwX64      # Windows (SDL3)
```

CLI: `--gpu=…`, `--screen=<Name>` (one screen, no sidebar),
`--screenshot=out.bmp --frames=N`, `--width=W --height=H`.

## apidemo — API Manager

<img src="screenshots/apidemo.png" width="100%" alt="ComposeNativeSDL3 API Manager" />

`:apidemo` is a Postman-style REST client built entirely on the library:
request collections (**packs**, nested sub-packs, linked copies), a
**Session → Pack → Request** inheritance ladder for variables / headers /
query params / client certs, syntax-highlighted JSON / XML / YAML / HTML body
editors, a response viewer with timing, size and TLS-chain inspection, mTLS
client certificates, drag-and-drop tree management, request history and
file-based sessions.

```bash
./gradlew :apidemo:runDebugExecutableMacosArm64
./gradlew :apidemo:runDebugExecutableLinuxX64
gradlew.bat :apidemo:runDebugExecutableMingwX64
```

Add `-Prenderer=sdl3` on macOS/Linux to drop Skiko and use the pure-SDL3
renderer everywhere.

## Minimal app

```kotlin
import androidx.compose.material.Text
import com.compose.desktop.native.nativeComposeWindow

fun main() = nativeComposeWindow(title = "Hello") {
    Text("Hello from ComposeNativeSDL3")
}
```

The lambda runs with a `ComposeWindowScope` receiver exposing
`window: ComposeNativeWindow` (`setTitle` / `setSize` / `minimize` /
`maximize` / `setFullscreen` / `close` / …); the same handle is reachable from
any nested composable via `LocalComposeNativeWindow.current`.

## Building

- **macOS:** `brew install sdl3` (Skia is the default; Skiko klibs come from
  Maven).
- **Linux:** `sudo apt install libsdl3-dev`.
- **Windows:** SDL3, SDL3_ttf, SDL3_image and FreeType are built from source as
  **static** libraries by `tools/build-all.sh` into the gitignored in-repo
  `libs/`, then linked into the executable — the Windows distributable is just
  `<app>.exe` + `data.kres`, no runtime DLLs.

See [CLAUDE.md](CLAUDE.md) for the full module layout and build details.

## License

[MIT](LICENSE.md).
