# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project Overview

**ComposeNativeSDL3** — a subset of Compose Desktop running on Kotlin/Native with SDL3 as the rendering backend. No JVM. Compiles to native binaries for macOS (arm64), Linux (x64/arm64) and Windows (mingwX64).

The project re-implements just enough of `androidx.compose.*` to host Composable hierarchies, measure/place them as a layout tree, dispatch SDL3 input events into the tree, and render with SDL3 + SDL3_ttf.

## Module Layout

- `compose-sdl3/` — the library
  - `commonMain` — `androidx.compose.*` re-implementations: `Modifier`, `LayoutNode`, `NodeApplier`, `Box`/`Row`/`Column`, `Text`, `Button`, `Theme`, `Color`, `Dp`, `Constraints`, `Alignment`, `Arrangement`, `PointerEvent`, `KeyEvent`. Pure Kotlin, no SDL.
  - `nativeMain/sdl3backend` — SDL3 bindings glue: `SDL3Backend` (window/renderer), `SDL3Renderer` (walks the `LayoutNode` tree and draws), `SDL3TextRenderer` (TTF fonts + glyph atlas), `SDL3EventMapper` (SDL_Event → AppEvent), `ComposeWindow` (entry point — runs the main loop, Recomposer, frame clock).
  - `nativeInterop/cinterop` — `sdl3.def` and `sdl3_ttf.def` cinterop bindings.
- `demo/` — example app (`Main.kt`) that calls `composeWindow { App() }`.

## Build / Run

```bash
# macOS Apple Silicon
./gradlew runDebugExecutableMacosArm64

# Linux x64
./gradlew runDebugExecutableLinuxX64

# Windows
.\gradlew.bat runDebugExecutableMingwX64
```

System libraries required (see README):

- macOS: `brew install sdl3 sdl3_ttf`
- Linux: `libsdl3-dev`, `libsdl3-ttf-dev`
- Windows: SDL3 + SDL3_ttf release zips extracted to `C:\SDL3` and `C:\SDL3_ttf`

## Architecture Notes

### Compose runtime integration

- Composables build a `LayoutNode` tree via `NodeApplier` (`AbstractApplier<LayoutNode>`).
- `Recomposer.runRecomposeAndApplyChanges()` runs as a child coroutine of the `runBlocking` in `composeWindow`.
- `SDL3FrameClock` is a `MonotonicFrameClock` driven by `frameCh.trySend(...)` once per main-loop iteration.
- **Snapshot apply notifications**: `Snapshot.sendApplyNotifications()` MUST be called each frame (or via a `registerGlobalWriteObserver` handler) — without it, `mutableStateOf` writes never reach the recomposer and the UI silently stops updating. See `ComposeWindow.kt`.

### Layout pipeline (per frame)

1. Poll SDL events → `AppEvent` list (Quit / Pointer / Key / WindowResized).
2. Dispatch pointer events: `rootNode.hitTest(x, y)` then walk up the tree via `findClickHandler()`.
3. `frameClock.sendFrame()` + `yield()` so the recomposer can apply changes.
4. `rootNode.measure(Constraints.fixed(w, h))` then `rootNode.place(0, 0)`.
5. `SDL3Renderer.draw(rootNode)` — recursive traversal, drawing background → border → text → children.

### Modifier system

`Modifier` is a small interface with `foldIn` / `foldOut`. Modifier elements are data classes (`PaddingModifier`, `BackgroundModifier`, `BorderModifier`, `SizeModifier`, `ClickableModifier`). Layout passes use `foldIn` to extract values (e.g. `node.paddingLeft`, `node.paddingTop`). Renderer also uses `foldIn` to find drawing modifiers.

### Text

- `BasicText` → `LayoutNode` with `text`, `textColor`, `fontSize`.
- `TextMeasurePolicy` uses the real TTF font metrics via a `TextMeasurer` interface (set by `composeWindow` once SDL3_ttf is initialised) so that `Box`/`Row`/`Column` alignment matches the rendered glyphs. If unset, falls back to a `charW = fontSize * 0.6` estimate.

## Conventions

Follow the global user style (`~/.claude/CLAUDE.md`):

- Constants: `k` prefix camelCase (`kSomeConstant`).
- Local variables: `v` prefix (`vParts`, `vResult`).
- Function parameters: `in` prefix (`inPath`).
- Class member fields in Java only: `f` prefix.
- Indent with tabs (this repo uses 4-space indent in existing code — match what's already in each file rather than reflowing).
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
- Concise function-level comments only where the name is not self-documenting; avoid line-by-line commentary.
- Kotlin standard syntax — no Spirtech internal rules apply here.

## Common Pitfalls

- **State changes don't update the UI** — verify `Snapshot.sendApplyNotifications()` is being called each frame in `ComposeWindow.kt`. The recomposer is otherwise idle.
- **Text appears misaligned inside buttons / centered containers** — `TextMeasurePolicy` must use the real TTF metric. If text measurement falls back to the `0.6 * fontSize` estimate, narrow characters (spaces, punctuation) produce massively oversized layout boxes.
- **Row with `Arrangement.spacedBy(...)`** — `RowMeasurePolicy` must add the inter-child gaps to its reported width, otherwise the Row reports a smaller width than the placed-children actually span and centering in a parent goes off-axis.
- `runBlocking(frameClock)` makes the frame clock the dispatcher — do not block the main loop with synchronous I/O or recomposition stalls.
- Cinterop is per-target — adding a new SDL function requires nothing extra (headers are pulled in via `headerFilter`), but adding a new system library means another `.def` file.

## Useful Files to Open First

- `compose-sdl3/src/nativeMain/kotlin/sdl3backend/ComposeWindow.kt` — main loop, recomposer lifecycle, event dispatch.
- `compose-sdl3/src/commonMain/kotlin/androidx/compose/ui/node/LayoutNode.kt` — the layout tree, hit testing, click handler walking.
- `compose-sdl3/src/commonMain/kotlin/androidx/compose/ui/Modifier.kt` — modifier elements (the data classes the renderer reads).
- `demo/src/nativeMain/kotlin/Main.kt` — minimal usage example.

## License

MIT — see [LICENSE](LICENSE.md).
