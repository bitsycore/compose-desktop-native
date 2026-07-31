# PLAN.md — Performance & Upstream-Parity Plan

Audited plan for closing the performance and feature-parity gap between
**ComposeNativeSDL3** and upstream Compose Multiplatform, after the removal of
the second (non-Skia) SDL renderer. This is the actionable companion to
[RENDERER.md](RENDERER.md) (which is partly stale — see §8) and

> **Headline conclusion.** The retained-layer render engine is **already
> byte-for-byte upstream** and is *not* where the performance gap lives. Two
> subsystems account for almost all of the observed "not as fast as the
> original" symptom:
>
> 1. **The hand-rolled SDL frame driver** in `:desktop-native-window` — a
>    vsync-reporting bug double-paces every GPU window to ~30 FPS, plus a
>    per-frame Metal surface reacquire, a redundant full-surface clear, and
>    over-eager snapshot-apply.
> 2. **The text engine** — every `Text` re-shapes (HarfBuzz + bidi +
>    line-break) on **every paint**, because our `Paragraph` actual rebuilds
>    the skia paragraph instead of caching it like upstream.
>
> Fixing the frame-pacing bug (Phase 0, ~1 line each) and the paragraph cache
> (Phase 1) is expected to recover the bulk of the gap.

> **Update (2026-07-29, macOS execution pass).** Phases 0–1 landed; macOS Metal
> measurement (§12) then showed there is **no steady-state perf gap** on
> macOS/Metal (P0.4/P0.6 closed as non-issues). The remaining work is FEATURE
> PARITY, and this pass closed the high-value, low-risk parity gaps: lineHeight /
> LineHeightStyle / textIndent / baselineShift / span-background in the text
> engine (Material3 typography sets lineHeight almost everywhere — parity harness
> Buttons dropped ~16% → 1.6% differing), generic font-family resolution
> (serif/cursive/…), a real DatePicker/TimePicker date formatter, NamedFont axis
> identity, and size-driven SVG on the project image path. The genuinely-large /
> high-risk items (P3.2 font-resolver rewire, P3.3 official-path `SVGPainter`,
> P4.1 RenderNode-shadow revert) stay deferred with rationale in their sections.

---

## 0. How to read this plan

Every item has an **ID** (`P0.1`…), an **impact** (High/Med/Low), an **effort**
(S ≤ half-day / M ≤ 2 days / L > 2 days), and a **verify** hook (which tooling
gates it — see [TOOLING.md](TOOLING.md)). Items are grouped into workstreams
(WS-A…WS-H) but sequenced into phases (§1). File references are
`path:line` against the tree as audited.

**Golden rule for this whole plan:** *prefer vendoring upstream skiko code over
hand-rolling* (CLAUDE.md philosophy). Several items below are literally "delete
our reduced local port and vendor the upstream file."

---

## 1. Phasing (do them in this order)

### Phase 0 — Quick wins (all S, mostly 1–3 lines, high leverage)
The cheapest, highest-impact changes. Land these first, measure, then continue.

**Status legend:** ✅ done · 🟡 partial · ⏸️ deferred · ⬜ todo. Status is the
leftmost column in every phase table below.

**Phase 0 status (branch `perf/phase0-quickwins`):** P0.1–P0.3, P0.5, P0.7
**DONE + verified** — compiled (`:ui-text`, `:desktop-native-window` mingwX64) +
`:ui-graphics` `klibApiCheck` green. **P0.4 + P0.6 now CLOSED as non-issues after
macOS Metal measurement (2026-07-29)** — see §12.

| Status | ID       | Item                                                                                | Impact         | Verify               |
|:------:|----------|-------------------------------------------------------------------------------------|----------------|----------------------|
|   ✅   | **P0.1** | Fix vsync double-pacing (GL + Metal report `vsyncEnabled`)                          | **High**       | profiler FPS, manual |
|   ✅   | **P0.2** | Add missing `else` in the pace block (kill the pending-spin)                        | Med            | profiler CPU         |
|   ✅   | **P0.3** | Delete dead `DrawStats` so the profiler stops lying                                 | Med            | `CDN_PROFILE=1`      |
|   ❌   | **P0.4** | ~~Drop per-frame full-surface `clear()` to first-frame-only~~ — WON'T FIX (§12)      | Med → none     | verify-mac           |
|   ✅   | **P0.5** | Set `FontRasterizationSettings` (edging/hinting/subpixel) on text styles            | Med            | parity, verify-mac   |
|   ❌   | **P0.6** | ~~Stop the per-frame Metal drawable reacquire~~ — WON'T FIX, inherent + not a cost (§12) | ~~High (mac)~~ none | verify-mac, profiler |
|   ✅   | **P0.7** | Explicit open/close of native paragraph resources (was: GC nudge)                   | Med (memory)   | manual RSS watch     |

**P0.4 / P0.6 resolution (measured on macOS Metal, ProMotion 120 Hz):** both
rested on a false premise — that Metal could reuse one persistent drawable/
surface across frames like GL reuses its FBO. It can't: a `CAMetalDrawable` is a
single-use, per-frame resource from a ~3-deep pool, so the per-frame reacquire in
`ensureSize` is **inherent to the Metal model and matches upstream skiko's
`MetalRedrawer`**. Profiling (§12) shows the reacquire + clear cost nothing
measurable: `present=0.1 ms`, `draw=0.05 ms`, real `layout=0.02 ms`, RSS flat at
~108 MB, **zero `nextDrawable` starvation**. The ~6.7 ms that looked like a cost
was pure vsync pacing (the `nextDrawable()` block) mis-attributed to the "layout"
phase — now split out as `acquire` (§12). No autorelease-pool fix needed either
(RSS is stable across a force-render soak).

**P0.7 note:** the fix is deterministic native-resource lifecycle, *not* a GC
tweak. `SkiaParagraphOps` now closes the previous `SkParagraph` + the
`ParagraphBuilder` on rebuild, skips the rebuild entirely when paint attributes
are unchanged (static text shapes once at measure, never on paint), and the
intrinsics-only throwaway is `dispose()`d. This removes the per-paint native
churn that ballooned RSS (issue #2). The 10 s `GC.collect()` in
`ComposeWindow.kt` stays as a genuine backstop for measure-time paragraph
churn (Compose holds `Paragraph` by GC, no dispose seam) — it can likely be
removed after an RSS check, but that's left as a follow-up. This also delivers
most of **P1.1** (see below).

### Phase 1 — The text engine (the big steady-state perf win)
| Status | ID       | Item                                                               | Impact   | Verify           |
|:------:|----------|--------------------------------------------------------------------|----------|------------------|
|   ✅   | **P1.1** | Cache the built skia paragraph; stop reshaping on every paint      | **High** | parity, profiler |
|   ⏸️    | **P1.2** | Reuse one layouter for intrinsics + final layout (no double-shape) | High     | parity           |
|   ✅   | **P1.3** | Coalesce `Snapshot.sendApplyNotifications()` toward once/frame     | Med      | profiler CPU     |

**P1.1 done:** the paragraph is built once at measure and never re-shaped when
color/shadow/decoration are unchanged (P0.7); a color-only change on
single-style, undecorated text now re-applies the foreground paint via skia's
`updateForegroundPaint` + `markDirty` + re-layout (no HarfBuzz re-shape),
mirroring upstream `ParagraphLayouter.setColor`. Spanned/decorated text and
shadow/decoration changes still take the full rebuild (colours are baked
per-run there).

**P1.3 done:** the global snapshot write-observer now only *schedules* a frame
(`markAllNeedFrame`) instead of calling `sendApplyNotifications()` inline on
every state write — the apply is coalesced to the once-per-iteration calls
(loop top / before each pump / before layout). Mirrors upstream
`GlobalSnapshotManager`.

**P1.2 deferred (evaluated):** reusing the intrinsics-pass paragraph for the
final layout is blocked by `maxLines`/ellipsis being baked at *build* time (the
unbounded intrinsics paragraph can't be re-laid-out with a line cap), and
holding a live paragraph per `ParagraphIntrinsics` trades the clean
build-then-`dispose()` (P0.7) for longer-lived native memory. Net win needs a
profiler measurement of the double-shape cost first — not done blind.

### Phase 2 — Frame-pacing correctness & the on-demand model
| Status | ID       | Item                                                                  | Impact | Verify               |
|:------:|----------|-----------------------------------------------------------------------|--------|----------------------|
|   ✅   | **P2.1** | FPS-lock to real display refresh (`SDL_GetCurrentDisplayMode`)        | Med    | manual multi-monitor |
|   ⏸️    | **P2.2** | Adopt upstream `GlobalSnapshotManager` invalidation-driven scheduling | Med    | probe, parity        |
|   ✅   | **P2.3** | Bound the idle `SDL_WaitEventTimeout` and fix woken-event latency     | Low    | manual idle CPU      |

**P2.1 done:** the non-vsync fallback pacing now derives its frame cap from the
rendered window's real display refresh (`SDL_GetDisplayForWindow` +
`SDL_GetCurrentDisplayMode`), min across non-vsync windows, instead of a
hardcoded 16 ms — so a 144 Hz panel on a Software/vsync-unavailable path isn't
capped to 60. No `sdl3.def` change (the cinterop binds all of SDL3). Post-P0.1
this path is only hit when vsync is unavailable.

**P2.3 done:** idle `SDL_WaitEventTimeout` raised 10 ms → 100 ms (a real event
still wakes it immediately; the timeout only bounds async-work re-checks while
truly idle), cutting idle wakeups.

**P2.2 deferred (evaluated):** the *coalescing* half of the upstream
`GlobalSnapshotManager` model is already delivered by P1.3 (write-observer
schedules, doesn't apply inline). Vendoring the full manager /
`FrameRecomposer` invalidation-driven scheduler is a larger, skiko-windowing-
coupled change (RENDERER.md §8 non-goal) needing probe/parity verification —
left as a bigger follow-up, not done blind.

### Phase 3 — Font & image parity
| Status | ID       | Item                                                                          | Impact                 | Verify     |
|:------:|----------|-------------------------------------------------------------------------------|------------------------|------------|
|   ✅   | **P3.1** | System/default + generic-family font resolution via `FontMgr.default`         | **High** (correctness) | parity     |
|   ⏸️    | **P3.2** | `FontListFontFamily` / resource-`Font` / async resolver (real font selection) | High                   | parity     |
|   ✅   | **P3.3** | Resolution-independent SVG (size-driven `DrawCache`) + XML→`ImageVector`      | Med                    | parity     |
|   🟡   | **P3.4** | Bound `SkiaImageCache` + `SkiaFonts.resolveCache` (LRU / eviction)            | Med                    | manual RSS |
|   ⏸️    | **P3.5** | `loadImageBitmap` / `loadSvgPainter` / `loadXmlImageVector` public APIs       | Low                    | compile    |

**P3.1 done:** generic families (serif/cursive/monospace/sans-serif) now resolve
through the per-OS concrete-name alias table (upstream `GenericFontFamiliesMapping`,
`SkiaFonts.genericFamilyAliases`) via `FontMgr.matchFamilyStyle`, instead of
falling back to the sans-serif default. Named system families already worked.

**P3.3 done (both paths).** (a) Project `ImageLoader` path: `SkiaImageCache`
rasterises SVG/Android-vector at the DESTINATION pixel size (`rasterizeSvgAt`,
size-keyed cache). (b) OFFICIAL `painterResource` path: `SvgElement.toSvgPainter`
now returns a size-driven `SvgPainter` (reports intrinsic size for layout,
re-rasterises at the draw size via new `EncodedImageDecoder.svgIntrinsicSize` /
`decodeSvgAt` hooks) instead of a fixed `BitmapPainter` — matching upstream
desktop's `SVGPainter`. XML `<vector>` was already scalable (parsed to
`ImageVector` via the vendored `XmlVectorParser`). P3.5's `loadImageBitmap(InputStream)`
/ `loadSvgPainter(InputStream)` are inherently JVM-only (no `java.io.InputStream`
on K/N), so those exact signatures are N/A on native.

**P3.2 deferred (large + low parity-harness impact):** the text engine
(`SkiaParagraphEngine`/`SkiaFonts`) reads `TextStyle.fontFamily` DIRECTLY via
`projectFontName()`/`projectFontVariations()` and never consults the
`FontFamily.Resolver` typeface (the resolver returns a no-op
`TypefaceResult.Immutable(Unit)`). Making resource-`Font` lists + weight-matching
+ async work therefore means rewiring the load-bearing font path to consume the
resolver's typeface — high regression risk, and the parity harness (shared
screens use `NamedFont` / bundled fonts) wouldn't even exercise it. Left as the
one genuinely-L, high-risk item.

**P3.1 (concrete names done):** unbundled family names now resolve against the
OS font set via `FontMgr.matchFamilyStyle` (e.g. `FontFamily("Arial")`) instead
of silently becoming Noto Sans. **Remaining:** generic families
(`serif`/`cursive`/…) still fall through to the bundled default — proper
generic→concrete mapping needs the per-OS alias table from upstream
`PlatformFont.skiko.kt` (folded into P3.2).

**P3.4 (images done):** `SkiaImageCache` is now a bounded (256) access-order
LRU that closes the evicted image — long-running apps showing many distinct
runtime images (`registerMemoryResource`) no longer grow image memory without
limit; on-screen images stay hot, an evicted one re-decodes on next use.
**Font cache deferred:** bounding `SkiaFonts.resolveCache` is low-value (few
family+axes combos) and messy — evicted typefaces stay referenced by live
paragraphs + registered in the `TypefaceFontProvider` (no clean unregister), so
eviction wouldn't free memory. Skipped deliberately.

**P3.2 / P3.3 / P3.5 deferred (large / needs runtime verification):** the async
`FontListFontFamily` resolver, resolution-independent SVG (`SVGDOM` +
size-driven `DrawCache`) / XML→`ImageVector`, and the public
`loadImageBitmap`/`loadSvgPainter`/`loadXmlImageVector` stream APIs are all
L-effort and need visual/parity verification — not done blind. Vendor targets
noted in §5/§6.

### Phase 4 — Vendoring cleanup & docs
| Status | ID       | Item                                                                    | Impact            | Verify             |
|:------:|----------|-------------------------------------------------------------------------|-------------------|--------------------|
|   ⏸️    | **P4.1** | Reverse the two second-renderer manual vendors                          | Med (maintenance) | verify-mac, parity |
|   🟡   | **P4.2** | Decompose `ComposeWindow.kt` (1131 lines) into focused files            | Low (maintenance) | build              |
|   ✅   | **P4.3** | Reconcile the "text vendored verbatim" doc claim; rewrite RENDERER.md   | Low               | n/a                |
|   ✅   | **P4.4** | Purge residual "second renderer / SDL renderer" language in code + docs | Low               | build              |
|   ✅   | **P4.5** | Vendor thin `Ripple.skiko.kt` (already vendored); real date/time formatter | Low            | parity             |

**P4.5 (date formatter done; Ripple already vendored; mirror-drift deferred):**
the material3 `PlatformDateFormat.native` English-ISO stub is replaced with a real
pattern/skeleton formatter (honours the requested CLDR pattern, so DatePicker /
TimePicker headlines read "Jul 29, 2026" / "July 2026"; field names stay English
— full CLDR name localization needs ICU data we don't bundle). The thin
`Ripple.skiko.kt` turned out to be **already vendored** (material3 manifest,
`src/vendor/native/.../internal/ripple/Ripple.skiko.kt`) — no project shim to
replace. Still deferred (tooling nicety, zero runtime impact): converting
byte-identical foundation `.native.kt` mirrors to `SET_FOLDER macosMain`
directives so drift-tracking covers them.

**P4.3 done:** RENDERER.md's biggest inaccuracy is fixed — it claimed text was
"upstream's own `SkiaParagraph`, vendored verbatim" with "upstream
`PlatformFont`/`FontCache` replacing the port's name-to-bytes engine", which is
the opposite of reality. Now describes the reduced local port
(`SkiaParagraph.native.kt` + `SkiaParagraphEngine`) that keeps the port's own
`SkiaFonts` model; the §5 B6.3 row is downgraded to Partial; the "leg" framing
is dropped throughout; the LazyColumn perf number is marked historical (vs the
removed SDL leg); and §6 native-resource lifecycle now reflects P0.7/P1.1/P3.4.

**P4.4 done:** purged the factually-wrong residue in code comments — references
to deleted modules (`renderer-sdl3`), the removed backend ("SDL3 backend",
"Skia or SDL3", "SDL renderer's per-vertex gradient sampler") across
`GradientBridge`, `DrawShape`, `ComposeOwner`, `IconFont`, `Popup.native`,
`BrushScreen`.

**P4.2 partly done:** extracted three self-contained units out of the
1131-line `ComposeWindow.kt` (now **978 lines**) as pure compiler-verified
moves — `FrameProfiler.kt` (+ `kForceRender`), `WindowArchitectureOwner.kt`,
`WindowInputHelpers.kt` (`BackNavigationInput` + `dispatchTypedText`). The
remaining bulk (the main loop, `WindowInstance`, and the probe/virtual-frame
timing cluster) is deeply intertwined and left in place — extracting it needs
visibility surgery on many top-level privates for marginal gain.

**P4.1 deferred (the last substantive item — risky maintenance, no parity/perf
win):** reversing the two manual vendors restores upstream RenderNode shadows via
`SkiaGraphicsContext.setLightingInfo` + relocates `prepareTransformationMatrix`.
Our current hand-rolled `NativeShadowCanvas` shadows already render, and macOS
profiling shows the shadow path costs nothing (`draw`=0.08 ms), so this buys only
vendoring cleanliness — while risking a shadow-lighting or hit-test-coordinate
regression that a quick smoke test wouldn't catch. It needs a full `verify-mac` +
parity pass (shadow lighting + hit-test agreement) as a focused follow-up, not a
blind late-session edit. **P4.5 done** (see above).

---

## 2. WS-A — Frame pacing & the SDL main loop

The loop (`compose/desktop/native/window/…/ComposeWindow.kt:158-273`) is
**already render-on-demand and idle-blocking** — `shouldRender()`
(`:921-922`) gates rendering on invalidation, and the idle branch blocks in
`SDL_WaitEventTimeout` (`:253`). The user's premise "spinning when too fast" is
only partly true; the real defects are below.

### P0.1 — VSync double-pacing (CONFIRMED bug) · High · S
`SDL3Backend.vsyncEnabled` defaults `false` (`SDL3Backend.kt:44`) and is set
**only** on the Software path (`SDL3Backend.kt:118`). The GL path calls
`SDL_GL_SetSwapInterval(1)` (`SDL3Backend.kt:96`) but never flips `vsyncEnabled`;
the Metal path (`SDL3Backend.kt:98-104`) relies on `nextDrawable()` blocking but
also never flips it. Result: in the pace block (`ComposeWindow.kt:231`)
`vAllVsync` is forced `false` for every GPU window, so after presenting an
already-vsync-blocked frame the loop *also* runs `SDL_Delay(16u)`
(`ComposeWindow.kt:249`) → effective ~30 FPS while animating.

**Fix:**
- GL branch (`SDL3Backend.kt:96`): capture the result of
  `SDL_GL_SetSwapInterval(1)` (and/or read back `SDL_GL_GetSwapInterval`); set
  `vsyncEnabled = true` on success, leave `false` if the driver refused.
- Metal branch (`SDL3Backend.kt:98-104`): set `vsyncEnabled = true` (CAMetalLayer
  blocks on `nextDrawable` by default — `SkiaMetalBridge.kt:116-117`).

Then the loop takes the `SDL_Delay(1u)` courtesy-yield path instead of stacking
16 ms. **This is the single biggest steady-state FPS win.** All cinterop symbols
already exist — no `sdl3.def` change.

### P0.2 — Pending-spin gap · Med · S
The pace block (`ComposeWindow.kt:248-255`) has no `else`: if nothing rendered
this iteration *but* `vAppPending == true` (app recomposer has work), neither
`SDL_Delay` nor `SDL_WaitEventTimeout` runs — the loop spins at full speed until
composition settles. Add an `else { SDL_Delay(1u) }` (or the P2.1 frame-time) so
a pending-but-not-yet-renderable state still yields. This is the actual
"busy-loop when too fast" the user senses.

### P2.1 — FPS-lock to real display refresh · Med · M
`SDL3FrameClock.kt` is a timestamp passthrough (`SDL_GetTicksNS`) with no notion
of the panel refresh; the fallback cap is a hardcoded 16 ms
(`ComposeWindow.kt:249`) — wrong on 144 Hz (caps to 60) and 30 Hz panels. Query
once per window and cache, refresh on `SDL_EVENT_WINDOW_DISPLAY_CHANGED`:
```kotlin
val disp  = SDL_GetDisplayForWindow(window)
val mode  = SDL_GetCurrentDisplayMode(disp)      // SDL_DisplayMode*, .refresh_rate (Hz)
val frameMs = if (mode.refresh_rate > 0f) (1000f / mode.refresh_rate) else 16f
```
Use `frameMs` as the non-vsync fallback delay. **No `sdl3.def` change** — the
`.def` binds all of SDL3 (`headerFilter = SDL3/**`); the symbols are confirmed
present in the commonized klib.

### P2.3 — Idle wake tuning · Low · S
`SDL_WaitEventTimeout(null, 10)` (`ComposeWindow.kt:253`) polls every 10 ms and
leaves the woken event in the queue (adds ~1 iteration of latency). Raise the
timeout (100–250 ms — a real event wakes it immediately regardless) to cut idle
wakeups. Keep it a `null` peek so `pollEvents()` still owns dispatch.

---

## 3. WS-B — Renderer per-frame overhead

The layer engine (`ComposeOwner`, `GraphicsLayerOwnerLayer`,
`SkiaGraphicsLayer.skiko.kt`) is upstream-identical and correct — record-once /
replay-clean works. Do **not** touch it. The overhead is around it:

### P0.6 — Per-frame Metal surface reacquire · High (mac) · M
`ensureSize` is called every rendered frame (`ComposeWindow.kt:938`), and on
Metal (`SkiaMetalBridge.kt:97-149`) it **unconditionally** tears down
`fSurface`+`fRT` and calls `vLayer.nextDrawable()` (`:118-126`) even when the
size is unchanged. `nextDrawable()` can block ~16 ms; rebuilding the
`BackendRenderTarget`+`Surface` each frame is real work. The GL/Software bridges
already short-circuit on unchanged size (`SkiaGLBridge.kt:51`,
`SkiaSurfaceBridge.kt:35`) — Metal must too. **Fix:** move `nextDrawable()` out
of `ensureSize` into the present path and make `ensureSize` a true no-op when
dimensions are unchanged; reuse the RT/Surface wrap across frames where the
drawable allows.

### P0.4 — Per-frame full-surface clear · Med · S
`SkiaRenderBackend.beginFrame` clears the whole physical backbuffer
(`SkiaRenderBackend.kt:82-91`) every frame. The root content is opaque and fills
the window, so on GPU this is a redundant full-screen fill (2× area on Retina).
The clear only guards against uninitialised drawable memory on the *first* frame
of a fresh drawable — which is only "every frame" *because* of the P0.6 bug.
**Do P0.6 first, then** reduce this to a first-frame-only clear.

### P1.3 — Coalesce snapshot-apply · Med · M
`Snapshot.sendApplyNotifications()` fires ~5× per loop iteration
(`ComposeWindow.kt:163, 197, 224, 947` + the per-write global observer at
`:144-147`). Upstream coalesces to ~once/frame (CONFLATED channel + CAS in
`GlobalSnapshotManager.skiko.kt`). Consolidate — but carefully: the pre-layout
apply at `:940-947` is documented as needed for scroll. This pairs with P2.2.

### P0.7 — Explicit open/close of native paragraph resources · Med (memory) · S — ✅ DONE
The root cause of the native-memory balloon (issue #2) and the reason the 10 s
`GC.collect()` nudge (`ComposeWindow.kt`) existed: `SkiaParagraphOps.rebuildAndPaint`
allocated a fresh `SkParagraph` (+ `ParagraphBuilder` + styles) on **every paint**
and dropped the old one **without closing it** — so native paragraphs piled up
until a GC ran. The intrinsics path (`paragraphIntrinsicWidths`) also built a
throwaway paragraph it never freed. Fix (deterministic lifecycle, not a GC tweak):
- `rebuildAndPaint` re-shapes only when a paint attribute (color/shadow/decoration)
  actually changed, and closes the previous `SkParagraph` when it does. Static
  text now shapes once at measure and never re-shapes on paint.
- `build()` closes the `ParagraphBuilder` in a `finally` (the paragraph owns its
  own native data and outlives the builder).
- `NativeParagraphOps.dispose()` added; it closes the paragraph **and** the
  per-ops `SkFont`, and the intrinsics-only throwaway (built per text measure)
  is now `dispose()`d instead of leaking both to GC.

Explicit `close()` also cancels skiko's own GC-driven `Cleaner` for that object,
so nothing double-frees. **Limit:** the *top-level* paragraph a `Text` keeps
while on screen still frees via skiko's Cleaner on GC — Compose holds `Paragraph`
by GC with no dispose seam, so there's no non-GC signal for "this text went
away" (upstream relies on JVM GC the same way). The 10 s `GC.collect()` therefore
**stays** as the backstop for that navigation-time churn; it is no longer the
mechanism for the per-frame/per-measure churn (that's now explicit). Fully
removing it would need a frame-idle lazy-close refactor (close idle paragraphs,
rebuild on next access) — tracked under **P1.1** (see Phase 1).

### P0.3 — Dead `DrawStats` blinds the profiler · Med · S
Nothing writes `DrawStats.*` anymore (they were bumped by the removed SDL
canvas); the profiler still *reads* them (`ComposeWindow.kt:343-347`) so
`CDN_PROFILE=1` prints zeros for all draw work — it can't answer "what inside
draw costs the time," the exact question this plan needs. **Either delete
`DrawStats` + the profiler suffix, or re-wire the counters into
`SkiaBackedCanvas` (geometry / saveLayer-mask realizations / text runs / image
blits).** Do this early — it gates measuring every other renderer item.

### Renderer allocation churn (secondary) · Low-Med · S
`drawDropShadow` (`SkiaBackedCanvas.skiko.kt:522-557`) allocates a fresh
`SkPaint` + Gaussian `MaskFilter` per record — bounded to dirty layers, but a
scrolling list of elevated cards reallocates every frame. Pool a paint keyed by
(elevation, colors), **or** better: adopt upstream RenderNode shadows (see P4.1
— the `setLightingInfo` path we dropped) and delete the hand-rolled
`NativeShadowCanvas` contract entirely.

---

## 4. WS-C — Text engine (`:ui-text`)

This is the largest *steady-state* perf gap and several correctness gaps.

### P1.1 — Cache the built paragraph; stop reshaping on paint · High · M
`SkiaParagraph.native.kt:238-257` → `ops.rebuildAndPaint(...)` →
`SkiaParagraphEngine.kt:120-123` calls `build()` **unconditionally**, which
rebuilds a `ParagraphBuilder`, re-pushes styles, re-adds text, and re-runs
`.layout(width)` (`SkiaParagraphEngine.kt:128-148`). **Every `paint()` of every
`Text` re-shapes** (HarfBuzz + bidi + line-break) each frame it's visible.

Upstream lays out **once** and caches the `SkParagraph` in `ParagraphLayouter`
(`ParagraphLayouter.skiko.kt` `paragraphCache`), re-running `.layout()` only when
width or a paint-affecting style changed, and `paint()` reuses the cached layout.
The reason ours rebuilds is that color/shadow/decoration arrive at *paint* time
(`rebuildAndPaint`) rather than baked at build — upstream mutates the existing
paragraph (`setColor`/`setTextStyle`) instead.

**Fix:** cache the built paragraph in `SkiaParagraphOps`; only re-layout on
width change; for color/shadow/decoration changes, update the foreground paint
without re-shaping. **Vendor `ParagraphLayouter.skiko.kt`** — this is the file
`SkiaParagraphEngine.build()` is a "reduced local version of" (its own header,
`SkiaParagraphEngine.kt:40-42`).

### P1.2 — Double-shape on measure · High · M (pairs with P1.1)
`NativeParagraphIntrinsics` (`ParagraphFactories.native.kt:47-57`) builds a
throwaway `SkiaParagraphOps` just to read min/max intrinsic widths
(`SkiaParagraphEngine.kt:248-256`), then the real `Paragraph` shapes *again*.
Every measured text shapes ≥ 2×. Upstream reuses one `ParagraphLayouter` for
intrinsics and final layout. Fold into the P1.1 layouter.

### P0.5 — `FontRasterizationSettings` never set · Med · S
`makeTextStyle` (`SkiaParagraphEngine.kt:189-218`) never sets `ts.fontEdging` /
`ts.fontHinting` / `ts.subpixel`. Upstream applies per-OS defaults to every
`SkTextStyle` (`ParagraphBuilder.skiko.kt:189-191`,
`FontRasterizationSettings.skiko.kt:77-110` — e.g. Windows AntiAlias+Normal+
subpixel, Linux AntiAlias+Slight). Text currently renders with skia's raw
defaults → blurrier/less-hinted, especially on Windows/Linux. Three assignments +
a per-OS constant; **vendor `FontRasterizationSettings.skiko.kt`.** Cheapest
visible-quality win.

### Reduced style coverage · Med · M
`makeTextStyle`/`build` omit features upstream's `ParagraphBuilder.skiko.kt`
applies: **lineHeight / `LineHeightStyle` trim**, **textIndent**,
**baselineShift**, **background span color**, **`TextGeometricTransform`**,
**localeList**, **brush/gradient text fill + drawStyle/blendMode** (our paint
path handles only `SolidColor`, `SkiaParagraph.native.kt:253-256`). lineHeight
especially is common. Fix by vendoring more of `ParagraphBuilder.skiko.kt`.

### Placeholders / inline content not built · Med · M
`SkiaParagraphOps` never calls `addPlaceholder`; `placeholderRects()` reads
`rectsForPlaceholders` (`SkiaParagraphEngine.kt:104-105`) but nothing adds one,
and `placeholders` is dropped at every factory (`ParagraphFactories.native.kt`).
`InlineTextContent` (chips in `Text`, `ClickableText`) renders no reserved space.

### Span segmentation drops partial overlaps · Low-Med · S
`appendWithSpans` (`SkiaParagraphEngine.kt:150-164`, `:159`) keeps only spans
fully covering a segment (`start <= segStart && end >= segEnd`) — partially
overlapping `SpanStyle`s vanish on boundary segments. This is the documented
span-model divergence (memory `b63-upstream-text-mingw.md`). Merge per-cut like
upstream.

### Stubbed `Paragraph` methods · Low-Med · S each
`getRangeForRect` hardcoded `TextRange.Zero` (`SkiaParagraph.native.kt:216-217`,
breaks rect-based selection); `isLineEllipsized` always `false` (`:155`);
`fillBoundingBoxes` no-op (`:224-226`, matches an upstream limitation — OK).

---

## 5. WS-D — Fonts (`:ui-text`)

Typeface caching itself is healthy (`SkiaFonts.baseCache` + `resolveCache`,
`SkiaFonts.kt:53-55`; no per-frame `makeClone`). The gaps are in *what can be
resolved*.

### P3.1 — No system/default fonts · High (correctness) · M
`baseTypeface()` (`SkiaFonts.kt:36,58-62`) resolves only via
`IconFont.bytesFor(family)`; anything unrecognised falls back to the single
bundled `NotoSans.ttf` (`:40-43`). Upstream resolves `GenericFontFamily`
(SansSerif/Serif/Monospace/Cursive) and named families through
`FontMgr.default.matchFamilyStyle(...)` with per-OS alias tables
(`PlatformFont.skiko.kt:373-420`). We *do* set
`fontCollection.setDefaultFontManager(FontMgr.default)` (`SkiaFonts.kt:48`) so
the shaper can glyph-fall-back for CJK/emoji — but there's no way to *select* a
system family by name, and `Serif`/`Cursive` are silently ignored. **Fix:** route
unknown/generic families through `FontMgr.default.matchFamilyStyle`; vendor the
`GenericFontFamiliesMapping` alias table from `PlatformFont.skiko.kt`.

### P3.2 — `FontListFontFamily` / resource `Font`s unsupported · High · L
`FontFamilyResolver.native.kt:38-51` returns `null` for `FontListFontFamily` and
a placeholder for everything else; `SdlPlatformFontLoader.loadBlocking` returns
`Unit` (`:30-34`). The standard upstream API — `Font(resource, weight, style)`,
`FontFamily(font1, font2, …)`, async `Font`s — is a no-op; real selection is
string-name lookup in `SkiaFonts`/`NamedFont` only. Async loading
(`AsyncFontListLoader`, `AsyncTypefaceCache`) is entirely missing. **Fix:** the
commonMain `FontListFontFamilyTypefaceAdapter.kt` is source-set-compatible —
vendor it to get weight-matching (static multi-file families) + async for free.
This also fixes the "`FontWeight` only maps to the `wght` axis" gap
(`SkiaParagraphEngine.kt:61-63,179`).

### Smaller font items · S each
- `NamedFont.equals/hashCode` ignore `axes`/`variationSettings`
  (`NamedFont.kt:40-48`) — two axis-differing fonts compare equal (latent cache
  collision once P3.2 lands).
- `resolveCache` is unbounded (`SkiaFonts.kt`) — bound it (upstream uses LRU 16).
  Folded into P3.4.
- Variation key passes `null` density (`SkiaFonts.kt:65,78`) → density-dependent
  axes (e.g. `opsz` in sp) resolve wrong; thread real density.

---

## 6. WS-E — Images (`:ui-graphics`)

Resource-image caching is *good* (`SkiaImageCache` caches decoded `Image`s by
path, caches decode failures, shares texture between measure+draw). Two real
gaps:

### P3.3 — SVG / XML-vector rasterised once at fixed size · Med · L
`SkiaImageCache.kt:46-54,101-112` rasterises SVG via `SVGDOM` into a fixed-size
offscreen and caches the *raster*; the resources path does the same
(`ImageResources.native.kt:33-37`). Scaled-up icons render blurry. Upstream keeps
a live `SVGDOM` and re-renders into a size-driven `DrawCache`
(`DesktopSvgResources.desktop.kt:60-137`) and parses `<vector>` XML into a
scalable `ImageVector` (`DesktopXmlVectorResources.desktop.kt`). **Fix:** vendor
`DesktopSvgResources.desktop.kt` (`SVGPainter` + `DrawCache`, portable); for XML
use the upstream parser front-ended by the project's pure-Kotlin `DomXmlParser`
(the `javax.xml` dep is the only blocker).

### P3.4 — Unbounded caches leak memory · Med · S-M
`SkiaImageCache` is a plain `HashMap<String, Image?>` (`SkiaImageCache.kt:32`) —
no eviction. A long-running app showing many distinct runtime images
(`registerMemoryResource`, `ResourceIO.kt:227-234`) grows GPU/CPU image memory
forever. Add LRU eviction. (Same treatment for `SkiaFonts.resolveCache`.)

### P3.5 — Missing public stream APIs · Low · M
`loadImageBitmap(InputStream)` / `loadSvgPainter` / `loadXmlImageVector` don't
exist; apps porting from Compose Desktop that call them won't compile. Vendor the
trivial `ImageResources.desktop.kt` `loadImageBitmap` + the SVG/XML painters from
P3.3.

### At parity (no action)
`drawImageRect` re-wraps the bitmap into a skia `Image` per draw
(`SkiaBackedCanvas.skiko.kt:315`) — but **upstream does exactly the same**; not a
regression. A future win (beating upstream) would cache the skia `Image` on the
`SkiaBackedImageBitmap`. Formats are at parity (both via `Image.makeFromEncoded`).

---

## 7. WS-F — Vendoring cleanup

Drift tripwire is green (8 `VENDOR-BASE` files, all match the pin
`COMPOSE_CORE_REF = v1.12.0-beta03+dev4483`). The opportunities:

### P4.1 — Reverse the two second-renderer-driven manual vendors · Med · S-M
Both local edits existed to serve *both* renderer legs; with one leg they may be
reversible:
- `GraphicsLayerOwnerLayer.kt:17-26` — dropped the trailing
  `SkiaGraphicsContext.setLightingInfo`; our shadows went via
  `NativeShadowCanvas`. Re-sync verbatim and let upstream RenderNode shadows +
  `setLightingInfo` back in (also kills the `drawDropShadow` paint alloc, WS-B).
- `LayerTransformationMatrix.kt:5-16` — a rename/relocation of
  `Matrices.skiko.kt`'s `prepareTransformationMatrix` to `nativeMain`; RENDERER's
  own "P1.5" says to reverse it once the Skia leg owns `Matrices.skiko`.

**Gate both on `verify-mac` + parity** (hit-test coord agreement, shadow
lighting). Follow-up: audit whether the `NativeShadowCanvas` / `NativePainterCanvas`
/ `ShapeClipCanvas` seams in `:ui-graphics/commonMain` can collapse now that only
the skiko canvas exists (`ShapeClipCanvas` is already dead — its own doc says the
Skia backend doesn't implement it; `NativeFinishableCanvas.finish()` is a no-op).

### P4.5 — Small vendoring wins · S each
- Vendor `Ripple.skiko.kt` (15-line thin actual; a DIAGNOSTIC GAP in
  `material3/compose-fork.txt`) instead of the project shim.
- material3 date/time: `PlatformDateFormat.native.kt:14-20` is an English-ISO
  stub (ignores locale + pattern → DatePicker/TimePicker wrong everywhere).
  Neither upstream actual is cross-platform on K/N — write a
  `kotlinx-datetime`-based formatter honouring the requested pattern/skeleton.
- Audit foundation `.native.kt` text/selection files that are byte-identical
  mirrors of `macosMain` (e.g. `CoreTextField.native.kt`,
  `TextFieldCoreModifier.native.kt`) → convert to `SET_FOLDER macosMain →
  src/vendor/native` directives so drift-tracking covers them.

### Not worth it (recorded so nobody re-investigates)
The `:ui-graphics`/`:foundation`/`:material3` DIAGNOSTIC GAPS are dominated by
`androidMain`/`desktopMain`/`webMain` actuals inapplicable to our targets or
already covered by project `.native.kt` actuals. Vendoring headroom is
concentrated in **text** (`:ui-text`, WS-C/WS-D) and the two reversible manual
vendors — not in graphics/foundation.

---

## 8. WS-G — `ComposeWindow.kt` decomposition (`:desktop-native-window`)

The file is 1131 lines mixing six responsibilities. **The bloat is mostly
organizational, not per-frame cost** — the perf items are in WS-A/WS-B; splitting
files won't move FPS by itself. But two extractions coincide with perf fixes and
should be done together:

- Per-window pump (`ComposeWindow.kt:217-236`) → a `WindowInstance.pumpAndRender()`
  returning `(rendered, vsync)`, so the P0.1/P0.2 pace logic lives in one place.
- `installGlobals()` (`:771-779`) calls `registerGenericFonts()` **every frame** —
  hoist to `init` (one-time).

**P4.2 extraction map** (mechanical, Low impact):

| Extract                                                                        | New file                                                      |
|--------------------------------------------------------------------------------|---------------------------------------------------------------|
| `WindowInstance` (SDL window + renderer + composition + events + FPS + render) | `WindowInstance.kt`                                           |
| `WindowArchitectureOwner`                                                      | `WindowArchitectureOwner.kt`                                  |
| `BackNavigationInput` + `dispatchTypedText`                                    | `WindowInputHelpers.kt`                                       |
| `FrameProfiler`                                                                | `FrameProfiler.kt`                                            |
| Virtual-frame/screenshot timing flags                                          | `FrameTiming.kt` (co-locate the P2.1 `DisplayRefresh` helper) |
| The 60-line composition-local seeding block                                    | `WindowCompositionLocals.kt`                                  |

Also reduce `pollEvents()` allocation churn (`SDL3EventMapper.kt:65-75` allocates
a fresh list + an `AppEvent` per event every iteration — GC pressure that feeds
the P0.7 GC nudge) by reusing a buffer.

### Optional larger convergence (evaluate, don't commit blindly)
Upstream's `FrameDispatcher` (skiko), `GlobalSnapshotManager.skiko.kt`, and
`FrameRecomposer.skiko.kt` encode exactly the invalidation-driven scheduling the
port hand-rolls. **P2.2** adopts the portable `GlobalSnapshotManager` pattern
(coalesced apply + write-observer-as-frame-trigger) — recommended.
`FrameRecomposer` is `@InternalComposeUiApi` and skiko-windowing-coupled; weigh
against RENDERER.md §8's "don't vendor the ComposeScene stack" non-goal. Treat
`FrameDispatcher` as a *reference* — our multi-window shared-SDL-queue loop
doesn't map 1:1 to its per-scope coroutine.

---

## 9. WS-H — Documentation debt

### P4.3 — RENDERER.md is stale (still two-renderer framing) · Low · S
Rewrite these specifically:
- Lines 13-21, 23-39, 55-60: drop the "Skia **leg** vs SDL leg" framing — there
  is one renderer now.
- Line 79 ("Skia `draw` … fell from 1.75 ms to 0.2 ms") compares against the
  removed SDL leg — mark historical, there's no live baseline.
- Line 192 (§7): where vsync-capping is treated as expected — call out the P0.1
  double-pacing bug instead.
- Add a new section: the current perf gap is the **frame driver + text reshape**,
  not the layer engine. Note the dead `DrawStats` profiler fields (also
  TOOLING.md).
- Reconcile lines 99-105 / 129: the "B6.3 done — upstream `SkiaParagraph`
  vendored verbatim" claim is an **overstatement**. Reality is a *reduced local
  port* (`SkiaParagraph.native.kt`, `SkiaParagraphEngine.kt`) — downgrade the
  claim or scope full skiko-text vendoring (WS-C) as an open L-item.

### P4.4 — Residual second-renderer language in code · Low · S
Comments/dead code referencing the removed leg: `ComposeOwner.kt:318`,
`SDL3Backend.kt:43`, `GraphicsContextFactory.kt:9`, `Popup.native.kt:30`
("per-renderer factory seam"), `GradientBridge.kt:20,23` (`renderer-sdl3`),
`DrawShape.kt:8,21` ("Skia or SDL3"), `demo/…/BrushScreen.kt:17`. Inline the
per-renderer seams (one leg → no abstraction needed) and fix comments.

---

## 10. Verification strategy

Per [TOOLING.md](TOOLING.md) — gate each workstream:
- **Any renderer / layout / text change** → `verify-mac` runbook before commit,
  and `scripts/parity/parity.py` (native-vs-JVM) as the broad net.
- **A specific interaction** (selection, scroll, drag) → `scripts/probe/`.
- **Perf claims** → `CDN_PROFILE=1` — **but land P0.3 first**, or the profiler
  reports zeros for draw work and can't confirm anything.
- **Memory items (P3.4, P0.7)** → watch RSS while navigating (issue #2 repro).
- **Font/image parity (P3.x)** → parity harness + eyeball scaled SVG/icons and
  Serif/CJK text.

Suggested measurement baseline before Phase 0: record FPS + `CDN_PROFILE=1`
frame breakdown on `:demo` (a scrolling `LazyColumn` screen) and `:apidemo`
(the drag-heavy screen), on GL (Windows/Linux) and Metal (macOS). Re-measure
after Phase 0 and Phase 1 — those two phases should recover most of the gap.

---

## 11. Expected impact summary

| Change             | Where the time goes today                                                       | After                     |
|--------------------|---------------------------------------------------------------------------------|---------------------------|
| P0.1 vsync         | ~30 FPS cap while animating (16 ms delay on vsync-blocked present)              | true refresh rate         |
| P1.1/P1.2 text     | every visible `Text` re-shapes (HarfBuzz+bidi+break) each frame, ≥2× on measure | shape once, cache         |
| ~~P0.6 Metal~~     | *(closed §12 — reacquire is inherent to Metal, costs nothing measurable)*        | no change                 |
| ~~P0.4 clear~~     | *(closed §12 — depended on P0.6; per-frame drawable can't skip its clear)*        | no change                 |
| P1.3/P2.2 snapshot | ~5 `sendApplyNotifications` per iteration                                       | ~1 per frame              |
| P0.5 raster        | skia raw defaults (blurry on Win/Linux)                                         | upstream per-OS hinting   |
| P3.1/P3.2 fonts    | non-bundled families → Noto Sans; no static weight/async                        | system + full resolver    |
| P3.3 SVG           | icons blurry when scaled                                                        | resolution-independent    |

**Do Phase 0 + Phase 1 first.** They are small, and they target the two things
that actually cost the gap — everything else is correctness/parity/maintenance
that can follow.

---

## 12. macOS Metal measurement (2026-07-29) — what the profiler actually shows

First real profiling pass on a Mac (Apple Silicon, ProMotion 120 Hz),
`:demo` `CDN_FORCERENDER=1` on Metal. Two things had to be fixed before any
number could be trusted:

**Build blocker (fixed).** `kotlin.incremental.native=true` in
`gradle.properties` broke the executable link on macOS (and would on any native
target): per-file native incremental compilation fails to emit a `value class`'s
box/unbox helpers into the declaring file's cache unit when only *other* files
box the value, so `ld` dies with undefined symbols
(`androidx.compose.material3.NavigationItemIconPosition`'s box constructor,
referenced from `ShortNavigationBar` / `WideNavigationRail`). The demo/apidemo
executable link had never run since those M3-expressive APIs were vendored
(Phase 0 verified on Windows only did klib compiles + `klibApiCheck`). Fix:
`kotlin.incremental.native=false` (comment in `gradle.properties`).

**Profiler blocker (fixed).** The `"  layout"` phase charged everything since
`"pump"`, which *includes* `ensureSize()` — where Metal's `nextDrawable()` blocks
on vsync. So the vsync wait read as ~6.7 ms of "layout." Split out a dedicated
`acquire` phase after `ensureSize` (`ComposeWindow.renderFrame`). This is the
completion of P0.3's intent — the profiler now tells the truth.

**Steady-state numbers (avg ms/frame, corrected profiler):**

| Screen     | acquire (vsync) | layout | draw | present | events | total real CPU |
|------------|:---------------:|:------:|:----:|:-------:|:------:|:--------------:|
| Counter    | 6.67            | 0.02   | 0.05 | 0.12    | 0.09   | ~0.33          |
| LazyColumn | 6.67            | 0.02   | 0.08 | 0.13    | 0.08   | ~0.34          |
| Full app   | 6.77            | 0.03   | 0.07 | 0.10    | 0.03   | ~0.28          |

RSS flat at ~108 MB over a 10 s force-render soak; **zero `nextDrawable`
starvation** messages.

**Conclusion.** On macOS/Metal there is **no steady-state perf gap** — the app
runs at the 120 Hz refresh with ~0.3 ms of real CPU work per frame; the rest is
correct vsync pacing (`acquire`). Real layout is 0.02–0.03 ms even for LazyColumn
and the full sidebar app, so the Phase-1 text-reshape work already landed
(P0.7/P1.1/P1.3) is not a macOS bottleneck, and the Metal-path items (P0.6/P0.4,
and a speculative autorelease-pool fix) buy nothing measurable — all closed. The
"performance not as good as the original" symptom, to the extent it remains, must
be chased on **Windows (GL)** or in a **specific heavy interaction** (measure
with the `acquire`-corrected profiler there), not in the macOS steady state.

---

## 13. Release-readiness pass toward 1.0.0 (2026-07-31)

Goal restated by the user: **stable, vendored as much as possible, as close to
Compose as possible.** Driven by the API-coverage tool + three parallel audits
(vendoring completeness, text-input/IME, true upstream divergences).

**Where we stand.**
- **API coverage vs upstream: 99%** (`compose-coverage.py`, 8665/8751 decls).
  Remaining misses are AppKit/UIKit/web host actuals (N/A for SDL desktop),
  `ImageComposeScene`/`renderComposeScene` (test util), and the `ui-text.platform`
  font layer (== the deferred P3.2 resolver).
- **Vendoring is already near-maximal**: zero rule-1 violations, zero manual-vendor
  drift, no vendorable DIAGNOSTIC GAPS. commonMain is 100% vendored; the only
  non-vendor `androidx.compose.*` files are documented manual vendors. The
  "vendor as much as possible" goal is effectively met.

**Closed this pass (commits):**
- `androidx.compose.foundation` **Scrollbar** vendored (Vertical/Horizontal +
  ScrollbarAdapter + v2 adapters) — exact upstream desktop API, +34 coverage decls.
- **Inline-content placeholders** — `Text`/`BasicText` `inlineContent` now reserves
  space (was collapsing); the divergence audit's top functional gap.
- **IME candidate window** positioned on focus-gain (not just first TEXT_EDITING).
- (plus §12's build/profiler fixes and the text/font/SVG/date parity work.)

**Verified NOT broken (audit corrected earlier assumptions):**
- Right-click **text context menu** (Cut/Copy/Paste/Select-All) already works —
  `isNewContextMenuInitiallyEnabled=false` on native, so the default path uses the
  real vendored `CommonContextMenuArea`. The no-op only affects the new API.
- **Text input** is fully wired (modern `PlatformTextInputModifierNode` + real SDL
  IME bridge: commit + preedit composition + candidate rect). Not the NoOp service.
- Overscroll / magnifier / selection-handle / `getRangeForRect` / `isLineEllipsized`
  no-ops all **match upstream desktop** — not gaps.

**Known 1.0 gaps left, with rationale (all deferred deliberately):**
| Gap | Why deferred |
|-----|--------------|
| **P3.2** resource-`Font`/async font resolver | Engine reads `TextStyle.fontFamily` directly, bypassing `FontFamily.Resolver`; rewiring the load-bearing font path is high-risk and the parity harness wouldn't exercise it. |
| **Drag OUT of the window** + drag ghost | Platform-limited: SDL3 has no portable "start OS drag" (needs NSDraggingSession/DoDragDrop/XDND per OS). Drop-IN works. Document as a known 1.0 limitation. |
| Per-focus `SDL_StartTextInput/StopTextInput` | Always-on start feeds the `dispatchTypedText` fallback; removing it risks that path for edge-case IME-state-leak correctness. |
| `CalendarLocale` real locale | No user-visible effect until `PlatformDateFormat` localizes month/weekday NAMES (needs ICU data we don't bundle); date PATTERN is already honored. |
| Semantics / a11y pipeline | Large; out of scope for a desktop 1.0. |
| P4.1 RenderNode shadows, P1.2, P2.2 | See §1/§7 — risky maintenance / non-goal / blocked, no parity or perf win. |

**Bottom line:** the port is at 99% upstream API coverage with maximal vendoring;
the remaining deltas are either platform-inherent (SDL drag-out), deliberately
project-owned (SDL/skiko bridges), or large-and-risky (font resolver, a11y). None
block a desktop 1.0 for the common app surface.

### 13a. Second hardening pass (2026-07-31, cont.)

Another fidelity/vendoring/perf sweep (coverage tool + two parallel audits):

- **Fidelity is clean.** The 1076 "extra" decls are dominated by umbrella-repo
  modules the coverage tool can't compare (resources, tooling-preview) and
  version-skew where our vendored ref is newer than the tool's upstream dump
  (material3's 467 — AppBarWithSearch/BasicAlertDialog/BottomAppBar are all
  vendored-verbatim, confirmed). No invented divergent public surface to remove.
- **Scrollbar fully vendored.** Migrated the demo to the vendored
  `androidx.compose.foundation.VerticalScrollbar` and **deleted** the ~200-line
  `com.compose.sdl.scrollbar` reimpl (verified the vendored one renders a
  correctly-sized thumb under Option-B density). apidemo keeps its app-level copy.
- **Vendoring is maximal** except one L-effort structural move: giving `:foundation`
  a `skikoRenderer` source set would unblock two real fidelity gaps at once —
  `StringHelpers.skiko` (ICU grapheme/word breaks vs our pure-K/N walker) and
  `DragAndDropSource.skiko` (cached drag ghost). Every other refused file is
  correctly refused (JVM/Darwin/AWT deps, iOS-only surface, or the architectural
  scene-layer reimpl). One convergence candidate remains: `LayerTransformationMatrix`
  dedup (== P4.1's Matrices half).
- **Perf: the double-shape (P1.2) is done** — measured cold text frames were
  15–23 ms dominated by shaping; every measured `Text` shaped twice (intrinsics +
  final). Now the intrinsics pass's shaped paragraph is reused for the final
  layout (re-break, no re-shape), halving per-measure shaping on scroll-in/nav.
  Force-render steady-state can't show it (no re-measure) and the cold frame is
  dominated by first-frame overhead, but it's upstream-faithful and pixel-identical
  in parity. Other perf items (synthetic-hover re-dispatch, event-loop allocation,
  per-frame shadow paint) left as evaluated-and-skipped: unmeasured micro-opts
  (events phase 0.03 ms, draw 0.08 ms) or a real stale-hover correctness risk.
