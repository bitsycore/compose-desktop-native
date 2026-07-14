# Renderer roadmap — fixes, performance, memory

Grounded in the bubble-wrap findings (2026-07): lazy clips took the stress
sheet from 23 → 39 FPS, but 75 Hz needs structural work; issue #2 showed that
Cleaner-managed native memory starves when the Kotlin heap is quiet.

## 1. Deterministic native-resource lifecycle

Ownership closes resources the moment they're finished; GC stays as the
safeguard, not the mechanism.

- [x] **`NativeReleaseQueue`** — SDL calls are main-thread-only, so nothing may
  destroy directly from a Cleaner or worker: release actions ENQUEUE, the main
  loop DRAINS between frames. Cleaners become safe from any thread because
  they only enqueue. Verified end-to-end (direct enqueue + a real
  Cleaner→enqueue→drain cycle).
- [x] **`SdlImageBitmap` textures** — previously never destroyed (manual SDL
  memory, invisible to the GC nudge). A holder + Cleaner now enqueues
  `SDL_DestroyTexture` / `SDL_DestroySurface` for the texture, render target
  and never-realized decoded surface; explicit `close()` frees promptly.
- [ ] **Wire `close()` into cache eviction** — the Cleaner is the current
  release path for decoded/vector bitmaps (correct but GC-timed). Call
  `SdlImageBitmap.close()` on image-cache eviction / `removeMemoryResource`
  for prompt release (needs a cross-module hook — the caches hold the
  `ImageBitmap` interface, not the concrete type).
- [ ] **Cache-eviction closes (rest)** — every image/text cache closes what it
  evicts (Sdl3ImageCache already does; sweep the rest, both renderers).
- [ ] **Renderer `destroy()` chain** — window close synchronously frees every
  pool (clip targets already do; text caches, image caches, shadow cache,
  typefaces to follow).
- [ ] **`SkiaImageBitmap.close()`** (surface + snapshot) + close-on-eviction in
  the Skia caches. *Skia source sets build only on macOS/Linux — needs a mac
  or CI leg to verify.*
- [ ] **Demote the GC nudge** — once ownership does the work, stretch the 10s
  interval / gate it on churn; it remains the backstop.

Verification: `demo --leaktest`-style probe (cycle screens N times, print
RSS; expect a plateau without GC sawtooth).

## 2. SDL renderer performance (the 39 → 75 Hz path)

- [x] **Frame profiler** — `CDN_PROFILE=1` env flag prints per-phase timings
  (events / app pump / window pump / render / present) every ~2 s. Measure
  first, optimize second.
- [ ] **Right-size clip scratch targets** — the pool allocates window-sized
  textures per mask depth; allocate at the clip bbox (size-bucketed pool) to
  cut fill-rate for the masks that remain after lazy clips.
- [ ] **Dirty-region rendering** — the big structural item: accumulate damage
  from invalidated layers, scissor redraw to it. One bubble's spring should
  not re-tessellate 84. Most likely single item to reach 75 Hz.
- [ ] **Glyph atlas** — text draws currently break geometry batches (z-order
  flush per run); an atlas texture lets glyphs ride the vertex batches.
- [ ] **Retained layer textures** (later) — RenderNode-style caching of static
  subtrees keyed by draw content.

## 3. Skia renderer

- [ ] Bounded `SkiaImageCache` (LRU + eviction close) — unbounded HashMap today.
- [ ] Eager-close audit of the text pipeline (mirror of the SDL sweep).
- [ ] `saveLayer` huge-bounds clamp (GPU offscreen memory spikes — see
  CLAUDE.md pitfall).

## 4. Correctness / parity gaps (future bug reports waiting)

Ordered by likelihood of a user hitting them:

- [ ] **Gradient brushes on SDL** — `Brush.linearGradient/radialGradient`
  render solid; the per-vertex sampler infrastructure is half-present.
- [ ] **Layer rotation** — rotationZ repositions but content doesn't rotate
  (hit-testing too).
- [ ] **Real `saveLayer` alpha on SDL** — overlapping content composites at
  paint level; needs an offscreen (the clip-target pool can serve it).
- [ ] **`clipPath` generic shapes** — bbox fallback clips square.

## 5. Permanent tooling

- [x] **Parity harness** — scripts/parity/: renders every :demo screen native +
  JVM from the same commonMain composables, pixel-diffs, ranks by %differ,
  emits per-screen diff heatmaps + side-by-side compares (pct in filename).
  build/parity/ (gitignored). See scripts/parity/README.md.
- [ ] Promote the press/hover automation rig (window-handle-relative input +
  screenshot) into `scripts/`.

## 6. Long-term

- [ ] **SDL_GPU render backend** — real stencil clipping (no masks at all),
  pipelined batching, shader gradients. Weeks of work across three
  platforms; items 2–4 stay useful beneath it.

Suggested order: profiler → lifecycle queue + leak closes → clip-target
right-sizing → gradients → dirty regions → the rest by demand.
