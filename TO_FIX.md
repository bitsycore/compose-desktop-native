# TO_FIX — large response body (~1000+ lines) truncates around line 955

Status: **OPEN**. The rendering/scroll optimization is done and committed (and is
correct + a big win), but a remaining bug truncates a tall response body in the
API Manager (`:apidemo`). Resume here on the other machine.

## Symptom (user, on Windows / SDL3 renderer)

Loading a ~1000-line HTTP response into the API Manager response viewer:

- The **line-number gutter stops at ~955** (no numbers past it).
- The **body text stops a little after** (~960).
- **You cannot see the full body** — the last ~45 lines are unreachable.

This was reported after the perf optimization (below) made the view fast enough
to actually scroll. Three fixes were attempted; the cap at ~955 persists in all.

## What IS fixed and committed (keep all of this — it works)

Per-frame the layout tree is fully measured + drawn every frame. A 1000-line
body was being fully re-wrapped (split on `\n`, one substring per line) in BOTH
the measure pass and the draw pass, plus fully drawn (1000 text blits) plus an
O(chars × spans) per-character colour scan. Fixed by:

1. **Node-level wrap+size cache** — `LayoutNode.layoutText(maxWidth)` +
   `textContentWidth()` + `textMeasuredHeight`
   (`core/.../androidx/compose/ui/node/LayoutNode.kt`). Caches the `WrappedText`
   keyed by `text` identity (+ width/size/family/tab/vars); measure and draw
   share one wrap. Content width is computed lazily (skipped for fillMaxWidth).
   `TextMeasurePolicy` (`core/.../foundation/text/BasicText.kt`) now calls it.
2. **Per-line culling in the text leaf** — both renderers skip drawing wrapped
   lines whose y falls outside the window. `Sdl3Renderer.kt` uses
   `backend.windowHeight`; `SkiaRenderer.kt`/`SkiaTextRenderer.kt` take a
   viewport height plumbed from `SkiaRenderBackend.draw` (`sdl.windowHeight`) via
   new `inWrapped`/`inViewTop`/`inViewBottom` params on Skia `drawText`.
3. **Per-line colour runs** — `lineColorRuns()` + `ColorRun`
   (`core/.../androidx/compose/ui/text/AnnotatedString.kt`) replace the
   per-character span scan in both renderers (O(spans + line length) per visible
   line). Old `spanColorAt` helpers removed from both renderers.
4. **Off-screen node culling** — `drawNode` in both renderers skips a node whose
   bounds are fully outside the window, *only* when it's a leaf or clips its
   children (`clipsChildren` helper) and isn't under a graphics-layer transform.
5. **Gutter is now a single multi-line `Text`** (`apidemo/.../Main.kt`,
   `BodyView`) — one `"1\n2\n…\nN"` string instead of one `Text` per line, so it
   goes through the exact same (working) path as the body. Right-aligned
   (`TextAlign.End`), `softWrap = false`. (Revert to centered if the look is
   unwanted — but keeping it is what proved the bug is NOT gutter-specific.)

Build is green: `./gradlew :apidemo:linkReleaseExecutableMingwX64`.
The body genuinely scrolls + culls correctly now (see trace below). renderer-skia
is NOT in the mingwX64 graph — review it by hand / build on mac/linux.

## CONFIRMED facts (from a file trace, since stdout is suppressed under the GUI subsystem)

Trace was added to the SDL3 body text-leaf logging once per ~120 frames. Output
while scrolling a real response:

```
BODY lines=1003 lh=17 absY=278    w=477 h=17051 winH=820 pxH=820 drawn=32
BODY lines=1003 lh=17 absY=-9518  w=477 h=17051 winH=820 pxH=820 drawn=50
BODY lines=1003 lh=17 absY=-12518 w=477 h=17051 winH=820 pxH=820 drawn=49
BODY lines=1003 lh=17 absY=-16268 w=477 h=17051 winH=820 pxH=820 drawn=47
```

Reading:
- Body is **1003 lines**, line height **17px**, node height **h=17051** (full,
  NOT capped) — so `LayoutNode` stores the full height fine.
- `winH == pxH == 820` → **DPR = 1** (NOT HiDPI). So any pixel limit is in
  *logical* px.
- `drawn` is ~32–50 → **per-line culling works** (only a screenful drawn).
- `absY` moved from +278 down to **−16268** → scrolling works and reached offset
  ≈ 16546.

The suspicious number: **955 × 17 ≈ 16235 ≈ 16384 = 2¹⁴**. The deepest `absY`
seen (−16268) and the cutoff (~16235) both sit right at **16384**.

## Key contradiction to resolve FIRST

At `absY = −16268`, the body's bottom is at `−16268 + 17051 = 783 < 820`, i.e.
line 1003 SHOULD be on screen — yet the user says it stops at ~955/960. So
either:

- (A) the trace's `−16268` was an **overscroll/fling transient** that snaps back,
  and the *steady-state* max scroll is only ~offset 15400 (≈ line 955). i.e. the
  **scroll `maxValue` / content height is capped at ~16384**, OR
- (B) there's a render-coordinate limit at ~16384 — but the culled single `Text`
  only ever draws at *small* y when scrolled, so this should NOT bite. (A) is far
  more likely.

Leading hypothesis: **the scrollable content height (response `Column`
`result.height`) or `ScrollState.maxValue` is being clamped near 16384 logical
px**, so the last ~48 lines can never be scrolled into view. Need to confirm.

## What's been ruled out

- `Constraints.Infinity == Int.MAX_VALUE` (no cap there). The scroll measures
  content with `maxHeight = Infinity` (`LayoutNode.measure`, the
  `childConstraints` block ~line 262), and sets
  `ScrollState.setMaxInternal(result.height − adjusted.maxHeight)`.
- Body node height = 17051 (full) — measure is NOT capping the body node.
- Per-line culling and the single-Text gutter — both done; cap persists, which
  means it's **not** node-count or a per-leaf SDL limit; it's in the **shared
  scroll/measure/content-height path**.
- No literal `16384`/`32767`/`Short`/`toShort` clamp anywhere in
  `core/src renderer-sdl3/src renderer-skia/src` (grepped).

## NEXT STEP — exact diagnostic to run first on the other machine

Re-add this trace to `renderer-sdl3/.../Sdl3Renderer.kt` to see which body lines
are actually visible at max scroll (and confirm whether scroll reaches line 1003
or caps ~955). Add the helper near the `kClearR` fields:

```kotlin
// TEMP diagnostic — stdout is suppressed under the GUI subsystem.
private var fTraceCount = 0
private fun traceLine(inMsg: String) {
    val vF = platform.posix.fopen("apidemo_trace.log", "a") ?: return
    platform.posix.fputs(inMsg + "\n", vF)
    platform.posix.fclose(vF)
}
```

…and in the text-leaf multi-line `else` branch, track first/last drawn index and
log it (replace the plain cull loop):

```kotlin
val vViewBottom = backend.windowHeight
var vFirstIdx = -1; var vLastIdx = -1
for ((idx, line) in vLines.withIndex()) {
    val vSlotTop = inNode.absoluteY + idx * vLineHeight
    if (vSlotTop + vLineHeight < 0 || vSlotTop > vViewBottom) continue
    if (vFirstIdx < 0) vFirstIdx = idx
    vLastIdx = idx
    textRenderer.drawText(line, inNode.absoluteX, vSlotTop, inNode.width, vLineHeight,
        inNode.textColor, inNode.fontSize, inNode.textAlign, inNode.fontFamily,
        inNode.fontVariationSettings, inNode.textSpans, vWrapped.lineStarts[idx])
}
if (vLines.size > 200 && (fTraceCount++ % 90 == 0)) {
    val vTag = vText.take(6).replace("\n", "|")
    traceLine("TXT '$vTag' lines=${vLines.size} lh=$vLineHeight absY=${inNode.absoluteY} " +
        "h=${inNode.height} winH=$vViewBottom visibleLines=${vFirstIdx + 1}..${vLastIdx + 1}")
}
```

Build `:apidemo:linkReleaseExecutableMingwX64`, run the exe, load the body,
**scroll fully to the bottom**, quit, read `apidemo_trace.log` (written to the
launch directory). The decisive line is the one with the most-negative `absY`:

- If `visibleLines` reaches `..1003` → the body IS fully reachable; the bug is
  cosmetic/gutter-only and re-examine the gutter alignment instead.
- If `visibleLines` caps at e.g. `..955` → **scroll `maxValue` is capped**. Then
  trace `ScrollState.maxValue` / the response `Column` `result.height` directly
  in `core/.../foundation/Scroll.kt` (`setMaxInternal`) and
  `LayoutNode.measure` (the `setMaxInternal(result.height - adjusted.maxHeight)`
  line) to find what clamps it near 16384. (commonMain trace: use `okio`, or add
  a tiny `expect/actual` logger, or temporarily route through a renderer hook.)

Then find/remove the ~16384 clamp (candidates: a `RowMeasurePolicy` /
`ColumnMeasurePolicy` cross-axis height constraint, an `Arrangement.arrange`
position clamp, or the scroll `maxValue` math). Once found, delete this trace.

## Key files / locations

- `apidemo/src/nativeMain/kotlin/Main.kt` — `BodyView` (~line 3030); gutter +
  body. Response viewer scroll: `Column(Modifier.fillMaxSize().verticalScroll(...))`
  (~line 2953).
- `core/.../foundation/Scroll.kt` — `ScrollState`, `setMaxInternal`, `maxValue`.
- `core/.../androidx/compose/ui/node/LayoutNode.kt` — `measure()` scroll handling
  (`childConstraints` ~262, `setMaxInternal` ~272); `layoutText` cache (~70–135).
- `core/.../foundation/layout/Row.kt` / `Column.kt` — measure policies (check the
  cross-axis height constraint passed to children for any clamp).
- `renderer-sdl3/.../Sdl3Renderer.kt` — `drawNode` (off-screen cull), text leaf
  (per-line cull) ~395.
- `renderer-skia/.../SkiaRenderer.kt` + `SkiaTextRenderer.kt` — same on Skia.

## Build / run

```bash
gradlew.bat :apidemo:linkReleaseExecutableMingwX64
# exe + data.kres at: apidemo/build/bin/mingwX64/releaseExecutable/
# compile-only verify of the mingw graph (core/material/window/renderer-sdl3):
gradlew.bat :apidemo:compileKotlinMingwX64 :demo:compileKotlinMingwX64
```
