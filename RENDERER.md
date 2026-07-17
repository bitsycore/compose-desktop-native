# RENDERER.md

The durable reference for the rendering layer: how it is structured, the
convergence work that shaped it, the decisions worth remembering, and what is
left. Read this before touching anything under `renderer/`, the graphics
actuals, or the layer engine.

For build and verification commands see [TOOLING.md](TOOLING.md). For module
layout, source-set hierarchy, and vendoring rules see [CLAUDE.md](CLAUDE.md).

## 1. The two renderer legs

Rendering is pluggable behind one `RenderBackend` interface. Exactly one leg is
active per target:

- **Skia leg** (`skikoRendererMain`, macOS + Linux). Draws through upstream
  Compose Multiplatform's own Skia stack, vendored verbatim: `SkiaBackedCanvas`,
  `SkiaBackedPaint`, `SkiaShader`, `actual class GraphicsLayer` backed by
  `org.jetbrains.skiko.node.RenderNode`, `SkiaGraphicsContext`. On this leg the
  layer and draw engine internals are literally upstream.
- **SDL leg** (`sdlRendererMain`, Windows always; macOS/Linux under
  `-Prenderer=sdl3`). A from-scratch renderer on `SDL_RenderGeometry` +
  `SDL3_ttf` + FreeType. SDL is a triangle blitter, so this leg carries a
  permanent bespoke surface (about 3,800 lines): `Sdl3Canvas`,
  `Sdl3TextRenderer`, `FreeTypeIcons`, and the SDL render nodes.

Both legs share one project-owned text and image pipeline (`SdlParagraph`,
`NativeTextMeasurer`, `IconFont`). Text layout, line-breaking, metrics, and
hit-test are the port's engine on both legs; only glyph rasterization differs
(Skia vs SDL3_ttf/FreeType). This is a deliberate axiom, not a gap: see the
text decision in section 4.

The seam is kept as narrow and low as possible. Code flows
`Common (upstream) -> shared native engine -> Skia actual / SDL actual`. What we
minimize is hand-rolled actual surface, not actual-side line count: a fat
vendored-upstream actual (the Skia leg carrying upstream's whole GraphicsLayer
stack) is preferred over a thin hand-written one.

## 2. The retained-layer engine (the model we copied)

Upstream skiko skips work at three levels. The port has all three.

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
`SkiaGraphicsLayer.skiko.kt`) must match on both legs. It decides when a layer
needs an offscreen:

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
- **SDL leg ships the geo node as the Windows default.** `SdlDisplayListRenderNode`
  records a leaf's layer-local tessellated geometry, text runs, and icon glyphs
  by params (not texture pointers), then re-emits through the layer transform.
  Crisp under any transform, bit-exact, deterministic (no render-target state),
  and eviction-safe (replay re-looks-up via the run LRU / FreeType glyph cache).
  0.000% across a 57-screen self-consistency sweep bar a sub-0.06% rotated-edge
  AA fringe. LazyColumn steady-state `draw` fell 57% versus the un-cached
  baseline. Fallbacks: `CDN_LAYERCACHE=off|texture`.
- **Parity is a golden-master on the Skia leg** (about 2% median, dominated by
  the shared text engine's small metric delta) and a bounded, ranked signal on
  the SDL leg. See TOOLING.md for how to read it.
- **Memory is stable.** The historical composition leak is fixed (see section 6)
  and guarded by the `--soaktest` gate in the verify runbook.

## 4. Decisions to remember

- **Goal is G1, cheap upstream-tracking.** The target is low per-bump
  reconciliation cost when following upstream, not Windows pixel-parity or
  feature completeness. The JVM Compose Desktop target (`:demo:run`) is the
  documented Windows fidelity/feature tier: real Skia, full fidelity, on any
  host. That makes SDL-leg fidelity work (B3) and real-Skia-on-Windows (Track A)
  optional polish, not obligations.
- **Vendor, do not hand-roll.** Copy upstream verbatim wherever it compiles.
  Edit-to-compile becomes a manual vendor with a `// VENDOR-BASE:` header so the
  drift tripwire can track it. The litmus test for any divergence: "Is this what
  upstream does? If not, what real platform constraint forces the difference?"
  Valid answers name a constraint (no Skiko on Windows K/N; SDL is a triangle
  blitter). "It was easier" is not valid.
- **B2 convergence is complete.** The Skia leg was migrated off the port's
  hand-rolled GraphicsLayer onto upstream's own. This was a source-set migration,
  not a file-flip: the SDL node cluster relocated `nativeMain -> sdlRendererMain`,
  `GraphicsContext` forked per-leg behind a `createGraphicsContext()` seam, and
  the upstream skiko files were un-refused in `compose-fork.txt`. Measured
  sync-tax reduction on the skiko path: about 8.5x (roughly 1240 hand-rolled
  lines down to about 145 lines of drift-tracked edits; roughly 1006 lines now
  auto-sync verbatim on a ref bump). The beta02 -> beta03 bump proved it: zero
  reconciliation, all manual-vendor bases unchanged.
- **Text stays the port's engine (B6.3 skipped).** Making Skia-leg text truly
  upstream (`SkiaParagraph`) is a font-subsystem replacement, not a canvas swap:
  two coexisting font-identity models (the port's name-to-bytes `IconFont` +
  no-op resolver vs upstream `PlatformFont`/`FontCache`/`FontCollection`), about
  13 skiko files to re-vendor, about 10 shared actuals to split, a
  `data.kres`-to-`FontCache` bridge, no green intermediate, and it discards the
  P3.1 metrics work. ROI is low: the Skia leg already measures text with skiko
  `Font` metrics (via `currentTextMeasurer` -> `SkiaTextRenderer`), which is how
  parity reached about 2%. Revisit only if complex-script/bidi fidelity on the
  native Skia leg becomes a hard requirement.
- **`GraphicsLayerOwnerLayer` stays shared.** The fork point is `GraphicsLayer` /
  `GraphicsContext`, not the owner layer. The shared owner layer compiles
  against both `GraphicsLayer` actuals (upstream skiko + port SDL) only if both
  satisfy the commonMain `expect class GraphicsLayer` API. That API-parity
  invariant is enforced by `:ui:compileCommonMainKotlinMetadata` on every build.
- **Lifetime model.** The SDL leg uses GC / release-queue for layer lifetime and
  does NOT vendor `ChildLayerDependenciesTracker`. The Skia leg uses upstream's
  `SkiaGraphicsLayer`, which inherently uses the tracker (already vendored in
  commonMain). A per-leg lifetime divergence is the class of bug that caused an
  early navigation crash, so any change here goes to both legs plus a soak gate.
- **Module split (`:ui-graphics` / `:ui-text`) is shelved as infeasible.** Not
  cosmetic churn as originally scoped: the `sdl3` cinterop is a shared substrate
  used by 11 graphics-side files AND 12 platform/windowing/node/resources files
  that stay in `:ui`. Kotlin/Native cannot cleanly share a cinterop klib across a
  module boundary, which is exactly why they were merged. A real split needs a
  new non-upstream `:ui-cinterop` base module plus cross-module cinterop
  api-exposure, which is bigger and more divergent than the "match upstream
  artifacts" goal it was meant to serve. The one keeper from the attempt:
  `RenderBackend.drawRoot` now takes `(Canvas) -> Unit`, decoupling the backends
  from `ComposeRootHost` (a genuine improvement, retained).
- **Track A (real Skia on Windows K/N) is shelved.** Kotlin/Native mingwX64 is
  GNU-ABI; skia-pack Windows is MSVC-ABI, which cannot be statically fused into a
  GNU-ABI binary. Building current C++20 Skia to a GNU/mingw static archive is an
  open-ended fork (only abandoned Mozilla-era precedent). The only working route
  is a runtime DLL, which breaks the no-DLL invariant and adds tens of MB on the
  one platform it targets. The SDL leg is the permanent Windows renderer; JVM is
  the fidelity escape hatch.

## 5. Convergence status

| Item | Status |
|------|--------|
| Guardrails (parity gate, verify-mac runbook, drift + vendor-clean checks) | Done |
| B2: Skia leg on upstream GraphicsLayer/GraphicsContext | Done |
| B6.1: Skia leg on upstream `SkiaBackedCanvas` + paint/shader | Done |
| B6.2: upstream `GraphicsLayer` + delete transient port cluster | Done |
| B6.3: upstream text (`SkiaParagraph`) on the Skia leg | Skipped (decision) |
| B5: engine-convergence deltas audit | Done (clean wins spent by B6) |
| P2.2: composition memory leak | Fixed + soak-gated |
| P2.3: outsets / blur / renderEffect | Done via upstream `SkiaGraphicsLayer` |
| P3.1: SDL text metrics parity (17% median -> 2%) | Done |
| B3: further SDL fidelity | Capped (parity-ranked wins only) |
| Module split | Shelved (infeasible as specified) |
| Track A: real Skia on Windows | Shelved |

## 6. Remaining and future work

Nothing here blocks day-to-day work. These are the open threads worth
remembering.

- **WIN-SMOKE (pre-ship, Windows only).** The Mac runbook covers both renderers
  but cannot cover the shipped mingwX64 binary, the Windows-only `PrintWindow`
  probe, or the common-metadata publish job. Run these on a Windows host before
  any release. This is the only outstanding verification.
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
    both-legs matrix fn).
  - D4/D5: `SemanticsRegion` intersect/difference are stubs, and `CharHelpers`
    is naive grapheme/bidi vs upstream ICU. Real fidelity gaps, gated on an
    accessibility or complex-script roadmap.
  - D6: `Focusability` / `PlatformVelocityTracker` are byte-equal to upstream;
    vendoring them saves nothing.
- **SDL performance (opportunistic).** Shared glyph atlas to replace per-run
  textures; `drawImageRect` capture in the geo node (journal rates it low-ROI).
- **SDL_GPU backend (long-term).** Real stencil clipping, pipelined batching,
  shader gradients. The `NativeRenderNode` seam makes a GPU node a clean
  drop-in.
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
- **Offscreen-texture caching is timing-nondeterministic on complex screens.**
  This is why the geo (no-render-target) node is the robust default, not the
  texture node.
- **Rounded/path layer clips must be applied or deferred on the fast path.** The
  geo path submits raw geometry, which clips only to a rect; rounded clips are a
  lazy offscreen mask the fast replay bypasses. Rect clip on the fast path;
  rounded/generic clips fall back to block-replay.
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
  `SkiaLayer` and windowing; it fights the `:window` SDL loop. The port borrows
  only the layer engine.

## 9. Key files

- `compose/ui/ui/src/nativeMain/.../RenderBackend.kt`: the interface.
- `compose/ui/ui/src/nativeMain/.../GpuMode.kt`: renderer / driver picker.
- `compose/ui/ui/src/skikoRendererMain/.../renderer/skia/SkiaRenderBackend.kt`.
- `compose/ui/ui/src/sdlRendererMain/.../renderer/sdl/Sdl3RenderBackend.kt`.
- `compose/ui/ui/src/sdlRendererMain/.../renderer/sdl/FreeTypeIcons.kt`:
  variable-font axis rasterization.
- `compose/ui/ui/src/commonMain/.../node/ComposeRootHost.kt`: root host,
  hit-test, event dispatch, snapshot observer sweep.
- `compose/ui/ui/src/commonMain/.../node/impl/ComposeOwner.kt`: the project
  `Owner` + `GraphicsLayerOwnerLayer` bridge.
- `compose/ui/ui/src/nativeMain/.../ui/text/SdlParagraph.native.kt`: the bridged
  `Paragraph` (measurement, hit-test, line metrics, span painting).
