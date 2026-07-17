# RENDERER_TASKS.md — executable task tracker

The **why/analysis lives in `RENDERER_CONVERGE.md`** (the study file). This file is the
actionable checklist an agent works through over time.

**Goal DECIDED: G1 — upstream-tracking** (cheap upstream tracking with minimal per-bump
reconciliation; JVM is the Windows 100%-fidelity tier; do convergence + guardrails, NOT
Windows pixel-parity). See `RENDERER_CONVERGE.md` §0.5.

## How to use (agent instructions)
- Status marks: `- [ ]` todo · `- [~]` in progress · `- [x]` done · `- [-]` deferred/shelved.
- Finish a task → check it, then append a dated line to **PROGRESS LOG** (bottom) with the
  commit hash and how it was verified.
- Respect **blocked-by**: don't start a task until its blocker is `- [x]`.
- Every *code* task's done-criteria includes a **MAC-VERIFY** pass (both renderers build +
  self-tests + parity on macOS/Linux — see primitives below). Commit only after it's green.
- Keep tasks atomic. If one balloons, split it and note why in the log.
- Do NOT pick up anything under **Deferred / shelved** without an explicit new decision.

## Verification primitives (referenced by task done-criteria)
- **MAC-VERIFY** (macOS or Linux — runs BOTH renderers): `:demo`/`:apidemo`
  `runDebugExecutableMacosArm64` (Skia leg, default) **and** the same with `-Prenderer=sdl3`
  (SDL leg); run `--nav3test --backtest --clicktest --scrolltest --multiwintest` on both and
  gate on PASS/FAIL; run `scripts/parity/parity.py`; run the `--soaktest` memory gate; a `CDN_PROFILE=1 CDN_FORCERENDER=1`
  `draw`-ms spot-check on LazyColumn/Tabs (fail on >~20% regression vs the pre-change run).
- **WIN-SMOKE** (Windows, pre-ship only): link the mingwX64 target + run `scripts/probe/`.
- **DRIFT-CHECK**: re-run `sync.py` on a clean checkout + diff `src/vendor/`; run the
  manual-vendor provenance check (P0.6).

---

## Phase 0 — Guardrails & harness (do FIRST — makes everything else trustworthy)

- [x] **P0.1** Make `scripts/parity/parity.py` target-aware (`macosArm64` default + a
  `-Prenderer=sdl3` variant), not hardcoded to the mingw exe — so the Mac runs parity for
  BOTH legs. *Done:* one flag switches leg; both produce a ranked report. [CONVERGE §5/§8]
- [x] **P0.2** Add a one-command **MAC-VERIFY runbook** (`scripts/verify-mac.sh` or a
  Makefile/Gradle task) chaining the MAC-VERIFY primitive; non-zero exit on any failure.
  *Done:* `verify-mac` builds both legs + runs self-tests + parity + perf spot-check. [§5]
  *Ran end-to-end GREEN on macOS arm64 (exit 0): drift-check (vendor-clean + provenance) →
  both legs build (demo+apidemo) → 5 probes each (nav3/back/click/scroll/multiwin) →
  parity each → perf seeded (skia LazyColumn 1.75ms/Tabs 2.19ms; sdl3 2.13/6.98). Seeded
  the `macosArm64/{skia,sdl3}` parity keys + the perf baseline. First Mac run also surfaced
  + fixed a real parity harness bug (HiDPI crop-vs-resize — see P0.3 note / log).*
- [x] **P0.3** Font-align the JVM parity leg — load the bundled `NotoSans` in `MainJvm.kt`'s
  `ScreenHost` (stage it into jvm resources; apply via M3 Typography + `LocalTextStyle`).
  *Done (impl + load-verified on Windows):* resource staged, font loads + applies (no
  "not aligned" warning). **Finding:** on the **SDL leg** parity is NOT dominated by typeface
  but by an **accumulating vertical line-metric delta** (SDL3_ttf's NotoSans line height ≠
  Skia's) → text ghosts down the page, so the SDL-leg % barely moves (Buttons 16.5→17.1).
  The ~0% golden-master payoff is **Skia-leg (Mac) only** — pending a Mac run. Confirms the
  §1 "layout-engine delta". [§8]  *blocked-by: P0.1*
- [x] **P0.4** `parity.py`: per-screen golden baselines (`scripts/parity/baselines.json`,
  keyed `target/renderer`) + `--update-baselines` seeder + **exit non-zero** on breach
  (`> baseline + max(3pts, 25%)`) or `NATIVE FAILED`. *Done (Windows):* seeded 57 screens
  for `mingwX64/sdl3`; PASS exit 0, simulated regression exit 1 (`REGRESSION (+16.07)`),
  restore clean; output made ASCII-only (cp1252 console). *Re-seeded post-P0.5 (only
  Pickers 20.02→19.49 and Remember moved — the rest were already settled at frame 6).
  Now also seeded `macosArm64/skia` + `macosArm64/sdl3` (57 each) on the Mac — all three
  keys gate.* [§8]  *blocked-by: P0.1*
- [x] **P0.5** Render-to-quiescence capture on both legs (native `hasInvalidations()`; JVM
  `render(nanos)`/`hasInvalidations`), retiring the fixed `--frames=6`; "disable animations"
  seed for never-settling screens. *Done:* Pickers native-vs-JVM stable across repeated
  runs. **Implementation went further than planned:** quiescence alone didn't stabilise
  Pickers (its TimeInput focus → bring-into-view scroll races real-time frames → final
  offset varied ±10px per run), so the native leg also got **virtual frame time**
  (`useVirtualFrameTime` — clocks step a fixed 16.6ms/frame, mirroring the JVM leg's
  `render(nanos)`). The "disable animations" seed is `disableInfiniteAnimations`: a
  cancelling `InfiniteAnimationPolicy` in each window's Recomposer context (upstream's
  test mechanism), JVM via `ImageComposeScene(coroutineContext=…)`. Result: BOTH legs
  byte-identical across repeated runs, all 57 screens. `--frames=N` is now a quiescence
  CAP (default 300), not a fixed capture point. [§8]
- [x] **P0.6** Manual-vendor **drift tripwire**: `// VENDOR-BASE: <upstream-path>@<ref>`
  headers + a script that flags when a manual vendor's recorded ref lags the current pin.
  *Done:* `scripts/compose-fork/check-vendor-drift.py` reports clean today (exit 0) and a
  simulated stale ref fails (exit 1). *(File-set corrected: `NativeRenderNode.kt` is a port
  invention with no upstream base — annotated the real copy-edits instead: `GraphicsLayer
  OwnerLayer.kt`, `GraphicsLayer.native.kt`, `LayerTransformationMatrix.kt`.)* [§8/§9]
- [x] **P0.7** **Vendor-clean** check target: `sync.py` on a clean checkout + diff
  `src/vendor/`; wire into the runbook. *Done:* passes clean; fails if a hand-edit leaks into
  `src/vendor/`. *(Implemented as `scripts/compose-fork/check-vendor-clean.py`: hash
  src/vendor → run sync.py → re-hash; any changed/stale/missing file or manifest-annotation
  churn fails exit 1 — and the tree is left RESTORED to the pin (sync's normal behaviour).
  Wired into `verify-mac.sh` as step 0 together with the P0.6 tripwire.)* [§8]

## Phase 1 — B2: vendor upstream GraphicsLayer on the Skia leg (the core convergence)

*The maintainability payoff — macOS/Linux run upstream's own layer/draw engine. Re-rated
MODERATE (a source-set migration, not a file-flip). See CONVERGE §4 (B2), §6, §7.*

- [x] **P1.1** **skiko-version alignment check** (DoD gate): confirm `skiko:0.150.1` exposes
  the `RenderNode`/`GraphicsContext` API the compose-core `beta01+dev4324` vendored files
  use; if not, bump `libs.versions.toml` skiko (or the compose ref) in lockstep. *Done:* a
  throwaway skiko-leg compile referencing `org.jetbrains.skiko.node.RenderNode` succeeds on
  the Mac. [§3/§7]  *blocked-by: P0.2*  — **blocks all of P1**
  *Done (Mac): skiko:0.150.1 macosArm64 klib exports `package org.jetbrains.skiko.node`
  with `RenderNode` + `RenderNodeContext` (constructor `RenderNode(ctx)`, `RenderNodeContext
  (measureDrawBounds)`, `.setLightingInfo(...)`, `.close()` — the exact surface beta02's
  SkiaGraphicsContext/SkiaGraphicsLayer use). Verified two ways: klib linkdata inspection +
  a throwaway `skikoRendererMain` file compiling those references via `:ui:compileKotlin
  MacosArm64` (BUILD SUCCESSFUL, then removed). NO skiko/compose-ref bump needed.*
- [x] **P1.2** Relocate the SDL node cluster `nativeMain → sdlRendererMain`:
  `GraphicsLayer.native.kt`, the `createNativeRenderNode` `expect` + SDL actual,
  `DeferredRenderNode`, `SdlDisplayList*`/`SdlRenderNode`. **Keep `GraphicsLayerOwnerLayer`
  SHARED.** *Done:* per-target compile green; MAC-VERIFY green (behaviour unchanged — still
  the port's GraphicsLayer on both legs at this step). [§4/§6]  *blocked-by: P1.1*
  *Done (commit `f8a211cb`): 4 files moved nativeMain→sdlRendererMain (`GraphicsLayer.native
  .kt`, `NativeRenderNode.kt` interface+context, `DeferredRenderNode.kt`, `GraphicsLayer
  Factory.native.kt`). `SdlDisplayList*`/`SdlRenderNode`/`NativeRenderNode.sdl.kt` were
  ALREADY in sdlRendererMain. The `expect fun createNativeRenderNode` dropped (return type no
  longer shared) → the two factories are plain `internal fun`s. `createProjectGraphicsLayer`
  stays a commonMain `expect` with an actual now in each renderer set. Skia leg keeps a
  TRANSIENT copy of the cluster in skikoRendererMain (P1.6 deletes it). Verified: Skia+SDL
  per-target compile, `:ui:compileCommonMainKotlinMetadata` clean, full MAC-VERIFY ALL GREEN
  (10/10 probes, parity PASS both legs, perf within/below baseline).*
- [x] **P1.3** Fork `GraphicsContext` per-leg: replace the ad-hoc anonymous
  `ComposeOwner.graphicsContext` (`createProjectGraphicsLayer()`) with an `expect`/factory
  seam; SDL → the relocated project impl. *Done:* MAC-VERIFY green. [§4]  *blocked-by: P1.2*
  *Done (commit `4a3d8e5c`): `createGraphicsContext()` = `internal expect` in nativeMain,
  actual per renderer set; shared `ProjectGraphicsContext` class (nativeMain). Both legs
  return it this step; skiko actual is TRANSIENT (P1.6 → vendored SkiaGraphicsContext). Full
  MAC-VERIFY ALL GREEN.*
- [ ] **P1.4** Un-refuse + vendor the skiko files into `skikoRendererMain` (flip the `!` in
  `compose-fork.txt`, re-sync): `SkiaGraphicsLayer.skiko.kt`, `SkiaGraphicsContext.skiko.kt`,
  `Matrices.skiko.kt`, `Blur.skiko.kt`. *Done:* `sync.py` brings them in; they compile. [§7]
  *blocked-by: P1.3*
  **⚠ BLOCKER FOUND (2026-07-16, Mac) — the §7 vendoring closure is INCOMPLETE.**
  Upstream `SkiaGraphicsLayer.skiko.kt` records into skiko `RenderNode` and hands its draw
  block an upstream **`SkiaBackedCanvas`** (`renderNode.beginRecording().asComposeCanvas()
  as SkiaBackedCanvas`, lines 343/347 @ beta02). `SkiaBackedCanvas` is REFUSED (manifest
  line 518) because the port has its OWN `SkiaCanvas` (raw `org.jetbrains.skia.Canvas`
  wrapper implementing `NativeTextCanvas`/`NativePainterCanvas`/`NativeShadowCanvas`) — the
  entire Skia leaf-draw / text (`SkiaTextRenderer`) / painter / shadow pipeline is coded
  against `SkiaCanvas`, NOT `SkiaBackedCanvas`. So "flip 4 `!`s and it compiles" is FALSE;
  the real closure either (a) also adopts upstream's `SkiaBackedCanvas` + Paint/Shader/text
  stack = the **shelved B6**, or (b) manual-vendors `SkiaGraphicsLayer` with an edit bridging
  `beginRecording()`'s skia Canvas into the port `SkiaCanvas` — which makes it a NON-verbatim
  manual vendor, eroding B2's whole sync-tax payoff (P1.8). The port uses skiko `RenderNode`
  NOWHERE today (grep-confirmed). **Decision required before P1.4 proceeds — see log + the
  three options below.** [§7]  *blocked-by: P1.3 (done); now blocked on a scope decision*
  **DECISION (2026-07-16, user): Option B — full upstream Skia canvas (=B6).** P1.4/P1.5/P1.6
  are SUBSUMED by the new **Phase 1B (B6)** below — the skiko leg switches to upstream's
  SkiaBackedCanvas so `SkiaGraphicsLayer` vendors cleanly. Deeper scoping (from a full draw-
  dispatch map, 2026-07-16) below.
- [x] **P1.5 — DONE via B6.2** (matrix): skiko DRAW uses upstream `SkiaGraphicsLayer`'s own
  skiko-RenderNode transform; the shared `prepareLayerTransformationMatrix` stays for
  `GraphicsLayerOwnerLayer` HIT-TEST on both legs (it's shared, can't drop from skiko) and
  provably agrees (clicktest/scrolltest/backtest PASS on transformed layers). The literal
  "drop the copy" doesn't apply — it's shared, not skiko-only.
- [x] **P1.6 — DONE via B6.2** (commit `2210d1fa`): upstream `actual class GraphicsLayer(skiko
  .RenderNode)` provided by the un-refused `SkiaGraphicsLayer.skiko.kt`; the skiko `NativeRender
  Node` cluster (incl. `NativeRenderNode.skia.kt`) deleted. MAC-VERIFY Skia leg green, parity
  holds golden-master, PERF IMPROVED (draw 1.75ms→0.2ms).
- [x] **P1.7 — DONE via B6.2** (the actual-API-parity invariant): `:ui:compileCommonMain
  KotlinMetadata` is the enforcement — `GraphicsLayerOwnerLayer` (shared) compiles against
  BOTH `GraphicsLayer` actuals (upstream skiko + port SDL) only if both satisfy the commonMain
  `expect class GraphicsLayer` API; it's in the MAC-VERIFY runbook (verify-mac step) and in
  every per-leg compile. A skew fails the metadata compile. *(Kept as an ongoing invariant,
  not a one-off check.)*
- [x] **P1.8** (measure — the G1 justification) Quantify the **sync-tax**. *Done (2026-07-17,
  grounded in the actual B2 diff + the O.2 beta01→beta02 bump):* B2 cut the hand-maintained
  upstream-tracking surface on the skiko rendering path **~8.5×**. BEFORE: ~1240 lines of
  hand-rolled skiko rendering (`SkiaCanvas` 508, `GraphicsLayer.native` 321, the port node
  cluster `DeferredRenderNode`/`NativeRenderNode`/`.skia` ~344, `SkiaImageBitmap` ~65) — every
  upstream Canvas/GraphicsLayer semantic change had to be hand-reconciled. AFTER: 1006 lines
  (`SkiaGraphicsLayer` 515 + `SkiaGraphicsContext` 78 + `Blur` 40 + `SkiaBackedPaint` 174 +
  `SkiaShader` 175 + `ImageBitmap` 24) auto-sync VERBATIM on a ref bump (zero reconciliation);
  the hand-reconciled surface shrinks to ~145 lines of edits across 2 drift-tracked manual
  vendors (`SkiaBackedCanvas` Native* tail, `SkiaImageAsset` 2 inlined funcs), which
  `check-vendor-drift.py` (P0.6) flags precisely on a bump. Bonus: fidelity is now INHERITED
  from upstream's engine (real display-list caching, correct clip/shadow) instead of
  hand-approximated — the O.2 bump proved the verbatim re-sync path works end-to-end. [§0.5]

## Phase 1B — B6: full upstream Skia canvas (the enabler for B2, DECIDED 2026-07-16)

*Why here: P1.4 proved the skiko leg can't vendor upstream `SkiaGraphicsLayer` while it draws
through the port `SkiaCanvas`. User chose Option B — adopt upstream's Skia canvas so the layer
vendors cleanly. A full draw-dispatch map (2026-07-16) established the real surface below.*

**Scoping facts (from the dispatch map).** The composition draws text/images/shadows by casting
the frame `Canvas` to project contracts — `NativeTextCanvas` (`SdlParagraph.paint` →
`drawNativeText`), `NativePainterCanvas` (`ResourcePainter.onDraw`), `NativeShadowCanvas`
(`DeferredRenderNode.draw`) — plus a `NativeFinishableCanvas` no-op. `NativeShapeClipCanvas` is
SDL-only (Skia already falls back to `clipPath`, needs no bridge). Upstream `SkiaBackedCanvas`
implements NONE of these; every cast is a defensive `as?` that silently no-ops → text/images/
shadows go DARK on `SkiaBackedCanvas` unless bridged. Measurement (`currentTextMeasurer`) does
NOT go through the canvas and is unaffected — but it and drawing share ONE `SkiaTextRenderer`
instance (must stay coupled). Real gradients are a fidelity WIN: the port `SkiaCanvas` uses
solid-color brush fallback; `SkiaBackedCanvas`+`SkiaShader` draw true gradients (Brushes screen).

- [x] **B6.1** Adopt upstream Skia GRAPHICS actuals on the skiko leg. Split
  `CanvasPaintActuals.native.kt` (which defines the port `Paint`/`Shader`/all gradient
  shaders/`ActualImageBitmap`/`createImageBitmap`/`ActualCanvas`/`NativeCanvas`/`NativePaint`)
  `nativeMain → sdlRendererMain`; un-refuse (flip `!` + re-sync) `SkiaBackedCanvas.skiko.kt`,
  `SkiaBackedPaint.skiko.kt`, `SkiaShader.skiko.kt`, `SkiaImageAsset.skiko.kt`,
  `ImageBitmap.skiko.kt` into `skikoRendererMain` (they provide the SAME actuals, skia-typed;
  disjoint targets → no clash). Route the frame + offscreen draw canvas (SkiaRenderBackend
  `drawRoot`, SkiaOffscreen `createCanvas`) through `SkiaBackedCanvas`. **Bridge the 3 live
  contracts**: keep the port `SkiaTextRenderer`/`SkiaImageCache`/shadow code, expose them via a
  GLOBAL skiko draw-renderer (like the existing `offscreenRenderer`/`currentTextMeasurer`
  globals) and MANUAL-VENDOR `SkiaBackedCanvas.skiko.kt` to also implement `NativeTextCanvas`/
  `NativePainterCanvas`/`NativeShadowCanvas`/`NativeFinishableCanvas`, delegating via its own
  `.skiaCanvas`. (VENDOR-BASE header + drift tripwire required — it becomes a manual vendor.)
  *Done:* MAC-VERIFY green; Brushes parity IMPROVES (real gradients); text/icons/shadows/images
  unchanged. *blocked-by: P1.3 (done)*
  *DONE (commit `7f5773fc`). Findings: (1) the port ALREADY had real gradient shaders (the
  "solid-color fallback" header comment was stale) → Brushes unchanged, not improved. (2)
  `SkiaImageAsset` also needed manual-vendoring — its `toBitmap`/`putBytesInto` expect+actual
  collapse into one native source set here (flattened, bodies inlined). (3) `skiaLeafDrawer`
  MUST be re-pointed per-frame in drawRoot (+ cleared on destroy), not set once in the ctor —
  a ctor global dangled at a CLOSED window's destroyed renderer → multiwintest crash. Full
  MAC-VERIFY ALL GREEN both legs; 56/57 parity byte-identical (Images fluctuates in noise).*
- [x] **B6.2** Now the canvas is `SkiaBackedCanvas`: un-refuse + vendor `SkiaGraphicsLayer.skiko
  .kt` + `SkiaGraphicsContext.skiko.kt` + `Blur.skiko.kt`; point the skiko `createGraphicsContext`
  actual (P1.3 seam) at `SkiaGraphicsContext`; provide upstream `actual class GraphicsLayer
  (skiko.RenderNode)`; DELETE the transient skiko port cluster (GraphicsLayer.native.kt,
  NativeRenderNode.kt, DeferredRenderNode.kt, NativeRenderNode.skia.kt, GraphicsLayerFactory,
  GraphicsContextFactory.skia's ProjectGraphicsContext use). Reverse the
  `prepareLayerTransformationMatrix` rename → upstream `Matrices.skiko` (this is P1.5). Do NOT
  vendor `ChildLayerDependenciesTracker` (P2.2) — reconcile if `SkiaGraphicsLayer` hard-needs it.
  *Done:* MAC-VERIFY Skia leg — parity holds ~golden-master, blur/RenderEffect improve (P2.3).
  This IS P1.6 + P1.7 (the actual-API-parity invariant). *blocked-by: B6.1*
  *DONE (commit `2210d1fa`). ChildLayerDependenciesTracker was ALREADY vendored (commonMain)
  → SkiaGraphicsLayer vendors VERBATIM, no manual edit (supersedes P2.2's "don't vendor
  tracker" for the skiko leg — upstream's GraphicsLayer inherently uses it). Also had to move
  `Blur.native.kt` nativeMain→sdl (same split as the paint actuals) and relocate
  `ProjectGraphicsContext` + `createProjectGraphicsLayer` nativeMain→sdl (drop the commonMain
  expect — SDL-only now). GraphicsLayerOwnerLayer stays SHARED, compiles against both
  actuals (P1.7 invariant holds via the metadata compile). Hit-test agrees with the new
  skiko-RenderNode draw (clicktest/scrolltest/backtest PASS). Layer screens byte-stable
  (transparent swap). PERF WIN: skia draw 1.75ms→0.2ms (skiko RenderNode display-list cache).
  P1.5 rename-reversal N/A: prepareLayerTransformationMatrix stays shared (GraphicsLayerOwnerLayer
  hit-test needs it on both legs); skiko DRAW uses upstream's matrix internally and agrees.*
- [-] **B6.3 — DECIDED: SKIP (2026-07-17, user).** Full upstream TEXT on the skiko leg is NOT
  worth it under G1. A full subsystem map (Explore agent, 2026-07-17) showed it is NOT a canvas-
  style swap but a **font-subsystem replacement with two coexisting identity models**:
  • The port uses a name→bytes `IconFont` registry + a no-op `FontFamilyResolver` + renderer-
    owned typeface resolution; upstream `SkiaParagraph` needs `PlatformFont`/`LoadedFont` in a
    real `FontCache`/`FontCollection` resolved by `FontFamilyResolverImpl(SkiaFontLoader)`.
  • The shared ICON path (`IconText`/`TextDrawNode`/`currentTextMeasurer`/`drawNativeText`,
    foundation commonMain) is used on BOTH legs → the port text model can't be removed, it must
    COEXIST with upstream on the skiko leg.
  • Cost: re-vendor ~13 dropped skiko files + split ~10 shared actuals per-renderer (the
    `Paragraph` interface itself is expect/actual) + a `data.kres`→`FontCache` bridge; NO green
    intermediate (atomic, multi-session, long font-debug tail); DISCARDS P3.1.
  • ROI: LOW. On the skiko leg text MEASUREMENT already uses skiko `Font` metrics
    (`currentTextMeasurer`→`SkiaTextRenderer`) — that's how P3.1 hit ~2%. `SkiaParagraph` would
    mainly add ICU line-breaking/bidi/complex-shaping + exact wrapping, a modest gain for the
    demo's Latin text, and under G1 the JVM leg is the fidelity tier anyway.
  Left as the port's permanent text architecture. Revisit only if complex-script/bidi fidelity
  on the NATIVE skiko leg becomes a hard requirement.

**B2 CONVERGENCE COMPLETE (2026-07-17):** the skiko leg runs upstream's own Canvas (B6.1) +
GraphicsLayer/GraphicsContext (B6.2) verbatim. Text stays the port's engine (B6.3 skipped).

## Phase 2 — B5: engine-convergence deltas

- [x] **P2.1** Audit CDN-common vs upstream `skikoMain`; list every hand-rolled divergence
  and vendor the deltas (or record a named constraint). *Done (2026-07-17, Explore audit):*
  **ui-graphics vendoring closure is DONE — B6 spent that lever.** Of the whole
  `skikoMain/.../graphics/` tree only 2 files stay `!`-refused, both ON PURPOSE (the B6 manual
  vendors `SkiaBackedCanvas` + `SkiaImageAsset`). Reviewable post-B6 deltas ranked:
  • **D2** `GraphicsLayerOwnerLayer.kt` (shared) drops upstream `setLightingInfo`/`LIGHT_*`
    (skiko shadow lighting) + renames the matrix fn — could split per-leg (skiko verbatim,
    un-refuse manifest) to restore elevation-shadow lighting fidelity. INVESTIGATE.
  • **D3** `LayerTransformationMatrix.kt` — now a DUPLICATE of the vendored `Matrices.skiko`
    `prepareTransformationMatrix` (B6.2 vendored it). Relocate the hand copy to sdl, let skiko
    use the vendored one. Low-risk dedupe (its own header predicted this). INVESTIGATE.
  • **D4** `SemanticsRegion.native.kt` — intersect/difference are STUBS (a11y region math inert)
    vs upstream skia `Region`. Fidelity gap; vendor on skiko if a11y is on the roadmap.
  • **D5** `CharHelpers.native.kt` — naive grapheme/bidi vs upstream ICU. Non-BMP/RTL gap.
  • **D6** `Focusability`/`PlatformVelocityTracker` — byte-equal to upstream; trivial vendor.
  Everything else: load-bearing port constraints (SDL graphics actuals, the whole
  ComposeScene→ComposeOwner replacement behind manifest !lines 362–406, the port text engine,
  cinterop infra). Full delta list captured in the session. [§7]
  **D2–D6 evaluated in depth (2026-07-17) — decision: DEFER all; none is a worthwhile win now:**
  • **D2 (GraphicsLayerOwnerLayer per-leg split): DEFER.** CORRECTION: upstream
    `GraphicsLayerOwnerLayer.skiko.kt` is NOT coupled to the refused `RootNodeOwner` (verified:
    same ctor `(graphicsLayer, context, layerManager, drawBlock, invalidateParentLayer)`;
    `setLightingInfo` is a self-contained `SkiaGraphicsContext` extension + private `LIGHT_*`).
    So CONVERGE §7's stated blocker is OUTDATED. BUT the ROI is still poor: it's a ~400-line
    per-leg split of the file EVERY hit-test + draw flows through (high blast radius), for a
    marginal gain — elevation shadows already match JVM (Shadows parity 1.49%), so exact
    upstream lighting adds little, and the change would swap the skiko shadow mechanism (parity
    risk) for a modest sync-tax reduction. Not worth destabilizing the clean B2 work.
  • **D3 (matrix dedupe): DEFER — depends on D2.** The shared `GraphicsLayerOwnerLayer` hit-test
    needs a BOTH-legs matrix fn; `Matrices.skiko` is skiko-only, so the port
    `LayerTransformationMatrix.kt` must stay shared while the owner layer is shared. The
    duplicate is 15 lines of identical, proven-agreeing math — benign. Only dedup-able if D2 is
    done first.
  • **D4/D5 (SemanticsRegion / CharHelpers): DEFER — gated on roadmap.** Real fidelity gaps
    (a11y region math / ICU bidi+grapheme) but only matter if accessibility or complex-script/
    RTL text becomes a requirement; under G1 neither is. Vendor-on-skiko when needed.
  • **D6 (Focusability/PlatformVelocityTracker): SKIP — negligible.** 2 tiny byte-equal files;
    vendoring them saves nothing meaningful.
- [x] **P2.2** Lifetime model + soak test — **leak FIXED (commit `c59caf72`).** *Superseded note:* B6.2 made the skiko leg use
  upstream `SkiaGraphicsLayer` WITH `ChildLayerDependenciesTracker` (it was already vendored);
  SDL keeps the port GC/release-queue. So "keep shared GC/release-queue on both legs; do NOT
  vendor the tracker" holds for SDL but NOT skiko (upstream's layer inherently uses it).
  *Soak test ADDED (`demo --soaktest`, commit `4bd9ca23`) — and it CAUGHT A REAL PRE-EXISTING
  LEAK on BOTH legs:* peak RSS climbs ~linearly, no plateau over 6 cycles — skia 264→458MB
  (~40MB/cycle), SDL 523→916MB/3 (~195MB/cycle, ~5× worse → offscreen textures). Persists with
  animations disabled (not animation churn); the layer release path is wired, so it's deeper
  (composition/snapshot on both legs + SDL texture retention). **NOT yet passing → P2.2 stays
  open.** Needs a dedicated heap-profiled leak hunt (RSS alone can't pinpoint it). Kept as a
  standalone diagnostic, NOT wired into verify-mac's gate until fixed. [§7/§8] *(Now FIXED +
  WIRED as a verify-mac gate — see the FIXED note below + the MAC-VERIFY primitive.)*
  *Leak HUNT done (commit `c1fabc5b`) — characterized, not yet fixed:* layers CLEAN (live
  OwnerLayer counter flat while RSS climbed → destroy/release runs); NOT GC-lag (Counter x10
  linear 114→176MB, no plateau); NOT animation churn; bisected to a BASELINE composition-
  machinery leak (~0.15MB/mount, even trivial Counter) + CONTENT amplification on image/vector/
  list screens (~0.55MB/mount); image decode caches ruled out (bounded, keyed). Remaining
  suspects (need heap tooling): composition/snapshot observation retention + DrawCache/vector
  offscreen bitmap lifetime. `CDN_SOAK_SCREEN=<name>` added for single-screen bisection. A
  precise FIX is a dedicated focused session — P2.2 stays open.
  **FIXED (2026-07-17, commit `c59caf72`) — ROOT CAUSE: the port never called
  `OwnerSnapshotObserver.clearInvalidObservations()`.** Upstream RootNodeOwner runs it after
  every measure pass; without it, snapshot read-observations for scopes invalidated on dispose
  (detached nodes, destroyed layers, disposed DRAW scopes) lingered forever, each pinning its
  observed object graph via a K/N `ExternalRCRef` → the K/N-heap leak (vmmap Memory Tag 246).
  Most visible on ripple/indication draws (Button reads animatedAlpha/colors in draw; that
  layer draw-scope observation was never swept → pinned the whole button subtree). FIX:
  `ComposeRootHost.measureAndLayout()` sweeps `clearInvalidObservations()` after layout (+ the
  earlier onDetach `snapshotObserver.clear(node)`). Soak now PASSES: all-63-screens 260→458MB
  (was FAIL) → 238→240MB; Counter/Images/LazyGrid all flat. Hunt method: exact live-counters
  (ruled out layers/RenderNodes/nodes), static-mode (ruled out per-frame), `leaks` (no malloc
  leak → referenced), `heap`/`vmmap` (isolated to K/N heap + ExternalRCRef), component
  bisection (isolated to ripple indication). MAC-VERIFY ALL GREEN, perf unchanged.
  *Focused leak-fix session (2026-07-17, commit `7189433e`):* FIXED one confirmed contributor —
  `ComposeOwner.onDetach` now calls `snapshotObserver.clear(node)` (upstream RootNodeOwner does;
  the port omitted it → detached nodes' observation scopes grew unbounded). Small RSS impact but
  a real correctness fix; MAC-VERIFY ALL GREEN. Deepened diagnostics: soak now uses CURRENT RSS
  (via `ps`, drops after GC — the correct metric vs monotonic peak) + `CDN_SOAK_STATIC=1`
  (isolates per-frame). Exhaustive ruling-out with EXACT counters + macOS `leaks`:
  the residual leak is REAL + LINEAR (~141KB/mount Counter, x25 no plateau), REFERENCED (macOS
  `leaks` = 0 malloc leaks in our code → not orphaned, held by something), survives GC, per-MOUNT
  (static flat), and NOT: layers/RenderNodes (live counts flat), LayoutNodes (attach/detach
  balanced), the skia GrContext cache budget (linear, not plateau), or observation scopes (the
  fix barely moved it). It scales with node count (Counter 141KB vs Images ~550KB/mount). Prime
  remaining suspect: native skia/text objects (measure-time) referenced by a growing structure —
  needs a **skia-aware heap profiler** (K/N ships none; `leaks` can't see referenced growth) to
  pin. P2.2 stays open for that follow-up; the soak reproduces it deterministically.
- [x] **P2.3 — DONE via B6.2.** Outsets/blur-bounds expansion + `renderEffect` are now handled by
  upstream `SkiaGraphicsLayer` (real `setOutsets` used in draw bounds + `renderEffect`→
  `skiaImageFilter`; `Blur.skiko` vendored). The port's `setOutsets` was a no-op. No demo screen
  exercises `.blur()`/RenderEffect so there's no parity number to move, but the capability is now
  upstream-correct (add a blur screen if native blur fidelity ever needs measuring). [§7]

## Phase 3 — B3: SDL fidelity (CAPPED under G1 — only parity-ranked user-visible wins)

- [~] **P3.1** From the parity ranking, list the worst *user-visible* SDL gaps (gamma
  gradients / AA / blur / complex-script text). Do ONLY the ones that rank as real wins, each
  gated by a parity improvement. **Stop when the ranking flattens** — JVM is the fidelity
  tier for the rest. [§0.5.3, §4 (B3)]
  **RESOLVED: the whole "font-drift baseline" was line-metric drift.** Fixing the text
  metrics (see log: lineHeight modes + JVM-matched font cells) collapsed the ranking from
  a ~17% median to **~2%** — Tabs 33.3→1.8, GridsExtra 28.5→1.3, Shadows 24.6→1.5,
  Brushes 22.3→2.2, Carousel 23.1→1.9, Animation 24.5→3.0. The previously-suspected
  "gradient ramp" and "shadow falloff" gaps were ALSO mostly drift — both now sit in the
  ~2% noise floor. **Post-fix ranking (what's actually left):**
  1. Pickers 19.5 — TimeInput focus auto-scroll positioning differs between legs
     (bring-into-view final offset); interaction-layout, not rendering.
  2. Images 16.6 — NOT image rendering: the JVM leg soft-WRAPS the descriptions one word
     earlier (glyph advances run slightly wider) → +1 line → content below shifts. A
     per-glyph advance-width parity item (kerning/advance rounding), its own investigation.
  3. AnnotatedString 8.1 — metric-span line heights (styled cells vs upstream span boxes).
  4. Search 5.3 — unread; then Canvas 4.3 / CustomLayout 4.1 and the ~2% tail — JVM is
     the fidelity tier for the rest (§0.5.3).
  *(Text 9.2 was the h=1.0 lineHeight boundary — fixed, now 3.1.)*

## Ongoing — vendoring cadence

- [x] **O.1** Pin `COMPOSE_CORE_REF` to a durable **tag** (not the `+dev` commit that upstream
  may GC). *Done:* a fresh clone can `sync.py` from the pinned ref. *(Pin switched to tag
  `v1.12.0-beta01+dev4324` — verified to dereference to the exact previous hash `1be9d64…`
  and to exist on upstream's remote (`ls-remote`), so clone/fetch-by-name always works;
  the 3 `VENDOR-BASE` headers re-spelled to the tag so P0.6's ref-compare stays green.)* [§9]
- [~] **O.2** Write + follow the **ref-bump runbook**: bump → `sync.py` → build → DRIFT-CHECK
  → MAC-VERIFY + WIN-SMOKE. Run it on each upstream bump. [§9] *(First real bump executed:
  beta01+dev4324 → **v1.12.0-beta02**, see log. Windows legs green end-to-end; the Mac legs
  of MAC-VERIFY still pend P0.2's first Mac run.)*

## Deferred / shelved (NOT active — do not pick up without a new decision)

- [-] **Track A** — real Skia on Windows K/N. Shelved under G1 (likely infeasible + breaks
  no-DLL; maintenance payoff doesn't justify the spike). [§0.5.4, §3]
- [-] **B1** — SDL-only node-dedup base (`AbstractNativeRenderNode` in `sdlRendererMain`).
  Deferred; cleanup on the newly-default hot path, no G1 payoff yet. [§4 (B1)]
- [-] **Module split** — extract `:ui-graphics` / `:ui-text` from `:ui`. Deferred until a
  consumer needs the publishing granularity (relocation churn + bridge-substitution fan-out,
  no user benefit yet). [§6]
- [-] **B6** — make Skia-leg text truly upstream (skiko `SkiaParagraph` + `SkiaBackedCanvas`).
  Optional; only if the text-metrics parity delta proves worth it. [§1, §7]

---

## PROGRESS LOG (append newest last: `YYYY-MM-DD · task · commit · verification`)

- 2026-07-16 · **P0.1** · parity.py target-aware (`--target`/`--renderer`, host-inferred
  default) · verified on **Windows**: default mingw/SDL run intact (Buttons 16.47%, its
  normal font-drift baseline; `--gpu=sdl3` passed); macOS Skia + macOS SDL legs resolve to
  the right task/exe/gpu; bad target rejected. macOS/Linux end-to-end pending a Mac run.
- 2026-07-16 · **P0.6** · vendor-drift tripwire (`check-vendor-drift.py` + `VENDOR-BASE:`
  headers on the 3 renderer manual-vendors) · verified on **Windows**: clean run exit 0
  (3 files match pin `1be9d64`), simulated stale ref exit 1; upstream clone auto-detected
  for the deeper base..pin diff.
- 2026-07-16 · **P0.3** · font-align JVM parity leg (stage + load NotoSans; M3 Typography +
  LocalTextStyle) · verified on **Windows**: staged to `demo/build/processedResources/jvm/
  main/font/NotoSans.ttf`, loads + applies (no warning). SDL-leg % ~unchanged - text diff is
  metric-dominated (accumulating vertical line-height drift SDL3_ttf-vs-Skia), NOT typeface;
  ~0 golden-master is Skia-leg/Mac (pending). Finding logged in the task note.
  **CONFIRMED on Mac (2026-07-16):** the Skia-leg golden-master lands — ~2% median (Buttons
  1.96, Counter 0.65, LazyGrid 1.77). BUT the first Mac run first read 24-78% because of a
  parity HARNESS bug, not the port: on Retina the native leg renders at physical density
  (2000x1400) and `diff_pair` CROPPED the top-left min(w,h) instead of resizing to the JVM
  density-1.0 reference (1000x700) — comparing a 2x-magnified quarter. `align_native()`
  (LANCZOS resize, no-op at DPR 1.0) fixed it. The SDL leg on Mac lands at the same ~2%.
- 2026-07-16 · **P0.4** · parity.py is now a GATE (baselines.json keyed target/renderer,
  --update-baselines, non-zero exit on >baseline+max(3pts,25%) or NATIVE FAILED) · verified
  on **Windows**: full 57-screen `mingwX64/sdl3` baseline seeded; PASS exit 0, simulated
  regression exit 1, restore clean; fixed cp1252 console crash (ASCII-only output).
- 2026-07-16 · **P0.5** · render-to-quiescence on both legs + deterministic screenshot mode
  (native: `windowHasInvalidations()` [needsFrame + recomposer.hasPendingWork + both
  BroadcastFrameClocks' hasAwaiters], `disableInfiniteAnimations` [cancelling
  InfiniteAnimationPolicy in the window Recomposer context], `useVirtualFrameTime` [clocks
  step 16.6ms/frame]; JVM: `render(nanos)` virtual-clock loop until `hasInvalidations()`
  clears + the same policy in ImageComposeScene's context; `--frames` repurposed as cap,
  default 300) · verified on **Windows**: Pickers/Animation/Buttons byte-identical across
  3 native runs (Pickers settles at exactly frame 12 every run; was drifting ±10px scroll
  offset from its TimeInput bring-into-view race) and all 57 JVM screens byte-identical
  across 2 sweeps; 5 interaction probes (click/scroll/back/multiwin/nav3) PASS — the flags
  are inert outside screenshot mode; full parity PASSed against the OLD frame-6 baselines
  (55/57 identical %, only Pickers 20.02→19.49 + Remember 14.72→14.71 moved), re-seeded,
  fresh full run PASS exit 0 with every screen exactly on baseline.
- 2026-07-16 · **P3.1 (started)** · classified the top-12 diff images (ranked gap list in the
  task) + landed fix #1: SdlParagraph now honours `TextStyle.lineHeight` with upstream's
  COMPAT-TRIM semantics (first line = tight font cell; lineHeight = the advance between
  lines; single-line boxes unchanged), threaded as `inLineHeightPx` through NativeTextCanvas
  → Sdl3Canvas/Sdl3TextRenderer AND SkiaCanvas/SkiaTextRenderer (skiko side textual —
  compile pends Mac). First attempt (uniform lineHeight bands) REGRESSED Counter
  2.53→9.18 and was rejected by the parity gate — the trim model then passed everywhere ·
  verified on **Windows**: full 57-screen parity PASS, LazyExtra 30.00→27.74 +
  AnnotatedString 24.88→24.59, other 55 screens byte-identical; paragraph/key/click/
  scroll/back probes PASS; baselines re-seeded.
- 2026-07-16 · **P3.1 fix #2+#3 (the big one)** · SDL text metrics now match upstream
  EXACTLY, collapsing parity from ~17% median to **~2%** (Tabs 33.3→1.8, GridsExtra
  28.5→1.3, Shadows 24.6→1.5, Brushes 22.3→2.2 — the whole "font-drift baseline" was
  metric drift, not typeface). Three pieces, each driven by a new metrics probe pair
  (`demo --metricsprobe` native / `:demo:run --args=--metrics` JVM — prints matched
  Paragraph-height tables): (1) font CELL = round(hhea_ratio × size) via a metrics-only
  FreeType read of the face tables (SDL3_ttf only exposes grid-fitted CEILED ints: +1px
  at the M3 body sizes 12/14) — icon families keep the TTF path; (2) lineHeight modes:
  `lineHeightStyle=null` → compat trim (first line = tight cell), M3's `Trim.None` →
  UNIFORM bands (every line exactly lineHeight, even compressing below the cell — probe:
  22sp/28 → 28); (3) lineHeight < fontSize is IGNORED (probe: 48sp/24 → 65 cell; this
  was the first uniform-band attempt's Counter regression). Native/JVM probe tables now
  agree on all configs (one raw quirk left: JVM's 18.5px advance at 12sp/18 raw) ·
  verified on **Windows**: full parity PASS all 57, 9/9 probes PASS, baselines re-seeded
  at the collapsed levels. skiko-side mirrors compile-pend the Mac run.
- 2026-07-16 · **P3.1 fix #4** · lineHeight h=1.0 boundary: upstream applies the multiplier
  only STRICTLY above 1em (probe: 24sp/24lh → 33px cell, 24sp/25lh → 25) — the `>=` gate
  was off-by-cell on every `Text(fontSize == inherited lineHeight)` row · verified on
  **Windows**: Text 9.19→3.08, all other screens byte-identical, parity PASS, probes PASS,
  baselines re-seeded. Boundary cases added to both metrics probes.
- 2026-07-16 · **P0.2 (in progress)** · authored `scripts/verify-mac.sh` (host-target
  detect, both legs: build → nav3/back/click/scroll/multiwin probes gated on PASS →
  parity per leg → LazyColumn/Tabs draw-ms spot-check vs self-seeded baseline, +20% gate;
  `--update-perf-baseline` reseeds) · verified on **Windows** only to the extent possible:
  `bash -n` clean, profiler-line parse + regression awk unit-smoked. End-to-end run,
  perf-baseline seed and parity mac keys all pending the first Mac session.
- 2026-07-16 · **P0.7** · vendor-clean check (`scripts/compose-fork/check-vendor-clean.py`:
  hash src/vendor → sync.py → re-hash → fail on any change/stale/missing or manifest
  churn; tree left restored) · verified on **Windows**: clean run PASS exit 0 (1553 files,
  15 modules, both repos at their pins); simulated hand-edit + orphan file FAIL exit 1
  (`changed`/`stale` both listed), immediate re-run PASS. Wired into verify-mac.sh as
  step 0 (with the P0.6 provenance tripwire); the runbook itself still pends a Mac run.
- 2026-07-16 · **O.1** · COMPOSE_CORE_REF pinned to tag `v1.12.0-beta01+dev4324` (was the
  bare hash `1be9d64…`; VENDOR-BASE headers re-spelled to match) · verified on **Windows**:
  tag dereferences to the exact old hash (`rev-parse ^{}`), exists on upstream's remote
  (`ls-remote`), full re-sync at the tag byte-identical (check-vendor-clean PASS, 1553
  files), check-vendor-drift PASS (3/3 base == pin).
- 2026-07-16 · **P0.6 follow-up (user request)** · VENDOR-BASE provenance is now MANDATORY
  for every manual vendor (CLAUDE.md vendoring rule 3 updated) and applied retroactively:
  swept all manifests' `!` exclusions + derivation-marked project files; 4 more files
  annotated (2× Synchronization.kt, SelectionLayout.kt, DomXmlParser.kt — each base
  verified UNCHANGED beta01→beta02 in its clone before stamping `v1.12.0-beta02`);
  `check-vendor-drift.py` extended with the `VENDOR-BASE(<VARNAME>):` form so
  umbrella-repo files track COMPOSE_REF with their own clone, plus a path-exists guard
  (rename at pin → "diff unavailable", not a silent "unchanged"). 7 files tracked, all
  OK; simulated stale COMPOSE_REF ref exit 1 with the correct clone verdict.
  (NavDisplay.native.kt turned out manifest-managed — upstream macos actual mapped by
  the manifest, regenerated each sync — and NativeRenderNode.kt / ProjectPath.kt /
  the demo compat files are port inventions with no upstream base: correctly unannotated.)
- 2026-07-16 · **O.2 (first bump)** · both pins → **v1.12.0-beta02** (user request):
  COMPOSE_CORE_REF + COMPOSE_REF, catalog `compose=1.12.0-beta02`, JVM forcing
  `1.12.0-beta02` / m3 `1.12.0-alpha03` (released pairs confirmed on Maven Central) ·
  flow: sync (fixed a real sync.py bug the bump exposed: fetch-by-TAG only reached
  FETCH_HEAD, checkout failed — now fetches `refs/tags/x:refs/tags/x` with FETCH_HEAD
  fallback) → DRIFT-CHECK (3 manual vendors flagged; clone diff proved all 3 upstream
  bases UNCHANGED base..pin → refs re-stamped) → builds green (demo+apidemo, native+jvm,
  zero code fallout) → 8/8 probes PASS → full parity PASS vs the beta01-era baselines
  with 56/57 screens at their EXACT % (only GraphicsLayer +0.01) → baselines re-seeded.
  Windows-only; Mac legs pend P0.2.
- 2026-07-16 · **FIRST MAC SESSION — P0.2 / P0.3 / P0.4 closed** · commits `5686e878`
  (parity HiDPI align fix), `bb0b5a9a` (macosArm64/sdl3 baselines) · on macOS arm64:
  (1) Skia leg builds clean — the P3.1 skiko-side text mirrors that "compile-pended the
  Mac" all compile; demo runs on Metal (`SkiaMetalBridge drawable 2000x1400 @ 2.0`),
  nav3test PASS. (2) Found + fixed the parity harness HiDPI bug (`diff_pair`/`side_by_side`
  cropped instead of resizing the 2x native render → 24-78% false diffs; `align_native()`
  LANCZOS-resizes native to the JVM density-1.0 size, no-op on Windows). Skia-leg parity
  then collapsed to the predicted golden-master (~2% median, Buttons 1.96 / Counter 0.65 /
  LazyGrid 1.77). (3) `verify-mac.sh` ran end-to-end GREEN (exit 0): drift-check → both legs
  build (demo+apidemo) → 5 probes each PASS → parity each → perf seeded. (4) Seeded parity
  keys `macosArm64/skia` + `macosArm64/sdl3` (57 each) and the perf baseline. SDL leg on Mac
  also ~2% median. **Unblocks P1.1 (blocked-by P0.2).**
- 2026-07-16 · **P1.1** (skiko-version alignment gate) · no commit (probe was throwaway) ·
  confirmed `skiko:0.150.1` exposes `org.jetbrains.skiko.node.{RenderNode,RenderNodeContext}`
  — the API beta02's vendored `SkiaGraphicsContext.skiko.kt`/`SkiaGraphicsLayer.skiko.kt`
  need. Klib linkdata shows `package_org.jetbrains.skiko.node` with both classes; a throwaway
  `skikoRendererMain` file using `RenderNodeContext(measureDrawBounds=…)`, `RenderNode(ctx)`,
  `.setLightingInfo(...)`, `.close()` compiled clean (`:ui:compileKotlinMacosArm64` SUCCESS)
  then was removed. No skiko/compose-ref bump required. **Unblocks P1.2–P1.7.**
- 2026-07-16 · **P1.2** · commit `f8a211cb` · relocated the SDL node cluster nativeMain→
  sdlRendererMain (GraphicsLayer.native.kt, NativeRenderNode.kt, DeferredRenderNode.kt,
  GraphicsLayerFactory.native.kt) + transient skikoRendererMain copies so both legs keep the
  port GraphicsLayer this step; dropped the `expect fun createNativeRenderNode` (factories
  now plain `internal fun`). Verified per-target (Skia+SDL) + metadata compile + full
  MAC-VERIFY ALL GREEN (perf actually a hair BELOW baseline both legs). Behaviour unchanged.
- 2026-07-16 · **P1.3** · commit `4a3d8e5c` · forked GraphicsContext behind
  `createGraphicsContext()` (expect nativeMain, actual per renderer set; shared
  `ProjectGraphicsContext`). Behaviour-neutral (both legs return the project context this
  step). Compiles Skia+SDL+metadata; full MAC-VERIFY ALL GREEN.
- 2026-07-16 · **P1.4 BLOCKER (investigation, no code)** · un-refusing upstream
  `SkiaGraphicsLayer.skiko.kt` does NOT compile in isolation: it casts its recording canvas
  to `SkiaBackedCanvas` (upstream's Canvas actual), which the port refuses in favour of its
  own `SkiaCanvas`. The whole Skia draw/text pipeline binds `SkiaCanvas`. The §4/§7 "vendor
  4 files verbatim" closure missed this. Three ways forward (a decision the maintainer owns,
  since it changes B2's cost/benefit under G1):
    **Option C (recommended) — wrap skiko RenderNode behind the port's `NativeRenderNode`.**
      Back `NativeRenderNode.skia.kt` with skiko `RenderNode` (record the port draw block
      into `beginRecording()`'s canvas wrapped as the port `SkiaCanvas`; replay via
      `RenderNode.drawInto`). KEEP the port `GraphicsLayer` + `SkiaCanvas`. Gets the real
      display-list node (record-once caching + skiko clip/shadow fidelity — the engine win)
      with NO B6 entanglement and NO SkiaBackedCanvas. The port's ORIGINAL "Phase 2a"; drops
      the "vendor upstream GraphicsLayer verbatim" sub-goal (and its P1.8 sync-tax claim).
    **Option A — manual-vendor `SkiaGraphicsLayer` with a SkiaCanvas bridge edit.**
      Non-verbatim → partial sync-tax; still needs textRenderer threading; keeps upstream's
      `ChildLayerDependenciesTracker` (P2.2 says DON'T vendor it).
    **Option B — full upstream Skia canvas adoption** (un-refuse `SkiaBackedCanvas` + Paint +
      Shader + ImageAsset + upstream text): this IS the shelved B6. Largest; abandons the
      port's SkiaCanvas/SkiaTextRenderer investment.
- 2026-07-16 · **B6 DECISION + plan (user chose Option B)** · no code yet · mapped the full
  skiko draw-dispatch (Explore agent): the frame canvas carries 4 project contracts
  (`NativeTextCanvas`/`NativePainterCanvas`/`NativeShadowCanvas`/`NativeFinishableCanvas`);
  `NativeShapeClipCanvas` is SDL-only (Skia falls back to clipPath). Upstream `SkiaBackedCanvas`
  implements none → text/images/shadows would go dark unless bridged. Concluded clean "full
  upstream canvas" cascades into text/painter/shadow (upstream `SkiaParagraph` is a whole
  separate subsystem that discards P3.1). Staged B6 as **B6.1** (graphics actuals: split
  `CanvasPaintActuals` per-renderer, un-refuse SkiaBackedCanvas/Paint/Shader/ImageAsset/
  ImageBitmap, route frame+offscreen through SkiaBackedCanvas, bridge the 3 live contracts via a
  global draw-renderer + a MANUAL-VENDOR of SkiaBackedCanvas) → **B6.2** (= P1.4/1.5/1.6/1.7: vendor
  SkiaGraphicsLayer/Context/Blur now the canvas is upstream, delete the transient skiko port
  cluster) → **B6.3** (optional upstream text, discards P3.1 on skiko — defer). Plan written to
  Phase 1B. Real gradients (Brushes) are a B6.1 fidelity win. Starting B6.1.
- 2026-07-17 · **B6.1** · commit `7f5773fc` · Skia leg now draws through upstream
  SkiaBackedCanvas + SkiaBackedPaint/Shader/ImageAsset/ImageBitmap. Split port graphics
  actuals (CanvasPaintActuals + BlendMode) nativeMain→sdlRendererMain; manual-vendored
  SkiaBackedCanvas (+ port NativeText/Painter/Shadow/Finishable contracts forwarding to a
  global skiaLeafDrawer) and SkiaImageAsset (flattened toBitmap/putBytesInto); deleted the
  port SkiaCanvas; offscreen + decode now upstream. Fixed a real multi-window crash (per-frame
  skiaLeafDrawer, cleared on destroy) that a ctor-set global caused. MAC-VERIFY ALL GREEN both
  legs (10/10 probes, parity PASS 56/57 byte-identical, perf below baseline, metadata + drift
  + vendor-clean pass). **Unblocks B6.2 (= P1.4/P1.5/P1.6/P1.7 — vendor upstream
  SkiaGraphicsLayer now the canvas is upstream).** Note: the port already had real gradients
  (Brushes unchanged); the fidelity win is deferred to whatever upstream paint/shader adds.
- 2026-07-17 · **B6.2** (= P1.4/P1.5/P1.6/P1.7) · commit `2210d1fa` · Skia leg now runs
  upstream's OWN GraphicsLayer + GraphicsContext (org.jetbrains.skiko.node.RenderNode) — the
  core B2 convergence. Un-refused SkiaGraphicsLayer/SkiaGraphicsContext/Blur VERBATIM
  (ChildLayerDependenciesTracker already vendored → no manual edit). Wired the P1.3 context
  seam (skiko→SkiaGraphicsContext, SDL→ProjectGraphicsContext moved to sdl with the
  createProjectGraphicsLayer factory; commonMain expect dropped); moved Blur.native.kt→sdl;
  deleted the 5-file transient skiko port cluster. GraphicsLayerOwnerLayer stays SHARED
  (P1.7 API-parity invariant holds via metadata compile). MAC-VERIFY ALL GREEN both legs
  (10/10 probes, parity layer-screens byte-stable = transparent swap, drift+vendor-clean pass).
  **PERF WIN: skia draw 1.75ms→0.2ms** (skiko RenderNode record-once/replay display-list
  cache). Baselines re-seeded. B6.3 (upstream text) remains optional/deferred.
- 2026-07-17 · **B6.3 DECISION: SKIP** (user) · no code · full subsystem map (Explore agent)
  showed upstream text is a font-subsystem replacement with two coexisting identity models
  (port name→bytes IconFont + no-op resolver vs upstream PlatformFont/FontCache/FontCollection),
  ~13 dropped skiko files to re-vendor + ~10 shared actuals to split, a data.kres→FontCache
  bridge, no green intermediate, discards P3.1 — for a modest gain (skiko metrics already used
  via currentTextMeasurer→SkiaTextRenderer; JVM is the G1 fidelity tier). Skipped; port text
  engine is permanent. **B2 CONVERGENCE COMPLETE** (B6.1 canvas + B6.2 layer).
- 2026-07-17 · **P1.8** (sync-tax, the G1 justification) · no code, measurement · B2 cut the
  hand-maintained skiko upstream-tracking surface ~8.5× (~1240 hand-rolled lines → ~145 lines
  of drift-tracked edits; 1006 lines now auto-sync verbatim). Details in the P1.8 task entry.
  Phase 1 (B2) + Phase 1B (B6) DONE. Remaining open: Phase 2 (B5 engine deltas), Phase 3 (B3
  SDL fidelity, capped), O.2 ongoing bumps.
- 2026-07-17 · **Phase 2 (B5)** · P2.1 audit + P2.2 soak + P2.3 · commit `4bd9ca23` (soak) ·
  **P2.1 (audit) DONE**: ui-graphics closure confirmed done by B6; only D2 (GraphicsLayerOwnerLayer
  per-leg split, restores skiko shadow lighting) + D3 (LayerTransformationMatrix dedupe vs the now-
  vendored Matrices.skiko) are worthwhile cleanups; D4/D5 (SemanticsRegion/CharHelpers) are
  fidelity gaps for a11y/complex-text if wanted; rest are load-bearing port constraints.
  **P2.3 DONE via B6.2** (upstream SkiaGraphicsLayer gives real outsets/renderEffect; port's was
  a no-op). **P2.2: soak test added (`--soaktest`) and it CAUGHT A REAL PRE-EXISTING LEAK on
  BOTH legs** (skia ~40MB/cycle, SDL ~195MB/cycle, ~linear over 6 cycles, not animation churn,
  SDL 5× worse → textures). P2.2 stays OPEN pending a dedicated heap-profiled leak hunt; soak is
  a standalone diagnostic, not wired into the verify-mac gate yet. **Next candidates: the leak
  hunt (P2.2), or the D2/D3 convergence cleanups.**
- 2026-07-17 · **P2.2 leak hunt + D2–D6 evaluation** · commit `c1fabc5b` (soak bisect mode) ·
  Leak HUNT done (characterized, not fixed): real slow leak, both legs; layers CLEAN, not GC-lag,
  not animations; baseline composition-machinery ~0.15MB/mount + content amplification (image/
  vector/list) ~0.55MB/mount; image caches bounded/ruled out; remaining suspects (composition/
  snapshot retention + DrawCache offscreen bitmap lifetime) need a heap-tooling session. P2.2
  stays open. D2–D6 (from the P2.1 audit) evaluated in order and ALL DEFERRED: D2 feasible (the
  RootNodeOwner blocker is outdated) but poor ROI + high blast radius (shadows already parity);
  D3 depends on D2; D4/D5 gated on a11y/complex-text roadmap; D6 negligible. Net: Phase 2's
  clean wins are spent (P2.1 audit + P2.3-via-B6.2); the only substantive open item is the leak.
- 2026-07-17 · **P2.2 focused leak-fix session** · commit `7189433e` · FIXED one confirmed
  contributor: `ComposeOwner.onDetach` now clears the detached node's snapshot observations
  (`snapshotObserver.clear(node)`, matching upstream RootNodeOwner) — MAC-VERIFY ALL GREEN both
  legs. Deepened the soak (current RSS via `ps`; CDN_SOAK_STATIC). Exhaustively characterized the
  RESIDUAL leak (real, linear ~141KB/mount, referenced per macOS `leaks`, survives GC, per-mount,
  scales with node count; NOT layers/RenderNodes/nodes/observation-scopes/GrContext-budget/
  per-frame). Root cause is native-or-referenced growth needing a skia-aware heap profiler to
  pin — P2.2 stays open with a deterministic reproducer (`--soaktest`).
- 2026-07-17 · **P2.2 leak FIXED** · commit `c59caf72` · root cause: the port never swept
  `OwnerSnapshotObserver.clearInvalidObservations()` (upstream RootNodeOwner does, after each
  measure). Detached scopes' snapshot observations lingered, pinning object graphs via K/N
  ExternalRCRefs → the K/N-heap leak. Fix: sweep it in ComposeRootHost.measureAndLayout().
  Soak: all-63-screens 260→458MB (FAIL) → 238→240MB (PASS); Counter/Images/LazyGrid flat.
  Found by exact live-counters + static-mode + macOS leaks/heap/vmmap + component bisection
  (→ ripple indication draw observations). MAC-VERIFY ALL GREEN, perf unchanged. **Soak now
  WIRED into verify-mac.sh as a per-leg gate** (regression guard). Phase 2 (B5) COMPLETE.
