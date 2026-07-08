# CLAUDE.md

Guidance for Claude Code working in this repository. This is the primary
context — read it first, then look at the files it points to.

## What this project is

**ComposeNativeSDL3** — a Kotlin/Native port of Compose Multiplatform running
on SDL3, no JVM. Compiles to native binaries for macOS (arm64), Linux
(x64/arm64), Windows (mingwX64).

Rendering is pluggable behind one `RenderBackend`:

- **Skia** (via Skiko klibs) on macOS + Linux — Metal / OpenGL / CPU raster.
- **SDL3** (`SDL3_ttf` + `SDL_RenderGeometry`) on Windows, and on macOS/Linux
  when `-Prenderer=sdl3` is passed.

Windowing, input, audio, filesystem access, and the OS-integration surface
(file dialogs, clipboard, "open in Finder/Explorer"…) all go through
**SDL3**. The runtime (`androidx.compose.runtime.*`: composition, snapshots,
recomposer, `mutableStateOf`, `remember`, …) is the **official
`org.jetbrains.compose.runtime` klibs from Maven** — this project only
re-implements the layers on top (`androidx.compose.ui.*`, `.foundation.*`,
`.animation.*`, `.material3.*`).

## Module layout

Library modules mirror upstream Compose Multiplatform's `compose/` tree.
Only `:window` is the SDL integration layer ("Compose SDL" — SDL3 main loop)
and lives under `compose/sdl/`.

One Gradle module per upstream artifact; the directory mirrors the upstream
`compose/` path, the gradle path is kept short (redirected via `projectDir`).

```
compose/
├── ui/
│   ├── ui/                          → :ui        — androidx.compose.ui.* (ui + ui-graphics + ui-text) +
│   │                                               com.compose.sdl.* — cinterops + BOTH renderer pipelines live
│   │                                               here. ui-graphics/ui-text can't split off: their Canvas /
│   │                                               Paragraph `expect`s are the renderers' `actual`s (same-module).
│   ├── ui-util/                     → :ui-util       — androidx.compose.ui.util.* (+ Experimental/InternalComposeUiApi)
│   ├── ui-geometry/                 → :ui-geometry   — androidx.compose.ui.geometry.*
│   ├── ui-unit/                     → :ui-unit       — androidx.compose.ui.unit.*
│   └── ui-backhandler/              → :ui-backhandler — androidx.compose.ui.backhandler.*
├── animation/
│   ├── animation-core/              → :animation-core     — androidx.compose.animation.core.*
│   ├── animation/                   → :animation          — androidx.compose.animation.* (non-core)
│   └── animation-graphics/          → :animation-graphics — androidx.compose.animation.graphics.*
├── foundation/
│   ├── foundation/                  → :foundation       — androidx.compose.foundation.*
│   └── foundation-layout/           → :foundation-layout — androidx.compose.foundation.layout.*
├── material3/
│   └── material3/                   → :material3   — androidx.compose.material3.*
├── material/
│   └── material-ripple/             → :material-ripple — androidx.compose.material.ripple.*
└── sdl/                             ("Compose SDL" — project modules, not upstream CMP artifacts)
    ├── window/                      → :window     — nativeComposeApp { Window(...) {} } multi-window
    │                                               shell + SDL3 main loop; nativeComposeWindow() wrapper
    ├── material-symbols/            → :material-symbols — codepoints + all three style objects
    │                                               (Outlined / Rounded / Sharp). Apps get one dep;
    │                                               the consumer Zip task bundles only the fonts used.
    └── navigation3-ui/              → :navigation3-ui — a minimal NavDisplay reimpl. Navigation 3's
                                                    runtime (androidx.navigation3:navigation3-runtime +
                                                    lifecycle-viewmodel-navigation3) ARE real Maven KMP
                                                    artifacts (all targets, api-exposed by :ui), but
                                                    navigation3-ui/NavDisplay has no K/N desktop build
                                                    (its non-Android upstream is NotImplemented), so the
                                                    display is reimplemented here on this project's
                                                    AnimatedContent.

demo/                → :demo      — flagship showcase app (30+ screens)
apidemo/             → :apidemo   — Postman-style REST API manager
tools/               → vendor-sync + Windows static-lib build scripts (bash + python)
libs/                → gitignored per-host static SDL3 / SDL3_ttf / SDL3_image / FreeType
                      output of tools/build-*.sh on Windows
```

Module PATHS stay short (`:ui`, `:foundation`, `:window`, …) —
`settings.gradle.kts` redirects `projectDir` for each so build files across
the repo stay terse. `androidx.collection` is a plain Maven dependency
(`androidx.collection:collection`), not a module — same as other simple
androidx KMP libs.

## Dependency graph

```
:ui  ←  :animation-core  ←  :animation          ←  :foundation  ←  :material3  ← :demo, :apidemo
:ui  ←  :foundation-layout  ←──────────────────────┘   ↑              ↑
                        :material-ripple  ←────────────┘──────────────┘
:foundation, :animation-core  ←  :window ;  :foundation, :material3  ←  :material-symbols
```

All edges are `api`, so a consumer of `:foundation` / `:material3` transitively
sees the split modules. Full DAG: `:ui-util → collection`; `:ui-geometry → :ui-util`;
`:ui-unit → :ui-geometry, :ui-util`; `:ui-backhandler → :ui-util, navigationevent`;
`:ui → :ui-util, :ui-geometry, :ui-unit, :ui-backhandler`; `:animation-core → :ui`;
`:foundation-layout → :ui`; `:animation → :animation-core, :foundation-layout`;
`:foundation → :animation, :foundation-layout, :animation-core, :ui`;
`:material-ripple → :foundation, :animation-core`;
`:material3 → :foundation, :material-ripple, :animation-core, :foundation-layout`.

`:ui` is the renderer/cinterop hub (the ui-graphics + ui-text `expect`s resolve to
its SDL/Skia renderer `actual`s). The pure lower artifacts (`:ui-util`,
`:ui-geometry`, `:ui-unit`, `:ui-backhandler`) are split out below it. Everything
above `:ui` can only touch renderer / cinterop internals via its public surface.
`:window` depends on `:ui` + `:foundation` (needs `LazyList`-style scaffolding to
install the popup / scaffold layer at the composition root).

## Vendoring philosophy — read this before writing any `androidx.compose.*` code

**Prefer vendoring verbatim from upstream Compose Multiplatform over hand-rolling anything.**

Every module that ships `androidx.compose.*` code carries a
`<module>/compose-fork.txt` manifest — a text file with one line per file:
`<upstream-path> <local-dest-path>`. `tools/compose-fork/sync.sh` walks all
manifests and copies each listed file byte-for-byte from a pinned
`JetBrains/compose-multiplatform-core` checkout (ref in
`tools/compose-fork/compose-ref.txt`) into
`<module>/src/vendor/{common,native,skikoRenderer,sdlRenderer}/kotlin/`. The
`src/vendor/` tree is **gitignored** — you don't check it in, you re-sync
on demand.

Two categories of code live in each module:

1. **Vendored (in `src/vendor/…`)** — copied byte-for-byte from upstream.
   Never hand-edit these. If upstream diverges, adjust the manifest or the
   pinned ref and re-sync. This is the bulk of the codebase (~1500 files
   across the modules).
2. **Project code (in `src/commonMain/`, `src/nativeMain/`, `src/skikoRendererMain/`,
   `src/sdlRendererMain/`)** — code we author (project actuals, glue between
   Compose and SDL3, project-specific extensions).

### The 5 rules for adding upstream Compose surface

1. **`commonMain` should contain NO `androidx.compose.*` code you authored.**
   Anything under `androidx.compose.*` in `commonMain` MUST be a vendored
   copy. Hand-rolled `commonMain` is fine only when the package is
   `com.compose.sdl.*` (project code).

2. **Vendor the `actual`s too whenever possible.** Upstream ships
   `skikoMain` and `native`-flavored actuals. If they compile against
   the current source-set hierarchy, add them to the manifest and let
   them come in verbatim. That includes `.skiko.kt` files (go to
   `src/vendor/skikoRenderer/kotlin/`) and `.native.kt` files (go to
   `src/vendor/native/kotlin/`).

3. **If an upstream file needs a small edit to compile / behave
   correctly for us, copy it locally and edit — MANUAL VENDORING,
   NON-IDEMPOTENT.** Move it OUT of `src/vendor/` into the corresponding
   `src/{commonMain,nativeMain,…}` tree, add a header comment noting
   which upstream file it derived from and what changed, and comment
   its line out of `compose-fork.txt`. Now it's a project file — the
   next sync won't overwrite it, and future upstream changes to that
   file need to be reconciled by hand. This is fine; do it when the
   edit is small and the file is unlikely to churn upstream.

4. **Skiko-specific things go in `:ui/src/skikoRendererMain/`, with
   an SDL3 equivalent in `:ui/src/sdlRendererMain/`.** When upstream
   ships a `.skiko.kt` file that uses Skiko's Canvas / Paragraph / …,
   the `.skiko.kt` variant is fine to vendor into `skikoRendererMain`.
   Then hand-roll (or minimally vendor) the SDL3 counterpart in
   `sdlRendererMain` — `SkiaCanvas.kt` ↔ `Sdl3Canvas.kt`,
   `SkiaTextRenderer.kt` ↔ `Sdl3TextRenderer.kt` (using SDL3_ttf +
   FreeType), `SkiaImageCache.kt` ↔ `Sdl3ImageCache.kt` (using SDL3_image).

5. **Multi-OS project code goes through SDL3, not hand-rolled per-target
   ifdefs.** SDL3 already handles the platform differences for filesystem
   paths (`SDL_GetBasePath`, `SDL_GetPrefPath`), clipboard, cursor,
   fullscreen, high-DPI, subprocess launch, MessageBox, and more.
   `file dialog / "reveal in Finder or Explorer" / app-data path` — do
   it through SDL3 first, hand-roll the target-specific version only if
   SDL3 doesn't expose it (currently the file-open/save dialog uses
   SDL3's `SDL_ShowOpenFileDialog` / `SDL_ShowSaveFileDialog`).

If a piece of upstream is too Compose-specific to make sense on our
stack (e.g. Android AWT layer, iOS `UIView`, JVM `Toolkit`), do a fresh
reimpl in project code — same signature (same package, same params) so
call sites don't care.

## Source-set hierarchy (:ui only)

`:ui` owns cinterops + both renderer pipelines. Its source-set tree:

```
commonMain
└── nativeMain                                  (vendored .native.kt + project native code)
      ├── skikoRendererMain                     (Skia drawing pipeline; Skiko on classpath)
      │     ├── skikoRendererMacosMain          (macOS-only Skia actuals — Metal bridge)
      │     └── skikoRendererLinuxMain          (Linux-only Skia actuals — OpenGL)
      │            attached to: macosArm64Main / linuxX64Main / linuxArm64Main
      │            ONLY when the Skia renderer is active for the target.
      └── sdlRendererMain                       (SDL3 drawing pipeline + TTF/image/FreeType)
            ├── sdlRendererMacosMain            (macOS-only SDL3 driver hint)
            ├── sdlRendererLinuxMain            (Linux-only SDL3 driver hint)
            └── sdlRendererMingwMain            (mingwX64-only SDL3 driver hint)
                   attached to: mingwX64Main always; macOS/Linux when -Prenderer=sdl3.
```

`createRenderBackend(…)` + `rendererPreferredGpuMode()` are declared identically
in both `skikoRendererMain` and `sdlRendererMain`. `:window` calls them straight
from `:ui` — no `expect`/`actual`, no factory layer — and the right impl
resolves because **only one of the two renderer source sets is attached to a
given target**. Under `-Prenderer=sdl3`, the `skikoRenderer*` source sets are
**not even created**, so Gradle has nothing to warn about and Skiko is never
pulled in.

### Cinterop sibling-dependency gotcha

`:ui` owns four cinterops in `src/nativeInterop/cinterop/`:
`sdl3`, `sdl3_ttf`, `sdl3_image`, `freetype`. The `.def` files for `sdl3_ttf` /
`sdl3_image` carry `depends = sdl3` so their `SDL_Surface` / `SDL_Color`
references resolve to the *same* types `sdl3` produces (not duplicates inside
`sdl3_image.SDL_Surface`). **Gradle does not automatically add a sibling
cinterop's klib to a cinterop task's `-library` list**, so the manifest
directive silently fails and you get cryptic
`expected CPointer<sdl3.SDL_Surface>?, actual CPointer<sdl3_image.SDL_Surface>?`
errors.

`:ui/build.gradle.kts` works around this by passing the sdl3 cinterop output
klib path explicitly via `extraOpts("-library", vSdl3Klib)` on `sdl3_ttf` /
`sdl3_image`, plus a task dependency
(`cinteropSdl3_ttf*Target.dependsOn(cinteropSdl3*Target)`). If you ever add
another `depends = sdl3` cinterop, add it to that list too.

## Density flow (Option B — layout in physical pixels)

We use the **physical-pixel layout flow** on HiDPI. Concretely:

- `SDL_WINDOW_HIGH_PIXEL_DENSITY` is set on the SDL window.
- `LocalDensity` = the DPR (2.0 on Retina, 1.0 otherwise) — from
  `pixelWidth / windowWidth`.
- Constraints passed to `rootNode.measure(…)` are the **physical pixel** size,
  not logical points.
- `renderBackend.beginFrame(1f)` — no renderer-side scale; layout already ran
  in physical pixels.
- Pointer coords from SDL are logical points; they're multiplied by DPR
  before dispatch so the whole event pipeline is in the same physical-px
  coord space as layout.

Consequence: `Modifier.width(20.dp)` at density 2 → 40 physical pixels wide.
`onSizeChanged { it.width }` reports physical pixels. If you're passing pixel
integers into pixel-based modifiers, use the lambda forms
(`Modifier.offset { IntOffset(x, y) }`) — they take pixels directly.
Passing raw `px.dp` will double-scale on Retina.

## Building

```bash
# macOS Apple Silicon, default Skia (Metal on macOS)
./gradlew :demo:runDebugExecutableMacosArm64
./gradlew :apidemo:runDebugExecutableMacosArm64

# Linux x64
./gradlew :demo:runDebugExecutableLinuxX64
./gradlew :apidemo:runDebugExecutableLinuxX64

# Windows (from Windows — mingw cross-build from macOS/Linux fails at cinterop)
gradlew.bat :demo:runDebugExecutableMingwX64
gradlew.bat :apidemo:runDebugExecutableMingwX64

# Skiko-free build on macOS/Linux — SDL3 renderer everywhere
./gradlew :demo:runDebugExecutableMacosArm64 -Prenderer=sdl3
```

### System dependencies

**macOS (default Skia build):** `brew install sdl3`. Only need `sdl3_ttf`
if you set `-Prenderer=sdl3`. Skiko klibs come from Maven.

**Linux (default Skia build):** `sudo apt install libsdl3-dev`. Same caveat
for SDL3_ttf.

**Windows (mingwX64 — always uses SDL3 + SDL3_ttf + SDL3_image + FreeType):**
these four libraries + image codecs are **linked statically into the
executable** — no runtime DLLs, distributable is just `<app>.exe` +
`data.kres`. They're not downloaded — `tools/build-all.sh` (from Git Bash)
builds them from source as static libs into a gitignored, in-repo `libs/`
folder. Needs: git, cmake, mingw-w64 gcc/g++ on PATH, plus curl + python for
ninja fetch. `tools/build-all.sh` = `build-freetype.sh` → `build-sdl3.sh` →
`build-sdl3-image.sh` → `build-sdl3-ttf.sh`.

## Runtime bundling — data.kres

Every app ships `<app>.exe` + `data.kres` (a STORED zip alongside the
executable, loaded via `SDL_GetBasePath()`). Contents:

- App drawables + files under `composeResources/{drawable,files}/`
- `font/NotoSans.ttf` — the default variable font, downloaded once by
  `:ui:downloadNotoFonts` into `compose/ui/build/fonts/`. Pass
  `-PbundleDefaultFont=false` to skip.
- `font/NotoSansMono.ttf` — mono body font (apidemo only)
- Material Symbols fonts for the styles the app **actually uses** — the
  Zip task scans the app's Kotlin sources for `MaterialSymbolsOutlined` /
  `Rounded` / `Sharp` and only bundles the fonts referenced.
- `-PsubsetIcons=true` (default on): `scripts/subset-material-symbols.py`
  scans app sources for `MaterialSymbols.<Name>` usage and hb-subsets each
  bundled font down to just those glyphs. Needs `hb-subset` on PATH
  (`brew install harfbuzz` / `apt install harfbuzz-utils`) — falls back
  to the full font if absent.

## Vendor sync workflow

```bash
# Sync every module's compose-fork.txt against the pinned upstream ref.
tools/compose-fork/sync.sh

# Sync one module by direct manifest path (module names use ':' → '/' → skip
# 'compose/' prefix — nested modules like compose/sdl/window pass the full path).
tools/compose-fork/sync.sh compose/ui/compose-fork.txt

# Re-format a manifest (align columns, group by upstream folder).
tools/compose-fork/format-manifest.py --discover ../cmp-ref \
    --manifest compose/ui/compose-fork.txt
```

Upstream ref: `tools/compose-fork/compose-ref.txt` — set to a specific commit
of `JetBrains/compose-multiplatform-core`. Bump the ref → re-sync → let the
build tell you what broke.

## Key files by area — start here when you need to find something

### Renderer + main loop
- `compose/sdl/window/src/nativeMain/…/ComposeWindow.kt` — main loop,
  recomposer lifecycle, SDL event dispatch, composition-local seeding.
- `compose/ui/src/nativeMain/…/RenderBackend.kt` — the interface.
- `compose/ui/src/nativeMain/…/GpuMode.kt` — sealed renderer / driver picker.
- `compose/ui/src/skikoRendererMain/…/renderer/skia/SkiaRenderBackend.kt`.
- `compose/ui/src/sdlRendererMain/…/renderer/sdl/Sdl3RenderBackend.kt`.
- `compose/ui/src/sdlRendererMain/…/renderer/sdl/FreeTypeIcons.kt` —
  variable-font axis rasterisation (SDL3_ttf has no axis-set API; we go
  to FreeType directly for icon families).

### Layout / composition wiring
- `compose/ui/src/commonMain/…/node/ComposeRootHost.kt` — root LayoutNode
  host, hit-test, event dispatch, snapshot observer.
- `compose/ui/src/commonMain/…/node/impl/ComposeOwner.kt` — the
  project `Owner` implementation + `ProjectOwnedLayer` (graphicsLayer / clip /
  alpha bridge).
- `compose/ui/src/commonMain/…/node/NodeApplier.kt`.

### Text
- `compose/ui/src/nativeMain/…/ui/text/SdlParagraph.native.kt` — the
  bridged `Paragraph` implementation (measurement, hit-test, line metrics,
  span painting).
- `compose/ui/src/nativeMain/…/ui/text/ParagraphFactories.native.kt` —
  actuals for the `Paragraph(…)` / `ParagraphIntrinsics(…)` factory family.
- `compose/ui/src/commonMain/…/text/TextMeasurer.kt` — `NativeTextMeasurer`
  interface (per-renderer implementations: `SkiaTextRenderer` /
  `Sdl3TextRenderer`).

### Icons
- `compose/foundation/src/nativeMain/…/icons/IconFontIcon.kt` —
  codepoint-based `Icon` composable + `MaterialIconAxes` /
  `MaterialIconAxisDefaults`.
- `compose/sdl/material-symbols/src/…/MaterialSymbols{Outlined,Rounded,Sharp}.kt`.

### Resources
- `compose/ui/src/commonMain/…/res/Res.kt` — the project's
  `androidx.compose.ui.res` reimpl (`Painter`, `ImageLoader`).
- `compose/ui/src/nativeMain/…/ResourceIO.kt` — opens `data.kres` once via
  `SDL_GetBasePath()` + parses central directory; each entry served by an
  `fseek + fread`.

### Apps
- `demo/src/nativeMain/kotlin/Main.kt` — sidebar demo (`--gpu`, `--screen`,
  `--screenshot`).
- `apidemo/src/nativeMain/kotlin/Main.kt` — API manager entry.
  `apidemo/src/nativeMain/kotlin/UiCompat.kt` — project-local
  `Dialog` / `DropdownMenu` / `DropdownMenuItem` / `TooltipBox` (m3 doesn't
  ship drop-in equivalents for our anchor / scrim patterns).

## Conventions

Kotlin standard style — plain `camelCase` for parameters, local variables,
and fields. **No `f`/`in`/`v` prefixes**, no SPIRTECH scheme (see the global
CLAUDE.md — that scheme is C-only).

Section headers inside a file, when useful:

```kotlin
// ==================
// MARK: Name (file-level / between classes)
// ==================
```

In-function smaller scope:

```kotlin
// ============
//  Name
```

Function-level comments only where the name isn't self-documenting — avoid
line-by-line commentary. Prefer KDoc (`/** … */`) over `/* … */` for docs
that should surface in tooling.

## Common pitfalls

- **State changes don't repaint the UI** — check
  `Snapshot.sendApplyNotifications()` is being called each frame in the main
  loop (`ComposeWindow.kt`). Without it, `mutableStateOf` writes never
  reach the recomposer.
- **Physical-pixel modifiers double-scale on Retina** — under Option-B
  density, layout runs in physical pixels but `Modifier.width(20.dp)` still
  goes through `density.toPx()`. If you have a value already in physical
  pixels (from `onSizeChanged`), convert it back to Dp via
  `with(LocalDensity.current) { pxInt.toDp() }` before passing to
  `Modifier.width(…)`, or use pixel-based lambdas
  (`Modifier.offset { IntOffset(x, y) }`).
- **Skia's `saveLayer(bounds, paint)`** — GPU backends allocate the offscreen
  to `bounds`. If content inside translates beyond those bounds, it gets
  clipped. `SkiaCanvas.saveLayer` passes a huge fixed bounds to sidestep this.
- **`Modifier.alpha` clips to bounds** — upstream contract:
  `Modifier.alpha(x)` desugars to `graphicsLayer(alpha = x, clip = true)`.
  For a drag ghost that also translates, put `alpha` and `translationX` on
  the SAME `graphicsLayer(...)` so clip stays false.
- **Cinterop `depends = sdl3`** — silently doesn't propagate; see the
  gotcha section above.
- **Configuration cache + `-Prenderer=`** — Gradle caches configuration;
  toggling the renderer property may not invalidate it. Delete
  `.gradle/configuration-cache/` between switches if you see weird
  "couldn't find sdl3_ttf" errors.
- **`Path()` in commonMain returns different actuals per renderer** —
  the Skia renderer produces a `SkiaBackedPath` (wraps
  `org.jetbrains.skia.Path`), the SDL renderer produces a project
  `ProjectPath` (command-list based). `SkiaCanvas.toSkiaPath` handles
  both — never assume the type without checking.

## Useful Gradle tricks

- `--args="--gpu=sdl3.opengl --screen=Buttons"` — pass CLI to the demo.
- `--info` — see cinterop classpath + include paths actually used.
- `--rerun-tasks` — force rebuild after toggling `-Prenderer=`.
- After a module rename or IC-cache mismatch: nuke
  `demo/build/kotlin-native-ic-cache` (or `apidemo/build/…`). Kotlin/Native
  pins module IDs into its klib metadata; a stale cache surfaces as
  `Unknown dependent library com.bitsycore.compose.sdl:core` (or
  whatever the old module name was).

## License

MIT — see [LICENSE.md](LICENSE.md).
