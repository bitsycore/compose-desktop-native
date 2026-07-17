# Tooling

Helper scripts and workflows used to build, vendor, and verify Compose Desktop
Native. Each has a focused job; the deeper ones link to their own README.

For architecture, source-set layout, and vendoring rules, see
[CLAUDE.md](CLAUDE.md).

## Native libraries

SDL3, SDL3_ttf, SDL3_image, and FreeType are built from source as static
libraries and linked straight into the executable. One script does it on every
OS. Output lands in the gitignored `libs/`; versions are pinned in
`scripts/build-sdl/build-sdl.properties`.

```bash
python3 scripts/build-sdl/build-all.py            # freetype, sdl3, sdl3-image, sdl3-ttf
python3 scripts/build-sdl/build-all.py sdl3-ttf   # rebuild one step
```

Needs `git`, `cmake`, and Python 3 on every host (`ninja` is fetched when
absent). Run it once per machine, or after bumping the pinned versions.

## Vendoring upstream Compose

Most `androidx.compose.*` code is copied byte for byte from
`JetBrains/compose-multiplatform-core` into each module's gitignored
`src/vendor/`. A `compose-fork.txt` manifest per module lists which upstream
files it pulls; the pinned ref lives in `scripts/compose-fork/compose.properties`.

```bash
scripts/compose-fork/sync.sh                             # re-sync every module
scripts/compose-fork/sync.sh compose/ui/ui/compose-fork.txt   # one module
```

Reach for this after a fresh checkout or a ref bump. Details:
[scripts/compose-fork/README.md](scripts/compose-fork/README.md).

Guardrails that keep the vendor tree honest:

```bash
python3 scripts/compose-fork/check-vendor-clean.py   # src/vendor matches the pinned refs
python3 scripts/compose-fork/check-vendor-drift.py   # hand-edited "manual vendors" match their pin
```

`check-vendor-clean` fails if a hand-edit ever leaks into `src/vendor/`.
`check-vendor-drift` reads the `// VENDOR-BASE:` header on every edited copy and,
using the local upstream clone, reports whether the base actually changed since
the pin (needs reconciling) or is merely stale (safe to re-stamp). Run both on
every ref bump.

### Coverage against upstream

How much of upstream's public API the port covers is measured, not guessed.

```bash
./gradlew apiDump && python3 scripts/compose-coverage.py   # per-module coverage tables
python3 scripts/compose-coverage.py --missing ui-text      # list uncovered declarations
```

## Verification

### One-command gate

`verify-mac.sh` is the runbook to run after any renderer or layout change. On
macOS or Linux it exercises BOTH renderer legs (Skia by default, then
`-Prenderer=sdl3`) and exits non-zero on any failure:

```bash
scripts/verify-mac.sh
```

It runs, per leg: the vendor drift and clean checks, a build of `:demo` and
`:apidemo`, the interaction probes, the parity sweep, a memory soak, and a
frame-time spot check. The Windows target is verified separately (see below).

### Parity: native vs JVM

`:demo` renders the same shared screens on two stacks: this port, and stock JVM
Compose Desktop. `parity.py` screenshots every screen on both and pixel-diffs
them, so a screen that diverges is a porting bug. It gates against per-screen
golden baselines.

```bash
python3 scripts/parity/parity.py                 # all screens
python3 scripts/parity/parity.py Buttons Shapes  # a subset
python3 scripts/parity/parity.py --no-build      # reuse the last renders
```

How to read the diff and the percentage: [scripts/parity/README.md](scripts/parity/README.md).

### Interaction probe

`probe.py` launches a native window, sends window-relative input (click, hover,
hold), and captures the client area. It packages the rigs used to reproduce
interaction bugs. Windows-only. Details:
[scripts/probe/README.md](scripts/probe/README.md).

### Frame profiler

Set `CDN_PROFILE=1` (or `CDN_PROFILE=<path>`) and run any app. Every couple of
seconds it writes per-phase main-loop timings (events, app pump, render, with
draw sub-phases and per-frame draw counters) to a file. `CDN_FORCERENDER=1`
renders every frame so steady-state timings read on an otherwise idle screen.

```bash
CDN_PROFILE=1 CDN_FORCERENDER=1 ./demo/build/bin/macosArm64/debugExecutable/demo.kexe
```

Note: `present` is vsync-blocking, so profile on the target refresh rate before
drawing conclusions about a frame-rate gap.

### Demo probes

`:demo` ships headless regression probes driven by CLI flags, used by the
verify runbook and in isolation:

```bash
demo.kexe --nav3test        # Navigation 3 + ViewModel + lifecycle
demo.kexe --backtest        # predictive-back / BackHandler
demo.kexe --clicktest       # pointer -> upstream clickable
demo.kexe --scrolltest      # wheel -> scrollable
demo.kexe --multiwintest    # multi-window lifecycle
demo.kexe --soaktest        # memory soak (CDN_SOAK_SCREEN, CDN_SOAK_STATIC, CDN_SOAK_CYCLES)
```

### Windows smoke

The Mac runbook cannot cover the Windows target. Before shipping, on a Windows
host: build the mingwX64 executables, run `scripts/probe/`, and run a publish
dry run (only Windows compiles the full common-metadata variant table).

```bat
gradlew.bat :demo:runDebugExecutableMingwX64
gradlew.bat :apidemo:runDebugExecutableMingwX64
```

## Consuming the port

To build a third-party app against the published klibs, apply the bridge Gradle
plugin and declare official Compose Multiplatform coordinates; the plugin swaps
in the port's klibs on native desktop targets. Setup:
[gradle-plugin/compose-desktop-native-bridge/README.md](gradle-plugin/compose-desktop-native-bridge/README.md).

## Versioning and releasing

### Version map

Versions live in a few places, some of which move in lockstep. There is no
single file to edit; know which axis you are changing.

| Version | Where | Notes |
|---------|-------|-------|
| Project release version | The git tag `vX.Y.Z`. `PUBLISH_VERSION` (from the tag) feeds `vPublishVersion` in `build.gradle.kts`, which strips the leading `v`. Group is `com.bitsycore.compose.sdl`. | Set by the tag, not edited by hand. A non-publish build is `0.0.0-SNAPSHOT`. |
| Vendored Compose (native side) | `COMPOSE_CORE_REF` and `COMPOSE_REF` in `scripts/compose-fork/compose.properties`, plus `compose` in `gradle/libs.versions.toml`. | Pin to a durable tag (not a `+dev` commit upstream may GC). Re-sync after changing. |
| JVM parity forcing | `vComposeJvmVersion` in `demo`, `apidemo`, and `material-symbols` `build.gradle.kts`. | Must be a version PUBLISHED to Maven Central. It may lag the vendored native ref (a documented skew) until the matching version is published. |
| Skiko | `skiko` in `gradle/libs.versions.toml`. | Must expose the `org.jetbrains.skiko.node` `RenderNode` / `GraphicsContext` API the vendored compose-core uses. Verify with a throwaway `skikoRendererMain` compile if unsure. |
| SDL3 / SDL3_ttf / SDL3_image / FreeType | `scripts/build-sdl/build-sdl.properties`. | Rebuild `libs/` with `build-all.py` after any change. |
| Bridge substituted version | Defaults to the bridge plugin's own published version; consumers override with the `composeDesktopNative.version` Gradle property. | The plugin publishes with the release tag, so a consumer on the matching plugin version resolves the right klibs automatically. |

### Bump the upstream Compose ref

Run this on each upstream bump; it is the flow that keeps the sync tax low.

1. Edit `COMPOSE_CORE_REF` / `COMPOSE_REF` in `compose.properties` (and `compose`
   in `libs.versions.toml` if the coordinate version moved).
2. `scripts/compose-fork/sync.sh`
3. `python3 scripts/compose-fork/check-vendor-drift.py`. For any manual vendor
   whose upstream base actually changed, reconcile by hand; otherwise re-stamp
   its `// VENDOR-BASE:` header to the new ref.
4. Build both legs and fix any fallout.
5. `scripts/verify-mac.sh` (both legs green, including the soak and parity gates).
6. If a matching Compose version is now published to Maven, bump
   `vComposeJvmVersion` in demo/apidemo/material-symbols to close the skew.
7. Run WIN-SMOKE on a Windows host.
8. Re-seed parity baselines only if metrics legitimately moved
   (`parity.py --update-baselines`), and record why.

### Cut a release

1. Complete the ref-bump flow above and confirm it is green. For a STABLE
   release, `vComposeJvmVersion` should match the vendored ref (no skew).
2. On a Windows host, run `gradlew :window:compileCommonMainKotlinMetadata`.
   Only Windows compiles the full common-metadata variant table, and the root
   KotlinMultiplatform publications live there; a macOS-only publish leaves the
   roots without mingwX64 variants (this bit v0.1.15).
3. `git tag vX.Y.Z && git push origin vX.Y.Z`. This triggers `.github/workflows/publish.yml`.

What the publish job does, so you can read a failure:

- Each host publishes only its own target's publications to GitHub Packages
  under `com.bitsycore.compose.sdl:*`. Windows additionally publishes the root
  KotlinMultiplatform metadata, the `jvm` publication, and the bridge plugin
  (only Windows declares every target).
- The `MODULES` list in the workflow must stay in sync with the modules in
  `settings.gradle.kts`.
- A partial publish (for example a dropped connection mid-upload) deletes this
  host's half-published versions for the tag and retries, so a rerun is safe.
- The demo and apidemo release binaries are zipped and attached to the GitHub
  Release for the tag.

4. Verify a consumer build against the new version resolves through the bridge.
