# CLAUDE.md

Guidance for Claude Code working in this repository. This is the primary
context — read it first, then look at the files it points to.

## Documentation map

- [README.md](README.md) — public overview + quickstart (bridge plugin,
  `nativeComposeWindow`, sample apps).
- [RENDERER.md](RENDERER.md) — the rendering layer: architecture, the retained-
  layer engine, convergence decisions, learnings, and open work. Read it before
  touching any renderer, graphics-actual, or layer-engine code.
- [TOOLING.md](TOOLING.md) — build/vendor/verify scripts and workflows
  (build-sdl, sync + drift checks, parity, probe, profiler, coverage,
  verify-mac) + the version map and the ref-bump / release runbooks.
- [PLAN.md](PLAN.md) §2 — audited list of no-ops, stubs, and hardcodes left in the
  port, with P0/P1/P2 severity, plus the road-to-1.0.0 fidelity / SDL / release work
  (this subsumes the former TODO.md, which was never committed).
- This file — architecture, module layout, vendoring rules, source-set
  hierarchy, density flow, conventions, and common pitfalls.

## What this project is

**ComposeNativeSDL3** — a Kotlin/Native port of Compose Multiplatform running
on SDL3, no JVM. Compiles to native binaries for macOS (arm64), Linux
(x64/arm64), Windows (mingwX64).

Rendering is **Skia everywhere** behind one `RenderBackend` — Metal / OpenGL
/ CPU raster:

- **macOS + Linux** link the OFFICIAL Skiko klibs from Maven.
- **Windows (mingwX64)** links the bitsycore skiko FORK — skiko+Skia compiled
  into `skiko-windows-x64.dll` with a flat extern-C surface, bound from K/N via
  an embedded GNU import lib, published to GitHub Packages as
  `com.bitsycore.skiko:skiko:0.150.1-mingw.1` (override with
  `-PskikoMingwVersion`). The runtime DLL is auto-provisioned next to the exe by
  the bridge plugin (`installWindowsSkiaDll`). See SKIKO-MINGW-FEASIBILITY.md.

Windowing, input, audio, filesystem access, and the OS-integration surface
(file dialogs, clipboard, "open in Finder/Explorer"…) all go through
**SDL3**. The runtime (`androidx.compose.runtime.*`: composition, snapshots,
recomposer, `mutableStateOf`, `remember`, …) is the **official
`org.jetbrains.compose.runtime` klibs from Maven** — this project only
re-implements the layers on top (`androidx.compose.ui.*`, `.foundation.*`,
`.animation.*`, `.material3.*`).

## Module layout

Library modules mirror upstream Compose Multiplatform's `compose/` tree.
The SDL layer is two modules: `:sdl-core` (the NAKED sdl3 cinterop + platform
primitives — zero Compose dep, like `skiko`) at `sdl/sdl-core/`, and
`:desktop-native-window` (the SDL3 main-loop shell + app entry point) at
`compose/desktop/native/window/`. `:ui` depends on `:sdl-core` and its renderer
+ SDL↔Compose bridges pick the cinterop from it.

One Gradle module per upstream artifact; the directory mirrors the upstream
`compose/` path, the gradle path is kept short (redirected via `projectDir`).

```
compose/
├── ui/
│   ├── ui/                          → :ui        — androidx.compose.ui.* CORE (Modifier, LayoutNode,
│   │                                               composition, semantics, input, focus) + com.compose.sdl.* —
│   │                                               the Skia RenderBackend + GPU bridges + the SDL↔Compose
│   │                                               bridges (events / clipboard / cursors / window). Depends on
│   │                                               :ui-graphics, :ui-text, :sdl-core. (ui-graphics + ui-text +
│   │                                               the sdl3 cinterop were split OUT — upstream layout.)
│   ├── ui-graphics/                 → :ui-graphics — androidx.compose.ui.graphics.* + the Skia actuals
│   │                                               (SkiaBackedCanvas/Path/Paint, GraphicsLayer, SkiaImageCache,
│   │                                               painter/image + resource seams). → skiko; SDL-free.
│   ├── ui-text/                     → :ui-text   — androidx.compose.ui.text.* + the skiko text engine
│   │                                               (SkiaParagraph → NativeParagraphOps → SkiaFonts, IconFont,
│   │                                               NamedFont). → :ui-graphics, skiko; SDL-free.
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
└── desktop/native/window/           → :desktop-native-window — nativeComposeApp { Window(...) {} }
                                                    multi-window shell + SDL3 main loop; nativeComposeWindow()
                                                    wrapper (project app-shell, not an upstream CMP artifact).
                                                    Published as com.bitsycore.compose:desktop-native-window.

sdl/
└── sdl-core/                        → :sdl-core   — the NAKED sdl3 cinterop + platform primitives
                                                    (zero Compose dep, like skiko); the single `sdl3` cinterop
                                                    lives here. :ui depends on it. com.bitsycore.compose.sdl:sdl-core.

utils/
└── material-symbols/                → :material-symbols — codepoints + all three style objects
                                                    (Outlined / Rounded / Sharp). COMMON API (usable from
                                                    shared app code) + per-stack actuals: native renders
                                                    via :foundation IconFontIcon (Skia), jvm() via Skiko directly
                                                    (Typeface.makeClone per axes — upstream's FontCache
                                                    drops variationSettings from its key). Its commonMain
                                                    declares official Maven compose coords — the root
                                                    build's FULL-COMMONIZATION BRIDGE substitutes the
                                                    whole ui / foundation / animation / material3 /
                                                    nav3-ui / components-resources family to project
                                                    modules on native configs
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
                                                    image decode via the :ui EncodedImageDecoder hook
                                                    (Skia), NamedFont registration, SDL locale/theme env.
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
                                    DOGFOODS the bridge plugin: data.kres packaging + app icon come
                                    from compose.desktop.native {}; the font pipeline (Noto +
                                    Material Symbols subsetting) comes from the shared buildSrc
                                    helper (registerComposeFontBundling).
buildSrc/            → shared build logic for the app modules: ComposeFontBundling.kt —
                      registerComposeFontBundling { } (everything OPT-IN; no flag = no-op)
                      registers downloadNotoFonts (bundleNotoSans / bundleNotoSansMono /
                      autoDetectNotoSansMono), detects the Material Symbols styles the
                      sources use (bundleMaterialSymbols), wires the font/ entries into
                      every data.kres Zip (the bridge plugin's package* tasks; hand-rolled
                      copy*ComposeResources* tasks also match) and stages the same fonts on
                      the JVM classpath; hb-subset pipeline behind enableIconSubsetting
                      (findMaterialSymbolsUsage + subsetMaterialSymbols<Style>).
gradle-plugin/
└── compose-desktop-native-bridge/ → the CONSUMER-side bridge as a published Gradle plugin
                                    (id com.bitsycore.compose-desktop-native.bridge, applies to
                                    Settings or Project). An INCLUDED build (pluginManagement.
                                    includeBuild in settings.gradle.kts), NOT a subproject — a
                                    plugins{} block can only resolve plugins from repositories or
                                    included builds; :demo and :apidemo apply it from source
                                    (dogfooding: data.kres packaging — and apidemo's app icon —
                                    run through the plugin).
                                    Three halves: (1) substitution — third-party apps declare
                                    OFFICIAL CMP coords in commonMain and native configurations
                                    swap in the published com.bitsycore.compose.sdl klibs
                                    (version defaults to the plugin's own; override:
                                    composeDesktopNative.version property; disable:
                                    composeDesktopNative.substitution=false — set repo-wide in
                                    gradle.properties since the root build's FULL-COMMONIZATION
                                    BRIDGE substitutes to project modules in-repo); (2) data.kres
                                    packaging (package<Variant>ComposeResources<Target> per native
                                    executable, honours -PcompressResources); (3) the
                                    compose.desktop.native { entryPoint / icon {} } DSL (.rgba
                                    runtime icons + windres .exe embed — injected into
                                    hand-declared executables too). Published by the WINDOWS
                                    publish job.
scripts/             → vendor-sync + python helper scripts (compose-coverage = API
                      coverage/fidelity vs upstream, material-symbols generate/subset)
                      + compose-fork/;
                      scripts/build-sdl/ = static-lib build script (python)
libs/                → gitignored per-host static SDL3 output of
                      scripts/build-sdl/build-all.py on Windows
```

Module PATHS stay short (`:ui`, `:foundation`, `:desktop-native-window`, …) —
`settings.gradle.kts` redirects `projectDir` for each so build files across
the repo stay terse. `androidx.collection` is a plain Maven dependency
(`androidx.collection:collection`), not a module — same as other simple
androidx KMP libs.

## Dependency graph

```
:ui  ←  :animation-core  ←  :animation          ←  :foundation  ←  :material3  ← :demo, :apidemo
:ui  ←  :foundation-layout  ←──────────────────────┘   ↑              ↑
                        :material-ripple  ←────────────┘──────────────┘
:foundation, :animation-core  ←  :desktop-native-window ;  :foundation, :material3  ←  :material-symbols
```

All edges are `api`, so a consumer of `:foundation` / `:material3` transitively
sees the split modules. Full DAG: `:ui-util → collection`; `:ui-geometry → :ui-util`;
`:ui-unit → :ui-geometry, :ui-util`; `:ui-backhandler → :ui-util, navigationevent`;
`:sdl-core → sdl3 cinterop (NAKED — no Compose)`;
`:ui-graphics → :ui-geometry, :ui-unit, :ui-util, skiko`;
`:ui-text → :ui-graphics, :ui-unit, :ui-util, skiko`;
`:ui → :ui-graphics, :ui-text, :sdl-core, :ui-util, :ui-geometry, :ui-unit, :ui-backhandler`; `:animation-core → :ui`;
`:foundation-layout → :ui`; `:animation → :animation-core, :foundation-layout`;
`:foundation → :animation, :foundation-layout, :animation-core, :ui`;
`:material-ripple → :foundation, :animation-core`;
`:material3 → :foundation, :material-ripple, :animation-core, :foundation-layout`.

`:ui` is the Compose core + the Skia RenderBackend + the SDL↔Compose bridges; it
sits on `:ui-graphics` / `:ui-text` (the graphics/text primitives + their skiko
`actual`s, SDL-free) and the naked `:sdl-core` (sdl3 cinterop). The pure lower
artifacts (`:ui-util`, `:ui-geometry`, `:ui-unit`, `:ui-backhandler`) are below.
Everything above `:ui` touches renderer internals only via its public surface.
`:desktop-native-window` depends on `:ui` + `:foundation` (needs `LazyList`-style scaffolding to
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
`<module>/src/vendor/{common,native,skikoRenderer}/kotlin/`. The
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
2. **Project code (in `src/commonMain/`, `src/nativeMain/`,
   `src/skikoRendererMain/`)** — code we author (project actuals, glue between
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
   its line out of `compose-fork.txt`. **ALWAYS also add the
   machine-readable provenance line** so the copy can be diffed against
   its base and against later upstream versions:

   ```
   // VENDOR-BASE: <upstream-repo-relative-path> @ <pinned-tag>
   // VENDOR-BASE(COMPOSE_REF): <path> @ <tag>     ← for umbrella-repo files
   ```

   `scripts/compose-fork/check-vendor-drift.py` reads these at every ref
   bump: it flags any file whose recorded base lags the current pin and
   (with the local clone) reports whether the upstream base ACTUALLY
   changed base..pin — i.e. whether the copy needs hand-reconciling or
   just a ref re-stamp. Now it's a project file — the next sync won't
   overwrite it, and future upstream changes to that file need to be
   reconciled by hand. This is fine; do it when the edit is small and
   the file is unlikely to churn upstream.

4. **Skiko-specific things go in a `skikoRendererMain` source set.** When
   upstream ships a `.skiko.kt` file that uses Skiko's Canvas / Paragraph / …,
   the `.skiko.kt` variant is fine to vendor into `skikoRendererMain` — graphics
   into `:ui-graphics` (`SkiaBackedCanvas.skiko.kt`, `SkiaImageCache.kt`, …),
   text into `:ui-text` (`SkiaParagraphEngine.kt`), the render backend into
   `:ui` (`SkiaRenderBackend.kt`). All native targets attach this source set;
   mingwX64 layers its fork actuals on top under `skikoRendererMingwMain`.

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

`:ui` owns the Skia RenderBackend + GPU bridges. The SAME source-set tree is used
by `:ui-graphics` / `:ui-text` for their skiko `actual`s (only the deps differ);
the `sdl3` cinterop lives in `:sdl-core` (see below). `:ui`'s tree:

```
commonMain
└── nativeMain                                  (vendored .native.kt + project native code)
      ├── skikoRendererMain                     (Skia drawing pipeline; official Skiko on classpath)
      │     ├── skikoRendererMacosMain          (macOS-only Skia actuals — Metal bridge)
      │     └── skikoRendererLinuxMain          (Linux-only Skia actuals — OpenGL)
      │            attached to: macosArm64Main / linuxX64Main / linuxArm64Main.
      └── skikoRendererMingwSharedMain
            └── skikoRendererMingwMain          (mingwX64-only Skia actuals — the bitsycore
                                                 skiko FORK: skiko-windows-x64.dll bound via
                                                 an embedded GNU import lib; OpenGL context)
                   attached to: mingwX64Main.
```

`createRenderBackend(…)` + `rendererPreferredGpuMode()` are `expect`s in
`:ui`'s nativeMain with `actual`s in `skikoRendererMain` (shared by every native
target). `rendererPreferredGpuMode()` picks Metal on macOS, OpenGL on
Linux + Windows, with a Software (CPU raster) auto-fallback if the GPU context
fails to come up.

### Cinterop

The `sdl3` cinterop lives in the NAKED `:sdl-core` module
(`sdl/sdl-core/src/nativeInterop/cinterop/sdl3.def`: SDL_Window / SDL_Event /
SDL_GetBasePath / clipboard / dialogs / GL+Metal context / SDL_Renderer-for-CPU-
raster). `:ui` depends on `:sdl-core` (`api`), so `:ui`'s renderer + SDL↔Compose
bridges — and `:desktop-native-window` downstream — see `sdl3.*` and inherit SDL3's static-lib +
linker-opt propagation (the `.def` bakes in `staticLibraries = libSDL3.a` + the
per-OS `linkerOpts`, carried through the klib chain to the final exe, so apps
link SDL just by depending on `:ui`/`:desktop-native-window`). `:sdl-core` has NO Compose
dependency, so `:ui → :sdl-core` is cycle-free — the compose way (like `ui → skiko`).

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

# Windows (from Windows — mingw cross-build from macOS/Linux fails at cinterop).
# Links the bitsycore skiko FORK from GitHub Packages (com.bitsycore.skiko:skiko:
# 0.150.1-mingw.1, override -PskikoMingwVersion); the bridge plugin drops
# skiko-windows-x64.dll next to the exe (installWindowsSkiaDll).
gradlew.bat :demo:runDebugExecutableMingwX64
gradlew.bat :apidemo:runDebugExecutableMingwX64

# Stock JVM Compose Desktop (any host) — the parity reference: the SAME shared
# screens on upstream Compose; differences vs the native build = port bugs.
./gradlew :demo:run
./gradlew :apidemo:run
```

### System dependencies

SDL3 is **built from source as a static library on every OS** and linked
straight into the executable — no brew/apt SDL packages. Build it once per host
with `python scripts/build-sdl/build-all.py`. Per-host toolchain requirements
and the step breakdown are in [TOOLING.md](TOOLING.md#native-libraries).

Skia comes in through the Skiko klibs: the official Maven ones on macOS/Linux
(no runtime .so/.dylib — Skia is statically inside the klib), and the bitsycore
fork on Windows, whose `skiko-windows-x64.dll` the bridge plugin provisions next
to the exe. So a macOS/Linux distributable is `<app>` + `data.kres`; a Windows
one is `<app>.exe` + `data.kres` + `skiko-windows-x64.dll`.

## Runtime bundling — data.kres

Every app ships `<app>.exe` + `data.kres` (a STORED zip alongside the
executable, loaded via `SDL_GetBasePath()`). Contents:

- App drawables + files under `composeResources/{drawable,files}/`
- `font/NotoSans.ttf` — the default variable font. Bundling it is the APP's
  job: each app opts in via `registerComposeFontBundling { bundleNotoSans =
  true; … }` (buildSrc — every flag is opt-in, no flag = no-op), which
  registers its `downloadNotoFonts` task (into `<app>/build/fonts/`); the
  library ships no download task. Pass `-PbundleDefaultFont=false` to skip.
- `font/NotoSansMono.ttf` — `autoDetectNotoSansMono = true` bundles it when
  `FontFamily.Monospace` appears in the app sources (demo);
  `bundleNotoSansMono = true` forces it (apidemo's body font, loaded through
  its own seam)
- Material Symbols fonts for the styles the app **actually uses**
  (`bundleMaterialSymbols = true`) — the Zip task scans the app's Kotlin
  sources for `MaterialSymbolsOutlined` / `Rounded` / `Sharp` and only
  bundles the fonts referenced.
- `-PsubsetIcons=true` (default on, opted into per app via
  `enableIconSubsetting = true` — apidemo yes, demo no):
  `scripts/subset-material-symbols.py` scans app sources for
  `MaterialSymbols.<Name>` usage and hb-subsets each bundled font down to
  just those glyphs. Needs `hb-subset` on PATH (`brew install harfbuzz` /
  `apt install harfbuzz-utils`) — falls back to the full font if absent.

## Vendor sync workflow

```bash
# Sync every module's compose-fork.txt against the pinned upstream ref.
scripts/compose-fork/sync.sh
```

Upstream ref: `scripts/compose-fork/compose.properties` — set to a durable tag
of `JetBrains/compose-multiplatform-core`. Bump the ref → re-sync → let the
build tell you what broke. Per-module sync, manifest re-formatting, and the
drift / vendor-clean guardrails are in
[TOOLING.md](TOOLING.md#vendoring-upstream-compose).

## Key files by area — start here when you need to find something

### Renderer + main loop
- `compose/desktop/native/window/src/nativeMain/…/ComposeWindow.kt` — main loop,
  recomposer lifecycle, SDL event dispatch, composition-local seeding.
- `compose/ui/ui/src/nativeMain/…/RenderBackend.kt` — the interface.
- `compose/ui/ui/src/nativeMain/…/GpuMode.kt` — sealed driver picker
  (`Auto` / `Software` / `Skia.OpenGL` / `Skia.Metal`).
- `compose/ui/ui/src/skikoRendererMain/…/renderer/skia/SkiaRenderBackend.kt` —
  the Skia render backend (shared by every native target).
- `compose/ui/ui/src/skikoRendererMingwMain/…` — mingwX64-only Skia actuals
  bound against the skiko fork's flat extern-C surface.

### Layout / composition wiring
- `compose/ui/ui/src/commonMain/…/node/ComposeRootHost.kt` — root LayoutNode
  host, hit-test, event dispatch, snapshot observer.
- `compose/ui/ui/src/commonMain/…/node/impl/ComposeOwner.kt` — the
  project `Owner` implementation + `ProjectOwnedLayer` (graphicsLayer / clip /
  alpha bridge).
- `compose/ui/ui/src/commonMain/…/node/NodeApplier.kt`.

### Text (all in `:ui-text` now)
- `compose/ui/ui-text/src/nativeMain/…/ui/text/SkiaParagraph.native.kt` — the
  `Paragraph` actual (skiko-free; the 30 interface methods over plain data),
  driving the skiko engine through the `NativeParagraphOps` seam.
- `compose/ui/ui-text/src/skikoRendererMain/…/renderer/skia/SkiaParagraphEngine.kt`
  — `SkiaParagraphOps` (builds/queries the real skiko `skparagraph`) + `SkiaFonts`
  (data.kres bytes → skiko Typeface; the port's family/variable-axis model).
- `compose/ui/ui-text/src/nativeMain/…/ui/text/ParagraphFactories.native.kt` —
  actuals for the `Paragraph(…)` / `ParagraphIntrinsics(…)` factory family.

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

## Tooling

The full tooling reference is [TOOLING.md](TOOLING.md): the static-lib build,
vendor sync + drift/clean guardrails, API coverage, the `verify-mac` runbook,
the native-vs-JVM parity harness, the interaction probe, and the frame
profiler. Quick rules of thumb:

- Whole-project renderer/layout change → run **parity** (`scripts/parity/parity.py`),
  the broad net.
- Chasing one reported interaction → run the **probe** (`scripts/probe/`), targeted.
- Slow frame → **profiler** first (`CDN_PROFILE=1`), optimize second.
- Any renderer change → the **`verify-mac`** runbook gates it before commit.

See [RENDERER.md](RENDERER.md) for the renderer work these support.

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

## Useful Gradle tricks

- `--args="--gpu=skia.opengl --screen=Buttons"` — pass CLI to the demo.
- `--info` — see cinterop classpath + include paths actually used.
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
