# Renderer refactor — adopt upstream's retained-layer engine

> **Preamble — where this lands.** In the end the **Skia renderer will be REALLY
> close to just a vendored copy of Compose's skiko target** — we run upstream's own
> drawing/layer code — *except* where it genuinely needs special SDL
> interoperability (windowing, surface/GL setup, event loop) or has to layer on top
> of the SDL renderer's plumbing. The **SDL renderer is where the real work is**:
> unlike the Skia leg it has no upstream to vendor, so every retained-layer piece
> (the `SdlRenderNode` display list, offscreen compositing, matrix/clip/shadow
> handling) is ours to build against the same contract. Read the two legs with that
> asymmetry in mind: Skia ≈ "un-refuse and vendor"; SDL ≈ "build a faithful twin."

**Thesis.** Our renderer is *immediate-mode*: every rendered frame re-walks the
whole `LayoutNode` tree and re-tessellates every shape and text run from
scratch. Upstream Compose Multiplatform (the skiko path — iOS/native and
JVM/desktop share it) is **not** immediate-mode: each layer records its drawing
**once** into a retained display list and **replays** it every frame, re-recording
only the layers that actually changed. That retained-layer engine — plus the
dirty-tracking that decides what to re-record — is where all the caching and
performance already lives upstream, and **most of it is already vendored into
`:ui` but deliberately switched off** in favour of hand-rolled immediate-mode
stand-ins.

The plan: **stop hand-rolling. Adopt upstream's `OwnedLayer` / `GraphicsLayer`
engine wholesale, put a thin per-renderer "RenderNode" under it, and delete our
immediate-mode stand-ins.** This buys us, in one move:

- **Skia fidelity → ~100%** (we run the same layer/record/replay code upstream runs).
- **SDL fidelity → close** (same engine; only the leaf rasterization differs).
- **Upstream performance & memory behaviour** (record-once/replay, dirty layers,
  compositing-strategy offscreens) instead of per-frame re-tessellation.
- **Far fewer decisions on our side** — the layer semantics, invalidation, matrix
  math, compositing rules become vendored upstream code, not ours to design.

**Scope — the whole per-renderer rendering stack, not just the retaining side.**
The retained-layer engine is the organizing *spine*, but the effort covers
**everything in the two renderer source sets** (`skikoRendererMain` +
`sdlRendererMain`) and the shared graphics actuals in `nativeMain`: `Canvas`,
`DrawScope`/paint-brush translation, `Path`/`PathEffect`/`PathMeasure`,
`Shader`/gradients/`TileMode`, `ColorFilter`, `RenderEffect`, images/`ImageBitmap`,
clip, shadow, offscreen, and the `GraphicsLayer`/RenderNode itself. Track V
([§6](#6-phased-implementation-plan)) and the feature audit
([§8](#8-feature-parity-audit-vs-skiko)) sweep all of it. **Held out of scope by
design** (the "special SDL interoperability" of the preamble): **text** shaping/
paint (`SkiaTextRenderer` / `SdlParagraph` — genuinely platform) and the **GL /
surface / windowing / event-loop glue** (`SkiaBridge`, `SkiaGLBridge`,
`SkiaSurfaceBridge`, `GpuReadback`, and the SDL backend's surface plumbing) — those
stay ours because there is no renderer-agnostic upstream to vendor for them.

**Guiding rule — minimize divergence from "normal" upstream.** Scope also reaches
the *top layer we treat as the renderer* — the owner's layer plumbing, the
`GraphicsLayer` actual, the `OwnedLayer` — wherever **our version was edited more
than SDL/windowing interop actually requires**. The divergence budget is exactly
"what the SDL loop / windowing / no-upstream-exists forces, and no more"; anything
past that gets re-aligned to upstream (re-vendor it, or trim the edit back). By
this rule:
- **Re-align (over-edited vs normal):** `ProjectOwnedLayer` + `ComposeOwner.createLayer`
  (hand-rolled stand-in for `GraphicsLayerOwnerLayer` + `OwnedLayerManagerImpl`);
  the lambda-replay body of `GraphicsLayer.native.kt` (vs `SkiaGraphicsLayer`); the
  dead `cacheKey` scaffolding. These are the "too much edited" files.
- **Diverged on purpose (within budget), keep:** `ComposeRootHost` / `ComposeOwner`'s
  no-op subsystems and the SDL main-loop wiring in `:window`; the GL/surface glue —
  the interop the preamble calls out.
- **Zero divergence already, leave alone:** the verbatim-vendored `NodeCoordinator` /
  `LayoutNode` / `MeasureAndLayoutDelegate` and the common draw DSL.

This document supersedes `ROADMAP.md` and `NEXT-SESSION.md` (see
[§12](#12-disposition-of-the-old-docs)).

---

## 1. How upstream skiko renders (the model we're copying)

Upstream skips work at **three** levels. We already have level 1 and level 2;
we are missing level 3, which is the expensive one.

### Level 1 — frame scheduling (we have an equivalent)
After each frame the scene checks `hasInvalidations()` (=`hasPendingMeasureOrLayout
|| hasPendingDraw`) and only schedules another frame if still dirty; otherwise the
host stops calling `render`. Invalidations arrive **targeted** from the snapshot
observer, not by polling.
*Our equivalent:* `ComposeWindow.shouldRender()` gates the whole `renderFrame()`
(`compose/sdl/window/src/nativeMain/.../ComposeWindow.kt:755`), and the loop
blocks on `SDL_WaitEventTimeout` when nothing is pending (`:238`). This part is
fine.

### Level 2 — measure/layout only dirty nodes (we already have this, verbatim)
`MeasureAndLayoutDelegate.relayoutNodes` is a depth-sorted set holding **only**
nodes that requested remeasure/relayout; `measureAndLayout` early-outs when it's
empty, and per-node `measurePending`/`layoutPending` flags gate the actual work.
*Our code already uses the vendored `MeasureAndLayoutDelegate`* through
`ComposeOwner.measureAndLayout()` (`compose/ui/ui/src/commonMain/.../node/impl/ComposeOwner.kt:139`).
Calling it every rendered frame is cheap because it self-skips when clean. **No
change needed here.**

### Level 3 — draw: record-once / replay (WE DON'T HAVE THIS — the whole point)
Each `LayoutNode` that needs isolation gets an `OwnedLayer`. On skiko that's
`GraphicsLayerOwnerLayer`, which owns a `GraphicsLayer` (upstream's "RenderNode
concept"). The mechanism:

```kotlin
// cmp-ref: compose/ui/ui/src/skikoMain/.../node/GraphicsLayerOwnerLayer.skiko.kt:246
override fun drawLayer(canvas: Canvas, parentLayer: GraphicsLayer?) {
    updateDisplayList()                 // re-record ONLY if dirty
    scope.drawContext.also { it.canvas = canvas; it.graphicsLayer = parentLayer }
    scope.drawLayer(graphicsLayer)      // REPLAY the cached display list
}
override fun updateDisplayList() {
    if (isDirty) {                      // <-- the caching gate
        graphicsLayer.record(density, layoutDirection, size, recordLambda)
        isDirty = false
    }
    // else: skip — the previously recorded display list is replayed as-is
}
override fun invalidate() { isDirty = true; layerManager.invalidate() }   // content changed
```

The critical property: **transform/alpha/clip changes do NOT set `isDirty`.**
`updateLayerProperties()` copies each changed field (guarded by `Fields`
bitflags) onto the layer and calls `triggerRepaint()` (schedule a frame) — but
**not** `invalidate()` (`GraphicsLayerOwnerLayer.skiko.kt:103-207`). So moving,
scaling, rotating, or fading a layer just **replays the cached content under a new
transform** — no re-tessellation. `move()` (scroll offset) likewise only sets
`topLeft` + repaints. Only a genuine content change (state read during the draw
block, or a `resize`) flips `isDirty` and re-records.

The per-frame draw loop (`OwnedLayerManagerImpl.draw`,
`RootNodeOwner.skiko.kt:967`) is: re-record the handful of layers in
`dirtyLayers`, clear it, then `root.draw(canvas)` walks the tree and every clean
layer **replays** (`NodeCoordinator.draw` → `layer.drawLayer`,
`NodeCoordinator.kt:483`). Note upstream **does not do dirty-region / partial
present** on skiko — it replays the whole scene each frame. The win is entirely
from **not re-recording clean layers**, not from redrawing less screen.

### What actually stores the display list — `RenderNode`
`GraphicsLayer` (skiko `actual`, `SkiaGraphicsLayer.skiko.kt`) is a **thin façade**
over `org.jetbrains.skiko.node.RenderNode` (from the **skiko** artifact, *not* from
compose-multiplatform-core):

```kotlin
// SkiaGraphicsLayer.skiko.kt:343  record  →  begin/endRecording brackets the block
val recordingCanvas = renderNode.beginRecording()
val composeCanvas = recordingCanvas.asComposeCanvas() as SkiaBackedCanvas
block(composeCanvas)                       // draws into the display list
renderNode.endRecording()
// :362  draw  →  replay the display list, transforms applied by the node
renderNode?.drawInto(canvas.skiaCanvas)
```

All transforms/effects are **RenderNode properties** applied at replay:
`translationX/Y`, `scaleX/Y`, `rotationX/Y/Z`, `pivot`, `alpha`, `bounds`,
`shadowElevation`, `ambient/spotShadowColor`, `clip` + `setClipRect/RRect/Path`,
and a `layerPaint` (a `saveLayer` paint carrying alpha/colorFilter/blendMode/
imageFilter). The **offscreen-buffer decision** is `requiresLayer()`
(`SkiaGraphicsLayer.skiko.kt:486`): allocate an offscreen iff `alpha<1 && strategy
!= ModulateAlpha`, or any of `colorFilter`/non-`SrcOver` `blendMode`/`renderEffect`
is set, or `strategy == Offscreen`.

**The self-contained blueprint:** `LegacyRenderNodeLayer.skiko.kt` implements the
identical `OwnedLayer` contract **without** skiko's `RenderNode`, using raw
`org.jetbrains.skia.PictureRecorder`/`Picture` directly (record into a `Picture`,
replay with `canvas.drawPicture`, apply the matrix at replay, bake clip/shadow/
alpha into the recording, dirty-flag ≡ `picture == null`). It's the reference for
"how to do record/replay in plain Skia" and the template for our SDL node.

### Key upstream files (all under `C:\Sources\Perso\cmp-ref`)
| File | Role |
|---|---|
| `.../node/GraphicsLayerOwnerLayer.skiko.kt` | the `OwnedLayer`: dirty-gated record + replay + property diff (**vendor this**) |
| `.../node/OwnedLayerManager.skiko.kt` (impl in `RootNodeOwner.skiko.kt:897`) | `dirtyLayers` list, `notifyLayerIsDirty`, `invalidate`, `recycle` |
| `.../node/LegacyRenderNodeLayer.skiko.kt` | pure-Skia `Picture` record/replay reference (blueprint for SDL) |
| `ui-graphics/.../graphics/layer/SkiaGraphicsLayer.skiko.kt` | `GraphicsLayer` actual = façade over skiko `RenderNode` |
| `ui-graphics/.../graphics/SkiaGraphicsContext.skiko.kt` | `GraphicsContext` actual (creates/releases layers) |
| `ui-graphics/.../graphics/layer/CompositingStrategy.kt`, `ChildLayerDependenciesTracker.kt` | common support (already vendored) |
| `ui-graphics/.../graphics/SkiaBackedCanvas.skiko.kt` | upstream's `Canvas` impl (we hand-rolled our own instead) |

---

## 2. What our port does today (the gap)

We satisfy levels 1 and 2. **Level 3 is entirely faked with immediate-mode
stand-ins**, and they no-op exactly the methods that make caching work.

### Stand-in A — `ProjectOwnedLayer` (every layout-driven layer)
`ComposeOwner.createLayer()` returns a `ProjectOwnedLayer` for **every** node with
a layer modifier (`ComposeOwner.kt:175`). It stores transform/clip/alpha in plain
fields and, on `drawLayer`, **re-runs the node's `drawBlock` every frame** wrapped
in `canvas.save()/translate/clip/restore` (`ComposeOwner.kt:434-523`). Fatally:

```kotlin
// ComposeOwner.kt:525
override fun updateDisplayList() {}   // no display list
override fun invalidate() {}          // dirtiness ignored
override fun destroy() {}
```

So there is **no cache to invalidate** and **no display list to replay** — every
frame re-executes the whole subtree draw.

### Stand-in B — `GraphicsLayer.native.kt` (explicit `GraphicsContext` layers)
The `androidx.compose.ui.graphics.layer.GraphicsLayer` actual (used by
`rememberGraphicsLayer` / `SharedTransitionLayout`) is a **lambda-replay**: `record`
stores the block, `draw` re-invokes it under transforms
(`GraphicsLayer.native.kt:94-160`). Its own comment says it: *"This renderer is
immediate-mode, so record() stores the draw lambda and draw() REPLAYS it."*
`toImageBitmap()` throws.

### The consequence (matches the profiling)
Every rendered frame: re-walk the vendored coordinator/modifier tree from
`rootNode.draw`, and re-tessellate every primitive — shapes → triangles via
`Sdl3DrawScope`'s `*Core` helpers, text re-wrapped and re-blit line by line (only
glyph *textures* are cached, not the layout). `DrawStats`
(`compose/ui/ui/src/nativeMain/.../graphics/DrawStats.kt`) exists to measure this
per-frame cost. The always-present sidebar (30+ text rows + icons) is
re-tessellated on **every** invalidation, anywhere in the window, because there is
no retained layer to replay.

### The good news: the driving machinery is already wired
The whole apparatus that *drives* a real `OwnedLayer` is already present in our
vendored tree — we just plugged a dumb layer into it:

```
compose/ui/ui/src/vendor/common/.../node/NodeCoordinator.kt
  :483   layer.drawLayer(canvas, graphicsLayer)
  :637   layer.updateLayerProperties(graphicsLayerScope)
  :1424  layer?.invalidate()
  :1633  onCommitAffectingLayer = { coordinator.layer?.invalidate() }   // snapshot-driven
```

Swapping `ProjectOwnedLayer` for the real `GraphicsLayerOwnerLayer` **lights all of
this up** with no changes to the coordinator, the snapshot observer, or the
modifier chain. The vendored `OwnedLayer` and `OwnedLayerManager` interfaces
(`src/vendor/common/.../node/OwnedLayer.kt`, `src/vendor/native/.../node/OwnedLayerManager.skiko.kt`)
are the exact upstream contracts `GraphicsLayerOwnerLayer` implements.

**And it was already vendored once:** `:ui`'s `compose-fork.txt` *lists*
`GraphicsLayerOwnerLayer.skiko.kt`, `RootNodeOwner.skiko.kt`,
`LegacyRenderNodeLayer.skiko.kt`, `SkiaGraphicsLayer.skiko.kt`,
`SkiaGraphicsContext.skiko.kt`, `SkiaBackedCanvas.skiko.kt` — then **refuses each
one** with a `!` line (compose-fork.txt:368-370, 518-523). Undoing those refusals
(with small edits) is a large part of this work.

---

## 3. The seam is already in place

Agent research confirmed the abstraction boundary is clean and **already
satisfied** by our code:

- **`Canvas` is a common interface** (not an `expect class`). Both our
  `Sdl3Canvas` and `SkiaCanvas` already implement it. Therefore **`CanvasDrawScope`,
  `DrawScope`, the whole drawing/transform DSL, and `MultiParagraph`'s
  save/translate/paint loop are common code we already reuse** — nothing to do
  there.
- A renderer only needs to supply, per platform: the **`Canvas` impl**, a
  **`GraphicsLayer`/RenderNode**, a **`GraphicsContext`**, the graphics **primitive
  actuals** (`Paint`/`Path`/`ImageBitmap`/`Matrix`/`Shader`/`ColorFilter`), and its
  own **`Paragraph`** (text bypasses the `Canvas` interface). We already have all
  of these **except** the retained `GraphicsLayer`/RenderNode.
- **Text is out of scope.** `SkiaParagraph` paints straight to the native
  `SkCanvas`; our `SdlParagraph` already owns its own layout+paint. The refactor
  doesn't touch text shaping — text simply gets recorded into a layer's display
  list like any other draw op, and stops being re-shaped every frame as a
  side-effect of caching.

---

## 4. Strategy — one common layer engine, a thin RenderNode per renderer

Make the layer engine **common** and push the only genuinely renderer-specific
piece — the display list — behind a small `NativeRenderNode` abstraction. This is
the "Skia-like layer on top, even more common" the refactor is aiming for.

```
                 (vendored common, shared by BOTH renderers — nativeMain)
   GraphicsLayerOwnerLayer  ──owns──▶  GraphicsLayer (façade)  ──delegates──▶  NativeRenderNode
   OwnedLayerManager (impl)                                                          │  expect/actual
                                                                    ┌────────────────┴─────────────────┐
                                                          skikoRendererMain                   sdlRendererMain
                                                     RenderNode-backed (skiko)          SdlRenderNode (display list
                                                     — or Picture-backed fallback        + offscreen for effects)
```

`NativeRenderNode` is our common interface mirroring the surface
`SkiaGraphicsLayer` needs from skiko's RenderNode:

```kotlin
internal interface NativeRenderNode {
    var bounds: IntRect; var pivot: Offset
    var alpha: Float; var scaleX: Float; var scaleY: Float
    var translationX: Float; var translationY: Float
    var rotationX: Float; var rotationY: Float; var rotationZ: Float; var cameraDistance: Float
    var shadowElevation: Float; var ambientShadowColor: Int; var spotShadowColor: Int
    var clip: Boolean
    fun setClipRect(...); fun setClipRRect(...); fun setClipPath(path)
    var layerPaint: NativePaint?           // saveLayer paint: alpha/colorFilter/blend/imageFilter
    fun beginRecording(): Canvas           // returns a recording Canvas
    fun endRecording()
    fun drawInto(canvas: Canvas)           // replay
    fun close()
}
```

Created behind the project's existing per-renderer `expect`/`actual` pattern
(the same one `createRenderBackend` / `rendererPreferredGpuMode` already use — one
actual in `skikoRendererMain`, one in `sdlRendererMain`, unambiguous because only
one renderer source set is attached per target):

```kotlin
internal expect fun createNativeRenderNode(context: NativeRenderNodeContext): NativeRenderNode
```

### 4a. Skia path → ~100% fidelity, upstream caching for free
Two sub-options; pick during Phase 1 based on a fast probe:

- **S1 (preferred): back `NativeRenderNode` with `org.jetbrains.skiko.node.RenderNode`.**
  This is literally what upstream does. Vendor `SkiaGraphicsLayer.skiko.kt` /
  `SkiaGraphicsContext.skiko.kt` nearly verbatim; our `NativeRenderNode` skiko
  actual wraps `RenderNode`/`RenderNodeContext`. Gives elevation shadows, clip,
  offscreen compositing, and crisp re-rasterization at replay transform — all from
  skiko.
  *Blocker to verify:* `RenderNode` must be present in **skiko 0.150.1** for the
  native `macosArm64`/`linuxX64` targets. It's part of skiko's public API and used
  by upstream at a comparable version, so this is expected to hold — but it
  **cannot be verified from this Windows box** (the native skiko klibs aren't
  downloaded here; the Skia path only builds on macOS/Linux). Confirm on the first
  mac/CI leg (see [§11](#11-verification--risks)).

- **S2 (fallback, zero external assumptions): back it with `org.jetbrains.skia.Picture`.**
  Copy the record/replay from `LegacyRenderNodeLayer.skiko.kt` (raw
  `PictureRecorder`/`Picture`, matrix at replay, `ShadowUtils.drawShadow` for
  elevation, the `±2³⁰` recording-bounds trick). No skiko-RenderNode dependency.
  Slightly lower fidelity on shadows/effects than S1 but fully self-contained.

**Also strongly recommended (fidelity multiplier): replace the hand-rolled
`SkiaCanvas` with upstream's vendored `SkiaBackedCanvas`.** Our `SkiaCanvas` is
exactly the kind of "too much work on our side" this refactor targets — every
gradient/blend/path-effect nuance we re-implemented is a fidelity risk. Vendoring
`SkiaBackedCanvas` (+ the `SkiaBackedPaint`/`Path`/`Shader`/`ColorFilter`/
`RenderEffect` primitive actuals it needs, most already partly present) makes the
Skia leg *upstream's own drawing code*, which is the surest route to 100%. This is
a **separate track (S-Canvas)** — valuable but larger; can land after the layer
engine.

### 4b. SDL path → close fidelity, retained performance
No `Picture` and no `RenderNode` exist, so we build `SdlRenderNode`. Mirror
`SkiaGraphicsLayer`'s structure exactly (same property setters, same
`requiresLayer()` offscreen rule) so behaviour matches; only the display list
differs. Two mechanisms, combined per upstream's own compositing rule:

- **Common case (`requiresLayer()` == false): display list of cached geometry.**
  `beginRecording()` returns a *recording* `Sdl3Canvas` that, instead of submitting
  to the GPU, **appends already-tessellated ops** to a command list — the
  `SDL_RenderGeometry` vertex batches, glyph-quad draws, and image blits computed
  once at record time. `drawInto()` re-submits those cached vertex buffers with the
  layer transform folded in (SDL render transform or a cheap vertex re-map). This
  matches Skia's `Picture` semantics: **resolution-independent, crisp under
  transform, low memory, cheap replay, no re-tessellation.** This is the primary
  performance win.

- **Effect case (`requiresLayer()` == true): offscreen texture.** When the layer
  needs a real offscreen (alpha<1 under `Auto`, `colorFilter`, non-`SrcOver`
  `blendMode`, `renderEffect`, or `CompositingStrategy.Offscreen`), record into an
  SDL render-target texture and replay by blitting it with the layer paint applied
  — exactly the case upstream allocates a `saveLayer` for. **We already have all of
  this infrastructure**: `Sdl3Offscreen`/`SdlImageBitmap` render targets,
  `Sdl3ClipTargets`' resize-aware target pool, and the `beginOffscreen`/
  `endOffscreen` + `blitRegion` blit-back path in `Sdl3Canvas`
  (`Sdl3Canvas.kt:339-576`). The `NativeReleaseQueue` already frees these textures.

Start with the **offscreen-texture** mechanism for *all* layers in Phase 2 (it's
the smallest step and reuses existing infra, and it already kills the sidebar
re-tessellation) — then upgrade `Auto`/no-effect layers to the **cached-geometry
display list** in Phase 4 for crispness under scale and lower memory.

### 4c. What to vendor vs build — summary
| Piece | Action | Where |
|---|---|---|
| `GraphicsLayerOwnerLayer` | **Vendor** (drop the `SkiaGraphicsContext.setLightingInfo` tail → move to skiko set; strip its skiko import) | `nativeMain` (shared) |
| `OwnedLayerManager` interface | already vendored | `vendor/native` |
| `OwnedLayerManager` impl (`dirtyLayers`) | **Build** small (or lift from `RootNodeOwner.skiko.kt:897`) onto/beside `ComposeOwner` | project |
| `GraphicsLayer` actual (façade) | **Vendor/adapt** `SkiaGraphicsLayer` → delegate to `NativeRenderNode` | `nativeMain` |
| `NativeRenderNode` interface | **Build** (surface above) | `nativeMain` |
| `NativeRenderNode` skiko actual | **Vendor-thin** over skiko `RenderNode` (S1) or `Picture` (S2) | `skikoRendererMain` |
| `NativeRenderNode` sdl actual (`SdlRenderNode`) | **Build** (display list + offscreen) | `sdlRendererMain` |
| `CompositingStrategy`, `ChildLayerDependenciesTracker`, `Fields`, `ReusableGraphicsLayerScope`, `isInOutline` | already vendored common | — |
| `prepareTransformationMatrix`, `invertTo` (Matrix helpers) | **Ensure present in BOTH renderer sets** — `prepareTransformationMatrix` is currently `skikoRenderer`-only (`vendor/skikoRenderer/.../Matrices.skiko.kt`); it's pure math, vendor/copy an SDL variant | `sdlRendererMain` |
| `SkiaBackedCanvas` (+ primitive actuals) | **Vendor** (track S-Canvas, optional but recommended) | `skikoRendererMain` |
| `RootNodeOwner`, `ComposeScene*` stack | **Do NOT vendor** — coupled to skiko `SkiaLayer`/`ComposeScene`; conflicts with our `:window` SDL loop. Keep our `ComposeOwner` + loop. | — |

---

## 5. Wiring into our thin owner + SDL loop

We keep `ComposeOwner` and the `ComposeWindow` main loop; we only change how
layers are created, tracked, and drawn.

1. **`ComposeOwner` implements `OwnedLayerManager`** (it already implements
   `Owner`). Add a `dirtyLayers` list + `postponedDirtyLayers` guard (lift from
   `OwnedLayerManagerImpl`, `RootNodeOwner.skiko.kt:897-1001`).
   - `createLayer(...)` → `GraphicsLayerOwnerLayer(graphicsLayer = graphicsContext.createGraphicsLayer(), context = graphicsContext, layerManager = this, drawBlock, invalidateParentLayer)`.
   - `notifyLayerIsDirty(layer, dirty)` → add/remove in `dirtyLayers`.
   - `invalidate()` → **set the window's `needsFrame = true`** (this is the seam
     to our loop; today it's the coarse per-window flag, which is exactly right).
   - `recycle(layer)` → return `false` (discard, like upstream) initially; pool later if measured worthwhile.
   - `voteFrameRate(...)` → no-op (we don't do frame-rate voting).
2. **`GraphicsContext` returns real layers.** `ComposeOwner.graphicsContext`
   already creates our `GraphicsLayer` actual (`ComposeOwner.kt:224`); once that
   actual is RenderNode-backed, `rememberGraphicsLayer`/`SharedTransitionLayout`
   get real retained layers and `toImageBitmap()` starts working.
3. **Draw goes through the manager.** Add `ComposeRootHost.drawRoot(canvas)` (or
   have the backend call it) that does what `OwnedLayerManagerImpl.draw` does:
   re-record `dirtyLayers`, clear, then `rootNode.draw(canvas, null)`. The backends'
   `drawRoot` change from `inHost.rootNode.draw(vCanvas, null)`
   (`Sdl3RenderBackend.kt:111`, `SkiaRenderBackend.kt`) to `inHost.drawRoot(vCanvas)`.
4. **Delete the stand-ins:** `ProjectOwnedLayer` (`ComposeOwner.kt:363-568`) and
   the lambda-replay body of `GraphicsLayer.native.kt`. Also delete the dead
   `cacheKey` scaffolding flagged in `NEXT-SESSION.md` (the `GraphicsLayerModifier`/
   `GraphicsLayerNode` that store `cacheKey` and never read it) — it's obviated by
   the real engine; fix the demo's misleading "(cached)" label.

Nothing in the snapshot observer, `NodeCoordinator`, or modifier chain changes —
they already call `updateLayerProperties`/`drawLayer`/`invalidate` on whatever
`createLayer` returns.

---

## 6. Phased implementation plan

Each phase is independently shippable and independently verifiable (parity +
probe + profiler). Windows builds/tests the SDL leg; the Skia leg needs a mac/CI
leg (noted per phase).

### Phase 0 — probe & scaffold (no behaviour change)
- Confirm `org.jetbrains.skiko.node.RenderNode` compiles on `macosArm64`/`linuxX64`
  with skiko 0.150.1 (tiny throwaway reference in `skikoRendererMain`). Decide S1
  vs S2. *(mac/CI)*
- Define `NativeRenderNode` + `NativeRenderNodeContext` + `createNativeRenderNode`
  expect in `nativeMain`; stub actuals in both renderer sets that throw. Build both
  legs green. *(SDL leg on Windows; Skia on CI)*

### Phase 1 — Skia leg: real retained layers
- Vendor `GraphicsLayerOwnerLayer` into `nativeMain` (edit: strip the skiko
  `setLightingInfo` tail into `skikoRendererMain`). Vendor/adapt `SkiaGraphicsLayer`
  → `GraphicsLayer` façade over `NativeRenderNode`. Implement the skiko
  `NativeRenderNode` actual (S1 RenderNode, or S2 Picture).
- `ComposeOwner` becomes the `OwnedLayerManager`; `createLayer` returns
  `GraphicsLayerOwnerLayer`; `drawRoot` re-records dirty layers first.
- Ensure `prepareTransformationMatrix`/`invertTo` resolve on the Skia leg.
- **Verify (mac/CI):** parity sweep — expect text-heavy screens to *improve* (no
  re-shape drift), no regressions; `--nav3test`, transforms/scroll/clip screens
  render identically; `rememberGraphicsLayer`/SharedTransition overlays correct;
  `toImageBitmap` works.

### Phase 2 — SDL leg: offscreen-texture RenderNode (the big perf win)
- Implement `SdlRenderNode` with the **offscreen-texture** mechanism for all
  layers, reusing `Sdl3Offscreen`/`Sdl3ClipTargets`/`beginOffscreen`+`blitRegion`.
  `record` → render block into a target texture sized to `bounds`; re-record on
  `isDirty`/resize; `drawInto` → blit with transform + `layerPaint`.
- **Verify (Windows):** `CDN_FORCERENDER=1 CDN_PROFILE=1` — `draw` per frame drops
  sharply on the sidebar demo (sidebar records once, replays as one blit; only the
  hovered/changed row re-records); `DrawStats.geo`/`verts` per frame fall by the
  static-content fraction. Parity sweep unchanged or better. Probe: click/hover/
  scroll behave identically (hit-testing goes through `mapOffset`/`isInLayer`,
  which now use the layer matrix).

### Phase 3 — fidelity parity pass (both legs)
- **Run the full feature audit ([§8](#8-feature-parity-audit-vs-skiko))** — walk
  every row of the matrix, native-vs-skiko, and resolve each to Matches / Gap /
  vendor-instead. This is where "verify each feature against upstream" happens.
- Match `requiresLayer()` exactly (alpha/colorFilter/blendMode/renderEffect/
  Offscreen/ModulateAlpha) so offscreen allocation and alpha modulation agree with
  upstream. Wire real `saveLayer` alpha on SDL through the offscreen node (kills
  ROADMAP §4 "Real saveLayer alpha on SDL"). Clip via the node's outline
  (rect/rrect/path) so `Modifier.clip(RoundedCornerShape)` and generic `clipPath`
  are exact (kills ROADMAP §4 "clipPath generic shapes"). Elevation shadows via the
  node (skiko `RenderNode` shadow on Skia; our `NativeShadowCanvas` fed by the node
  on SDL).
- **Verify:** parity ranking on Brushes/Shadows/GraphicsLayer/Canvas screens
  improves toward the font-drift baseline; targeted probes on rounded-clip hover
  fills, alpha overlaps, drop shadows.

### Phase 4 — SDL cached-geometry display list (crispness + memory)
- For `Auto`/no-effect layers, swap the offscreen texture for the **cached
  tessellated-geometry** display list (crisp under scale, resolution-independent,
  less VRAM). Keep offscreen only for `requiresLayer()` layers.
- **Verify:** scaled/rotated layers stay crisp (no bilinear softening vs Phase 2);
  RSS lower on scroll-heavy screens; profiler `draw` still flat.

### Phase 5 — cleanup & lifecycle
- Delete `ProjectOwnedLayer` + lambda-replay `GraphicsLayer` body + dead `cacheKey`
  scaffolding; fix demo label. Wire `GraphicsLayer`/RenderNode `close()` into
  cache eviction + the renderer `destroy()` chain (folds ROADMAP §1 remaining
  boxes). Consider a small layer pool if `recycle` churn shows up in the profiler.
- **Sign-off gate:** the [§8](#8-feature-parity-audit-vs-skiko) matrix is all-green
  (every feature Matches upstream or is an explicitly-scoped exception) and Track V's
  deletion list is emptied — no hand-rolled renderer code remains that a vendored
  upstream file could replace.

### Track V (parallel) — vendor consolidation & dead-code removal
"Do less work on our side" made literal: **inventory every hand-rolled renderer
file, and for each one that duplicates something upstream ships, delete ours and
vendor theirs.** This runs alongside the layer phases and is driven by the
feature audit in [§8](#8-feature-parity-audit-vs-skiko) — a feature marked
"vendor-instead" there becomes a deletion here.

**The enabling move: relocate the graphics `actual`s per-renderer.** Today the
`Canvas`/`Paint`/`Path`/`ImageBitmap`/`PathIterator`/… actuals are the *shared*,
renderer-agnostic `Project*` classes in `nativeMain`
(`compose/ui/ui/src/nativeMain/.../graphics/ProjectCanvas.kt`, `ProjectPaint.kt`,
`ProjectPath.kt`, `ProjectPathIterator.kt`, `ProjectImageBitmap.kt`) plus the SDL
actuals in `sdlRendererMain/.../graphics/*.sdl.kt`. Because the `actual` lives in
shared `nativeMain`, it **cannot** be Skia-specific — which is *why* the Skia leg
re-implements everything (`SkiaCanvas.kt`, `SkiaDrawScope.kt`, …) instead of using
upstream's. Move each `expect class`'s `actual` down into the two renderer source
sets (the proven `createRenderBackend` expect/actual-in-leaf pattern — legal
because only one renderer set attaches per target). Then:

- **Skia leg → vendor the upstream skiko actual set verbatim** into
  `skikoRendererMain` (all under `cmp-ref/.../ui-graphics/src/skikoMain/.../graphics/`):
  `SkiaBackedCanvas`, `SkiaBackedPaint`, `SkiaBackedPath`, `SkiaPathIterator`,
  `SkiaColorFilter`, `SkiaShader` (Brush→shader), `SkiaBackedPathEffect`,
  `SkiaBackedPathMeasure`, `SkiaBackedRenderEffect`, `SkiaTileMode`,
  `SkiaVertexMode`, `SkiaImageAsset`, `BlendMode.skiko`, `Matrices.skiko`,
  `Rects.skiko`, plus `graphics/layer/` (the `SkiaGraphicsLayer` from Phase 1) and
  `graphics/shadow/`.
- **Then delete the hand-rolled Skia duplicates:** `SkiaCanvas.kt` (→
  `SkiaBackedCanvas`), `SkiaDrawScope.kt` (brush/gradient/paint translation →
  `SkiaShader` + `SkiaBackedPaint`), `SkiaImageCache.kt` (→ `SkiaImageAsset` behind
  a small bounded LRU), and the Skia leg's use of the `Project*` abstractions.
  **Keep** the genuinely-ours surface/GL glue (`SkiaRenderBackend`, `SkiaBridge`,
  `SkiaGLBridge`, `SkiaSurfaceBridge`, `GpuReadback`, `SkiaOffscreen`) and the text
  renderer (text is out of scope — [§3](#3-the-seam-is-already-in-place)).

Net: the Skia leg becomes **upstream's own drawing code** — the single biggest
fidelity step, at negative maintenance cost. *(mac/CI verification.)*

**SDL leg — dedup against common, don't reimplement it.** The SDL primitives
(`Project*` / `*.sdl.kt`) must stay (no Skia there), but each should be a *minimal*
actual over the vendored common `expect`, not a re-derivation of common logic.
Audit them to: (a) push any renderer-agnostic math into vendored common rather
than a project copy (e.g. `prepareTransformationMatrix` is `skikoRenderer`-only
today — vendor it, don't hand-write an SDL twin; reuse `isInOutline`,
`ChildLayerDependenciesTracker`, `CompositingStrategy`); (b) delete the immediate-
mode stand-ins the layer engine obsoletes — `ProjectOwnedLayer`, the lambda-replay
body of `GraphicsLayer.native.kt`, and the dead `cacheKey` scaffolding
(`GraphicsLayerModifier`/`GraphicsLayerNode`) — this overlaps Phase 5. Windows is
the SDL-leg verifier.

**Publish/metadata guard:** relocating actuals out of `nativeMain` must keep the
Windows common-metadata compile green (only that job compiles it). Mirror the
existing `createRenderBackend` split and test with
`gradlew :ui:compileCommonMainKotlinMetadata` before tagging (CLAUDE.md pitfall).

---

## 7. Fidelity: the compositing-strategy contract to match

Implement `requiresLayer()` **identically** on both renderers (from
`SkiaGraphicsLayer.skiko.kt:486`) so offscreen allocation and alpha handling match
upstream pixel-for-pixel in intent:

| Condition | `Auto` | `Offscreen` | `ModulateAlpha` |
|---|---|---|---|
| `alpha < 1` | offscreen | offscreen | **per-op alpha multiply** (no offscreen) |
| `colorFilter != null` | offscreen | offscreen | offscreen |
| `blendMode != SrcOver` | offscreen | offscreen | offscreen |
| `renderEffect != null` | offscreen | offscreen | offscreen |
| none of the above | replay in place | **always offscreen** | replay in place |

`ModulateAlpha` bakes alpha into recorded ops via `canvas.alphaMultiplier` at
record time (so alpha changes re-record) — matches `SkiaGraphicsLayer.record`
(`:326`). Getting this table right is what makes overlapping-content alpha, tinted
layers, and blend modes look correct without over-allocating offscreens.

---

## 8. Feature-parity audit vs skiko — the acceptance gate

A systematic, capability-by-capability comparison of our renderer against upstream
skiko. Two goals, both requested for this refactor: **(1) verify every feature
behaves as upstream does** (semantics *and* pixels), and **(2) flag every feature
whose behaviour we hand-rolled** so it can be *replaced by vendored code* (→
[Track V](#track-v-parallel--vendor-consolidation--dead-code-removal)) rather than
patched. This audit is executed during Phase 3 and gates Phase 5 sign-off.

**Process (per feature):**
1. Name the upstream skiko reference file and our implementation file(s).
2. Compare semantics against upstream; render the relevant `:demo` screen
   native-vs-JVM (`scripts/parity/`) and run a targeted `scripts/probe/` where
   interaction matters.
3. Classify:
   - **Matches** — behaviour + pixels agree (modulo the known font-drift baseline).
   - **Gap** — diverges from upstream → fix in Phase 3.
   - **Vendor-instead** — ours is a hand-rolled duplicate of a vendorable upstream
     file → delete it in Track V (Skia leg), or thin it to a minimal actual over
     the common `expect` (SDL leg).
   - **Out-of-scope** — text shaping, GL/surface glue (kept ours by design).
4. Record the verdict in the matrix; Gaps feed Phase 3, Vendor-instead feed Track V.
   Re-run after every change. Skia-leg rows verify on mac/CI; SDL-leg rows on Windows.

**Feature matrix** (fill the two status columns during the audit; seeded with what
research already established). Upstream refs are under
`cmp-ref/.../ui-graphics/src/skikoMain/.../graphics/` unless noted.

| Feature | Upstream skiko ref | Skia leg (ours) | SDL leg (ours) |
|---|---|---|---|
| save/restore/**saveLayer** | `SkiaBackedCanvas.skiko.kt` | vendor-instead (`SkiaCanvas`) | verify; real `saveLayer` alpha was a gap → Phase 3 offscreen node |
| translate/scale/rotate/skew/**concat** | `SkiaBackedCanvas`, `Matrices.skiko.kt` | vendor-instead | verify; `prepareTransformationMatrix` must exist SDL-side |
| **clipRect** + `ClipOp` | `SkiaBackedCanvas` | vendor-instead | verify |
| **clipPath** rect / rrect / **generic** | `SkiaBackedCanvas`, `SkiaBackedPath` | vendor-instead | rrect via offscreen mask OK; **generic = bbox fallback (Gap)** → node outline in Phase 3 |
| drawRect / RoundRect / Oval / Circle / **Arc** | `SkiaBackedCanvas` | vendor-instead | verify tessellation vs skiko (AA fringe) |
| drawLine / **drawPoints** / drawRawPoints + `PointMode` | `SkiaBackedCanvas` (stepping is in the actual) | vendor-instead | verify — port must own point/line iteration |
| **drawPath** | `SkiaBackedCanvas`, `SkiaBackedPath`, `SkiaPathIterator` | vendor-instead (`ProjectPath`→`SkiaBackedPath`) | keep `ProjectPath`; verify linearise cache vs skiko |
| drawImage / **drawImageRect** + `FilterQuality` | `SkiaBackedCanvas`, `SkiaImageAsset.skiko.kt` | vendor-instead (`SkiaImageCache`→`SkiaImageAsset`) | verify blit + tint |
| **drawVertices** + `VertexMode` | `SkiaBackedCanvas`, `SkiaVertexMode.skiko.kt` | vendor-instead | verify |
| **Brush**: Solid / Linear / Radial / Sweep + `TileMode` | `SkiaShader.skiko.kt`, `SkiaTileMode.skiko.kt` | vendor-instead (`SkiaDrawScope` gradient code) | gradients grid-meshed (fixed); **verify TileMode** parity |
| Stroke: cap / join / width / **PathEffect** | `SkiaBackedPaint.skiko.kt`, `SkiaBackedPathEffect.skiko.kt` | vendor-instead | `PathEffect.sdl.kt` — verify dashing etc. |
| **BlendMode** | `BlendMode.skiko.kt` | vendor-instead | verify each mode; non-SrcOver forces offscreen |
| **ColorFilter** | `SkiaColorFilter.skiko.kt` | vendor-instead | `ColorFilter.sdl.kt` — verify tint/matrix |
| **RenderEffect** (blur) | `SkiaBackedRenderEffect.skiko.kt` | vendor-instead | `RenderEffect.sdl.kt` — verify or scope |
| **GraphicsLayer** transforms (T/S/R/pivot/camera) | `SkiaGraphicsLayer.skiko.kt` (RenderNode props) | vendor (Phase 1) | `SdlRenderNode` (Phase 2/4) |
| **Compositing strategy** / offscreen (`requiresLayer`) | `SkiaGraphicsLayer.skiko.kt:486` | vendor (Phase 1) | match table in [§7](#7-fidelity-the-compositing-strategy-contract-to-match) |
| Layer **alpha** / colorFilter / blend on layer | `SkiaGraphicsLayer` `layerPaint` | vendor | offscreen node (Phase 3) |
| Layer **clip outline** | `SkiaGraphicsLayer.configureOutlineAndClip` | vendor | node outline (Phase 3) |
| **Elevation shadow** | skiko `RenderNode` shadow / `ShadowUtils` | vendor | `NativeShadowCanvas` fed by node (Phase 3) |
| **MultiParagraph / text draw** | `SkiaMultiParagraphDraw`, `SkiaParagraph` | **out-of-scope** (`SkiaTextRenderer`) | out-of-scope (`SdlParagraph`) |
| GL / surface / readback | — (skiko `SkiaLayer`) | **out-of-scope** (ours: `SkiaBridge`, `GpuReadback`, …) | out-of-scope (`Sdl3RenderBackend`) |

The audit *is* the fidelity acceptance test: **Skia leg green means ~100%
(we run upstream's code); SDL leg green means "close"** (same engine, same
compositing rules, only leaf rasterization differs).

## 9. What we explicitly do NOT need

- **Dirty-region / partial-present rendering** (old ROADMAP §2). Upstream skiko
  **replays the whole scene every frame** — it does not scissor to damage. The perf
  comes entirely from *not re-recording clean layers*. Once retained layers land,
  whole-window replay is cheap (blits / cached-geometry re-submits). Chasing
  dirty-regions would be extra complexity for a win upstream itself doesn't take.
- **A custom `cacheKey` API** (old NEXT-SESSION). Superseded by the real
  per-node display list. Delete the dead scaffolding.
- **Vendoring `RootNodeOwner` / the `ComposeScene` stack.** Coupled to skiko's
  `SkiaLayer`/windowing; would fight our `:window` SDL loop. Our `ComposeOwner` +
  loop already cover levels 1 & 2; we only borrow the *layer* engine from upstream.

---

## 10. Carried over from ROADMAP (still valid, now adjacent)

Kept because they remain real; several are folded into the phases above.

**Native-resource lifecycle** (ROADMAP §1 — mostly done). Remaining: wire
`GraphicsLayer`/RenderNode + `SdlImageBitmap.close()` into cache eviction and the
renderer `destroy()` chain (Phase 5); `SkiaImageBitmap.close()` + close-on-eviction
in the Skia caches *(mac/CI)*; demote the 10 s GC nudge once ownership covers it.
Verification: a `--leaktest`-style RSS-plateau probe.

**Skia renderer hygiene** (ROADMAP §3). Bounded `SkiaImageCache` (LRU + eviction
close; unbounded `HashMap` today); `saveLayer` huge-bounds clamp (GPU offscreen
spikes — see CLAUDE.md pitfall). Track V subsumes much of this if adopted.

**Correctness/parity gaps** (ROADMAP §4). "Real `saveLayer` alpha on SDL" and
"`clipPath` generic shapes" are **resolved by Phases 2–3** (offscreen node + node
outline clip). Gradient under-sampling and layer rotation already fixed. Re-verify
against parity heatmaps before touching.

**Glyph atlas** (ROADMAP §2). Text draws break geometry batches (z-order flush per
run). Independent of the layer refactor, but far less urgent once static text is
recorded once and replayed — revisit only if the profiler shows text batching as a
hot path on an animated, text-heavy screen.

**SDL_GPU backend** (ROADMAP §6, long-term). Real stencil clipping, pipelined
batching, shader gradients. The `NativeRenderNode` seam makes this cleaner later:
a GPU node would slot in behind the same interface. Weeks of cross-platform work;
the layer engine stands on its own beneath it.

**Tooling** (ROADMAP §5 — done, keep). `scripts/parity/` (native-vs-JVM screenshot
diff — the regression net for every phase here), `scripts/probe/` (drive
click/hover/hold + capture), `CDN_PROFILE=1` + `CDN_FORCERENDER=1` (per-phase
timings + `DrawStats`). **These are the verification harness for this refactor.**
Profiler caveat: `present` is vsync-capped by the *display* refresh — profile on
the target monitor before concluding a frame-rate gap (the demo-70/apidemo-144
report was two monitors, not a renderer bug).

---

## 11. Verification & risks

- **Skiko `RenderNode` availability (Phase 0 gate).** If `org.jetbrains.skiko.node.RenderNode`
  isn't in skiko 0.150.1 for native targets, take S2 (`Picture`-backed) — no
  external dependency, uses `org.jetbrains.skia.PictureRecorder`/`Picture` we
  already link. Either way the Skia leg is unblocked.
- **Windows can't build the Skia leg.** The Skia source sets compile only on
  macOS/Linux; native skiko klibs aren't in the Windows gradle cache. All Skia-leg
  work (Phase 1, Phase 3 Skia half, Track V) verifies on a mac/CI leg — same
  constraint that already governs the repo. The SDL leg (Phase 2, 4) is fully
  Windows-verifiable.
- **Hit-testing through the layer matrix.** `GraphicsLayerOwnerLayer.mapOffset`/
  `isInLayer` use the layer's transform matrix + outline (vs our stand-in's ad-hoc
  translate-only math). Probe click/hover/scroll on popups, rounded buttons,
  scrolled lists after Phase 2 — this is the most likely place for a subtle regression.
- **`prepareTransformationMatrix` portability.** Currently skiko-only; provide the
  SDL variant (pure math) in Phase 1/2 or `GraphicsLayerOwnerLayer` won't link on
  the SDL leg.
- **Metadata compile.** Only the Windows publish job compiles common metadata;
  keep `GraphicsLayerOwnerLayer`/`GraphicsLayer` façade in `nativeMain` (shared) and
  the `NativeRenderNode` actuals per renderer set, so no renderer type leaks into
  shared metadata (mirror the existing `createRenderBackend` expect/actual split;
  see CLAUDE.md "source-set hierarchy").
- **Re-sync discipline.** Vendored files come back via `compose-fork.txt`: flip the
  `!` refusals on the files we adopt; for files needing edits (the
  `setLightingInfo` strip), do manual vendoring per CLAUDE.md rule 3 (move to
  `src/{native,skikoRenderer}Main`, header-comment the origin, comment the manifest
  line).

---

## 12. Disposition of the old docs

- **`NEXT-SESSION.md` → delete.** Its content is the 70-vs-144 investigation, which
  *resolved* as "two monitors, not a bug" and concluded retained layers were "a
  SPECULATIVE optimization with no demonstrated need." That framing is superseded:
  the immediate-mode renderer is the correctness/fidelity/architecture problem this
  refactor fixes (matching upstream internals), independent of any single monitor's
  frame budget. Its one action item (delete dead `cacheKey` scaffolding) is folded
  into Phase 5.
- **`ROADMAP.md` → delete.** Its still-valid items are carried into §10; its §2
  (SDL performance / dirty regions / retained layers) is *this document*, now fully
  specified.

Both are replaced by this file.

---

## 13. Progress journal

Newest last. Each entry: what changed, how it was verified, what's next. Keep it
factual — this is the running record of where the refactor actually is, separate
from the plan above (which describes the intended end state).

### 2026-07-15 — Phase 0 + Phase 1 structural flip (branch `renderer-refactor`)

**Landed (SDL leg green; Skia leg mac/CI-only, not built here):**
- **Phase 0 — `NativeRenderNode` seam.** `com.compose.sdl.graphics.NativeRenderNode`
  interface + `NativeRenderNodeContext` + `createNativeRenderNode` `expect` in
  `nativeMain`, `TODO()` stub actuals per renderer set. Surface mirrors skiko
  `RenderNode`/`SkiaGraphicsLayer` (topLeft/size/pivot, transforms, shadow, clip
  rect/rrect/path, layerPaint, beginRecording/endRecording/drawInto/close).
  Additive; nothing constructed a node. *(commit `5a056630`)*
- **Vendored `GraphicsLayerOwnerLayer`** into `nativeMain` (byte-exact from skiko,
  `setLightingInfo` tail stripped) + `prepareLayerTransformationMatrix` lifted to a
  distinct-named `nativeMain` helper (the one helper that was skikoRenderer-only).
  Compiled unused first (zero behaviour risk). *(commit `86c97cba`)*
- **Phase 1 flip — `createLayer` → `GraphicsLayerOwnerLayer`.** `ComposeOwner` now
  implements `OwnedLayerManager` (dirtyLayers + `notifyLayerIsDirty` + `recycle` +
  `voteFrameRate`); `invalidate()` → window `needsFrame` via a callback hook;
  `renderRoot()` re-records dirty layers then walks the tree; `ComposeRootHost.drawRoot`
  delegates to it and both backends call it instead of `rootNode.draw`. Moved
  `ComposeOwner` + `ComposeRootHost` commonMain→nativeMain (they touch native-only
  `OwnedLayerManager`/`GraphicsLayerOwnerLayer`; `:ui` commonMain only compiles to
  native anyway); the `feed*Processor` expect/actual collapsed to plain native
  funs. *(commit `bbaa0435`)*

**Important caveat:** still backed by the existing lambda-replay `GraphicsLayer`, so
this is **behaviour-preserving — no re-tessellation win yet**. The structure
(dirty-gated `OwnedLayer` driven by the vendored `NodeCoordinator`) is in place; the
caching payoff arrives only when `GraphicsLayer`'s storage swaps to `NativeRenderNode`.

**Verified:** `:ui:compileKotlinMingwX64` green. Demo exe (built with the flip)
renders correctly — `GraphicsLayer` (rotation/scale/alpha), `Buttons` (rounded/
shape clip incl. circular FAB), `Shapes`/`Scroll`/`Text`/`Counter` non-blank
(PIL-inspected + eyeballed the first two). Full `sync.py` run confirmed the manual
vendoring is `!`-respected (`:ui`: 53 files excluded → manually vendored) and
non-disruptive (post-sync `:ui` rebuild up-to-date in 4s).

**Not yet verified:** hit-testing through the new layer *matrix* (`mapOffset`) — a
`scripts/probe/` click pass on scaled/rotated interactive layers is the next check.

**Gotcha (pre-existing on `main`, unrelated):** `:demo:compileKotlinMingwX64` fails
on `ColorsScreen.kt` — `androidx.compose.ui.tooling.preview.Preview` unresolved on
mingw (from commit `72e6f145`; only screen using `@Preview`). Temp-neutralize that
import + annotation to build the demo exe for verification. Worth a separate fix.

**Next:** implement `SdlRenderNode` (record → offscreen texture / cached-geometry
display list; replay cached) and make `GraphicsLayer` a façade over
`NativeRenderNode` — Phase 2, where the "stop re-tessellating" perf win lands. Then
the hit-testing probe pass, then Skia-leg Phase 1 verification on mac/CI.

### 2026-07-16 — navigation crash found + fixed; flip verified end-to-end

**Bug (caught by the probe pass, missed by screenshots):** the flip crashed with
`IndexOutOfBoundsException: index 1, size 1` on ANY navigation (`--nav3test` exit 3;
sidebar click killed the window). `--screen` screenshots never navigate, so they
missed it. Cause: my hand-written `notifyLayerIsDirty` removed a layer from
`dirtyLayers` unconditionally, but `renderRoot` iterates `0 until dirtyLayers.size`
calling `updateDisplayList()` which sets `isDirty = false` → `notifyLayerIsDirty(false)`
→ removed the layer mid-loop → next index ran off the end. A textbook case of the
plan's own "minimize divergence" rule: I diverged from upstream's
`OwnedLayerManagerImpl` and it bit me.

**Fix (commit `49b14337`):** aligned `notifyLayerIsDirty` + `renderRoot` to upstream
verbatim — while `isDrawingContent`, a clear is a no-op; the `dirtyLayers.clear()`
after the loop drops them all, so the list can't shrink during iteration.

**Verified end-to-end (SDL leg):** `--nav3test` PASS (was exit 3), `--backtest`
PASS, and the sidebar click navigates to GraphicsLayer correctly — the click lands
on the right row (hit-test through the new layer matrix works) AND the screen swaps
(layer create/destroy/recycle churn, no crash). Combined with the earlier render
checks, **the Phase 1 flip is now fully verified**: rendering, hit-testing, and the
navigation layer-lifecycle all correct.

**Next unchanged:** `SdlRenderNode` storage swap (the perf win), then Skia-leg
verification on mac/CI.

### 2026-07-16 — GraphicsLayer becomes a façade over NativeRenderNode

Restructured to the upstream shape so the caching node can slot in cleanly:

- **`GraphicsLayer.native` is now a copy-edit of `SkiaGraphicsLayer.skiko.kt`**
  (commit `44e09aa8`): it mirrors every visual property onto a backing
  `NativeRenderNode` and delegates `record`/`draw`, instead of the ad-hoc
  lambda-replay. (Trimmed vs upstream: outsets, ChildLayerDependenciesTracker.)
- **`NativeRenderNode`** reshaped to `record(block)` / `drawInto(canvas)` / `close()`
  + the full skiko-RenderNode property surface.
- **`DeferredRenderNode`** (nativeMain, renderer-agnostic) is the node both legs use
  *today* — replay-the-block, **unifying the old `GraphicsLayer.native` lambda-replay
  AND `ProjectOwnedLayer`'s transform/shadow/clip** in one place. Transform via
  `prepareLayerTransformationMatrix` + `canvas.concat` (same matrix as hit-testing).
- **`toImageBitmap` implemented** (was throwing): renders the layer into a fresh
  `ImageBitmap`, flushed via new `NativeFinishableCanvas` (SDL must commit its
  offscreen texture; Skia `finish()` is a no-op).

Still **behaviour-preserving** — `DeferredRenderNode` re-tessellates on replay, so no
perf win yet. Verified: `:ui` green; demo renders `GraphicsLayer` (rotation/scale/
alpha byte-identical), `Buttons`/`Shapes` correct; `--nav3test` PASS.

**Next — the actual perf win (the one remaining hard piece):** swap
`createNativeRenderNode` to a caching node so `drawInto` stops re-tessellating.
- **Skia leg (2a, mac/CI):** back it with skiko `org.jetbrains.skiko.node.RenderNode`
  — near-verbatim `SkiaGraphicsLayer` internals (record into `beginRecording()`'s
  canvas, `drawInto` replays). Needs a SkiaCanvas↔SkCanvas bridge + renderer
  resources (text/image caches) reachable from the node — the context is created
  resource-less today, so wire that (register on the backend like `offscreenRenderer`).
- **SDL leg (2b, Windows):** offscreen-texture node (record → SdlImageBitmap via
  `offscreenRenderer`, replay → transformed blit). **Design gotchas found:** (1) the
  offscreen infra already save/restores nested render targets, but the *parent*
  canvas's batched geometry must flush before a child switches target; (2) textures
  bake children by-value, so — unlike skiko's by-reference `Picture` — a child
  content change must re-bake ancestors: wire `invalidateParentLayer` propagation (a
  documented divergence from upstream `GraphicsLayerOwnerLayer`, necessary for a
  texture backend). Verify against `DeferredRenderNode` output (should be pixel-equal)
  + profiler (draw drops on the static sidebar). Best done with the parity harness,
  not just screenshots.

### 2026-07-16 — SDL caching node landed (opt-in), perf win measured

The SDL perf payoff shipped, flag-gated (commit `3f76f77e`). **`SdlRenderNode`**
records a leaf layer into an offscreen texture; replay is a blit, not a
re-tessellation.

- **Nesting solved WITHOUT the ancestor-invalidation divergence I'd scoped above.**
  Instead of "cache everything + re-bake ancestors", the node does **cache leaves,
  defer parents**: it auto-detects on first record whether its block drew a child
  layer (via a recording stack) and, if so, replays the block (children composite
  their own live textures → no staleness). Correct-by-construction, no
  `GraphicsLayerOwnerLayer` edit. This is cleaner than the earlier plan — supersedes
  the "wire invalidateParentLayer" note above.
- **Strictly non-regressing:** the texture fast-path is used only for opaque,
  untransformed, effect-free leaves (verified pixel-equal). scale/rotation (texture
  would bilinear-resample) and alpha/blend/colorFilter/renderEffect (blit ≠ offscreen
  compositing) fall back to a crisp block-replay.
- **Gated behind `CDN_LAYERCACHE=1`**; default is still `DeferredRenderNode`. The
  batch-flush-before-child-target-switch gotcha didn't bite — each node records into
  its own `offscreenRenderer` canvas, and the offscreen save/restore isolates targets.

**Verified (cached vs default):** GraphicsLayer / Shapes / Text / LazyColumn / Scroll
/ Brushes / Canvas **pixel-equal (0.000%)**; Buttons 0.006% + Icons 0.36% (AA fringe
from the premultiplied texture round-trip at rounded-clip / glyph edges — cosmetic,
not structural); `--nav3test` + `--backtest` PASS. Profiler (LazyColumn, forced
continuous): per-frame **verts 2958→1158 (−61%)**, text draws 22→6, draw 4.0→3.4 ms
— static leaves blit instead of re-tessellate.

**Remaining follow-ups:**
1. **Flip the default to caching** after a full parity-harness sweep (all screens,
   native-vs-JVM), not just the spot-diffs above.
2. **Fix the AA-fringe round-trip** (Buttons/Icons) — get the premultiplied blit-back
   bit-exact so icon/rounded-clip leaves are pixel-equal too.
3. **Phase 4 — cached-geometry display list** would widen the fast-path (crisp under
   scale/rotation, real compositing) and remove the opaque/untransformed restriction.
4. **Skia leg (2a, mac/CI)** — back `NativeRenderNode` with skiko `RenderNode` (needs
   the SkiaCanvas↔SkCanvas bridge + renderer-resource wiring noted above).

### 2026-07-16 — AA-fringe fix (#2 done); Phase 4 design (#3)

**#2 done (commit `8081fc13`):** image/vector/icon-drawing leaves are excluded from
the texture cache (detected via a `DrawStats.imageBlits` delta around the record
block) and fall back to the crisp block-replay — partial-alpha content doesn't
round-trip bit-exact through the 8-bit premultiplied offscreen. Result: the visibly-
wrong `painterResource` heart is gone; **Icons 0.36%→0.014%, Text/LazyColumn/
GraphicsLayer/Shapes 0.000%, Buttons 0.006%** (disabled semi-transparent fill).
Costs ~no perf (icons are blits, not tessellation; text still caches). The sub-
perceptual residual (≤0.014%, AA rounding on partial-alpha) is the inherent
texture-round-trip floor — only #3 removes it.

**#3 — Phase 4 cached-geometry display list — DESIGN (a major, standalone rework of
the hottest code; not yet implemented).** Replace the per-leaf texture with a
captured display list of the *tessellated geometry* `Sdl3DrawScope` already produces,
replayed under the layer matrix. Buys crisp-under-transform + bit-exact + drops every
fast-path restriction (transform/alpha/image/icon all cache).
- **Capture point:** the submit choke — `SDL_RenderGeometry` (shape/text vertex
  batches) and `SDL_RenderTexture` (image/icon blits). A recording `Sdl3Canvas` mode
  stores each submit as a command `{ vertexArray copy, texture ref, blend, clip }`
  instead of issuing it.
- **Coordinate space:** record with an IDENTITY CTM (layer-local), so captured
  vertices are pre-transform; replay applies the layer matrix to the cached positions
  (cheap float-array transform) then submits. This is what keeps it crisp under any
  transform (geometry re-transformed, never bilinear-resampled) — unlike the texture.
- **Nesting:** a "draw child layer" is captured as a by-reference command (replay the
  child node), like skiko's `Picture` — so nesting is by-reference, no leaf/parent
  split and no staleness (removes the current cache-leaves-defer-parents heuristic).
- **Memory:** one vertex-array copy per submit per dirty layer; re-recorded only on
  invalidation (dirty-gated), so steady-state cost is just the transform+submit.
- **Risk:** intrudes on `Sdl3DrawScope`'s native vertex buffers + `Sdl3Canvas`'s
  clip/state machine. Verify against the current renderer with the parity harness
  (not spot screenshots) + profiler. Sizeable focused effort — do it on its own.

### 2026-07-16 — #1 full sweep: DO NOT FLIP the default yet

Ran the cached-vs-default diff across **all 57 demo screens** (`build/rr_sweep/`).
A default-vs-default control was **0.000% on every outlier** (renders are
deterministic), so the diffs below are real, not screenshot jitter.

- **48 screens: ≤0.07%** (sub-perceptual — the texture-round-trip AA floor). Fine.
- **Pickers: 13.1%**, **Carousel: 4.4%** — REAL caching bugs. Pickers' cached content
  sits ~180px too high (the "TimePicker 14:30" header shows in cached but is scrolled
  off in default) — a **positioning/scroll discrepancy**, not colour/AA. Almost
  certainly caching a scrollable / larger-than-`size` / clipped-viewport container
  into a `size`-clamped texture, so the blit shows the wrong slice / loses the scroll
  offset. Carousel (auto-scrolling) is likely the same class.
- **GraphicsLayer 0.18% / Drawers 0.14% / Shadows 0.14%** — small; these screens
  animate, so most is animation-timing (cached vs default reach the screenshot frame
  at slightly different clock states), not a rendering bug.

**Decision: keep caching flag-gated (`CDN_LAYERCACHE=1`), default stays
`DeferredRenderNode`.** The sweep caught a real regression (Pickers) before it could
ship — exactly why the flag + sweep exist. The common static case (the perf win)
remains verified + available via the flag.

**Investigation (chasing the Pickers bug) — partial fix, deeper issue found:**
- **Root cause #1 (fixed, commit `29856d9b`): `beginOffscreen` never cleared the
  render target.** Fresh SDL target textures are zeroed (so static leaves were fine),
  but the retained-layer texture is REUSED across re-records → an un-cleared re-record
  ghosted prior content. That was the Pickers multi-frame instability (cached changed
  16.8% frame 6→60 while default was stable). Clearing to transparent on begin fixed
  it in controlled tests (Pickers → 0.000%).
- **Root cause #2 (open): the texture-cache path is NONDETERMINISTIC on complex
  screens.** Even isolated (no lingering processes, killed between runs), Pickers
  cached-vs-default swings between 0% and ~17% across identical launches — a
  timing-dependent correctness issue (frame-timing-dependent texture/dirty state).
  Screenshot-diffing can't reliably pin it (the target itself is nondeterministic).
- **Carousel: ~4.4% persistent** (stable across frames) — a separate, deterministic
  carousel-specific diff, still unexplained.

**Verdict:** the texture-cache approach is robust for static leaves but has a
timing-dependent robustness problem on complex screens that more texture patching is
unlikely to fully solve. **The path to flipping the default is Phase 4's
cached-geometry display list** (no offscreen texture → no fixed-resolution round-trip,
no per-frame texture state → no timing dependence), plus a deterministic verification
harness (frame-locked, or the JVM parity leg) rather than free-running screenshots.

### 2026-07-16 — Phase 4 STARTED: geometry recording foundation (commit `716a005d`)

First increment landed, compiling, **zero behaviour change** (opt-in machinery only):
- `SdlDisplayList` / `GeometryBatch` / `GeometrySink` — capture a layer's tessellated
  untextured triangle batches in layer-local coords; an `unsupported` flag trips on a
  not-yet-captured op so nothing leaks to the GPU.
- `Sdl3DrawScope.flush()` routes to a `recordingSink` (capture) instead of
  `SDL_RenderGeometry` when set; `currentDeviceMatrix()` exposes the CTM. **Key
  property proven by the code:** `writeVertex` bakes the CTM, so recording with an
  identity base CTM yields layer-local vertices → replay just re-applies the layer
  matrix (crisp, bit-exact), and a capturing pass touches NO render target → none of
  the texture-path timing nondeterminism.

**Next increments (scoped):**
1. **Capture-mode `Sdl3Canvas`** — a draw pass that sets the scope's `recordingSink`
   + identity base CTM, and gates text/image/clip to mark `unsupported` (suppress, no
   GPU) until they're captured.
2. **`SdlDisplayListRenderNode`** — record (capture list; defer if unsupported) +
   `drawInto` replay via a new `Sdl3DrawScope.replayBatch(...)` that re-emits captured
   layer-local vertices through the target CTM (reusing the target's batch/flush — no
   separate buffer). Behind `CDN_LAYERCACHE=geo`.
3. Extend capture to **text glyph blits, image blits, clip** (the win + drops the
   defer cases), then re-sweep and flip the default.
