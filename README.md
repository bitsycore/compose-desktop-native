[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)

# ComposeNativeSDL3

A **Compose Desktop subset** running on **Kotlin/Native** with **SDL3** as the rendering backend.
No JVM required — compiles to native binaries for macOS, Linux, and Windows.

## Architecture

- **Compose Runtime** (JetBrains) — `@Composable`, `State`, `Recomposer`, `Applier`
- **UI Core** — `LayoutNode`, `Modifier`, `Constraints`, `Canvas`, `DrawScope`
- **Foundation** — `Box`, `Row`, `Column`, `Text`, `Spacer`, padding, background, clickable
- **Material** — `Button`, `Surface`, `Theme`, `Colors`
- **SDL3 Backend** — windowing, 2D rendering, input events, text (via SDL3\_ttf)

## Prerequisites

| Platform | SDL3 | SDL3\_ttf |
|---|---|---|
| macOS | `brew install sdl3` | `brew install sdl3_ttf` |
| Linux | `sudo apt install libsdl3-dev` | `sudo apt install libsdl3-ttf-dev` |
| Windows | [SDL3 releases](https://github.com/libsdl-org/SDL/releases) → `C:\SDL3` | [SDL\_ttf releases](https://github.com/libsdl-org/SDL_ttf/releases) → `C:\SDL3_ttf` |

## Build & Run

```bash
# macOS (Apple Silicon)
./gradlew runDebugExecutableMacosArm64

# macOS (Intel)
./gradlew runDebugExecutableMacosX64

# Linux x64
./gradlew runDebugExecutableLinuxX64

# Windows
.\gradlew.bat runDebugExecutableMingwX64
```

## License

Licensed under [CC0](https://creativecommons.org/publicdomain/zero/1.0/deed.en).