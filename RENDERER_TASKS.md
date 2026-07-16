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
  gate on PASS/FAIL; run `scripts/parity/parity.py`; a `CDN_PROFILE=1 CDN_FORCERENDER=1`
  `draw`-ms spot-check on LazyColumn/Tabs (fail on >~20% regression vs the pre-change run).
- **WIN-SMOKE** (Windows, pre-ship only): link the mingwX64 target + run `scripts/probe/`.
- **DRIFT-CHECK**: re-run `sync.py` on a clean checkout + diff `src/vendor/`; run the
  manual-vendor provenance check (P0.6).

---

## Phase 0 — Guardrails & harness (do FIRST — makes everything else trustworthy)

- [x] **P0.1** Make `scripts/parity/parity.py` target-aware (`macosArm64` default + a
  `-Prenderer=sdl3` variant), not hardcoded to the mingw exe — so the Mac runs parity for
  BOTH legs. *Done:* one flag switches leg; both produce a ranked report. [CONVERGE §5/§8]
- [~] **P0.2** Add a one-command **MAC-VERIFY runbook** (`scripts/verify-mac.sh` or a
  Makefile/Gradle task) chaining the MAC-VERIFY primitive; non-zero exit on any failure.
  *Done:* `verify-mac` builds both legs + runs self-tests + parity + perf spot-check. [§5]
  *Status: script AUTHORED on Windows (`scripts/verify-mac.sh` — both legs: build → 5
  probes → parity → CDN_PROFILE draw-ms gate vs a self-seeded `build/verify-mac/
  perf-baseline.txt`, +20% margin); bash -n clean, parsing + awk gate unit-smoked. NOT yet
  run end-to-end — needs the Mac. First Mac run seeds perf baselines + the parity
  `macosArm64/{skia,sdl3}` baseline keys (P0.4 note).*
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
  Still to add: a `macosArm64/skia` key on the Mac.* [§8]  *blocked-by: P0.1*
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

- [ ] **P1.1** **skiko-version alignment check** (DoD gate): confirm `skiko:0.150.1` exposes
  the `RenderNode`/`GraphicsContext` API the compose-core `beta01+dev4324` vendored files
  use; if not, bump `libs.versions.toml` skiko (or the compose ref) in lockstep. *Done:* a
  throwaway skiko-leg compile referencing `org.jetbrains.skiko.node.RenderNode` succeeds on
  the Mac. [§3/§7]  *blocked-by: P0.2*  — **blocks all of P1**
- [ ] **P1.2** Relocate the SDL node cluster `nativeMain → sdlRendererMain`:
  `GraphicsLayer.native.kt`, the `createNativeRenderNode` `expect` + SDL actual,
  `DeferredRenderNode`, `SdlDisplayList*`/`SdlRenderNode`. **Keep `GraphicsLayerOwnerLayer`
  SHARED.** *Done:* per-target compile green; MAC-VERIFY green (behaviour unchanged — still
  the port's GraphicsLayer on both legs at this step). [§4/§6]  *blocked-by: P1.1*
- [ ] **P1.3** Fork `GraphicsContext` per-leg: replace the ad-hoc anonymous
  `ComposeOwner.graphicsContext` (`createProjectGraphicsLayer()`) with an `expect`/factory
  seam; SDL → the relocated project impl. *Done:* MAC-VERIFY green. [§4]  *blocked-by: P1.2*
- [ ] **P1.4** Un-refuse + vendor the skiko files into `skikoRendererMain` (flip the `!` in
  `compose-fork.txt`, re-sync): `SkiaGraphicsLayer.skiko.kt`, `SkiaGraphicsContext.skiko.kt`,
  `Matrices.skiko.kt`, `Blur.skiko.kt`. *Done:* `sync.py` brings them in; they compile. [§7]
  *blocked-by: P1.3*
- [ ] **P1.5** Reverse the `prepareLayerTransformationMatrix` rename — use upstream
  `Matrices.skiko` `prepareTransformationMatrix`; drop the `com.compose.sdl` copy on the Skia
  leg (SDL keeps its own). [§7]  *blocked-by: P1.4*
- [ ] **P1.6** Provide upstream `actual class GraphicsLayer(renderNode: skiko.RenderNode)` on
  `skikoRendererMain`; delete the skiko-side `NativeRenderNode` usage (`NativeRenderNode.skia
  .kt`). *Done:* MAC-VERIFY Skia leg — self-tests PASS, parity approaches **~0% golden-master**
  (post-P0.3), no perf regression. [§4]  *blocked-by: P1.5*
- [ ] **P1.7** Add the **actual-API-parity invariant**: an `apiDump`/
  `:ui:compileCommonMainKotlinMetadata` check asserting the SDL `GraphicsLayer` actual and
  the vendored Skia one both match the shared `expect` (the shared owner layer depends on
  it). *Done:* check runs green + fails on a deliberate API skew. [§8]  *blocked-by: P1.6*
- [ ] **P1.8** (measure — the G1 justification) Quantify the **sync-tax**: reconcile a mock
  `compose.properties` bump before vs after B2, record hours-saved in the log. [§0.5]

## Phase 2 — B5: engine-convergence deltas

- [ ] **P2.1** Audit CDN-common vs upstream `skikoMain`; list every hand-rolled divergence
  and vendor the deltas (or record a named constraint). *Done:* a checked-off delta list. [§7]
- [ ] **P2.2** Lifetime model: **keep shared GC/release-queue on BOTH legs; do NOT vendor
  `ChildLayerDependenciesTracker`.** Add a **soak test** (navigate all screens ×N, assert an
  RSS ceiling) to cover the leak class. *Done:* soak passes on both legs. [§7/§8]
- [ ] **P2.3** Restore layer **outsets / blur-bounds expansion** via `Blur.skiko` on the Skia
  leg (fixes clipped blur in parity). *Done:* a blur/RenderEffect screen's Skia parity
  improves. [§7]  *blocked-by: P1.6*

## Phase 3 — B3: SDL fidelity (CAPPED under G1 — only parity-ranked user-visible wins)

- [ ] **P3.1** From the parity ranking, list the worst *user-visible* SDL gaps (gamma
  gradients / AA / blur / complex-script text). Do ONLY the ones that rank as real wins, each
  gated by a parity improvement. **Stop when the ranking flattens** — JVM is the fidelity
  tier for the rest. [§0.5.3, §4 (B3)]

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
