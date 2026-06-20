# CLAUDE.md

Guidance for Claude Code working in this repository. This is the only
context the next agent will have — read it first.

## Project Overview

**ComposeNativeSDL3** — a Kotlin/Native subset of Compose Desktop running
on SDL3. No JVM. Compiles to native binaries for macOS (arm64), Linux
(x64/arm64), and Windows (mingwX64).

The project re-implements just enough of `androidx.compose.*` to host
Composable hierarchies, measure/place them as a layout tree, dispatch
SDL3 input events into the tree, and render them. Rendering is pluggable:

- **Skia** (via Skiko klibs) on macOS + Linux — Metal / OpenGL / CPU raster
- **SDL3** (SDL3_ttf + `SDL_RenderGeometry`) on Windows, and on
  macOS/Linux when `-Prenderer=sdl3` is set

## Module Layout

- `compose-sdl3/` — the library
- `demo/` — example app (`Main.kt` → `composeWindow { App() }`) with a
  sidebar-driven showcase covering every API surface

### Source set hierarchy (compose-sdl3)

```
commonMain                ← pure Kotlin, no SDL: androidx.compose.* re-impl
└── nativeMain            ← SDL3 + ComposeWindow + RenderBackend interface
    ├── sdl3RendererMain  ← pure-SDL3 renderer (Sdl3Renderer, Sdl3TextRenderer)
    │   ├── mingwMain        (always uses SDL3)
    │   ├── macosMain        (only when -Prenderer=sdl3)
    │   └── linuxMain        (only when -Prenderer=sdl3)
    └── skiaMain         ← Skia bridges + Skiko dep (default mode)
        ├── macosMain        (default)
        └── linuxMain        (default)
```

`-Prenderer=sdl3` (gradle property) swaps macOS/Linux off `skiaMain` and
onto `sdl3RendererMain`. It also turns on the `sdl3_ttf` cinterop for
those targets.

The `macosSkiaMain` / `macosSdl3Main` (and Linux counterparts) directories
each hold the platform-specific `actual fun preferredGpuMode()` —
included as srcDirs conditionally by `build.gradle.kts`.

### Key files (start here)

- `compose-sdl3/src/nativeMain/kotlin/sdl3backend/ComposeWindow.kt` —
  main loop, recomposer lifecycle, event dispatch, hooks up the
  `RenderBackend` and the `ComposeNativeWindow` handle.
- `compose-sdl3/src/nativeMain/kotlin/sdl3backend/ComposeNativeWindow.kt`
  — the per-window handle (title / size / minimize / fullscreen /
  rendererName / close), CompositionLocal + receiver scope.
- `compose-sdl3/src/nativeMain/kotlin/sdl3backend/RenderBackend.kt` —
  interface, `expect fun makeRenderBackend(...)`.
- `compose-sdl3/src/nativeMain/kotlin/sdl3backend/GpuMode.kt` —
  the sealed-hierarchy renderer/driver picker.
- `compose-sdl3/src/skiaMain/.../SkiaRenderBackend.kt` — Skia actual.
- `compose-sdl3/src/sdl3RendererMain/.../Sdl3RenderBackend.kt` — SDL3 actual.
- `compose-sdl3/src/commonMain/kotlin/androidx/compose/ui/node/LayoutNode.kt`
  — layout tree, hit testing.
- `compose-sdl3/src/commonMain/kotlin/androidx/compose/ui/Modifier.kt`
  — modifier elements the renderer reads.
- `demo/src/nativeMain/kotlin/Main.kt` — sidebar demo with --gpu /
  --screen / --screenshot CLI.

## Build / Run

```bash
# macOS Apple Silicon, default Skia (Metal on macOS, OpenGL on Linux)
./gradlew :demo:runDebugExecutableMacosArm64

# Linux x64
./gradlew :demo:runDebugExecutableLinuxX64

# Windows
gradlew.bat :demo:runDebugExecutableMingwX64

# Skiko-free build on macOS/Linux — SDL3 renderer everywhere
./gradlew :demo:runDebugExecutableMacosArm64 -Prenderer=sdl3
```

## System dependencies

### macOS (default Skia build)

`brew install sdl3` is enough. `sdl3_ttf` only needed if you set
`-Prenderer=sdl3`. Skiko klibs come from Maven (`org.jetbrains.skiko:0.148.2`).

### Linux (default Skia build)

`sudo apt install libsdl3-dev` is enough. Same caveat for SDL3_ttf.

### Windows (mingwX64 — always uses SDL3 + SDL3_ttf)

Both libraries must be extracted to fixed paths the cinterop `.def`
files reference:

```
C:\SDL3\
  include\SDL3\*.h
  lib\libSDL3.dll.a
  bin\SDL3.dll

C:\SDL3_ttf\
  include\SDL3_ttf\*.h
  lib\libSDL3_ttf.dll.a
  bin\SDL3_ttf.dll
```

Download the **MinGW** development releases (not MSVC) from:
- <https://github.com/libsdl-org/SDL/releases>
- <https://github.com/libsdl-org/SDL_ttf/releases>

Inside each release zip the right directory is `x86_64-w64-mingw32/`
(or `i686-w64-mingw32/` for 32-bit, which we don't target). Either copy
that subtree directly to `C:\SDL3` (so the `include/` and `lib/` dirs
land at `C:\SDL3\include` etc.), or adjust the paths in
`compose-sdl3/src/nativeInterop/cinterop/sdl3.def` and `sdl3_ttf.def`.

The runtime DLLs (`SDL3.dll`, `SDL3_ttf.dll`) must be findable at launch:

- Copy them next to the built `.kexe` (under
  `demo/build/bin/mingwX64/debugExecutable/`), or
- Put `C:\SDL3\bin` and `C:\SDL3_ttf\bin` on `PATH`.

The `demo/build.gradle.kts` copies `Roboto-Regular.ttf` next to every
binary already; you don't need to install fonts.

### Linker errors on Windows

If `cinteropSdl3MingwX64` fails with "cannot find -lSDL3", verify:
1. `C:\SDL3\lib\libSDL3.dll.a` exists (the MinGW import library).
2. The `lib\` directory under the SDL release zip has `libSDL3.dll.a`
   — some older releases name it `SDL3.lib` (MSVC) which Kotlin/Native
   can't link. Use the MinGW package.
3. If you put SDL3 somewhere else, edit `compilerOpts.mingw_x64` and
   `linkerOpts.mingw_x64` in the two `.def` files.

### Header errors on Windows

`fatal error: 'SDL3/SDL.h' file not found` → `C:\SDL3\include\SDL3\`
doesn't contain the headers. The MinGW zip's `include/` directory has
them; copy that whole tree.

## Architecture Notes

### Compose runtime integration (ComposeWindow.kt)

- Composables build a `LayoutNode` tree via `NodeApplier`
  (`AbstractApplier<LayoutNode>`).
- `Recomposer.runRecomposeAndApplyChanges()` runs as a child coroutine
  of the `runBlocking(frameClock)` in `composeWindow`.
- `SDL3FrameClock` is a `MonotonicFrameClock` driven by
  `frameCh.trySend(...)` once per main-loop iteration.
- **Snapshot apply notifications**: `Snapshot.sendApplyNotifications()`
  must be called each frame (or via a `registerGlobalWriteObserver`
  handler) — without it, `mutableStateOf` writes from click handlers
  never reach the recomposer.

### Layout pipeline (per frame)

1. Poll SDL events → `AppEvent` list (Quit / Pointer / Key / TextInput /
   WindowResized / MouseWheel).
2. Dispatch pointer events (hit-test the tree, walk up for
   click/hover/press/drag handlers).
3. `frameClock.sendFrame()` + `yield()` so the recomposer applies
   changes.
4. `backend.updateWindowSize()` then
   `renderBackend.ensureSize(pixelWidth, pixelHeight)`.
5. `rootNode.measure(Constraints.fixed(windowWidth, windowHeight))`
   then `rootNode.place(0, 0)` — both in **logical points**.
6. `renderBackend.beginFrame(pixelDensity)` — DPR-scales the canvas /
   SDL renderer so logical layout maps 1:1 to physical pixels.
7. `renderBackend.draw(rootNode)`.
8. `onFrame(renderBackend, frameIndex)` hook (used by --screenshot).
9. `renderBackend.endFrame()`.

### HiDPI

The window has `SDL_WINDOW_HIGH_PIXEL_DENSITY` set. `SDL3Backend`
tracks both **logical** (`windowWidth/Height`, from `SDL_GetWindowSize`)
and **physical** (`pixelWidth/Height`, from `SDL_GetWindowSizeInPixels`)
sizes. `pixelDensity = pixelWidth / windowWidth` is `2.0` on Retina,
`1.0` on standard displays.

- Layout always runs in **logical points** — `Modifier.size(64.dp)`
  means 64 logical points.
- Render backends allocate their back buffer in **physical pixels**.
- The shared scale step happens in `RenderBackend.beginFrame(dpr)`:
  Skia does `canvas.scale(dpr, dpr)`; SDL3 does `SDL_SetRenderScale(dpr,
  dpr)` *and* opens TTF fonts at `fontSize * dpr` so text textures land
  1:1 on physical pixels.

### Modifier system

`Modifier` is a small interface with `foldIn` / `foldOut`. Elements are
data classes (`PaddingModifier`, `BackgroundModifier`, `BorderModifier`,
`SizeModifier`, `ClickableModifier`, etc., all in `ui/Modifier.kt`).
Layout pulls values via `foldIn` (e.g. `node.paddingLeft`). Renderers
walk the same chain to draw background, border, and apply clip.

### Text measurement

The shared `TextMeasurePolicy` calls into a `TextMeasurer` interface
that the active `RenderBackend` provides (`SkiaTextRenderer` →
`vFont.getStringGlyphs() + vFont.getWidths().sum()`; `Sdl3TextRenderer`
→ `TTF_GetStringSize` with `length=0` so it strlens the UTF-8). Both
opt-in to subpixel measurement so layout matches drawn glyphs.

### GpuMode (sealed)

```kotlin
sealed class GpuMode {
    object Auto                 // resolve per-target (preferredGpuMode())
    object None                 // Skia CPU raster
    sealed class Skia {
        object OpenGL           // Skia + SDL3 GL context (Linux default)
        object Metal            // Skia + CAMetalLayer (macOS default)
    }
    sealed class Sdl3 {
        abstract val driverHint: String?  // for SDL_HINT_RENDER_DRIVER
        object Auto             // SDL3 picks its own driver (Windows default)
        object Software, OpenGL, Metal, Vulkan, D3D11, D3D12
    }
}
```

`makeRenderBackend(sdl, mode)` is `expect`/`actual` per source set.
Each actual rejects unsupported modes with an error so the user gets a
clear "Skia.Metal isn't available in this build" instead of silent
fallback.

### ComposeNativeWindow

Reactive handle on the window. Available two ways:

```kotlin
composeWindow(...) {
    // `this: ComposeWindowScope` — root scope
    window.setTitle("Hello")
}

@Composable
fun Deep() {
    val w = LocalComposeNativeWindow.current
    Text("Renderer: ${w.rendererName}")     // recomposes when it changes
    Button(onClick = { w.toggleFullscreen() }) { ... }
}
```

State (snapshot-backed): `width`, `height`, `pixelWidth`,
`pixelHeight`, `title`, `isMinimized`, `isMaximized`, `isFullscreen`,
`pixelDensity`, `gpuMode`, `rendererName`. Actions: `setTitle`,
`setSize`, `minimize`, `maximize`, `restore`, `setFullscreen`,
`toggleFullscreen`, `raise`, `close`.

`rendererName` calls `SDL_GetRendererName` for SDL3 modes so the live
driver string ("metal", "opengl", "direct3d11", …) shows what
`Sdl3.Auto` resolved to.

## Conventions

Follow `~/.claude/CLAUDE.md`:

- Constants: `k` prefix camelCase (`kSomeConstant`).
- Local variables: `v` prefix (`vParts`, `vResult`).
- Function parameters: `in` prefix (`inPath`).
- Class member fields in Java only: `f` prefix.
- Indent with tabs (this repo uses 4-space indent in existing code —
  match what's already in each file rather than reflowing).
- Section headers in Kotlin:
  - Major sections (file-level / between classes):
    ```kotlin
    // ==================
    // MARK: Name
    // ==================
    ```
  - In-function smaller scope:
    ```kotlin
    // ============
    //  Name
    ```
- Concise function-level comments only where the name is not
  self-documenting; avoid line-by-line commentary.
- Kotlin standard syntax — no Spirtech internal rules apply here.

## Common Pitfalls

- **State changes don't update the UI** — verify
  `Snapshot.sendApplyNotifications()` is being called each frame in
  `ComposeWindow.kt`. The recomposer is otherwise idle.
- **Text appears clipped on the right** — measurement must match the
  glyphs the renderer actually paints. In Skia this means
  `getStringGlyphs() + getWidths().sum()` (not `measureTextWidth`,
  which is broken in Skiko 0.148.2 Native). In SDL3 it means passing
  `0` to `TTF_GetStringSize / TTF_RenderText_Blended` so it strlens
  the UTF-8 — passing `inText.length` truncates non-ASCII strings.
- **Retina blur** — make sure the back buffer is at pixel size and the
  canvas / `SDL_SetRenderScale` applies the DPR. Text textures via
  SDL3_ttf must be opened at `fontSize * dpr` or they upsample blurry.
- **Row with `Arrangement.spacedBy(...)`** — `RowMeasurePolicy` adds
  inter-child gaps to its reported width; if you change it, make sure
  centering in a parent still works.
- **`-Prenderer=sdl3` mode** — Skia source set isn't compiled; the
  `SkiaMetalBridge.kt` file under `src/macosSkiaMain/` lives there
  precisely so it's excluded from the SDL3 build. Don't move it back
  into `macosMain/` or the SDL3 build will fail.
- **mingwX64 cross-compile from macOS / Linux fails** at the cinterop
  step — it can't find `C:\SDL3\include\SDL3\SDL.h`. That's expected;
  build the Windows target on Windows.
- **Configuration cache and `-Prenderer=`** — Gradle caches the
  configuration; toggling the renderer property may not invalidate the
  cache. Delete `.gradle/configuration-cache/` between switches if you
  see weird "couldn't find sdl3_ttf" errors.

## Useful gradle tricks

- `--args="--gpu=sdl3.opengl --screen=Buttons"` to pass CLI to the demo.
- `--info` to see the cinterop classpath / include paths actually used.
- `--rerun-tasks` to force-rebuild after toggling `-Prenderer=`.

## License

MIT — see [LICENSE](LICENSE.md).
