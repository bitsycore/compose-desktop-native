# RENDERER_CONVERGE.md

Plan for converging this port's rendering onto upstream Compose/Skiko internals —
"same internals as upstream, or as close as possible" — and for restoring the
upstream module boundaries (`:ui-graphics`, `:ui-text`) that are currently merged
into `:ui`.

The renderer **study file** — rationale, decisions, and analysis. The forward plan up top,
plus **Appendix A** (the retained-layer engine model + what already landed, consolidated
from the retired `RENDERER_REFACTOR.md`). **Revised after a 3-agent tactical review and a
5-agent council** (strategy / architecture / build-CI / vendoring-sustainability / QA) —
see §13 for the council synthesis; the corrections are folded in below.

> **The executable checklist is `RENDERER_TASKS.md`** — pure, ordered, checkbox tasks an
> agent works through and marks progress on. This file is the "why"; that file is the "do".

---

## 0. RULES (read first — these govern every decision below)

Non-negotiable principles. If a step violates one, the step is wrong, not the rule.

1. **Vendor as much as possible.** Copy upstream Compose / Skiko code verbatim into
   `src/vendor/` via `compose-fork.txt`. Hand-roll nothing upstream already provides.
   Edit-to-compile → copy-with-comment (manual vendoring). Never reinvent.

2. **Common Rendering layer, based on upstream Skiko as much as possible.** The shared,
   renderer-agnostic engine mirrors upstream's `skikoMain` structure and *mechanics*.
   We reuse upstream's machinery, not our own invention of it.

3. **ACTUAL only where genuinely backend-specific; commonize everything else.** Code
   goes: `Common` (upstream) → `CDN-common` (shared native engine) → `Skia actual` /
   `SDL actual`. Seam as narrow and low as possible. **What to minimize is *hand-rolled*
   (bespoke) actual surface, NOT actual-side line count** — a *fat vendored-upstream*
   actual (e.g. B2 carrying upstream's whole `GraphicsLayer`/`GraphicsContext` stack on
   the Skia leg) is fine and preferred; a thin *hand-written* one is the thing to shed.
   (Council fix: the old "leaf rasterization ONLY" wording contradicted B2.)

4. **Use upstream mechanics so our internals END UP identical to upstream** (or super
   close). Prefer the exact upstream class/algorithm over a lookalike.

5. **Restore upstream module boundaries.** `:ui-graphics` and `:ui-text` are their own
   modules, not merged into `:ui`.

**Litmus test:** *"Is this what upstream does? If not, what real platform constraint
forces the difference?"* Valid answers name a constraint (no Skiko on Windows K/N; SDL
is a triangle blitter). "It was easier" is not valid.

> **A caveat on Rule 4, surfaced by review:** upstream has **no renderer-agnostic node
> abstraction** — it uses skiko's concrete `RenderNode` directly, with no intervening
> interface. Our `NativeRenderNode` is a **port invention justified solely by the second
> (SDL) backend** (Rule 3). It is *not* an "upstream mechanic to converge toward"; on the
> Skia leg the goal is to **shed** it in favor of upstream's own `GraphicsLayer`/
> `RenderNode` (see B2). Keep this straight throughout.

---

## 0.5 GOAL / AUDIENCE / SUCCESS METRIC (the council's root finding — DECIDE THIS FIRST)

The strategy reviewer's central point: **the doc never states who this is for or what
"success" is, so its priority can't be defended.** This matters because the fidelity
and perf fights are *already effectively won* (SDL geo node 0.000% across 57 screens bar
a <0.06% fringe; LazyColumn −57%), so the remaining Track-B work (B1/B2/B5 + the module
split) is **internal cleanliness with no user-visible effect**, while the only
user-visible track (B3: solid→gamma gradients, clipped blur, complex text) is ranked
*lower*. The plan optimizes maintainability-purity but labels it "highest value."

**A first-class fact the doc ignored:** the project already ships a **JVM Compose Desktop
target** (`:demo:run`, the parity reference) — real Skia, 100% fidelity, on any host
including Windows. So the honest Windows story is already **two-tier**: *SDL native =
default (small, no-DLL, <0.06% off); JVM = the fidelity/feature escape hatch.* That alone
makes B3 and Track A **optional polish**, not obligations.

**Candidate goals — pick one; it flips the priority order:**
- **(G1) Cheap upstream-tracking for the maintainer** → B2/B5 lead honestly (they cut the
  per-`compose.properties`-bump reconciliation tax), *but only if paired with the drift
  tripwire + the local Mac verify runbook below*. Justify B2 on the **sync-tax economics**,
  not purity —
  quantify the hours a bump costs today vs after.
- **(G2) Consumer / third-party-app value** (the bridge plugin's audience) → **API
  coverage** (`compose-coverage.py` — cite a number the doc currently omits) + **B3
  visible fidelity** outrank the internal work; the module split waits for a real consumer.
- **(G3) "It already works"** → cap at the shipped state, treat JVM as the Windows
  fidelity tier, **kill Track A's spike entirely**, and stop.

**DECIDED (2026-07-16): G1 — upstream-tracking.** The goal is cheap future maintenance
(track upstream with minimal per-bump reconciliation), not Windows pixel-parity or feature
completeness. The locked priority order that follows:
1. **B5 + B2 are the work** — vendor upstream's `GraphicsLayer`/engine so each
   `compose.properties` bump re-syncs instead of being hand-reconciled. Justify/track B2 on
   the **sync-tax** (hours per bump before vs after), not aesthetics.
2. **Guardrails first / alongside:** the local Mac verify runbook (§5 Phase 0a) + the
   manual-vendor drift tripwire (§8) — without them, convergence silently rots.
3. **JVM is the documented Windows fidelity/feature tier** — so **B3 is capped** to cheap
   parity-ranked wins (the SDL leg need not chase Skia; users who need 100% use JVM).
4. **Track A shelved** — likely infeasible + breaks no-DLL, and under G1 its maintenance
   payoff doesn't justify even the spike. Skip §3 unless idle curiosity (then only the
   time-boxed kill-shot).
5. **B1 + the module split: DEFERRED** until a concrete need appears (internal cleanliness /
   publishing granularity, no G1 payoff yet).

---

## 1. NORTH STAR (revised for feasibility)

> Rendering is **upstream Compose/Skiko, vendored**, wherever the real Skia library
> exists — so on macOS/Linux the **layer/draw engine** internals *are* upstream. On
> Windows, where no Skia is available to Kotlin/Native, a **permanent SDL backend**
> implements the same seam and is converged as close as the backend allows. The two
> backends share the maximum common engine and diverge only at leaf rasterization.
> `:ui-graphics` and `:ui-text` are separate modules exactly as upstream ships them.

**Honesty correction (council, architecture):** "the internals *are* upstream on
macOS/Linux" is true for the **layer/draw engine** but **NOT for text, and not fully for
Canvas.** The Skia leg's `Paragraph` actual is the **shared `SdlParagraph`** — text
*layout, line-breaking, metrics, hit-test, and shaping* are the PORT's engine, only glyph
*rasterization* is Skia; `SkiaCanvas` is likewise a ~504-line bespoke bridge (upstream's
`SkiaBackedCanvas` is `!`-refused for the same shared-text/image reason). So part of the
parity "font drift" is a real **layout-engine delta**, not just a different default font.
This is a deliberate architectural axiom — **one shared, project-owned text+image
pipeline across both renderers** — and the doc must state it as such. To make text truly
upstream on the Skia leg would require a **B6** (Skia leg → skiko `SkiaParagraph` +
`SkiaBackedCanvas`); until/unless B6 is scheduled, "internals are upstream" means
*layer/draw engine*, and text drift is accepted and bounded by a text-metrics parity gate
(§8).

The review also established that **"real Skia on *Windows*" is probably not reachable
without breaking a hard project invariant** (§3). So the honest north star is: **upstream
layer/draw engine on macOS/Linux (text shared) + a first-class, converged SDL renderer on
Windows** — not "real Skia everywhere." The shipped SDL geo node is an **asset to invest
in**, not debt to delete.

---

## 2. CURRENT STATE (what we converge FROM)

- **Shared engine landed** (`nativeMain`, renderer-agnostic): `GraphicsLayerOwnerLayer`,
  `GraphicsLayer.native`, `NativeRenderNode` (interface + `expect createNativeRenderNode`),
  `DeferredRenderNode`, `ComposeOwner`, `ComposeRootHost`, `LayerTransformationMatrix`,
  `SdlParagraph.native`, `TextMeasurer`. Three-level skip in place. See Appendix A.
- **SDL leg shipped as the Windows default and verified** (this is new — treat as an
  asset): the cached-geometry `geo` node (`SdlDisplayListRenderNode`) is default, 0.000%
  across a 57-screen sweep bar a <0.06% cosmetic rotated-edge AA fringe, capturing
  geometry + plain/spanned text + icon glyphs; `CDN_LAYERCACHE=off|texture` fallbacks.
  ~3,800 lines; the perf win the refactor targeted is realized.
- **Skia leg** (`skikoRendererMain`, macOS+Linux): `SkiaCanvas` is a thin ~504-line bridge
  to `org.jetbrains.skia.*`. Its `createNativeRenderNode` still returns `DeferredRenderNode`
  — **not** skiko's `RenderNode`. Its `GraphicsLayer` is the port's hand-rolled
  `GraphicsLayer.native`, **not** upstream's `actual class GraphicsLayer`.
- **`compose-fork.txt` currently REFUSES (`!`) three vendorable skiko files** the Skia leg
  could take verbatim: `SkiaGraphicsLayer.skiko.kt`, `SkiaGraphicsContext.skiko.kt`,
  `GraphicsLayerOwnerLayer.skiko.kt`. The port hand-rolled equivalents instead — a Rule-1/2
  gap (see B2).
- **Module merge**: `:ui` contains `ui-graphics` + `ui-text` because their `Canvas` /
  `Paragraph` `expect`s must co-locate with their renderer `actual`s.
- **Hard constraints**: (i) **Skiko publishes no `mingwX64` K/N klib**; (ii) **the port
  ships no runtime DLL** — distributable = `<app>` + `data.kres`. Both bear on §3.

---

## 3. TRACK A SPIKE — can we get REAL Skia on Windows K/N? (rewritten; likely NEGATIVE)

Review consensus: **the direction is right to gate, but the honest expected outcome is
"no, not without a runtime DLL."** Do the spike anyway — it's cheap if front-loaded on
the kill-shot — but pre-decide the DLL tradeoff so a negative result is a clean shelving,
not a sunk cost.

**Why (verified by review):**
- Skiko already bridges Skia's C++ to K/N via hand-written `extern "C"` glue + cinterop on
  all its native targets — so **"can K/N call Skia" is solved**; `extern "C"` glue is
  *mandatory in every route* (cinterop is C-only). The unknown is purely the **Windows
  build/ABI**.
- **Kotlin/Native `mingwX64` is GNU-ABI** (mingw-w64 sysroot). **skia-pack / JetBrains-skia
  Windows is MSVC-ABI** (requires clang-cl). MSVC-ABI C++ **cannot be statically fused**
  into a GNU-ABI K/N binary (mangling, exception model, C++ runtime differ).
- The real crux is therefore: **can *current* Skia (C++20) be built to a GNU/mingw-ABI
  static `.a` at all?** Skia's own toolchain forces MSVC/clang-cl on Windows; the only
  precedent for mingw Skia is **Mozilla's tier-3 patches from the m55–m70 era**, largely
  abandoned. Against C++20 m138 this is an open-ended fork. **RED risk; likely infeasible.**
- **The Linux native precedent does NOT de-risk Windows.** Linux has one dominant C++ ABI
  (Itanium/libstdc++) shared by gcc+clang, so K/N-linux ↔ Skia-linux "just links." Windows
  is uniquely hard *because* of the GNU-vs-MSVC schism. Do not argue "Linux works → Windows
  is just another target."

**Routes (corrected):**
- **(a) Extend skiko's native build to `mingwX64`** — reuse skiko's whole glue/cinterop
  surface; needs a GNU-ABI Skia (same crux below). Most upstream-faithful *if* the archive
  exists.
- **(b) MSVC/clang-cl-built Skia + `extern "C"` shim as a **DLL***, consumed by K/N mingw
  across the C boundary via the import lib. **This is the reliable, standard MSVC↔mingw
  path — and it works — BUT it ships a runtime DLL, violating the no-DLL invariant.** A C
  shim resolves the ABI mismatch *only at a DLL boundary*; it does **not** make a static
  MSVC-ABI Skia statically linkable. (This corrects the prior draft, which called the shim
  a static alternative.)
- **(c) Skia `sk_capi`** — experimental, pre-1.0, incomplete; skiko deliberately avoids it.
  Confirm-and-discard cheaply.

**Re-scoped spike (kill-shot first, no Kotlin until it passes):**
1. **Experiment #1 — the kill-shot:** produce a **GNU-ABI static `libskia.a`** for the
   pinned Skia version. Try, in order, time-boxed ~2–3 days: **(i) clang targeting
   `x86_64-w64-windows-gnu`** (NOT clang-cl — keeps clang so Skia's SW rasterizer stays
   fast; the route the prior draft omitted); **(ii)** mingw-w64 GCC + the Mozilla patch
   set. **If neither yields a K/N-mingw-linkable archive, static Track A is dead — stop.**
2. **Experiment #2 (only if #1 passes):** compile a tiny `extern "C" skia_test_rect()`
   glue with the *same* toolchain, cinterop it from a throwaway `mingwX64` K/N binary, and
   raster to a **CPU `SkSurface`** (no GL). Validates glue-ABI + cinterop + lifetime.
   Reuse skiko's glue sources (route (a) done right).
3. **Default present path = CPU raster → `SDL_UpdateTexture`/`SDL_RenderTexture`** (infra
   the SDL leg already has). This **isolates the interop risk from the Windows-GL risk**:
   raw desktop GL is historically flaky on Windows — skiko uses **ANGLE/D3D** there, which
   would pull in ANGLE (~10–20 MB, normally DLLs). Add a GPU present path *only if* CPU
   raster measures too slow (the retained engine already re-rasters only dirty layers, so
   full-window cost is bounded).
4. **DLL decision, decided NOW:** if #1 fails, the only working real-Skia-on-Windows is the
   **route (b) DLL**. Is shipping one runtime DLL acceptable? If **no** (current invariant),
   **Track A is shelved on Windows and Track B is the permanent ceiling.** Wire into §11.

**Binary size (honest):** a full Skia (codecs + GPU) is **~20–40 MB**, +ANGLE if GL. Track
A *deletes* a kilobytes-scale hand-rolled rasterizer and *adds* tens of MB **on Windows —
the only platform Track A targets.** That is a distribution regression, not a wash.

---

## 4. TRACKS

### Track B — PRIMARY (do now; the real convergence path)

Track B is not "interim." Given §3's likely-negative outcome, Track B is *the* path: it
makes macOS/Linux literally upstream and keeps the SDL renderer as a permanent, converged
Windows backend. Ordered by value × spike-independence:

- **B2 — Skia leg: VENDOR upstream's `GraphicsLayer`, don't wrap. (Highest maintainability
  value, spike-independent — but re-rated MODERATE, not a low-risk file-flip.)** The core
  mechanic is sound and proven in-tree: `GraphicsLayer` is an `actual class` and the two
  renderer source sets attach to **disjoint targets** (same trick as `createRenderBackend`),
  so the Skia leg can carry upstream's `actual class GraphicsLayer(renderNode: skiko.
  RenderNode)` **verbatim** while SDL keeps its own — no shared-metadata leak (the
  `expect class` in vendored `commonMain` is unchanged). BUT the council (build + arch +
  sustainability, independently) found this is a **source-set migration, not "un-refuse 3
  files"**:
  - The port's `actual class GraphicsLayer` lives in **shared `nativeMain`** today; you
    cannot have it in both `nativeMain` and `skikoRendererMain`. So the whole SDL node
    cluster must **relocate `nativeMain → sdlRendererMain`**: `GraphicsLayer.native`, the
    `internal expect fun createNativeRenderNode` + its SDL actual, `DeferredRenderNode`,
    and the SDL-side node types. (Once the skiko `createNativeRenderNode` actual is deleted,
    a dangling `expect` in `nativeMain` won't compile — the expect itself must move down.)
  - `ComposeOwner.graphicsContext` is currently an **ad-hoc anonymous object** calling
    `createProjectGraphicsLayer()` — NOT upstream's actual. B2 forks it per-leg (Skia →
    vendored `SkiaGraphicsContext`; SDL → the relocated project one) via a new
    `expect`/factory seam in shared `ComposeOwner`. The doc previously ignored this.
  - **Vendoring closure** (must un-refuse together or it won't compile): `SkiaGraphicsLayer
    .skiko.kt`, `SkiaGraphicsContext.skiko.kt`, **`Matrices.skiko.kt`** (defines
    `prepareTransformationMatrix`, which the port renamed to `com.compose.sdl.
    prepareLayerTransformationMatrix` — a Rule-1 miss to reverse), and **`Blur.skiko.kt`**
    (RenderEffect/outset expansion). `RenderNodeContext` is surface-independent (a bare
    `Boolean` ctor — verified), so this does **not** drag in the refused `RootNodeOwner`/
    `SkiaLayer`/`ComposeScene` stack.
  - **Keep `GraphicsLayerOwnerLayer` SHARED** — strike the old "if the owner arch allows"
    hedge. It ends in a skiko-only `setLightingInfo`/`LIGHT_*` tail and is coupled to the
    (refused, out-of-scope) `RootNodeOwner`; forking it up drags that in for no benefit and
    shrinks the shared engine. It already compiles against BOTH `GraphicsLayer` actuals
    because they share the `expect`'s public API — which becomes a **standing invariant**
    (see §8: apiDump/metadata parity between the two actuals).
  - **skiko-version-alignment check (DoD):** vendored files are compose-core
    `beta01+dev4324`; the skiko klib is pinned separately at `0.150.1`. Confirm `0.150.1`
    exposes the `RenderNode`/`GraphicsContext` API those files expect, or bump in lockstep.
    The hand-rolled `GraphicsLayer` insulated the build from this; un-refusing removes the
    insulation, and the failure is invisible on the Windows dev box.
  - **Verify on the Mac:** B2 lands only on the Skia leg (macOS/Linux) — the Windows dev box
    can't build it, and `parity.py` is Windows-only. So the **Mac verify runbook** (§5
    Phase 0a) is a hard prerequisite, not parallel work — the maintainer builds + runs it
    on the Mac before committing.
  This supersedes the prior "wrap skiko RenderNode behind our façade" (that was *less*
  vendoring). Net: still the right convergence, but a moderate refactor that touches the
  shared engine's source-set boundary and needs a Mac verify pass before it can be trusted.
- **B5 — Common-engine convergence.** Audit CDN-common vs upstream `skikoMain`; vendor the
  deltas (see §8). Spike-independent.
- **B1 — SDL-only node dedup (re-scoped).** A base for the two **SDL** nodes
  (`DeferredRenderNode`* + `SdlDisplayListRenderNode`) sharing the "SDL is a triangle
  blitter" transform/shadow/clip scaffolding. **Lives in `sdlRendererMain`, NOT CDN-common,
  and explicitly does NOT bind the skiko node** (which delegates to skiko per B2). This is a
  real SDL constraint (Rule 3), not an upstream mechanic. Low-risk mechanical dedup, but it
  now touches the *newly-default* geo hot path → run it against a fresh parity+probe+perf
  baseline. *(\*`DeferredRenderNode` is the shared default node; after B2 it is used only by
  the SDL leg + as the SDL fallback, so co-locating the base in the SDL set is consistent.)*
- **B3 — SDL fidelity, parity-ranked.** Gamma-correct gradients, AA quality, `RenderEffect`/
  blur, complex-script text, true layer compositing. **Not throwaway** now that Track A is
  likely shelved (the SDL renderer is permanent). Still cap to parity-ranked wins so effort
  tracks visible impact.
- **B4 — SDL perf.** Mostly shipped (see Appendix A.3). Remaining: shared glyph atlas
  (replace per-run-string textures), `drawImageRect` capture (journal rates low-ROI — as
  needed). Do opportunistically.

### Track A — GATED, LIKELY-NEGATIVE (spike per §3)

Only if the spike passes **and** the DLL tradeoff is accepted:
- Windows gains real Skia (present-only SDL; CPU-raster present by default). macOS/Linux
  keep their direct Skia GPU path (no present-only regression there).
- **Do NOT delete the SDL rasterizer.** The prior draft's "delete it" contradicted "keep
  `-Prenderer=sdl3`" (a Skiko-free build *is* the rasterizer). Resolution: the **SDL geo
  node stays the default / small-binary / no-DLL Windows path**; real Skia is an **opt-in
  max-fidelity build**. Two configs, chosen deliberately — the rasterizer is not debt.

### Non-goal (recorded): "abstract + re-back with SDL" does not shrink SDL work

The Skia leg is a thin bridge; its substance is Skia's C++, not copyable Kotlin. The "layer
in between" already exists (`androidx.compose.ui.graphics.Canvas`, with the pipeline above
it shared). Re-seaming at `org.jetbrains.skia.Canvas` reproduces the Canvas interface and
leaves the SDL impl equally thick. SDL thickness is the cost of SDL being a triangle
blitter. The only way to *reuse* the Skia renderer is real Skia (Track A).

---

## 5. SEQUENCING (revised)

**Council found no PR/push CI (only tag-triggered `publish.yml`), and `parity.py` is
hardcoded to the Windows/mingw exe.** The maintainer does NOT want GitHub CI (slow feedback
for a solo project). Resolution: the gate is a **local Mac (or Linux) verify runbook** —
and, crucially, **macOS/Linux run BOTH renderers**: the Skia leg by default AND the SDL leg
under `-Prenderer=sdl3`. So the Mac is not just the "Skia box" — it is a **single machine
that verifies both renderers**. Windows is then only needed for the *shipped mingw target
binary*, the Windows-only `PrintWindow` probe, and the tag-time metadata publish.

```
Phase 0a (HARD GATE,      • MAC/LINUX VERIFY RUNBOOK (manual — the machine that builds BOTH
  do FIRST, local)          renderers; Windows builds only the mingw target). Before B2/B5:
                            - Skia leg (default): :demo/:apidemo runDebugExecutableMacosArm64
                              — catches B2/B5/skiko-version compile breaks invisible on Windows;
                            - SDL leg on the SAME box: add -Prenderer=sdl3 — verifies the SDL
                              renderer too, no Windows box needed for day-to-day SDL work;
                            - cross-platform self-tests on BOTH legs (--nav3test/--backtest/
                              --clicktest/--scrolltest/--multiwintest — live in nativeMain;
                              gate on PASS/FAIL);
                            - parity (both legs — needs parity.py made target-aware) + perf spot-check.
                          • Vendor-clean check (local script, on demand): re-run sync.py on a
                            clean checkout, diff the regenerated src/vendor/ (catches hand-edits
                            + stale clone). No CI needed — a pre-commit habit / Makefile target.
Phase 0b (parallel)       • Spike §3 kill-shot (Exp #1 GNU-ABI libskia.a) — time-boxed 2-3d.
Phase 1 (after 0a,        • B2 (relocate SDL node cluster + vendor upstream GraphicsLayer on Skia)
  spike-independent)        — the maintainability convergence; verify via the Mac runbook.
                          • B5 (engine convergence, vendor deltas).
Phase 2 (per §0.5 goal)   • B3 (user-visible SDL fidelity, parity-ranked) — promote ABOVE B1 if
                            the goal (§0.5) is consumer value. B1 (SDL node dedup) only if a
                            concrete need appears — it's cleanup on the newly-default hot path.
Phase 3 (gate)            • If spike PASS + DLL accepted → Track A (real-Skia opt-in, keep
                            SDL default). Else → shelve Track A; Track B is the ceiling.
Phase 4 (deferred)        • Module split §6 — only when a consumer needs the publishing
                            granularity (no user benefit otherwise; see §0.5).
Cross-cutting             • Multi-gate verification §8 on every rendering change.
```

**Why the Mac (or Linux) suffices as the ONE verify box (no CI):** macOS/Linux build the
Skia leg natively AND run the SDL leg under `-Prenderer=sdl3`, so a local run retires the
Skia-leg-invisible-on-Windows risk *and* covers SDL renderer work — one machine, both
renderers. Windows is only needed for: (1) the **shipped mingw target** (the actual binary
users get — final validation still wants a real Windows run before release); (2) the
**Windows-only `PrintWindow` probe** (`scripts/probe/`); (3) the **common-metadata
publication** (only the Windows publish job compiles it — a tag-time concern the maintainer
already handles; the module split, the main metadata risk, is deferred anyway). Caveat:
SDL-on-macOS is a faithful proxy for SDL-on-Windows for *renderer* correctness (same code;
SDL3 abstracts the platform) but not for platform specifics (per-OS driver hints, system
fonts) — so keep a Windows smoke run before shipping the mingw binary.

Rationale: the Mac runbook makes B2 and the split *safe to attempt* without slow CI; B5
pays off regardless of the spike; the kill-shot retires Track A's risk cheaply; B1 and the
module split are lowest-value and deferred per §0.5.

---

## 6. PART TWO — RESTORE `:ui-graphics` AND `:ui-text` AS MODULES

**Goal:** match upstream artifact boundaries. `:ui` holds only `androidx.compose.ui.*`
(minus graphics/text); `:ui-graphics` and `:ui-text` become their own modules.

**Why merged today:** `Canvas`/… (ui-graphics) and `Paragraph`/`ParagraphIntrinsics`
(ui-text) `expect`s resolve to renderer `actual`s; Kotlin requires `expect`+`actual` in the
same module; the renderers live in `:ui`.

**DAG (verified against upstream `cmp-ref` v1.12.0-beta01):**
`ui-graphics → ui-unit`; `ui-text → ui-graphics + ui-unit`; `ui → ui-graphics + ui-text +
ui-geometry + ui-unit + ui-util`. File placement upstream:
- **`ui-graphics`**: `Canvas`, `GraphicsLayer`, `GraphicsContext`, all skiko `RenderNode`
  usage (`SkiaBackedCanvas.skiko.kt`, `SkiaGraphicsLayer.skiko.kt`, `SkiaGraphicsContext.skiko.kt`).
- **`ui`**: `OwnedLayer`, `GraphicsLayerOwnerLayer`, `RootNodeOwner`, `LegacyRenderNodeLayer`,
  `LayoutNode`.
- **`ui-text`**: `Paragraph`, `ParagraphIntrinsics`.
"Put each file where upstream puts it" → no cycle (matches upstream).

**Cinterop placement (corrected — this was self-contradictory in the prior draft):**
`SdlParagraph.native.kt` is **cinterop-free** (it goes entirely through the
`com.compose.sdl.text.*` interface seam — `currentTextMeasurer`, `NativeTextCanvas`,
`NativeTextMeasurer`, already in `commonMain`). The text renderers (`Sdl3TextRenderer`,
`FreeTypeIcons`) DO use `sdl3`/`sdl3_ttf`/`freetype`, and `sdl3_ttf.def`/`sdl3_image.def`
carry `depends = sdl3`. Therefore:
> **Keep all four cinterops AND both concrete renderers in `:ui-graphics`. Move only
> `Paragraph`/`ParagraphIntrinsics` (expect+actual) + expose the `com.compose.sdl.text`
> interface to `:ui-text`.** No cinterop crosses a module boundary; the sibling
> `depends = sdl3` `-library` wiring stays intact inside `:ui-graphics`. (Answers Open Q4.)

**Cohesion cost (council, architecture) — name it, don't hide it.** The cinterop
resolution is the right pragmatic call, but it means `:ui-graphics` is **not** upstream's
pure `:ui-graphics`: it carries the SDL/TTF/FreeType cinterops AND the text *rasterizers*
(`Sdl3TextRenderer`/`SkiaTextRenderer`/`FreeTypeIcons`), while the text *model*
(`Paragraph`) moves to `:ui-text`. Upstream has no such model/rasterizer split. Defensible
("SDL is the rasterizer; cinterops live in one module"), but it is a deliberate divergence,
not the clean upstream mirror the DAG implies.

**Build fan-out the split touches (council, build) — an explicit checklist, not just file
moves:** `settings.gradle.kts` (`include`/`projectDir` ×2); `publish.yml`'s hardcoded
`MODULES=` list + per-host package-delete `SUFFIXES`; **the bridge-plugin substitution
rules MUST add `androidx.compose.ui:ui-graphics` + `:ui-text` → project modules** (else
consumer/`:demo`/`:apidemo`/`:material-symbols` builds break — those coords have no
mingw/linux Maven klib, the bridge's whole reason to exist); `compilerOptions`
(`-Xexpect-actual-classes` + opt-ins) replicated into both new modules (they carry
expect/actual classes); the `com.compose.sdl.text.*` interface widened to public in
`:ui-graphics` for `:ui-text` to see it; and the `-Prenderer` conditional source-set logic
duplicated into `:ui-graphics`'s build (doubling the config-cache "stale → phantom missing
cinterop" surface — expect to nuke `.gradle/configuration-cache/` between renderer switches).

**Track dependence:** trivial under Track A (actuals are upstream skiko's, vendored;
SDL gone from graphics). Under Track B it is pure relocation churn with the fan-out above
and **no user-facing benefit**, so **defer to Phase 4** unless a real consumer needs the
publishing granularity (per §0.5). B2's Skia-leg vendoring (which *reduces* the hand-rolled
surface to relocate) should NOT wait, however.

---

## 7. VENDORING TARGETS (Rule 2/4 — named)

Vendor these upstream `skikoMain` mechanics into the Skia leg / CDN-common; each via
`compose-fork.txt` (pin ref, re-sync, let the build report gaps):
- **B2 vendoring closure** (must un-refuse *together* or it won't compile — council, build
  + sustainability): `SkiaGraphicsLayer.skiko.kt`, `SkiaGraphicsContext.skiko.kt`,
  **`Matrices.skiko.kt`** (defines `prepareTransformationMatrix` — the port renamed it to
  `com.compose.sdl.prepareLayerTransformationMatrix`; **reverse that Rule-1 miss** and use
  upstream's), and **`Blur.skiko.kt`** (RenderEffect/outset bounds expansion). NOT
  `GraphicsLayerOwnerLayer.skiko.kt` — see the lifetime decision below.
- **Lifetime model — DECIDED (was "re-vendor OR re-implement", undecided → dangerous
  asymmetry per council/architecture).** Upstream's `ChildLayerDependenciesTracker`
  (parent→child layer lifetime) is deliberately trimmed; the port uses `NativeReleaseQueue`/
  GC. **Keep ONE shared lifetime model — GC/release-queue on BOTH legs; do NOT vendor
  `ChildLayerDependenciesTracker` onto the Skia leg.** A per-leg lifetime divergence is the
  exact class that caused the A.4 navigation crash; keeping it shared is simpler and safer.
  (If a concrete leak ever demands the tracker, add it to BOTH legs + a lifetime/soak gate
  in §8 — never one leg only.)
- **Keep `GraphicsLayerOwnerLayer` SHARED** (do not un-refuse / fork it): its skiko-only
  `setLightingInfo`/`LIGHT_*` tail + `RootNodeOwner` coupling would push the fork point up
  and force an SDL copy. It compiles against both `GraphicsLayer` actuals via the shared
  `expect` API — an invariant gated in §8.
- **Outsets / blur-bounds expansion** — trimmed today (→ clipped blur in parity); vendor
  back via `Blur.skiko.kt` on the Skia leg; on SDL, a named B3 fidelity item.
- **Text (§1 axiom):** one shared, project-owned `Paragraph`/`ParagraphIntrinsics` pipeline;
  SDL keeps its `NativeTextMeasurer` behind the interface. Making Skia-leg text *truly*
  upstream is **B6** (skiko `SkiaParagraph` + `SkiaBackedCanvas`) — optional, only if the
  text-metrics parity delta proves worth it.

---

## 8. VERIFICATION (expanded — parity alone is NOT sufficient)

**Council/QA baseline fact: today ALL gates are manual/local** — the only workflow is
tag-triggered `publish.yml`; there is no PR/push CI, no test source sets, and `parity.py`
always exits 0 with no thresholds and hides crashes as an easily-missed `NATIVE FAILED`
row. **The maintainer keeps verification LOCAL by choice (no slow CI)** — so the fixes
below make the *local runbook* trustworthy: give the scripts real thresholds + non-zero
exit so a bad run is obvious. The primary box is the **Mac/Linux (it runs BOTH renderers —
Skia by default, SDL under `-Prenderer=sdl3`)**; Windows is the pre-ship smoke for the
mingw target + the `PrintWindow` probe. The journal (A.4) proves screenshots missed a nav
crash (the **probe** caught it) and gave false 13–17% settle-timing signals (Pickers).

- **`scripts/parity/parity.py`** (native-vs-JVM) — the primary *fidelity* net, but it needs
  three fixes to *certify* rather than *rank*: (1) **align fonts** — load the bundled
  `NotoSans` into the JVM leg (`MainJvm.kt` `ScreenHost`) to collapse the dominant
  "font-drift" noise (and expose the real *layout-engine* delta from the shared
  `SdlParagraph`, §1); (2) **per-screen golden baselines + tolerances in-repo** and make the
  script **exit non-zero** on breach or `NATIVE FAILED`; (3) note the Skia leg is a
  categorically **stronger** signal than SDL — post-B2 + font-alignment it should approach
  **~0% (a true golden-master)**, unlike SDL's irreducible triangle-blitter delta.
- **Render-to-quiescence capture** (replaces the fixed `--frames=6`) — both legs expose
  invalidation state (`hasInvalidations()`; JVM `render(nanos)`/`hasInvalidations`); settle
  to quiescence then capture, with an explicit "disable animations" seed for never-settling
  screens. This is the real fix for the Pickers false-signal (mis-timed legs), cheaper and
  more robust than a bespoke frame-lock.
- **Run the existing cross-platform self-tests on the Mac** (`--nav3test/--backtest/--clicktest/
  --scrolltest/--multiwintest`) — they live in `nativeMain`, so they exercise the **Skia leg
  too** and plug its interaction/crash hole (the Windows-only probe cannot). `--nav3test`
  would have caught the A.4 crash. Add a **soak variant** (navigate all screens ×N, assert
  an RSS ceiling) to cover the lifetime/leak class (the GC-vs-tracker model, §7). These are
  the Mac-runbook's interaction gate (§5 Phase 0a) — no CI required.
- **Probe rigs** (`scripts/probe/`) — Windows-only; co-equal for SDL-leg interaction.
- **Perf spot-check** — a **before/after relative delta on the same machine** (`CDN_PROFILE`
  + **`CDN_FORCERENDER=1`**) on LazyColumn/Tabs `draw`-ms; flag a large regression (e.g.
  >20%). `draw` (not vsync-capped `present`) is the metric. Relative-on-one-box sidesteps
  the cross-machine variance that made absolute thresholds meaningless.
- **HiDPI / Option-B density** and **multi-window** checks — what a present-path/layer change
  (esp. Track A's present-only blit) breaks.
- **Vendor-fidelity checks** (sustainability) — the thing this doc is *about*, currently
  ungated: (a) the **vendor-clean check** (§5 Phase 0a — a local script / Makefile target,
  not a CI job); (b) a **manual-vendor drift tripwire** — machine-readable provenance
  headers (`// VENDOR-BASE: <path>@<ref>`) on the manual-vendored files
  (`GraphicsLayer.native.kt`, `GraphicsLayerOwnerLayer.kt`, `NativeRenderNode.kt`) + a
  script that flags when the upstream origin changed since the recorded ref, turning
  "reconcile by hand" from a hope into a checklist. Run at each `compose.properties` bump.
- **`GraphicsLayer`-actual API-parity invariant** (architecture) — the shared
  `GraphicsLayerOwnerLayer` silently depends on both `GraphicsLayer` actuals exposing the
  same public API as the `expect class`; gate with `apiDump`/`compileCommonMainKotlinMetadata`.
- **`:apidemo` has NO parity coverage** (harness is demo-only) — its `UiCompat`
  dialog/dropdown/mTLS actuals are human-checked; note the gap.

---

## 9. RISKS & ROLLBACK

- **Skia-on-mingw likely infeasible** (GNU-ABI build of C++20 Skia). RED. Mitigation:
  kill-shot spike first; near-zero sunk cost on a negative; B2/B5 proceed regardless.
- **No-DLL invariant vs the only working route (b) DLL.** Pre-decided in §3.4.
- **Windows-GL flakiness** (skiko uses ANGLE/D3D) + **ANGLE size/DLLs.** Mitigation: default
  present path = CPU raster.
- **Binary-size regression on Windows** under Track A (~20–40 MB +ANGLE vs kilobytes today).
  State the baseline + projected multiple before committing.
- **B1 touches the newly-default geo hot path.** Mitigation: fresh parity+probe+perf baseline;
  keep strictly mechanical. (Also lowest-value — deferred per §0.5.)
- **Module-split cinterop cross-boundary** — avoided by the §6 resolution; but the split's
  build fan-out (bridge substitution rules, `publish.yml MODULES`, config-cache) is real —
  see §6.
- **Verification is LOCAL by design** (maintainer's choice — no slow CI). The risk the
  council raised ("Skia leg unverified from Windows") is retired by the **Mac verify
  runbook** (§5 Phase 0a), not by GitHub Actions. Residual risk = *discipline* (remembering
  to run the Mac pass before committing B2/B5) — acceptable for a solo project; a pre-commit
  hook or Makefile target makes it a habit. CI stays a later option if contributors join.
- **skiko-version drift** (build) — vendored files (compose-core `beta01+dev4324`) vs the
  independently-pinned `skiko:0.150.1` klib. Un-refusing removes the hand-rolled insulation;
  an API mismatch breaks the Skia-leg compile (invisible on Windows). Check as B2 DoD.
- **Pinned ref is a `+dev` commit** (sustainability) — `COMPOSE_CORE_REF=…v1.12.0-beta01+dev4324`
  on a branch upstream force-pushes/GCs; `src/vendor/` is gitignored (no committed fallback),
  so a reaped ref makes a fresh checkout un-syncable. Mitigation: pin to a durable **tag**
  (or a mirror under the project's org).
- **Divergence drift** — mitigated by the **local vendor-clean check + manual-vendor
  provenance tripwire** (§8), NOT "periodic re-sync" (which decays to "never"). Add a
  ref-bump runbook: bump → `sync.py` → build → drift check → parity + probe + Mac verify.
- **Permanent bespoke SDL surface (~3,800 lines)** — `SdlDisplayListRenderNode`,
  `DeferredRenderNode`, SDL `NativeRenderNode`, `Sdl3Canvas/DrawScope/TextRenderer`,
  `FreeTypeIcons`, and the §7 re-implemented mechanics have **no upstream to vendor** and no
  re-sync will ever touch them; every upstream engine change must be chased in by hand. This
  is the true long-run maintenance cost — budget it as a named line item, not a one-time task.
- Everything on a branch; each phase independently revertible; multi-gate §8 is the net.

---

## 10. OPEN QUESTIONS

1. **Is real-Skia-on-Windows worth it at all**, given (a) likely-infeasible static build and
   (b) the DLL route breaks the no-DLL invariant + adds tens of MB? Default answer trending
   **no** → Track B is the permanent ceiling. Confirm via the kill-shot spike.
2. **Is one runtime DLL ever acceptable** for an opt-in max-fidelity Windows build? (If never,
   Track A is dead the moment Exp #1 fails.)
3. Should the `:ui-graphics`/`:ui-text` split happen under Track B for publishing/API reasons,
   accepting relocation churn, or wait? (Recommend wait; do B2 now regardless.)
4. ~~Module DAG~~ — **answered** in §6 (cinterops stay in `:ui-graphics`; only Paragraph →
   `:ui-text`).
5. Present path under Track A: CPU raster (default, simplest, isolates risk) vs GPU (measure
   only if CPU too slow).

---

## 11. TARGET END-STATE NODE SET (so the matrix doesn't sprawl)

- **Skia leg:** upstream `actual class GraphicsLayer` backed by skiko `RenderNode` (B2). No
  `NativeRenderNode`, no `DeferredRenderNode`.
- **SDL leg:** `SdlDisplayListRenderNode` (geo, default) + `DeferredRenderNode` (fallback),
  sharing the SDL-only base (B1). `SdlRenderNode` (texture, legacy) retired once geo is
  unquestionably dominant.
- **Track A (if it ever lands):** SDL present-only path is additive; nodes unchanged.

---

## 12. STATUS / JOURNAL

- 2026-07-16 — Doc created; revised after 3-agent review (all **APPROVE-WITH-CHANGES**).
  Key shifts folded in: Track A demoted to a likely-negative gated spike (mingw-ABI Skia
  RED; DLL route breaks the no-DLL invariant); Track B promoted to PRIMARY; **B2 reframed
  from "wrap" to "vendor upstream's `actual class GraphicsLayer`"** (un-refuse 3 skiko files
  — biggest Rule-1/2 win, spike-independent); **B1 relocated to `sdlRendererMain`, SDL-only,
  not binding the skiko node**; §6 cinterop resolution (all cinterops stay in `:ui-graphics`,
  only `Paragraph` → `:ui-text`); §8 verification expanded (probe + frame-locked + perf +
  HiDPI + multi-window + Linux CI); §7 named the outsets/blur + `ChildLayerDependenciesTracker`
  gaps; corrected the record that `NativeRenderNode` is a port invention, not an upstream
  mechanic. **Recommended first concrete step: B2 (vendor upstream GraphicsLayer on the Skia
  leg), verified via a local Mac pass — both spike-independent.** Spike §3 not yet run.
- 2026-07-16 — Revised again after the **5-agent council** (§13). Folded in: a §0.5
  goal/audience section (the council's root finding); Rule 3 restated (bespoke vs
  vendored-upstream surface); North Star text-honesty correction (Skia-leg text is the
  shared `SdlParagraph`, not upstream — new B6 option / stated axiom); **B2 re-rated from
  file-flip to a source-set MIGRATION** (relocate the SDL node cluster out of `nativeMain`;
  fork the ad-hoc `GraphicsContext`; closure = +`Matrices.skiko`/`Blur.skiko`; skiko-version
  DoD; keep owner layer shared); **§5 rewritten so a local MAC VERIFY RUNBOOK is the Phase-0a
  HARD GATE before B2** (maintainer doesn't want slow CI; the Mac builds the Skia leg that
  Windows can't); lifetime model DECIDED (shared GC on both legs); §6 build fan-out + cohesion
  cost enumerated; §8 verification made into real local gates (font-aligned golden-master,
  render-to-quiescence, Mac-run cross-platform self-tests, drift tripwire, actual-API-parity
  invariant); §9 risks added (local-verify-by-design, skiko-drift, `+dev`-ref fragility,
  permanent ~3,800-line SDL surface). **Net council verdict: correct project + right fork
  point, but re-ranked around a stated goal, guarded by a local Mac verify pass done first.**

---

## 13. COUNCIL SYNTHESIS (5-agent review, 2026-07-16)

Five reviewers, distinct lenses. Verdicts: **Strategy** = QUESTIONABLE PRIORITY;
**Architecture** = SOUND-WITH-CHANGES; **Build/CI** = BUILDABLE-WITH-CHANGES;
**Vendoring-sustainability** = SUSTAINABLE-WITH-CHANGES; **QA** = SOUND-WITH-CHANGES.
**Net: a correct project with the right fork point (`GraphicsLayer`), but over-claiming,
under-scoping B2, and resting on verification that isn't built.** No one said "wrong
project."

**Consensus (independently found by multiple members):**
1. **No CI exists** (build + QA verified) — only tag-triggered `publish.yml`; `parity.py`
   is Windows/SDL-only and can't test the Skia leg where B2 lands. → the maintainer verifies
   B2/B5 on the **Mac** (a Skia-leg target) via a local runbook (Phase-0a); no CI wanted.
2. **B2 is a source-set migration, not "un-refuse 3 files"** (build + arch + sustainability)
   — relocate the node cluster out of `nativeMain`, fork the ad-hoc `GraphicsContext`,
   +`Matrices.skiko`/`Blur.skiko` closure, skiko-version alignment risk.
3. **The North Star over-claims** (architecture) — "internals *are* upstream on macOS/Linux"
   is false for **text** (shared `SdlParagraph` = the layout engine) and Canvas. → B6 or
   stated axiom.
4. **Keep `GraphicsLayerOwnerLayer` shared; fork only at `GraphicsLayer`/`GraphicsContext`**
   (arch + build). The fork point itself is endorsed as the right, honest seam.
5. **Missing guardrails** (sustainability + QA) — vendor-drift tripwire, perf gate,
   font-aligned golden-master parity, `+dev`-ref fragility.

**The one divergence — the strategist vs the rest — resolves the same way.** Members 2/3/4
say "the fork is right, scope + guard it"; member 1 says "most of this is polish — fidelity
(0.000%) and perf (−57%) are already won, and the JVM Compose Desktop target is a free
Windows 100%-fidelity fallback, so B3/Track A are optional." Both point to: **don't rush
B2; first state the goal (§0.5), then set up the local Mac verify pass.** If the goal is
cheap upstream-tracking → B2/B5 lead (justified on sync-tax economics, not purity). If it's
consumer value → B3 + API coverage outrank the internal work; the module split waits.

**Full member reports** are in the session transcript; their concrete corrections are
folded into §0.5–§9 above.

---

# APPENDIX A — Retained-layer engine: model, contract, and what landed

Consolidated from the (now-deleted) `RENDERER_REFACTOR.md`. This is the durable
reference for *why* the engine is shaped as it is and *what already shipped*; the
blow-by-blow commit journal is dropped — the endpoints and learnings are kept.

## A.1 How upstream skiko renders (the model we copied)

Upstream skips work at **three levels**; we have all three now.

- **L1 — frame scheduling.** After each frame the scene only schedules another if
  still dirty (`hasInvalidations()`); invalidations arrive targeted from the snapshot
  observer. *Ours:* `ComposeWindow.shouldRender()` gates `renderFrame()`; the loop
  blocks on `SDL_WaitEventTimeout` when idle.
- **L2 — measure/layout only dirty nodes.** Vendored `MeasureAndLayoutDelegate.
  relayoutNodes` (depth-sorted, holds only nodes needing work; self-skips when clean).
  *Ours:* used verbatim via `ComposeOwner.measureAndLayout()`.
- **L3 — draw record-once / replay (the expensive one).** Each isolated `LayoutNode`
  gets an `OwnedLayer` (`GraphicsLayerOwnerLayer`) owning a `GraphicsLayer` (upstream's
  RenderNode concept). `updateDisplayList()` re-records **only if `isDirty`**;
  `drawLayer()` **replays** the cached display list.
  - **The critical property:** *transform / alpha / clip changes do NOT set `isDirty`.*
    Moving, scaling, rotating, or fading a layer just **replays cached content under a
    new transform** — no re-record. `move()` (scroll) only updates `topLeft`. Only a
    genuine content change (a state read inside the draw block, or a resize) re-records.
  - Upstream **replays the whole scene every frame** — no dirty-region / partial
    present. The entire win is from **not re-recording clean layers**, not from
    redrawing less screen. (So we explicitly do NOT need dirty-region rendering.)
  - `GraphicsLayer` (skiko actual) is a thin façade over `org.jetbrains.skiko.node.
    RenderNode` (a Skia `Picture` display list). All transforms/effects are RenderNode
    *properties* applied at replay. `LegacyRenderNodeLayer.skiko.kt` is the pure-Skia
    `PictureRecorder`/`Picture` reference — the blueprint for a from-scratch node.

Key upstream files (under `cmp-ref`): `node/GraphicsLayerOwnerLayer.skiko.kt` (the
OwnedLayer — dirty-gated record+replay+property-diff), `OwnedLayerManager` impl in
`RootNodeOwner.skiko.kt` (`dirtyLayers`, `notifyLayerIsDirty`, `recycle`),
`node/LegacyRenderNodeLayer.skiko.kt` (pure-Skia reference), `ui-graphics/.../layer/
SkiaGraphicsLayer.skiko.kt` (GraphicsLayer actual), `ui-graphics/.../SkiaGraphicsContext
.skiko.kt`, `ui-graphics/.../SkiaBackedCanvas.skiko.kt`.

## A.2 Compositing-strategy contract (`requiresLayer()` — match on both legs)

From `SkiaGraphicsLayer.skiko.kt`. Getting this right is what makes overlapping-content
alpha, tinted layers, and blend modes correct without over-allocating offscreens:

| Condition | `Auto` | `Offscreen` | `ModulateAlpha` |
|---|---|---|---|
| `alpha < 1` | offscreen | offscreen | per-op alpha multiply (no offscreen) |
| `colorFilter != null` | offscreen | offscreen | offscreen |
| `blendMode != SrcOver` | offscreen | offscreen | offscreen |
| `renderEffect != null` | offscreen | offscreen | offscreen |
| none of the above | replay in place | always offscreen | replay in place |

`ModulateAlpha` bakes alpha into recorded ops at record time (alpha changes re-record).

## A.3 What landed (the engine + the SDL geo node)

**Retained-layer engine (shared, `nativeMain`):** `createLayer` returns
`GraphicsLayerOwnerLayer` (vendored from skiko, `setLightingInfo` tail stripped);
`ComposeOwner` implements `OwnedLayerManager` (`dirtyLayers` + `notifyLayerIsDirty` +
`recycle` + `voteFrameRate`); `invalidate()` → window `needsFrame`; `renderRoot()`
re-records dirty layers then walks the tree. `GraphicsLayer.native` is a copy-edit of
`SkiaGraphicsLayer.skiko.kt` — a façade over `NativeRenderNode` (transforms trimmed of
outsets + `ChildLayerDependenciesTracker`). Drives entirely through the vendored
`NodeCoordinator`/snapshot observer — no changes there.

**SDL node evolution (all behind `CDN_LAYERCACHE`, geo now default):**
1. **`DeferredRenderNode`** (shared, `nativeMain`) — replay-the-block; the correct,
   un-cached baseline; still the fallback (`=off|defer|0`).
2. **`SdlRenderNode`** (texture, `=1|texture`, LEGACY) — records a leaf into an
   offscreen texture, replays as a blit. Fast for static leaves BUT **nondeterministic
   on complex screens** (offscreen-texture timing state; reused targets ghosted until
   an explicit clear was added, and even then swung 0↔17% run-to-run). Abandoned as
   default — the lesson that drove the geo node.
3. **`SdlDisplayListRenderNode`** (geo, **DEFAULT**) — a capture pass records a leaf's
   **layer-local tessellated geometry + text runs + icon glyphs BY PARAMS** (identity
   base CTM, no render target); `drawInto` re-emits through the layer transform. Crisp
   under any transform (geometry re-transformed, not resampled), bit-exact, **no
   render-target state → deterministic**. Text/icon capture is **eviction-safe**: it
   records run params (family/text/size/…), not texture pointers, and replay re-looks-up
   via the per-run LRU / FreeType glyph cache (re-rasterises if evicted). Ordered command
   stream (`GeometryBatch | TextRun | IconRun`) preserves z-order. **cache-leaves-defer-
   parents:** a child `drawInto` during a parent's record flags the parent to defer
   (draws nothing into the target-less capture pass) — nesting by defer, no by-value
   baking.

**Capture coverage:** geometry, plain text, spanned text (except `SpanStyle.background`),
icon-font glyphs — i.e. **every expensive (tessellate / shape / rasterise) op.**
Deferrals, all correct via block-replay: images (`drawImageRect`/`drawNativePainter`),
span backgrounds, `alpha<1`/blend/colorFilter/renderEffect layers, rounded/generic layer
clips, `saveLayer`, and parents with child layers.

**Perf (default now):** LazyColumn steady-state `draw` **3.55 → 1.52 ms (−57%)**, Tabs
−38%, NavRails −23%. **Never slower than Deferred** (a deferred leaf runs the identical
block-replay). Win ∝ how much plain geometry + plain text a screen has. Transform/alpha
animations are the best case — they change a node *property*, not content, so the display
list replays under the new transform without re-recording (same reason skiko replays a
RenderNode Picture under an updated matrix). **Granularity = the layer:** many-small-leaf
screens (LazyColumn, Tabs, list rows) cache big; monolithic single-layer screens re-record
wholesale — exactly upstream's RenderNode boundary.

## A.4 Hard-won learnings (durable — do not relearn)

- **Minimize-divergence is load-bearing.** A hand-written `notifyLayerIsDirty` that
  diverged from upstream `OwnedLayerManagerImpl` removed a layer from `dirtyLayers`
  mid-loop → `IndexOutOfBounds` crash on *any* navigation (screenshots missed it; the
  **probe** caught it). Fixed by matching upstream verbatim (clear-is-no-op while
  drawing; `clear()` after the loop). Don't hand-roll engine plumbing from a summary.
- **Offscreen-texture caching is timing-nondeterministic on complex screens** — the
  reason the geo (no-render-target) node is the robust path.
- **Rounded/path LAYER clips must be applied or deferred on the fast path.** The fast
  path submits raw geometry via `SDL_RenderGeometry`, which clips only to a RECT; the
  rounded clip is a lazy offscreen mask `replayBatch` bypasses. Symptom: the Carousel
  item's colour fill overflowed its morphing rounded mask (deterministic 4.4%). Fix: rect
  clip on the fast path (SDL clip rect honours it); rounded/generic → block-replay.
- **Pickers is NOT a render bug — it's screenshot timing.** A slow layout settle; geo's
  faster frames reach the fixed `--frames` capture point at a different settle phase.
  `geo-vs-geo` is 0.000% (deterministic) and phase-aligned `geo-vs-default` is 0.000%.
  Free-running screenshots on animated/settling screens give false signals → use a
  frame-locked/deterministic mode.
- **Rotated-edge AA fringe** is the one real static delta: geo rotates pre-tessellated
  local-space fringe vertices; block-replay tessellates in device space → <0.06%
  deterministic cosmetic diff on rotated shapes only (axis-aligned content is pixel-equal).
- **Verification layering:** `geo-vs-default` (same native stack) proves *self-
  consistency*, NOT upstream fidelity; only `parity.py` (native-vs-JVM) measures "match
  upstream." Screenshots miss crashes (use the probe) and settle-timing (use frame-lock).
- **Profiler caveat:** `present` is vsync-capped by the *display* refresh — profile on the
  target monitor before concluding a frame-rate gap (the demo-70/apidemo-144 report was
  two monitors, not a renderer bug).

## A.5 Carried-over future items (still valid)

- **Native-resource lifecycle** — wire `GraphicsLayer`/RenderNode + `SdlImageBitmap.
  close()` into cache eviction + the renderer `destroy()` chain; bounded `SkiaImageCache`
  (LRU + close-on-evict); demote the 10 s GC nudge once ownership covers it.
- **Glyph atlas** (→ Track B4 in the main plan) — SDL caches one texture per run-string;
  Skia uses a shared atlas (less memory, fewer binds, better batching). Less urgent now
  static text is recorded once and replayed.
- **SDL_GPU backend** (long-term) — real stencil clipping, pipelined batching, shader
  gradients. The `NativeRenderNode` seam makes a GPU node a clean drop-in later.
- **Feature-parity audit vs skiko** is the Phase-3 acceptance gate: per feature (save/
  saveLayer, transforms, clipRect/clipPath, draw primitives, Brush/gradients+TileMode,
  stroke/PathEffect, BlendMode, ColorFilter, RenderEffect, GraphicsLayer transforms,
  compositing/offscreen, layer alpha/clip/shadow) classify Matches / Gap / vendor-instead /
  out-of-scope (text shaping + GL/surface glue are out-of-scope by design). Skia-leg rows
  verify on mac/CI, SDL-leg rows on Windows.

## A.6 Explicit non-goals (recorded so they aren't re-attempted)

- **Dirty-region / partial-present** — upstream replays the whole scene; the win is
  not-re-recording, not redrawing-less.
- **A custom `cacheKey` API** — superseded by the real per-node display list.
- **Vendoring `RootNodeOwner` / the `ComposeScene` stack** — coupled to skiko's
  `SkiaLayer`/windowing; fights our `:window` SDL loop. We borrow only the *layer* engine.
