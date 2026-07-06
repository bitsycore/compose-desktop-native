# PLAN_OPTIM.md — SDL renderer performance / memory plan

Verified audit of the SDL3 renderer + project Compose internals (2026-07-06).
Each phase is independently shippable; order = payoff ÷ risk.

**Status 2026-07-06: all six phases implemented and compiling; apidemo + demo
link clean on mingwX64. Rendering spot-checked via `demo.exe --screenshot`
(Buttons + Icons screens: tints, alpha states, FILL/GRAD/opsz axes, rounded
corners all correct). Remaining: the interactive checks listed below.**

Remaining manual verification (needs a human at the keyboard):
- Idle CPU ~0 in Task Manager when apidemo sits untouched; UI still updates
  on hover/click/scroll/caret blink; un-minimise repaints (Phase 3).
- Text selection / caret placement in apidemo URL field + body editor
  (Phase 5).
- Fade/tint animations don't stutter on first play (Phase 1).

Verification baseline: run `apidemo` / `demo` on Windows, watch the FPS counter
in the window title, Task Manager CPU% at idle, and Commit size / GPU memory
while (a) idling, (b) typing in a TextField, (c) hovering animated buttons,
(d) scrolling the response viewer.

---

## Phase 1 — Glyph textures: white + color-mod (memory + animation stutter)

**Problem.** Text and icon textures are cached with the tint colour (and the
folded layer alpha) baked into the cache key and the pixels:

- `Sdl3TextRenderer.kt:129-131` — `TextureKey(family, text, fontSize, color, variations)`,
  one SDL texture per unique combination, never evicted.
- `Sdl3Canvas.kt:462` — layer alpha is folded into the colour before the cache
  lookup, so a fade animation re-rasterises + re-uploads every string it
  touches at every alpha step.
- `FreeTypeIcons.kt:78-84` — same flaw for icons (`GlyphKey.argb`).

**Change.**
1. Rasterise glyph surfaces **white** (R=G=B=255, A=coverage, gamma applied to
   coverage as today) in `getOrCreateTexture` and `FreeTypeIcons.rasterise`.
2. Drop `color` from `TextureKey` / `GlyphKey`.
3. At blit time apply `SDL_SetTextureColorMod(tex, r, g, b)` +
   `SDL_SetTextureAlphaMod(tex, a)` in `Sdl3TextRenderer.drawText` and
   `FreeTypeIcons.blit`.

**Result.** Colour/alpha animations become allocation-free; cache cardinality
drops from (strings × colours × alphas) to (strings). No visual change —
modulation is exactly `pixel × color/255`, what the bake does today.

**Risk.** Low. Check the span path (`drawText` per-run colours) still tints
per run; check gamma still applied once (it lives in coverage, not colour).

- [x] Sdl3TextRenderer white rasterise + colormod blit
- [x] FreeTypeIcons white rasterise + colormod blit
- [x] Remove colour from both cache keys
- [x] Visual check: verified via demo screenshots (Buttons: white/grey/purple
      text + dimmed Disabled state; Icons: all four axes tinted correctly).
      Span-coloured text worth one interactive glance in apidemo.

## Phase 2 — Trivial allocation wins (pure win, no behaviour change)

1. **Gradient stops hoisted** — `Sdl3DrawScope.kt:852`: `sampleColors` builds a
   uniform-stops `List` **per vertex** when `stops == null`. Hoist the list
   creation into `linearSampler` / `radialSampler` / `sweepSampler`
   (`Sdl3DrawScope.kt:796-841`) so it happens once per shape, not per vertex.
2. **Lazy paragraph intrinsics** — `SdlParagraph.native.kt:92-107`:
   `maxIntrinsicWidth` (measures every hard line) and `minIntrinsicWidth`
   (compiles `Regex("\\s+")` + measures every word) are computed **eagerly at
   every paragraph construction**, read or not. Make both `by lazy`.
3. **Kill the regex** — replace `text.split(Regex("\\s+"))` with a manual
   whitespace scan measuring each word slice.
4. **`mapRectAABB` array churn** — `Sdl3Canvas.kt:94-100`: two `floatArrayOf`
   per clip/save. Inline min/max of the four mapped corners.

- [x] Stops hoist
- [x] Lazy min/max intrinsics (+ lazy lineWidths / width — draw never reads them for bounded text)
- [x] Regex → manual scan
- [x] mapRectAABB inline min/max

## Phase 3 — Render-on-demand (idle CPU/GPU → ~0)

**Problem.** `ComposeWindow.kt:170-286` re-runs the full pipeline every frame:
`drawRoot` (264) re-tessellates and re-blits the whole tree; window size is
polled every frame (240); a synthetic hover Move + full hit-test runs every
frame (255-257); `Snapshot.sendApplyNotifications()` runs up to 3× per frame
(111 via global write observer, 171, 234).

**Change.** Introduce a `needsFrame` flag in the main loop, set by:
- the existing `Snapshot.registerGlobalWriteObserver` (`ComposeWindow.kt:110`)
  — any state write schedules a frame;
- any polled `AppEvent` (input, resize, expose, quit);
- pending animation work: `SDL3FrameClock` gains a `hasAwaiters` flag
  (`SDL3FrameClock.kt` — set before `frameCh.receive()`, cleared after
  resume), plus the owner animation clock's pending continuations;
- recomposer non-idle (`recomposer.currentState`).

When `needsFrame` is false: skip constraints/measure/draw/present entirely and
block in `SDL_WaitEventTimeout(…, ~10ms)`. When true: run the frame exactly as
today. Extras:
- `backend.updateWindowSize()` only on `WindowResized` (keep one initial call).
- Synthetic hover re-dispatch only when a frame actually ran layout/scroll,
  not on skipped frames.
- Always render one frame after window expose/show/resize events so the
  backbuffer is never stale; keep the FPS title update (it naturally shows 0
  when idle — acceptable, or freeze the title while idle).

**Risk.** Medium. Missed-wakeup bugs show as "UI doesn't update until mouse
moves" — every invalidation source must set the flag. Test: caret blink,
scroll fling decay, tooltip delay, popup open/close, window drag-resize,
minimise/restore.

- [x] needsFrame flag + wait-for-events idle path (`ComposeWindow.kt`)
- [x] Animation/recomposition pending detection — used `recomposer.hasPendingWork`
      (covers the broadcast clock awaiters) instead of instrumenting SDL3FrameClock;
      node animations re-arm via the global write observer when they write state
- [x] Resize/expose forces a frame — `SDL3EventMapper` now maps EXPOSED / SHOWN /
      RESTORED / MAXIMIZED / FOCUS_GAINED → `AppEvent.RedrawNeeded`, and
      PIXEL_SIZE_CHANGED / DISPLAY_SCALE_CHANGED → `WindowResized`
- [x] Kept: `updateWindowSize` + synthetic hover now only run on rendered frames
      (idle path skips them); the 3× sendApplyNotifications consolidation was
      deliberately NOT done (behaviour risk vs tiny win)
- [ ] Manual test checklist above

## Phase 4 — Cache eviction (long-session memory)

1. **`fTextureCache` LRU** — `Sdl3TextRenderer.kt:129-131`: cap (e.g. 512
   entries or ~64 MB estimated), `SDL_DestroyTexture` on evict. Typing,
   timestamps, and response bodies currently grow VRAM forever.
2. **`fWidthCache` cap** — `Sdl3TextRenderer.kt:135`: plain size cap + clear.
3. **`FreeTypeIcons.fGlyphs` LRU** — after Phase 1 the key has no colour, so
   cardinality is small; a simple cap is enough.

- [x] LRU helper (`LruCache.kt` — insertion-order map, re-insert on hit, onEvict hook)
- [x] `fTextureCache` → LRU(768, destroys textures); `fGlyphs` → LRU(512);
      `fWidthCache` → cap-and-clear at 16384 (keeps hot-path lookups single-hash)

## Phase 5 — Text interaction hot path

**Problem.** `SdlParagraph.native.kt:149-161` (`getOffsetForPosition`) measures
`substring(0, c)` for every column — O(n²) per click/drag — and permanently
pollutes `fWidthCache` with every prefix. `getHorizontalPosition` (142-147)
measures a prefix substring per call (cursor, selection, caret blink).

**Change.** Build a per-line **cumulative advance array** lazily on first
position query (`FloatArray(line.length + 1)`, one pass of per-char measures),
then:
- `getHorizontalPosition` = `advances[col]` (O(1));
- `getOffsetForPosition` = binary search (advances are monotonic);
- `fillBoundingBoxes` / `getPathForRange` read the same array.

**Risk.** Low-medium: per-char advance sum vs whole-string kerned width can
differ slightly; acceptable for icon-free UI text (SDL path has no ligatures
enabled), but verify caret lands correctly in apidemo URL field + body editor.

- [x] Lazy per-line advance arrays (prefix widths, exact — kerning-safe since
      they're measured as substrings, not summed per-char)
- [x] Binary search offset lookup (same nearest-edge semantics as the old scan)
- [ ] Selection/caret manual test in apidemo

## Phase 6 — Tessellation tuning (vertex volume)

1. **Adaptive segment counts** — `Sdl3DrawScope.kt:352, 391-394, 410-413, 492`:
   corners fixed at 8, ovals 24, circles 64, cubics 16 regardless of size.
   Use `segments = clamp(ceil(radius / 2f), 3, 16)` for corners/arcs (radius in
   px) and an arc-length estimate for path curves.
2. **Rounded-clip corner geometry cache** — `Sdl3Canvas.kt:338-382` rebuilds
   corner cutout vertices (ArrayList + native alloc) every frame; cache by
   (w, h, radii) since Material reuses a handful of shapes.
3. **Path polyline cache** — `Sdl3DrawScope.kt:457-510` re-linearises paths
   every frame; cache the flattened polyline on `ProjectPath` keyed by a
   mutation generation counter.

- [x] Adaptive segments — `arcSegments(sweep, radius)` = 1 chord per ~4px of
      arc, clamped [3, 48]; all hardcoded 8/24 call sites routed through it
      (small corners get cheaper, large rings get smoother)
- [x] Clip-corner cutouts — rewrote `zeroRoundRectCorners` to write straight
      into the native SDL_Vertex buffer (old ArrayList<Float> staging boxed
      ~300 floats per clip layer per frame); geometry cache judged unnecessary
      after that + render-on-demand
- [x] ProjectPath polyline cache — `contentKey` (generation ⊕ size, generation
      bumps on reset/translate) + cache slot on the path; `linearisePath`
      reuses it when the scope origin is 0 (the Sdl3Canvas flow)

## Parked (verify first / low ROI)

- `ProjectOwnedLayer.drawLayer` reportedly calls `shape.createOutline` per
  frame per clipped layer (`ComposeOwner.kt:465`) — cache with invalidation on
  shape/size/density change. **Agent-reported, not yet verified by hand.**
- Batch break on every text/image draw (`Sdl3Canvas.kt:458`) — inherent to
  painter's-order z; fixing needs a glyph atlas + interleaved batching. Big
  effort, revisit only if profiles say geometry submission dominates.
- `usePinned` + memcpy per flush, event-list churn, `Pair` allocs in
  `findUp` — noise compared to the above.
