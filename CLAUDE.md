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

Local module names drop the `compose-desktop-native-` / `compose-desktop-`
prefix for terseness; publication artifact IDs add it back (so consumers see
`compose-desktop-native-core` etc. on the dependency line). All modules share
the `com.compose.desktop.native` Kotlin package; the re-implemented Compose
APIs keep their upstream `androidx.compose.*` names, in `core/commonMain`.

- `core/` (publishes as `compose-desktop-native-core`) — renderer-agnostic
  base: the `androidx.compose.foundation` / `.ui` / `.animation` re-impl,
  `RenderBackend` interface, `GpuMode`, `SDL3Backend`, window / clipboard /
  event / resource IO, and the bundled default font. Owns the `sdl3`
  cinterop. **No Material code** — Material widgets live in `:material`.
- `material/` (publishes as `compose-desktop-native-material`) — Material
  widgets re-implemented on top of `:core` (Button / Text / MaterialTheme /
  Surface / TextField / Slider / Switch / Checkbox / Radio / Chip / Card /
  Dialog / DropdownMenu / SegmentedButton / Snackbar / Tooltip /
  ProgressIndicator). Apps that only want the foundation+ui base without
  Material can skip pulling this in.
- `renderer-sdl3/` (publishes as `compose-desktop-native-renderer-sdl3`) —
  pure-SDL3 renderer (+ `sdl3_ttf`, `sdl3_image`, `freetype` cinterops).
  Exposes `createRenderBackend(...)` / `rendererPreferredGpuMode()`. All
  four native targets.
- `renderer-skia/` (publishes as `compose-desktop-native-renderer-skia`) —
  Skia/Skiko renderer (Metal / OpenGL / CPU bridges). Same two functions.
  **macOS + Linux only** — Skiko publishes no mingwX64 artifact.
- `window/` (publishes as `compose-desktop-native`) — what apps depend on.
  Owns `composeWindow()` and selects a renderer per target by depending on
  exactly one renderer module: mingwX64 → sdl3 (always); macOS/Linux →
  skia, or sdl3 under `-Prenderer=sdl3`. Re-exports `:core` + `:material`
  via `api`.
- `material-symbols/{outlined,rounded,sharp}/` (publishes as
  `compose-desktop-material-symbols-{outlined,rounded,sharp}`) — Material
  Symbols icon-font modules. Each ships its variable font (downloaded at
  build time from Google) and a single `MaterialSymbols{Style}` object
  with a `@Composable operator fun invoke(...)` that auto-installs the
  font on first call.
- `demo/` — flagship example app: a full showcase of the re-implemented
  Compose + Material widgets and features (30+ sidebar screens; `--gpu` /
  `--screen` / `--screenshot` CLI). Depends on `:window` + the three
  `material-symbols` styles. Not published (app only).
- `apidemo/` — a Postman-style **API Manager** built on the library: HTTP
  request collections (packs / nested sub-packs / linked copies), a
  Session → Pack → Request inheritance ladder for variables / headers /
  query-params / client-certs (innermost wins, with per-level overrides),
  syntax-highlighted JSON/XML/YAML/HTML body editors, a response viewer with
  TLS-chain inspection, mTLS client certs, drag-and-drop tree, request history
  and file-based sessions. Networking is Ktor's Curl engine (one bundled
  static libcurl per target — Schannel on Windows, OpenSSL on macOS/Linux) +
  kotlinx.serialization + okio. Not published (app only).

### How renderer selection works

Both renderer modules expose identically-signed `createRenderBackend` /
`rendererPreferredGpuMode` in the same package. `:window` has a thin
per-target `expect`/`actual` (`makeRenderBackend` / `preferredGpuMode`,
in `RenderBackendFactory.{kt,mingw.kt,macos.kt,linux.kt}`) whose actuals just
forward to those functions — and since the build links **exactly one** renderer
module per target, the call resolves unambiguously ("include one" selection).
No conditional `srcDir`s; each renderer module has its own per-OS source sets
(`mingwMain` / `macosArm64Main` / `linuxMain`) for `rendererPreferredGpuMode`.
`SDL3Backend` only exposes `COpaquePointer` (never `sdl3.*` types), so each
module declares its own `sdl3` cinterop and reinterprets — no cross-module
cinterop export needed.

### Key files (start here)

- `window/src/nativeMain/.../ComposeWindow.kt` — main loop, recomposer
  lifecycle, event dispatch; calls the per-target makeRenderBackend.
- `core/src/nativeMain/.../ComposeNativeWindow.kt` — per-window handle
  (title / size / fullscreen / rendererName / close), CompositionLocal + scope.
- `core/src/nativeMain/.../RenderBackend.kt` — the interface.
- `core/src/nativeMain/.../GpuMode.kt` — the sealed renderer/driver picker.
- `renderer-skia/.../renderer/skia/SkiaRenderBackend.kt` (+ `RenderBackendFactory.skia.kt`).
- `renderer-sdl3/.../renderer/sdl/Sdl3RenderBackend.kt` (+ `RenderBackendFactory.sdl.kt`).
- `renderer-sdl3/.../renderer/sdl/FreeTypeIcons.kt` — variable-font axis
  rasterisation for the SDL3 path (SDL3_ttf has no axis API; we go to
  FreeType directly for icon families).
- `core/src/commonMain/.../ui/node/LayoutNode.kt` — layout tree, hit testing.
- `core/src/commonMain/.../ui/Modifier.kt` — modifier elements the renderer reads.
- `demo/src/nativeMain/kotlin/Main.kt` — sidebar demo with --gpu / --screen / --screenshot CLI.
- `apidemo/src/nativeMain/kotlin/Main.kt` — the API Manager (`App()` + every
  UI panel). Siblings: `Model.kt` (serializable packs / requests / certs),
  `Persist.kt` (session + app-state IO), `Http.kt` (Ktor request runner),
  `CurlMtls.kt` (client-cert / TLS-chain via libcurl), `Packs.kt`
  (import/export), `SyntaxHighlight.kt` (body/format tokenizers).

## Build / Run

```bash
# macOS Apple Silicon, default Skia (Metal on macOS, OpenGL on Linux)
./gradlew :demo:runDebugExecutableMacosArm64
./gradlew :apidemo:runDebugExecutableMacosArm64

# Linux x64
./gradlew :demo:runDebugExecutableLinuxX64
./gradlew :apidemo:runDebugExecutableLinuxX64

# Windows
gradlew.bat :demo:runDebugExecutableMingwX64
gradlew.bat :apidemo:runDebugExecutableMingwX64

# Skiko-free build on macOS/Linux — SDL3 renderer everywhere
./gradlew :demo:runDebugExecutableMacosArm64 -Prenderer=sdl3
```

## System dependencies

### macOS (default Skia build)

`brew install sdl3` is enough. `sdl3_ttf` only needed if you set
`-Prenderer=sdl3`. Skiko klibs come from Maven (`org.jetbrains.skiko:0.150.0`).

### Linux (default Skia build)

`sudo apt install libsdl3-dev` is enough. Same caveat for SDL3_ttf.

### Windows (mingwX64 — always uses SDL3 + SDL3_ttf + SDL3_image + FreeType)

On Windows these four libraries (plus the image codecs, and a static libcurl
for `:apidemo`) are **linked statically into the executable** — the
distributable is just `<app>.exe` + `data.kres`, **no runtime DLLs**. They are
not downloaded as prebuilt binaries; they are **built from source as static
libs** by the scripts in `tools/` into a gitignored, in-repo `libs/` folder:

```bash
# From Git Bash on Windows. Needs: git, cmake, a mingw-w64 gcc/g++ on PATH,
# plus curl + python (to fetch ninja when absent). Idempotent — re-runnable.
tools/build-all.sh
```

`build-all.sh` runs `build-freetype.sh` → `build-sdl3.sh` →
`build-sdl3-image.sh` → `build-sdl3-ttf.sh` in that order (later libs link the
earlier ones), installing each as a static `.a` under:

```
libs/FreeType/{include,lib}
libs/SDL3/{include,lib}
libs/SDL3_image/{include,lib}      # vendored PNG/JPG/SVG/WEBP
libs/SDL3_ttf/{include,lib}        # carries our variable-font axis patch
```

How the build wires that up:

- **Include paths** — each module's `build.gradle.kts` injects a host-side
  `-I<repo>/libs/SDL3/include` into the cinterop on Windows (`vHostSdlInclude`);
  the `.def` files themselves only carry the macOS/Linux system paths
  (`/opt/homebrew`, `/usr/include`) and a `# mingw_x64: static-linked` note.
  `sdl3.def` is duplicated in every module that touches SDL — `core`,
  `renderer-sdl3`, `renderer-skia`, `window` (each
  `src/nativeInterop/cinterop/`); `sdl3_ttf.def` + `sdl3_image.def` +
  `freetype.def` live only in `renderer-sdl3`.
- **Linking** — `demo/build.gradle.kts` and `apidemo/build.gradle.kts` add the
  static `linkerOpts` for mingwX64: `-L<repo>/libs/.../lib`, a
  `-Wl,--start-group … --end-group` around the circular static deps
  (`ttf ↔ freetype ↔ SDL3`, `image ↔ png/webp/zlib`), the Windows system libs
  SDL3 needs when static, and `-Wl,--gc-sections -Wl,-s` to shrink + strip.
  `:apidemo` also links `-lcrypt32` for its mTLS cert-store path.

`data.kres` (STORED, no compression) is bundled next to every binary by the
per-(variant × target) `copy*ComposeResources*` Zip tasks — drawables, files,
the default `font/Roboto-Regular.ttf` the text renderers load at startup, plus
each depended `material-symbols` style font. Pass `-PbundleDefaultFont=false`
to ship without the bundled Roboto (the renderers then fall back to a system
font).

FreeType is used by the SDL3 renderer for variable-font axis support on
Material Symbols icons (SDL3_ttf has no axis-set API; we go directly to
FreeType for those families — see `FreeTypeIcons.kt`).

### Build errors on Windows

`fatal error: 'SDL3/SDL.h' file not found` or `cannot find -lSDL3` at the
cinterop / link step means `libs/` is empty or incomplete — run
`tools/build-all.sh` (from Git Bash) to build the static deps into `libs/`,
then rebuild.

## Native dependency tooling

- `tools/` — Bash scripts (`build-all.sh`, `build-sdl3.sh`,
  `build-sdl3-ttf.sh`, `build-sdl3-image.sh`, `build-freetype.sh`) that build
  the Windows native deps from source as static libs into `libs/`. Not Gradle
  modules.
- `libs/` — gitignored output of the above (per-host static libs +
  headers); referenced by the build via `${rootDir}/libs`.
- `scripts/subset-material-symbols.py` — scans an app's Kotlin sources for
  `MaterialSymbols.<Name>` usages and writes a codepoint list so the icon
  fonts can be hb-subset down to only the glyphs used (`-PsubsetIcons=true`).

## Architecture Notes

### Resources / images (composeResources)

Drop assets under `demo/src/nativeMain/composeResources/` — `drawable/` for
images (png / jpg / svg / android `<vector>` xml), `files/` for raw bytes.
The `generateComposeResAccessors` Gradle task scans that tree and emits typed
`Res.drawable.<name>` (→ `Painter`) and `Res.files.<name>` (→ path string for
`Res.readBytes`). `:core` keeps its default font under
`core/src/nativeMain/composeResources/font/`; both roots merge into
a single `<exe>/data.kres` at build time (STORED, no compression —
see `-PbundleDefaultFont`). At runtime `ResourceIO.kt` opens that archive
once via `SDL_GetBasePath()`, parses its central directory, and serves each
entry with an `fseek` + `fread` on demand — no whole-archive memory load.
The official Compose resources runtime can't be used here — its generated
code needs real Compose UI (`Painter` / `ImageBitmap` / `ImageVector`), which
this repo re-implements — so this is a self-contained stand-in.

Image drawing mirrors text exactly: a commonMain `ImageLoader` interface +
`currentImageLoader` global (set in `ComposeWindow` from
`renderBackend.imageLoader`), a `painter` leaf on `LayoutNode`, and each
renderer paints it. Decoding is per-backend and cached by path —
`SkiaImageCache` (`Image.makeFromEncoded` / `SVGDOM` on raw bytes) and
`Sdl3ImageCache` (`IMG_Load_IO` / `IMG_LoadSVG_IO` from an in-memory
`SDL_IOFromConstMem` stream). SVG and Android `<vector>` XML both flow
through `AndroidVectorToSvg` → SVG → rasterise. `ContentScale` (Fit / Crop /
FillBounds / Inside / None) and `alpha` apply at draw time. Intrinsic pixel
size is treated as logical points by the layout pass.

Fonts follow the same byte-based path: Skia uses `FontMgr.makeFromData`,
SDL3_ttf uses `TTF_OpenFontIO`. The bundled font bytes are copied once into
a `nativeHeap` allocation that lives for the renderer's lifetime so the
SDL_IOStream's reads stay valid for every opened size (closed in `destroy()`).

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
- **Comment style**: use plain `/* ... */` block comments and `//` line
  comments — **never** KDoc/Javadoc `/** ... */`. In a multi-line `/* */`
  block, do **not** start continuation lines with `*`; the comment text
  begins each line directly. (This applies to Kotlin and Java here; Swift/C
  in other projects use `/** */`.)
- Kotlin standard syntax — no Spirtech internal rules apply here.
- **Exception for the re-implemented Compose API** (everything under
  `androidx.compose.*`): public symbols and parameters use **official
  Compose / standard-Kotlin names** (`modifier`, `content`, `onClick`,
  `degrees`, `fraction`, `targetValue`, …) — **never** the `in`/`v`/`k`/`f`
  prefix convention. The prefixes apply only to project-internal code
  (`com.compose.desktop.native.*`, `private`/`internal` helpers, local
  variables, constants). See **Compose API Fidelity** below.

## Compose API Fidelity (mirroring official Compose Multiplatform)

The `androidx.compose.*` packages here are a **re-implementation** whose
**public surface must track official Compose Multiplatform as closely as
possible** (same package, same names, same parameter order/defaults/return
types), written in standard Kotlin. Keep this section authoritative; when in
doubt, the official ABI wins.

### Ground truth & the runtime boundary

- **Official source** = `JetBrains/compose-multiplatform-core`. Keep a local
  clone (this repo was audited against one at `C:/Dev/cmp-ref`; sparse +
  shallow:
  `git clone --filter=blob:none --depth 1 https://github.com/JetBrains/compose-multiplatform-core`
  then `git sparse-checkout set compose/foundation compose/ui compose/animation`).
  The authoritative public **Kotlin/Native ABI** is each module's
  `<module>/api/<module>.klib.api` — grep it for exact members, return types,
  and which params have defaults (shown as `= ...`). Read the `commonMain`
  `.kt` source for exact **parameter names + default values** (the klib shows
  types, not names).
- **The runtime is OFFICIAL**: the build depends on
  `org.jetbrains.compose.runtime:runtime` (1.11.1) + the official compose
  compiler plugin. **Never re-implement or "fix" anything in
  `androidx.compose.runtime.*`** — `@Composable`, `remember`,
  `mutableStateOf`, `derivedStateOf`, `ComposeNode`, `AbstractApplier`,
  `Recomposer`, `Snapshot`, `CompositionLocal`, `MonotonicFrameClock` are the
  genuine artifact and are correct by definition.
- Only `androidx.compose.foundation*`, `androidx.compose.animation*`, and
  `androidx.compose.ui*` are re-implemented (they had to be redone to compile
  for native targets) — those are what must mirror official.

### Three fix strategies (classify every symbol)

- **pull-verbatim** — pure data/math types with **no platform / `expect`-`actual`
  / internal-node dependency** (`Dp`, `Sp`/`TextUnit`, `Color`, `Offset`,
  `Size`, `Rect`, `CornerRadius`, `Constraints`, `IntOffset`/`IntSize`,
  `TextRange`, `TextAlign`, `FontWeight`, easing curves, `Spring` constants…).
  The right fix is to **copy the official `commonMain` `.kt` verbatim**. The
  only prerequisite is porting the `androidx.compose.ui.util` packing/`lerp`
  helpers (`packFloats`/`packInts`/`unpackFloat*`/`unpackInt*`/`fastRoundToInt`/
  `lerp`/`toStringAsFixed`) and a `requirePrecondition` shim into core first.
- **surface-match** — types bound to this project's custom layout / render /
  event pipeline (`Modifier` elements, `LayoutNode`, `MeasurePolicy`,
  `DrawScope`, `Painter`, `Shape`/`Outline`, pointer/key events, `Arrangement`).
  Official `commonMain` for these is welded to the internal Compose engine and
  cannot be pulled. **Keep the simplified custom impl; align only the public
  signature** (names, order, defaults, return type, package).
- **intentional-custom** — deliberate stand-ins with no official common
  equivalent (`androidx.compose.ui.res` resource system, `ImageLoader`,
  `TextMeasurer`, `FontFamily.Named`, `Modifier.onSecondaryClick/onMiddleClick/
  onTextInput/onPressed/onDrag`, `SplitPane`, icon-font helpers). Keep, but
  **mark `internal` where possible and never present them as upstream API**.

### Layering — `androidx.compose.*` is a faithful mirror only

Anything that **diverges** from official Compose (no upstream equivalent, or a
public shape that can't match) does **not** live in `androidx.compose.*`. It
goes in the project's `com.compose.desktop.native.*` "layer on top" (where
`SplitPane` / `IconFont` already live), or is made `internal`. Applied: the
non-official `Modifier` extensions (`onSecondaryClick` / `onMiddleClick` /
`pressable` / `onTextInput` / `onPressed` / `onDrag` / `translate`) and
`rememberMutableInteractionSource` now live in
`com.compose.desktop.native.modifier`; the official `MutableInteractionSource()`
factory stays in `androidx.compose.foundation.interaction`.

Irreducible exceptions (custom code that must stay in `androidx.compose.*` for
now — mark each non-official in its doc comment): the render-bridge
`Modifier.Element` data classes + `Outline` / `PathCommand` /
`GraphicsLayerModifier` / `TextMeasurer` / `currentImageLoader` (public for
cross-module renderer access — can't be `internal`); `FontFamily.Named` (a
`sealed` subclass, load-bearing for icon fonts); the `androidx.compose.ui.res`
resource system (`Res` / `ImageLoader` / `ResourceKind` /
`painterResource(path, kind)` — generated-accessor-facing, and the one-arg
`painterResource` overlaps official).

### Universal rules

1. **Names**: public symbols and parameters use official/standard-Kotlin
   names — no `in`/`v`/`k`/`f` prefixes (`degrees` not `inDegrees`,
   `targetValue` not `inTarget`, `other` not `inOther`, `fraction` not
   `inFraction`).
2. **Package**: declare each symbol in its official package (see map below).
3. **Signature**: match parameter order, names, defaults and return type
   exactly. Layout/widget composables are `modifier` first → behavior params →
   **`content` last** with its scoped receiver and **no default**; for the
   "no content" case add a separate content-less overload (e.g.
   `Box(modifier)`, `Spacer(modifier)`) rather than `content: … = {}`.
4. **Companions**: expose **only** the official companion members/constants —
   no extras (no `Dp.Zero`/`Sp.Zero`/`TransformOrigin.TopLeft`/
   `Brush.solidColor`). Construct via official idioms (`0.dp`,
   `TransformOrigin(0f, 0f)`, `SolidColor(color)`).
5. **Value-class types**: official types that wrap a packed `Int`/`Long`
   (`Color`, `Offset`, `Size`, `TextRange`, `TextAlign`, `TextOverflow`,
   `FontStyle`, `FontWeight`, `StrokeCap`, `TileMode`, `TransformOrigin`,
   `KeyEventType`, `PointerEventType`, `PointerButton`, `IntOffset`/`IntSize`)
   are modeled as `value class` + a `Companion` of named constants
   (+ `values()`/`valueOf()` where official has them) — **never** as `enum` or
   `data class`. A plain float/int-pair `data class` is tolerable as a reduced
   impl **only** if construction syntax + every official member match; prefer
   pulling the real value class.
6. **No invented public API** in official packages. Project-only helpers live
   under `com.compose.desktop.native.*`, or are `internal`, or are explicitly
   documented as non-official.
7. **Render-bridge glue stays internal**: the `Modifier.Element` data classes,
   `Outline`, `Path.commands`/`PathCommand`, `GraphicsLayerModifier`,
   `TextMeasurer`, `currentImageLoader`, native event backings — keep
   `internal` (or per-module if cross-module renderers must read them) and
   expose only the official extension/factory in front of them.
8. **Additive-first**: prefer *adding* missing official params/members (even
   when no-op or ignored by this renderer — accept-and-ignore) over reshaping.
   Keep unimplemented official params present and defaulted so upstream call
   sites compile.

### Package map (official placement — APPLIED, keep here)

These symbols now live in their official packages (relocated in the fidelity
pass). Keep them there; do **not** move them back:

| Symbol(s) | Official package |
| --- | --- |
| `FontWeight`, `FontStyle`, `FontFamily`, `FontVariation` | `androidx.compose.ui.text.font` |
| `RoundedCornerShape`, `CircleShape` | `androidx.compose.foundation.shape` (`RectangleShape` stays in `ui.graphics`) |
| `Modifier.clip` | `androidx.compose.ui.draw` |
| `Modifier.zIndex` (+ `ZIndexModifier`) | `androidx.compose.ui` |
| `Modifier.onSizeChanged`, `Modifier.onGloballyPositioned` | `androidx.compose.ui.layout` |
| `Modifier.onKeyEvent` | `androidx.compose.ui.input.key` |
| `animateColorAsState` | `androidx.compose.animation` (the rest of `animate*AsState` stay in `.core`) |
| `TextRange.coerceIn` | top-level extension in `ui.text` (not a member) |

Still mis-placed (not yet moved): `InfiniteTransition.animateColor` (should be
in `androidx.compose.animation`).

### Per-area cheat-sheet

- **ui.unit** — `Dp.Infinity == Float.POSITIVE_INFINITY` (not `MAX_VALUE`);
  `Dp.compareTo` returns `0` when either side is `NaN`. Companions: `Dp`
  {`Hairline`,`Infinity`,`Unspecified`} (no `Zero`); `IntOffset` {`Zero`,`Max`};
  `IntSize`/`DpSize`/`DpOffset` {`Zero`,`Unspecified`}. Provide the standard
  `Dp` helpers (`lerp`/`min`/`max`/`coerceIn`/`coerceAtLeast`/`coerceAtMost`/
  `isSpecified`/`isUnspecified`/`isFinite`/`takeOrElse`, scalar
  `Int|Float|Double.times(Dp)`). Constrain via official `Constraints`
  extensions (`constrain(IntSize)`, `constrainWidth/Height`, `isSatisfiedBy`,
  `offset`) — not a bespoke `constrain(Int,Int)`. Text sizes are `TextUnit`
  (`.sp`/`.em`); `Sp` is a documented simplification.
- **ui.geometry** — `Offset`/`Size`/`CornerRadius` are value classes over a
  packed `Long`; the 2-arg forms are top-level `inline fun` factories.
  Complete the operator set + companions (`Offset.{Zero,Infinite,Unspecified}`,
  `Size.{Zero,Unspecified}`) + `lerp`. `min/maxDimension` use `abs`. Add the
  missing types (`Rect`, `MutableRect`, `CornerRadius`, `RoundRect`) when
  needed.
- **ui.graphics** — `Color`/`StrokeCap`/`StrokeJoin`/`TileMode`/
  `TransformOrigin` are value classes + companion constants + top-level
  factory funcs. `Shape.createOutline(size, layoutDirection, density): Outline`
  with `Outline.Rectangle(Rect)`/`Rounded(RoundRect)`/`Generic(Path)`. `Path`
  comes from top-level `fun Path()`, member names official, `Rect`-based
  `addRect/addOval`. Gradients via `Brush.linearGradient/verticalGradient/
  horizontalGradient/radialGradient/sweepGradient` (List<Color> + vararg
  `Pair<Float,Color>` overloads); `SolidColor.value`. `graphicsLayer` uses the
  official param order (`scaleX, scaleY, alpha, translationX, translationY,
  shadowElevation, rotationX/Y/Z, …, transformOrigin, shape, clip`) + a
  `GraphicsLayerScope` lambda overload. `draw*` keep official param order with
  `alpha`, `style`, `colorFilter`, `blendMode` trailing.
- **ui.text** — see package map for font types. `TextAlign`/`TextOverflow`/
  `FontStyle`/`FontWeight`/`TextRange` are value classes with full constant
  sets. Sizes are `TextUnit`. `Range` is nested `AnnotatedString.Range<T>`
  (with optional `tag`); `AnnotatedString : CharSequence` + `plus`. Keep
  `SpanStyle`/`ParagraphStyle`/`TextStyle` field **order** = official subset;
  keep `merge()`/`plus()`. `TextMeasurer`/`WrappedText`/
  `TextRendererCapabilities` are intentional-custom render glue.
- **ui core (Modifier/Alignment/draw/focus)** — `Alignment.align` and
  `Alignment.Horizontal.align` take a trailing `layoutDirection: LayoutDirection`
  (Vertical does not); keep `Horizontal.plus(Vertical)`/`Vertical.plus(Horizontal)`.
  `Modifier` adds `all`/`any` predicates. `FocusRequester` public surface is
  `requestFocus()`/`freeFocus(): Boolean` (+ `Companion.Default`); hide
  `attachedNode`/`focusManager`. Element data classes are internal glue.
- **ui.layout / ui.node** — `ContentScale` is an `interface` with
  `computeScaleFactor(srcSize, dstSize): ScaleFactor` + companion
  `Fit/Crop/FillBounds/FillHeight/FillWidth/Inside/None` and sibling
  `FixedScale`. `Placeable` nests `PlacementScope`; placement via
  `Placeable.PlacementScope.place(x, y, zIndex = 0f)`, `placeAt` protected.
  `MeasureScope.layout(width, height, alignmentLines = emptyMap()) { … }` with a
  `Placeable.PlacementScope` receiver. `MeasurePolicy` params are
  `measurables`/`constraints`. Keep the internal node-measure policy a distinct
  `internal` name (don't collide with the public `ui.layout.MeasurePolicy`).
- **ui.input / platform / window** — `KeyEvent`/`PointerEvent` are not flat
  data classes: expose state via value-class types (`Key`, `KeyEventType`,
  `PointerEventType`, `PointerButton`, `PointerId`) and extension props
  (`KeyEvent.key/type/utf16CodePoint/isCtrlPressed/…`;
  `PointerEvent.changes/type/buttons`). `KeyEventType.KeyDown/KeyUp/Unknown`
  (not `Down`/`Up`); `PointerButton.Primary/Secondary/Tertiary/Back/Forward`
  (not `Middle`); `PointerInputChange.isConsumed`. `awaitPointerEvent(pass =
  PointerEventPass.Main): PointerEvent`. No public `KeyModifiers`, no mutable
  `currentClipboard`. `Popup(alignment = Alignment.TopStart, offset =
  IntOffset(0,0), onDismissRequest: (() -> Unit)? = null, properties =
  PopupProperties(), content)` + a `PopupPositionProvider` overload — not
  `modal`/`scrimColor`.
- **foundation** — `clickable(enabled, onClickLabel, role, onClick)` and
  `hoverable`/`focusable(interactionSource, enabled)` emit interactions; expose
  state via `collectIs*AsState`. `Interaction` is a non-sealed interface;
  `PressInteraction`/`HoverInteraction`/`FocusInteraction` are interfaces whose
  nested `Press`(`pressPosition`)/`Enter`/`Focus`… implement them.
  `MutableInteractionSource.tryEmit: Boolean` + suspend `emit`;
  `InteractionSource.interactions: Flow<Interaction>`. `BorderStroke` wraps a
  `Brush` (+ top-level `BorderStroke(width, color)`). `BasicText`/
  `BasicTextField` take `style`/`textStyle: TextStyle` + `cursorBrush: Brush`,
  not flattened `color`/`fontSize`/`cursorColor`. `Image(painter,
  contentDescription, modifier, alignment = Alignment.Center, contentScale =
  ContentScale.Fit, alpha = DefaultAlpha, colorFilter = null)`. `verticalScroll`/
  `horizontalScroll(state, enabled, flingBehavior, reverseScrolling)`. Gesture
  detectors match official param order (`detectTapGestures(onDoubleTap,
  onLongPress, onPress, onTap)`).
- **foundation.layout** — `RowScope`/`ColumnScope`/`BoxScope` are
  `@LayoutScopeMarker interface`s with instance objects as receivers
  (`RowScope`: `weight`/`align`/`alignBy`×2/`alignByBaseline`; `ColumnScope`:
  `weight`/`align`/`alignBy`×2; `BoxScope`: `align`/`matchParentSize`).
  `Arrangement.Horizontal/Vertical` keep the official
  `fun Density.arrange(totalSize, sizes: IntArray, [layoutDirection,]
  outPositions: IntArray)` + `val spacing: Dp`. Dp defaults are `0.dp` /
  `Dp.Unspecified` (never `Dp.Zero`). `fillMax*(fraction)` must scale;
  `required*` must override constraints.
- **foundation.lazy** — entry points declare the full defaulted param set in
  order (`modifier, state, contentPadding, reverseLayout, arrangement/alignment,
  flingBehavior, userScrollEnabled, …, content` last). `item(key, contentType,
  content)`/`items(count, key, contentType, itemContent)` are the only
  interface members, with `LazyItemScope`/`LazyGridItemScope` receivers;
  `List<T>`/`Array<T>` `items`/`itemsIndexed` are top-level `inline` extensions.
  `LazyListState` exposes `firstVisibleItemIndex`/`firstVisibleItemScrollOffset`
  + `scrollToItem`/`animateScrollToItem` (not `ScrollState`-style
  `value`/`maxValue`). `GridCells` is an interface with
  `fun Density.calculateCrossAxisCellSizes(...)`; `Fixed`/`Adaptive`/`FixedSize`
  ctor params are `private`.
- **animation.core** — all `animate*AsState`/`Animatable` default spec is
  `spring()` (never `tween()`); `animateFloatAsState`/`spring()`/`SpringSpec`
  carry `visibilityThreshold`. `TweenSpec`/`SnapSpec` field is `delay` while
  the `tween()`/`snap()` factories take `delayMillis`. `repeatable`/
  `infiniteRepeatable` accept the duration-based spec family + `initialStartOffset`.
  `Spring` keeps `StiffnessMediumLow = 400f` and
  `DefaultDisplacementThreshold = 0.01f`. `AnimationEndReason` =
  {`BoundReached`,`Finished`}. The `AnimationVector`/`TwoWayConverter`/
  `Vectorized*` pipeline is intentionally absent — the sealed `AnimationSpec` +
  lerp-lambda design is the documented stand-in; don't fake the converter APIs.
- **ui.res** — intentional custom stand-in for `org.jetbrains.compose.resources`
  (the official `ui.res` is platform-only and not in the common ABI). Keep
  `Res`/`ImageLoader`/`ResourceKind`/`AndroidVectorToSvg` as documented glue,
  prefer `internal`; only `painterResource(resourcePath: String): Painter`
  genuinely overlaps official (use that param name).

### Known-diverging surface (still TODO — runtime-critical, compile-only here)

These reshapes ripple into the renderers / event pipeline / every call site
and can only be **compile**-verified on Windows (no runtime check), so they're
left for a focused pass — do each one alone, then build **and** run a demo
`--screenshot` to confirm nothing broke at runtime:

- `KeyEvent`/`PointerEvent`/`PointerEventType`/`PointerButton`/`KeyEventType`
  value-class + extension-prop redesign (touches `SDL3EventMapper`,
  `ComposeWindow`, `BasicTextField`, apidemo — the live input path).
- `BasicText`/`BasicTextField` → `style: TextStyle` / `cursorBrush: Brush`
  (touches material `Text`/`TextField` + every call site; note the project
  deviations `fontFamily: String?` and `fontVariationSettings` for icon fonts
  don't map cleanly to official `TextStyle`).
- `Color` → `value class` over packed `ULong` and `Sp` → `TextUnit`:
  **representation-only** gaps — the current `data class` / `Sp` already match
  the official *construction + member* surface (rule 5), so these are low
  priority; the repack is pervasive and packing-correctness can't be unit-tested
  here. Keep `r8`/`g8`/`b8`/`lighten`/`darken`/`blend` documented as non-official.
- `Shape.createOutline(size, layoutDirection, density)` + official `Outline`
  (`Rectangle(Rect)`/`Rounded(RoundRect)`/`Generic(Path)`) — currently
  `Shape.outline(Int,Int)` + pixel-int `Outline`; both renderers read it.
- `Arrangement.Horizontal/Vertical` `Density`-receiver + `IntArray` + `spacing:
  Dp`; `RowScope`/`ColumnScope`/`BoxScope` `object`→`@LayoutScopeMarker interface`;
  `Placeable.place`/`PlacementScope`.

**Done in the fidelity pass** (no longer diverging): all `in`/`v`/`f`-prefixed
public params renamed; the package moves above; `ContentScale` enum→interface
(+`ScaleFactor`/`FixedScale`); `Popup` → official `alignment`/`offset`/
`onDismissRequest?`/`properties: PopupProperties` (+ scrim moved into `Dialog`);
`Dp`/`Offset`/`Size`/`Constraints`/`IntOffset` operator+companion+helper surface;
`spring()` defaults + `Spring` constants + `visibilityThreshold`;
`FontWeight: Comparable` + `W100..W900`; `lerp(Color,…)`.

### Verifying fidelity

Re-grep the official ABI any time you add/rename a public symbol, e.g.
`grep -n "compose.ui.unit/Dp" C:/Dev/cmp-ref/compose/ui/ui-unit/api/ui-unit.klib.api`.
Build with `./gradlew :apidemo:compileKotlinMingwX64 :demo:compileKotlinMingwX64`
(compile-only on Windows verifies the whole common+native+mingw graph,
including `:material`/`:window`/`:renderer-sdl3`, without a full static link;
`:renderer-skia` is not in the mingw graph — grep it manually for any renamed
symbol).

## Common Pitfalls

- **State changes don't update the UI** — verify
  `Snapshot.sendApplyNotifications()` is being called each frame in
  `ComposeWindow.kt`. The recomposer is otherwise idle.
- **Text appears clipped on the right** — measurement must match the
  glyphs the renderer actually paints. In Skia this means
  `getStringGlyphs() + getWidths().sum()` (not `measureTextWidth`,
  which is broken in Skiko 0.150.0 Native). In SDL3 it means passing
  `0` to `TTF_GetStringSize / TTF_RenderText_Blended` so it strlens
  the UTF-8 — passing `inText.length` truncates non-ASCII strings.
- **Retina blur** — make sure the back buffer is at pixel size and the
  canvas / `SDL_SetRenderScale` applies the DPR. Text textures via
  SDL3_ttf must be opened at `fontSize * dpr` or they upsample blurry.
- **Row with `Arrangement.spacedBy(...)`** — `RowMeasurePolicy` adds
  inter-child gaps to its reported width; if you change it, make sure
  centering in a parent still works.
- **`-Prenderer=sdl3` mode** — flips `:window`'s macOS/Linux
  dependency from `:renderer-skia` to `:renderer-sdl3`; the Skia
  module just isn't on the dependency graph (not compiled, Skiko not pulled).
  Don't add a hard dependency on `:renderer-skia` from a shared source set,
  or mingwX64 (which has no Skia module) won't link.
- **mingwX64 cross-compile from macOS / Linux fails** at the cinterop
  step — it can't find `<repo>/libs/SDL3/include/SDL3/SDL.h` (and the static
  libs under `libs/` are built per-host by `tools/` anyway). That's expected;
  build the Windows target on Windows.
- **Configuration cache and `-Prenderer=`** — Gradle caches the
  configuration; toggling the renderer property may not invalidate the
  cache. Delete `.gradle/configuration-cache/` between switches if you
  see weird "couldn't find sdl3_ttf" errors.

## Useful gradle tricks

- `--args="--gpu=sdl3.opengl --screen=Buttons"` to pass CLI to the demo.
- `--info` to see the cinterop classpath / include paths actually used.
- `--rerun-tasks` to force-rebuild after toggling `-Prenderer=`.
- `-PsubsetIcons=true` (default on, see `gradle.properties`) —
  `scripts/subset-material-symbols.py` scans an app's sources for
  `MaterialSymbols.<Name>` uses and hb-subsets each bundled icon font to only
  the glyphs actually used (needs `python3` + `hb-subset` on PATH; falls back
  to the full font if `hb-subset` is absent).

## License

MIT — see [LICENSE](LICENSE.md).
