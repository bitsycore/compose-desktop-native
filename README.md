[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)

# ComposeNativeSDL3

A subset of **Compose Desktop** running on **Kotlin/Native** with **SDL3**
as the windowing / input layer. No JVM. Compiles to native binaries for
macOS (arm64), Linux (x64/arm64), and Windows (mingwX64).

Two renderers behind a shared `RenderBackend` interface:

- **Skia** (via Skiko) on macOS + Linux — Metal / OpenGL / CPU raster
- **SDL3** (via SDL3_ttf + `SDL_RenderGeometry`) on Windows, and on
  macOS/Linux when you opt in with `-Prenderer=sdl3`

## Prerequisites

| Platform | SDL3 | SDL3_ttf |
|---|---|---|
| macOS | `brew install sdl3` | `brew install sdl3_ttf` *(only needed when `-Prenderer=sdl3`)* |
| Linux | `sudo apt install libsdl3-dev` | `sudo apt install libsdl3-ttf-dev` *(only when `-Prenderer=sdl3`)* |
| Windows | [SDL3 release zip](https://github.com/libsdl-org/SDL/releases) → `C:\SDL3` | [SDL3_ttf release zip](https://github.com/libsdl-org/SDL_ttf/releases) → `C:\SDL3_ttf` |

On Windows the runtime `SDL3.dll` and `SDL3_ttf.dll` must be findable at
launch — copy them next to the built `.exe` or put `C:\SDL3\bin` /
`C:\SDL3_ttf\bin` on `PATH`.

## Build & Run

```bash
# macOS Apple Silicon (default = Skia + Metal)
./gradlew :demo:runDebugExecutableMacosArm64

# Linux x64 (default = Skia + OpenGL)
./gradlew :demo:runDebugExecutableLinuxX64

# Windows (SDL3 renderer)
gradlew.bat :demo:runDebugExecutableMingwX64

# Drop Skiko entirely on macOS/Linux — use the SDL3 renderer
./gradlew :demo:runDebugExecutableMacosArm64 -Prenderer=sdl3
```

The demo CLI accepts:

```
--gpu=auto | none | skia.metal | skia.opengl |
      sdl3 | sdl3.metal | sdl3.opengl | sdl3.vulkan | sdl3.d3d11 | sdl3.d3d12
--screen=Window | Buttons | TextField | ...   (single screen, no sidebar)
--screenshot=path.bmp --frames=N              (capture and quit)
--width=W --height=H
```

## Minimal app

```kotlin
import androidx.compose.material.Text
import sdl3backend.composeWindow

fun main() = composeWindow(title = "Hello") {
    Text("Hello from ComposeNativeSDL3")
}
```

The lambda gets a `ComposeWindowScope` receiver with `window:
ComposeNativeWindow` for `setTitle / setSize / minimize / maximize /
setFullscreen / close / …`. The same handle is also available to any
nested composable via `LocalComposeNativeWindow.current`.

## License

[MIT](LICENSE.md).
