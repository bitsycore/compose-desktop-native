# RENDERER.md

The durable reference for the rendering layer: how it is structured, the
convergence work that shaped it, the decisions worth remembering, and what is
left. Read this before touching anything under `renderer/`, the graphics
actuals, or the layer engine.

For build and verification commands see [TOOLING.md](TOOLING.md). For module
layout, source-set hierarchy, and vendoring rules see [CLAUDE.md](CLAUDE.md).

## 1. The renderer

> **This branch is Skia-only.** The from-scratch SDL renderer leg
> (`sdlRendererMain`: `Sdl3Canvas` / `Sdl3TextRenderer` / `FreeTypeIcons` / the
> SDL render nodes, ~3,800 lines) and its `SDL3_ttf` / `SDL3_image` / FreeType
> cinterops were removed once Skiko-on-mingwX64 landed (Route 1a — see
> [SKIKO-MINGW-FEASIBILITY.md](SKIKO-MINGW-FEASIBILITY.md)). SDL3 remains as the
> windowing / input / platform layer only. Below, references to "the SDL leg", a
> second `RenderBackend` actual, `-Prenderer=sdl3`, and the SDL-leg fidelity work
> are **historical** — kept because the convergence decisions still explain the
> Skia leg's shape.

Rendering goes through one `RenderBackend`, implemented once: the **Skia leg**
(`skikoRendererMain`). It draws through upstream Compose Multiplatform's own Skia
stack, vendored verbatim: `SkiaBackedCanvas`, `SkiaBackedPaint`, `SkiaShader`,
`actual class GraphicsLayer` backed by `org.jetbrains.skiko.node.RenderNode`,
`SkiaGraphicsContext` — the layer and draw engine internals are literally
upstream. macOS/Linux link the official Skiko klibs; mingwX64 links the
bitsycore skiko **fork** (`skikoRendererMingwMain`, Route 1a).

The text pipeline is now upstream's own `SkiaParagraph` (skparagraph, vendored
verbatim); glyph rasterization is Skia. GPU path per platform: Metal (macOS),
OpenGL (Linux + Windows), with a CPU-raster `Software` fallback.

The seam is kept as narrow and low as possible. Code flows
`Common (upstream) -> shared native engine -> Skia actual`. What we
minimize is hand-rolled actual surface, not actual-side line count: a fat
vendored-upstream actual (the Skia leg carrying upstream's whole GraphicsLayer
stack) is preferred over a thin hand-written one.

## 2. The retained-layer engine (the model we copied)

Upstream skiko skips work at three levels. The Skia leg has all three.

- **L1 frame scheduling.** A frame schedules the next only if still dirty.
  Ours: `ComposeWindow.shouldRender()` gates `renderFrame()`; the loop blocks on
  `SDL_WaitEventTimeout` when idle.
- **L2 measure/layout only dirty nodes.** Vendored `MeasureAndLayoutDelegate`
  used verbatim through `ComposeOwner.measureAndLayout()`.
- **L3 draw record-once / replay** (the expensive one). Each isolated
  `LayoutNode` gets an `OwnedLayer` (`GraphicsLayerOwnerLayer`) owning a
  `GraphicsLayer`. The display list re-records only when content is dirty;
  otherwise it replays.

The critical property: **transform, alpha, and clip changes do NOT re-record.**
Moving, scaling, rotating, or fading a layer replays cached content under a new
transform. Only a genuine content change (a state read inside the draw block, or
a resize) re-records. Upstream replays the whole scene every frame with no
dirty-region present; the entire win is not re-recording clean layers, not
drawing less screen. Dirty-region rendering is therefore an explicit non-goal.

The compositing-strategy contract (`requiresLayer()`, from
`SkiaGraphicsLayer.skiko.kt`) decides when a layer needs an offscreen:

| Condition | Auto | Offscreen | ModulateAlpha |
|---|---|---|---|
| `alpha < 1` | offscreen | offscreen | per-op alpha multiply (no offscreen) |
| `colorFilter != null` | offscreen | offscreen | offscreen |
| `blendMode != SrcOver` | offscreen | offscreen | offscreen |
| `renderEffect != null` | offscreen | offscreen | offscreen |
| none of the above | replay in place | always offscreen | replay in place |

## 3. Current state

- **Skia leg runs upstream's engine.** Canvas (`SkiaBackedCanvas`) and
  GraphicsLayer/GraphicsContext (`org.jetbrains.skiko.node.RenderNode`) are
  vendored verbatim. The Skia leg gets real display-list caching and correct
  clip/shadow/renderEffect for free from upstream. After this landed, Skia
  `draw` on LazyColumn fell from 1.75 ms to 0.2 ms.
- **Parity is a golden-master against JVM** (about 2% median, dominated by the
  shared text engine's small metric delta). See TOOLING.md for how to read it.
- **Memory is stable.** The historical composition leak is fixed (see section 6)
  and guarded by the `--soaktest` gate in the verify runbook.

## 4. Decisions to remember

- **Goal is G1, cheap upstream-tracking.** The target is low per-bump
  reconciliation cost when following upstream. All platforms now render through
  real Skia — mingwX64 via the bitsycore skiko fork (Route 1a; see
  [SKIKO-MINGW-FEASIBILITY.md](SKIKO-MINGW-FEASIBILITY.md)) — so there is no
  fidelity tier below the JVM cross-check. The JVM Compose Desktop target
  (`:demo:run`) remains the fidelity/parity reference on any host.
- **Vendor, do not hand-roll.** Copy upstream verbatim wherever it compiles.
  Edit-to-compile becomes a manual vendor with a `// VENDOR-BASE:` header so the
  drift tripwire can track it. The litmus test for any divergence: "Is this what
  upstream does? If not, what real platform constraint forces the difference?"
  Valid answers name a constraint (e.g. no windowing/input toolkit in K/N ->
  SDL3). "It was easier" is not valid.
- **Text is now upstream's engine (B6.3 done).** Skia-leg text draws through
  upstream's own `SkiaParagraph` (skparagraph), vendored verbatim — the
  font-subsystem replacement landed: upstream `PlatformFont`/`FontCache`/
  `FontCollection` back a `data.kres`-fed font supply, replacing the port's
  name-to-bytes engine. glyph rasterization stays Skia. This subsumes the P3.1
  metrics work (measurement is now upstream's, so the numbers match by
  construction).
- **Module split (`:ui-graphics` / `:ui-text`) is done.** The blocker — the
  `sdl3` cinterop being a shared substrate that Kotlin/Native can't cleanly
  share across a module boundary — was resolved by extracting a non-upstream
  `:sdl-core` base module that owns the cinterop and api-exposes it. With that
  base in place, `androidx.compose.ui.graphics.*` / `.text.*` split off into
  `:ui-graphics` / `:ui-text` (matching upstream artifacts), leaving `:ui` as
  the Compose core + Skia renderer + SDL bridges. `RenderBackend.drawRoot` takes
  `(Canvas) -> Unit`, decoupling the backends from `ComposeRootHost`.
- **Real Skia on Windows K/N shipped (Route 1a).** mingwX64 links the bitsycore
  skiko fork against `skiko-windows-x64.dll` (with an embedded GNU import lib),
  published to GitHub Packages and auto-provisioned by the bridge plugin. This
  replaced the SDL renderer as the Windows path. See
  [SKIKO-MINGW-FEASIBILITY.md](SKIKO-MINGW-FEASIBILITY.md) for the ABI details
  and the route trade-offs.

## 5. Convergence status

| Item | Status |
|------|--------|
| Guardrails (parity gate, verify-mac runbook, drift + vendor-clean checks) | Done |
| B2: Skia leg on upstream GraphicsLayer/GraphicsContext | Done |
| B6.1: Skia leg on upstream `SkiaBackedCanvas` + paint/shader | Done |
| B6.2: upstream `GraphicsLayer` + delete transient port cluster | Done |
| B6.3: upstream text (`SkiaParagraph`) on the Skia leg | Done |
| B5: engine-convergence deltas audit | Done (clean wins spent by B6) |
| P2.2: composition memory leak | Fixed + soak-gated |
| P2.3: outsets / blur / renderEffect | Done via upstream `SkiaGraphicsLayer` |
| P3.1: text metrics parity (17% median -> 2%) | Done |
| Module split (`:ui-graphics` / `:ui-text` via `:sdl-core`) | Done |
| Track A: real Skia on Windows (Route 1a) | Done |

## 6. Remaining and future work

Nothing here blocks day-to-day work. These are the open threads worth
remembering.

- **WIN-SMOKE (pre-ship, Windows only).** The Mac runbook cannot cover the
  shipped mingwX64 binary, the Windows-only `PrintWindow` probe, or the
  common-metadata publish job. Run these on a Windows host before any release.
  This is the only outstanding verification.
- **Stabilization at Compose 1.12.0 stable.** The vendored refs are pinned to
  `v1.12.0-beta03+dev4483` (no clean beta03 tag exists yet, and it is not on
  Maven). The native side leads the JVM parity leg (forced to beta02, the latest
  published) by a documented skew. When 1.12.0 stable ships: re-pin both refs to
  the clean tag, bump the `vComposeJvmVersion` forcing in demo/apidemo/
  material-symbols to close the skew, then cut the release. The concrete
  step-by-step (the version map, the ref-bump flow, and the release flow) lives
  in [TOOLING.md](TOOLING.md#versioning-and-releasing).
- **Deferred engine deltas (D2-D6, all low ROI under G1).**
  - D2: split `GraphicsLayerOwnerLayer` per-leg to restore upstream's exact
    `setLightingInfo` shadow lighting. Feasible (the old RootNodeOwner-coupling
    blocker was outdated) but high blast radius for a marginal gain; elevation
    shadows already match JVM.
  - D3: dedupe `LayerTransformationMatrix.kt` against the now-vendored
    `Matrices.skiko`. Blocked on D2 (the shared owner-layer hit-test needs a
    matrix fn usable from the owner layer).
  - D4/D5: `SemanticsRegion` intersect/difference are stubs, and `CharHelpers`
    is naive grapheme/bidi vs upstream ICU. Real fidelity gaps, gated on an
    accessibility or complex-script roadmap.
  - D6: `Focusability` / `PlatformVelocityTracker` are byte-equal to upstream;
    vendoring them saves nothing.
- **Native-resource lifecycle.** Wire `GraphicsLayer`/RenderNode +
  `SdlImageBitmap.close()` fully into cache eviction and the renderer
  `destroy()` chain; demote the periodic GC nudge once ownership covers it.

## 7. Hard-won learnings (do not relearn)

- **Minimize-divergence is load-bearing.** A hand-written `notifyLayerIsDirty`
  that diverged from upstream `OwnedLayerManagerImpl` removed a layer from
  `dirtyLayers` mid-loop and crashed on any navigation. Screenshots missed it;
  the interaction probe caught it. Match upstream verbatim; do not hand-roll
  engine plumbing from a summary.
- **The composition memory leak (root cause).** The port never called
  `OwnerSnapshotObserver.clearInvalidObservations()`, which upstream
  `RootNodeOwner` runs after every measure. Snapshot read-observations for
  scopes invalidated on dispose lingered forever, each pinning its observed
  object graph via a Kotlin/Native `ExternalRCRef` (a K/N-heap leak, vmmap
  Memory Tag 246). Most visible on ripple/indication draws. Fix:
  `ComposeRootHost.measureAndLayout()` sweeps `clearInvalidObservations()` after
  layout, plus `ComposeOwner.onDetach` clears the detached node's observations.
  Found by exact live-counters, static-mode isolation, macOS `leaks`/`heap`/
  `vmmap`, and component bisection. RSS alone cannot pinpoint a referenced leak;
  budget heap tooling for this class.
- **Screenshots miss crashes and settle-timing.** Free-running screenshots on
  animated/settling screens give false signals. Use render-to-quiescence +
  virtual frame time for parity, the probe for interaction/crash coverage.
- **The profiler `present` phase is vsync-capped by the display refresh.**
  Profile on the target monitor before concluding a frame-rate gap.

## 8. Explicit non-goals

- **Dirty-region / partial present.** Upstream replays the whole scene; the win
  is not re-recording, not redrawing less.
- **A custom `cacheKey` API.** Superseded by the real per-node display list.
- **Vendoring `RootNodeOwner` / the `ComposeScene` stack.** Coupled to skiko's
  `SkiaLayer` and windowing; it fights the `:desktop-native-window` SDL loop. The port borrows
  only the layer engine.

## 9. Key files

- `compose/ui/ui/src/nativeMain/.../RenderBackend.kt`: the interface.
- `compose/ui/ui/src/nativeMain/.../GpuMode.kt`: renderer / driver picker.
- `compose/ui/ui/src/skikoRendererMain/.../renderer/skia/SkiaRenderBackend.kt`.
- `compose/ui/ui/src/commonMain/.../node/ComposeRootHost.kt`: root host,
  hit-test, event dispatch, snapshot observer sweep.
- `compose/ui/ui/src/commonMain/.../node/impl/ComposeOwner.kt`: the project
  `Owner` + `GraphicsLayerOwnerLayer` bridge.
- `compose/ui/ui-text/src/nativeMain/.../ui/text/SkiaParagraph.native.kt`: the
  upstream `Paragraph` actual (measurement, hit-test, line metrics, span painting).
