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
│   ├── ui-backhandler/              → :ui-backhandler — androidx.compose.ui.backhandler.*
│   └── ui-tooling-preview/          → :ui-tooling-preview — androidx.compose.ui.tooling.preview.*
│                                                     (the common @Preview + PreviewParameterProvider,
│                                                     vendored verbatim; the Maven artifact ships no
│                                                     mingwX64/linux klibs). IDE-only metadata — previews
│                                                     render through the apps' jvm parity targets.
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
    └── window/                      → :window     — nativeComposeApp { Window(...) {} } multi-window
                                                    shell + SDL3 main loop; nativeComposeWindow() wrapper

utils/
└── material-symbols/                → :material-symbols — codepoints + all three style objects
                                                    (Outlined / Rounded / Sharp). COMMON API (usable from
                                                    shared app code) + per-stack actuals: native renders
                                                    via :foundation IconFontIcon (IconFont handles the
                                                    SDL3/Skia split), jvm() via Skiko directly
                                                    (Typeface.makeClone per axes — upstream's FontCache
                                                    drops variationSettings from its key). Its commonMain
                                                    declares official Maven compose coords — the root
                                                    build's FULL-COMMONIZATION BRIDGE substitutes the
                                                    whole ui / foundation / animation / material3 /
                                                    nav3-ui family to project modules on native configs
                                                    (:demo and :apidemo commonMains rely on the same
                                                    bridge; :compose-desktop-native-bridge ships it to
                                                    consumers). Apps get one dep; the consumer Zip task
                                                    bundles only the fonts used (native) and
                                                    jvmProcessResources stages the same fonts (jvm).

components/
└── resources/library/               → :components-resources — the OFFICIAL Compose resources
                                                    runtime (org.jetbrains.compose.components:
                                                    components-resources), VENDORED from the
                                                    compose-multiplatform UMBRELLA repo (the first
                                                    SET_REPO manifest) because the Maven artifact has
                                                    no mingwX64/linux klibs. Platform layer is project
                                                    code: data.kres ResourceReader, pure-Kotlin
                                                    DomXmlParser (upstream's is Darwin NSXMLParser),
                                                    SDL3_image decode via the :ui EncodedImageDecoder
                                                    hook, NamedFont registration, SDL locale/theme env.
                                                    Apps' JVM targets keep the Maven artifact — the
                                                    generated Res accessors work against BOTH.

navigation3/
└── navigation3-ui/                  → :navigation3-ui — androidx.navigation3.ui.* + scene machinery,
                                                    VENDORED verbatim from upstream (SET_FOLDER manifest).
                                                    Navigation 3's runtime layers (navigation3-runtime,
                                                    lifecycle-viewmodel-navigation3) are real Maven KMP
                                                    artifacts used as-is (see "Known Compatible" below);
                                                    only this UI module has no K/N desktop artifact. The
                                                    native actual (NavDisplay.native.kt) is MANUALLY
                                                    VENDORED: it mirrors the ANDROID transition defaults
                                                    (700ms fades, predictive-pop spring/scaleOut) because
                                                    the upstream macos actual ships all-None (no animation).

demo/                → :demo      — flagship showcase app (30+ screens) + the CLI probe suite.
                                    MULTIPLATFORM: also has a jvm() target running the SAME shared
                                    screens on stock JVM Compose Desktop (`./gradlew :demo:run`,
                                    MainJvmKt) — the parity reference; differences vs native = port bugs
apidemo/             → :apidemo   — Postman-style REST API manager. MULTIPLATFORM like :demo
                                    (`./gradlew :apidemo:run`): the whole UI lives in commonMain
                                    against the official Maven coords; SDL-backed APIs go through
                                    expect/actual seams (compat/Compat.kt — native actuals delegate
                                    to com.compose.sdl, jvm actuals use AWT + upstream desktop).
                                    mTLS / TLS-chain inspection stays native-only (bundled libcurl).
gradle-plugin/
└── compose-desktop-native-bridge/ → :compose-desktop-native-bridge — the CONSUMER-side bridge as a
                                    published Gradle plugin (id com.bitsycore.compose-desktop-native
                                    .bridge, applies to Settings or Project): substitution rules
                                    can't ship inside Maven artifacts, so third-party apps apply the
                                    plugin, declare OFFICIAL CMP coords in commonMain, and native
                                    configurations swap in the published com.bitsycore.compose.sdl
                                    klibs. Substituted version defaults to the plugin's own
                                    (override: composeDesktopNative.version property). Published by
                                    the macOS publish job.
scripts/             → vendor-sync + python helper scripts (compose-coverage = API
                      coverage/fidelity vs upstream, material-symbols generate/subset)
                      + compose-fork/;
                      scripts/build-sdl/ = static-lib build script (python)
libs/                → gitignored per-host static SDL3 / SDL3_ttf / SDL3_image / FreeType
                      output of scripts/build-sdl/build-all.py on Windows
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
`<module>/compose-fork.txt` manifest. Each one declares its upstream repo +
pinned ref up top with `SET_REPO=<https-url>@<ref>`, where `<ref>` is normally a
`<VARNAME>` resolved from `scripts/compose-fork/compose.properties` — a
`NAME=value` file that version-tags every pinned ref in ONE place (e.g.
`COMPOSE_CORE_REF` for compose-multiplatform-core, `COMPOSE_REF` for the
compose-multiplatform umbrella repo that `:components-resources` vendors from).
There is no implicit default — `SET_REPO` is required. `scripts/compose-fork/sync.sh`
walks all manifests and copies each selected file byte-for-byte from the pinned
checkout (each distinct repo sparse-cloned to `../cmp-ref[-<name>]`) into
`<module>/src/vendor/{common,native,skikoRenderer,sdlRenderer}/kotlin/`. The
`src/vendor/` tree is **gitignored** — you don't check it in, you re-sync
on demand.

Manifests are **folder-style**: a `SET_FOLDER=<module>/src` line sets an upstream
base, then `<sourceSet>/kotlin/ -> src/vendor/<area>/kotlin/` grabs a whole
source set (every `.kt` under it). `!<sourceSet>/kotlin/<pkg>/<File>.kt` refuses
one file inside a grabbed folder — use it for files hand-vendored + edited under
`src/{commonMain,…}` so the folder copy doesn't shadow them, or for upstream
files the port doesn't want. A plain `<src> -> <dest>` still pins (or renames on
copy) a single file. Re-declare `SET_FOLDER` to draw from a second upstream module
(`:ui` pulls from ui + ui-graphics + ui-text). Every sync re-annotates the
manifest in place: commented `#     | src -> dest` lines under each folder
directive list what it expands to, and a trailing `# >>> DIAGNOSTIC GAPS` block
lists every upstream `.kt` under SET_FOLDER that no directive selects (grouped by
source set) so new upstream files surface as comments to uncomment. That whole
annotated tail is generated — never hand-edit it; edit only the directives up
top and re-run sync.

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

`createRenderBackend(…)` + `rendererPreferredGpuMode()` are `expect`s in
`:ui`'s nativeMain with `actual`s in BOTH `skikoRendererMain` and
`sdlRendererMain` — unambiguous because **only one of the two renderer source
sets is attached to a given target**. (They used to be plain duplicate
declarations with no expect; that compiled per-target but shared nativeMain
METADATA couldn't see them on a host whose targets span both renderers, which
blocked the WINDOWS host from producing :window's KotlinMultiplatform
publication — and Windows must publish the root modules, see the publish
workflow.) Under `-Prenderer=sdl3`, the `skikoRenderer*` source sets are
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

# Stock JVM Compose Desktop (any host) — the parity reference: the SAME shared
# screens on upstream Compose; differences vs the native build = port bugs.
./gradlew :demo:run
./gradlew :apidemo:run
```

### System dependencies

SDL3 + SDL3_ttf + SDL3_image + FreeType (+ image codecs) are **built from
source as static libraries on every OS** and linked straight into the
executable — no brew/apt SDL packages, no runtime .dll/.so/.dylib;
a distributable is `<app>` + `data.kres`. One script does it all:
`python scripts/build-sdl/build-all.py` (plain Python 3, no Git Bash needed)
builds into the gitignored, in-repo `libs/` folder. Versions/URLs pinned in
`scripts/build-sdl/build-sdl.properties`. Steps run in order freetype → sdl3
→ sdl3-image → sdl3-ttf; pass step names to rebuild a subset
(`python scripts/build-sdl/build-all.py sdl3-ttf`).

Needs everywhere: git, cmake, python 3 (ninja fetched automatically). Per
host: macOS = Xcode CLT (Skia default; Skiko klibs from Maven); Linux =
gcc/g++ + the X11/Wayland/audio dev headers SDL3's configure detects (apt
list in .github/workflows/publish.yml); Windows = mingw-w64 g++ on PATH
(C compiles with K/N's bundled mingw).

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
scripts/compose-fork/sync.sh

# Sync one module by direct manifest path (module names use ':' → '/' → skip
# 'compose/' prefix — nested modules like compose/sdl/window pass the full path).
scripts/compose-fork/sync.sh compose/ui/compose-fork.txt

# Re-format a manifest (align columns, group by upstream folder).
scripts/compose-fork/format-manifest.py --discover ../cmp-ref \
    --manifest compose/ui/compose-fork.txt
```

Upstream ref: `scripts/compose-fork/compose.properties` — set to a specific commit
of `JetBrains/compose-multiplatform-core`. Bump the ref → re-sync → let the
build tell you what broke.

## Key files by area — start here when you need to find something

### Renderer + main loop
- `compose/sdl/window/src/nativeMain/…/ComposeWindow.kt` — main loop,
  recomposer lifecycle, SDL event dispatch, composition-local seeding.
- `compose/ui/ui/src/nativeMain/…/RenderBackend.kt` — the interface.
- `compose/ui/ui/src/nativeMain/…/GpuMode.kt` — sealed renderer / driver picker.
- `compose/ui/ui/src/skikoRendererMain/…/renderer/skia/SkiaRenderBackend.kt`.
- `compose/ui/ui/src/sdlRendererMain/…/renderer/sdl/Sdl3RenderBackend.kt`.
- `compose/ui/ui/src/sdlRendererMain/…/renderer/sdl/FreeTypeIcons.kt` —
  variable-font axis rasterisation (SDL3_ttf has no axis-set API; we go
  to FreeType directly for icon families).

### Layout / composition wiring
- `compose/ui/ui/src/commonMain/…/node/ComposeRootHost.kt` — root LayoutNode
  host, hit-test, event dispatch, snapshot observer.
- `compose/ui/ui/src/commonMain/…/node/impl/ComposeOwner.kt` — the
  project `Owner` implementation + `ProjectOwnedLayer` (graphicsLayer / clip /
  alpha bridge).
- `compose/ui/ui/src/commonMain/…/node/NodeApplier.kt`.

### Text
- `compose/ui/ui/src/nativeMain/…/ui/text/SdlParagraph.native.kt` — the
  bridged `Paragraph` implementation (measurement, hit-test, line metrics,
  span painting).
- `compose/ui/ui/src/nativeMain/…/ui/text/ParagraphFactories.native.kt` —
  actuals for the `Paragraph(…)` / `ParagraphIntrinsics(…)` factory family.
- `compose/ui/ui/src/commonMain/…/text/TextMeasurer.kt` — `NativeTextMeasurer`
  interface (per-renderer implementations: `SkiaTextRenderer` /
  `Sdl3TextRenderer`).

### Icons
- `compose/foundation/foundation/src/nativeMain/…/icons/IconFontIcon.kt` —
  codepoint-based `Icon` composable + `MaterialIconAxes` /
  `MaterialIconAxisDefaults`.
- `utils/material-symbols/src/…/MaterialSymbols{Outlined,Rounded,Sharp}.kt`.

### Resources
- `compose/ui/ui/src/commonMain/…/res/Res.kt` — the project's
  `androidx.compose.ui.res` reimpl (`Painter`, `ImageLoader`).
- `compose/ui/ui/src/nativeMain/…/ResourceIO.kt` — opens `data.kres` once via
  `SDL_GetBasePath()` + parses central directory; each entry served by an
  `fseek + fread`.

### Apps
- `demo/src/nativeMain/kotlin/Main.kt` — sidebar demo (`--gpu`, `--screen`,
  `--screenshot`).
- `apidemo/src/nativeMain/kotlin/Main.kt` — API manager entry.
  `apidemo/src/nativeMain/kotlin/UiCompat.kt` — project-local
  `Dialog` / `DropdownMenu` / `DropdownMenuItem` / `TooltipBox` (m3 doesn't
  ship drop-in equivalents for our anchor / scrim patterns).

## Tooling — what's available and when to reach for it

Index of the repo's tooling. Each entry says when to use it and points at its
own README for detail; the parity/profiler/probe entries are expanded below
since they're newer.

| Tool | Reach for it when | Detail |
|------|-------------------|--------|
| `python scripts/build-sdl/build-all.py` | building/refreshing the static SDL3/TTF/image/FreeType libs under `libs/` (once per host, or after bumping `build-sdl.properties`) | "Building" above |
| `scripts/compose-fork/sync.sh` | re-syncing vendored upstream `androidx.compose.*` after a fresh checkout or a `compose.properties` ref bump; `format-manifest.py` to re-align a `compose-fork.txt` | "Vendor sync workflow" above + `scripts/compose-fork/README.md` |
| `./gradlew apiDump && python scripts/compose-coverage.py` | measuring how much upstream public API the port actually covers, per module (`--missing <module>` lists uncovered decls) | "Vendoring" above |
| `scripts/generate-material-symbols.py` / `subset-material-symbols.py` | regenerating the Material Symbols codepoints, or hb-subsetting bundled icon fonts to used glyphs (the latter runs automatically in app Zip tasks under `-PsubsetIcons`) | — |
| **`scripts/parity/parity.py`** | after ANY renderer/layout change: catch a screen that visually diverged native-vs-JVM (missing content, wrong shape/colour, broken clip) | `scripts/parity/README.md` + below |
| **`scripts/probe/probe.py`** | reproducing a specific interaction bug (click/hover/hold at a point) or grabbing one screen's pixels deterministically | `scripts/probe/README.md` + below |
| **`CDN_PROFILE=1 <app>`** | finding where a slow frame goes (per-phase main-loop timings) before optimizing | below |
| bridge plugin (`com.bitsycore.compose-desktop-native.bridge`) | consuming the published klibs from a third-party app | `gradle-plugin/compose-desktop-native-bridge/README.md` |
| `demo --screen=<Name>` / `--screenshot=` / `--nav3test` / `--backtest` / `--multiwintest` | driving one screen headless, or the regression probes for nav3 / predictive-back / multi-window | `demo/src/nativeMain/kotlin/MainNative.kt` |

Whole-project renderer-touching change → run **parity** (broad net). Chasing
one reported interaction → **probe** (targeted). Slow → **profiler** first,
optimize second. See `ROADMAP.md` for the renderer work these support.

### Frame profiler — `CDN_PROFILE=1`

Set the env var and run any native app; every ~2 s of rendered frames it prints
avg/max ms per main-loop phase (`events` / `app` pump / `pump` per-window /
`render`). Implemented in `ComposeWindow.kt` via SDL performance counters. Use
it to confirm WHERE time goes before touching draw code — e.g. it showed
`render` is ~32 ms of a 39 ms bubble-wrap frame, i.e. the renderer, not
composition, is the bottleneck.

### Interaction probe — `scripts/probe/`

Launches a native app, sends **window-client-relative** input (click / hover /
hold, fractional coords addressed by process name so it ignores window
position/focus) and captures the client area via `PrintWindow` (works even
when occluded). The packaged form of the rigs that reproduced the
square-on-click and TLS-chain bugs. Windows-only. See its README.

### Parity harness — native-vs-JVM screenshot diff (`scripts/parity/`)

`:demo` renders the **same commonMain screens** on two stacks: native
(SDL/Skia, Kotlin/Native) and a `jvm()` target on upstream Compose Desktop.
`scripts/parity/parity.py` screenshots every screen on both and pixel-diffs
them, so a screen that visually diverges is a **port regression** (missing
content, wrong shape/colour, broken clip). Several past renderer regressions
would have been caught here.

```bash
python scripts/parity/parity.py                 # all screens (builds first)
python scripts/parity/parity.py Buttons Shapes  # a subset
python scripts/parity/parity.py --no-build      # reuse the last renders
```

Mechanics: the JVM leg renders all screens headlessly via `ImageComposeScene`
in ONE process (`:demo:run --args=--screenshot-all=<dir>`, wired in
`MainJvm.kt`); the native leg launches the exe once per screen
(`--screen=<Name> --screenshot=<x>.bmp`). Output lands in **`build/parity/`
(gitignored)**: `<pct>_<Name>_diff.png` (amplified difference heatmap),
`<pct>_<Name>_compare.png` (native ∣ jvm ∣ diff, side by side), and
`report.txt` ranked worst-first. Windows-only for the native leg today; needs
Pillow. See `scripts/parity/README.md`.

**What the number means — read this before trusting it.** The `%differ` is the
fraction of pixels whose per-channel difference exceeds a tolerance. It is NOT
a pass/fail score and pixel-perfection is not the goal: the two stacks use
different default fonts, so **every screen carries a steady baseline
difference** — in the heatmap, text shows as a faint *doubled ghost* from
slightly different line metrics/baselines. A healthy full sweep is a smooth
gradient (~2% for a sparse screen like Counter, up to ~32% for a text-dense
one like Tabs). Known SDL parity gaps also inflate specific screens
predictably (Brushes ~23% — gradients render solid on SDL; Shadows / Canvas /
GraphicsLayer — effect differences). **The signal is the RANKING and the
delta from a screen's own history**, not the absolute value: a text-light
screen suddenly reading 60%, or a screen jumping far above its neighbours, is
the bug. In a `_diff.png`: ghosted/doubled text + dark shapes = normal font
drift; a **solid bright block, or a shape present on only one side** = a real
regression — open the `_compare.png` to see which stack is wrong.

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
- **Substituted Maven modules hide their transitives from common metadata** —
  when the bridge swaps an official coord for a project module on native
  configs, KGP's granular-metadata visibility check drops that Maven module's
  TRANSITIVES from the commonMain classpath (symptom: `Unresolved reference
  'Color'` / `Cannot access class ...` in `compileCommonMainKotlinMetadata`,
  while per-target compilation is fine). Declare EVERY artifact the common
  code touches DIRECTLY (ui-graphics, ui-text, ui-unit, …) and give each its
  own bridge rule. Note only the WINDOWS publish job compiles common metadata
  (it owns the root KotlinMultiplatform publications — the only host that
  declares every target, so only its .module files carry the full variant
  table; macOS-published roots left v0.1.15 without mingwX64 variants) —
  test with `gradlew :<module>:compileCommonMainKotlinMetadata` before tagging.
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

## Known Compatible — official Maven KMP artifacts used AS-IS

Before reimplementing or vendoring ANYTHING androidx, check Maven: a lot of
the architecture stack publishes real Kotlin/Native desktop klibs
(mingwX64 + linuxX64/arm64 + macosArm64) and runs on this port unmodified.
Sometimes the GOOGLE coordinates (`androidx.*`) have the K/N variant,
sometimes the JETBRAINS ones (`org.jetbrains.*`) — check both before
concluding an artifact "doesn't exist" for our targets.

Verified in-tree (api-exposed by `:ui` unless noted):

- `org.jetbrains.compose.runtime:runtime` / `runtime-saveable` 1.11.1 —
  THE Compose runtime (composition, snapshots, recomposer). Never vendored.
- `androidx.compose.runtime:runtime-retain` 1.11.1 (google coordinates).
- `androidx.lifecycle:*` **2.11.0** (google): `lifecycle-runtime-compose`,
  `lifecycle-viewmodel`, `lifecycle-viewmodel-compose`,
  `lifecycle-viewmodel-savedstate`, **`lifecycle-viewmodel-navigation3`** —
  the whole ViewModel + SavedStateHandle + nav3-decorator stack needed ZERO
  reimplementation; only the window-side owners had to be provided (see
  caveats).
- `androidx.savedstate:savedstate` / `savedstate-compose` **1.5.0** (google).
- `androidx.navigation3:navigation3-runtime` **1.1.4** — backstack / NavEntry
  / decorators. Only navigation3-UI (NavDisplay) lacks a K/N desktop artifact
  → vendored as `:navigation3-ui`.
- `androidx.navigationevent:navigationevent-compose` 1.1.2 — predictive-back
  event plumbing (BackHandler, NavDisplay gestures).
- `androidx.collection:collection` — plain Maven dep, not a module.
- NOT compatible (vendored instead): `components-resources` (no mingw/linux
  klibs → `:components-resources`), `navigation3-ui` (same → `:navigation3-ui`).
- Infra: `kotlinx-coroutines-core`, `atomicfu`, `okio`,
  `kotlinx-serialization`.

Caveats that make these work here (all already wired — listed so nobody
"fixes" them away):

- The google `LocalViewModelStoreOwner` / `LocalSavedStateRegistryOwner` /
  `LocalLifecycleOwner` are PLAIN composition locals — the JB HostDefault
  mechanism (`compositionLocalWithHostDefaultOf`) does not exist in google
  artifacts. `WindowArchitectureOwner` (ComposeWindow.kt) provides all three
  per window, mirrors upstream desktop's DefaultArchitectureComponentsOwner,
  calls `enableSavedStateHandles()` at construction, and follows SDL focus /
  visibility (focused → RESUMED, unfocused → STARTED, minimised → CREATED).
- The window composes its FIRST composition at CREATED and resumes after —
  `enableSavedStateHandles()` callers running in composition require
  lifecycle ≤ CREATED. Related contract: `rememberViewModelStoreOwner()` with
  its default `savedStateRegistryOwner` THROWS at a RESUMED call site (same
  on Android) — scope shared VMs to the window owner (`viewModel { }` outside
  the entries, the `activityViewModels()` analog) instead.
- nav3 entry decorators: saveable BEFORE viewmodel —
  `listOf(rememberSaveableStateHolderNavEntryDecorator(),
  rememberViewModelStoreNavEntryDecorator())`.
- `Dispatchers.Main.immediate` must run inline on the main thread
  (Sdl3MainDispatcher) — androidx.lifecycle's main-thread enforcement
  round-trips through it; a queue-only Main deadlocks composition.
- Regression probes: `demo --nav3test` (nav3 + ViewModels + lifecycle,
  composed late at RESUMED like the real sidebar flow), `--backtest`
  (navigationevent), `--multiwintest` (per-window owners).

## License

MIT — see [LICENSE.md](LICENSE.md).
