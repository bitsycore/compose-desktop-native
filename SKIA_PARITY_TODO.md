# Skia renderer parity TODO

Work landed on the SDL renderer (Windows/mingwX64) that is **not wired on the
Skia renderer yet**. Written 2026-07-07 for the macOS/Linux session — the
skikoRenderer* source sets don't compile on Windows, so everything below is
either missing on Skia or was edited blind (marked COMPILE-UNVERIFIED).

Build/verify on the mac:

```bash
./gradlew :demo:runDebugExecutableMacosArm64            # default Skia (Metal)
./demo --screenshot=x.bmp --screen=AnnotatedString      # or via gradle --args
./gradlew :demo:runDebugExecutableMacosArm64 -Prenderer=sdl3   # SDL cross-check on mac
```

---

## 1. AnnotatedString span styles — PAINT side (HIGH, breaks visuals if skipped)

SDL commits `50fa0c2` + `ecd0011`. The run model and the MEASUREMENT are in
shared code, so **Skia builds already measure styled boxes** (per-run
fontSize/weight widen lines and heighten line boxes via
`SdlParagraph.native.kt` → `styledSliceWidth`/`styledLineCellHeight` in
`compose/ui/src/commonMain/.../text/ColorRun.kt`). Until the Skia PAINT path
catches up, mixed-size text on Skia gets a correctly-sized container with
base-size glyphs inside — worse than before. Port order matters; do this
first.

What `Sdl3TextRenderer.drawText` does now (mirror it in
`SkiaTextRenderer.drawText`, `compose/ui/src/skikoRendererMain/.../SkiaTextRenderer.kt`
around the `drawLine` helper):

- **Per-run fontSize**: `resolveRunPx(run, basePx, dpr)` (shared helper) →
  per-run font handle at that size. Skia side: `getFont(..., inSize = runPx)`.
- **Styled prefix advances**: segment x currently comes from
  `estimateTextWidth(line.substring(0, run.start))` at BASE style — replace
  with cumulative advances measured per run at ITS size/weight (walk runs
  left→right like the SDL path; do NOT re-measure prefixes at base).
- **Alignment width**: line width for Center/End = sum of styled run advances
  (`styledSliceWidth` works — it takes the `TextMeasurer`).
- **Common baseline for mixed sizes**: SDL centres the line's TALLEST run
  cell in the per-line band and puts every run's baseline on
  `top + (bandH - maxCellH)/2 + maxAscent`. Skia equivalent: font metrics
  ascent per run size.
- **Per-line band stacking**: `Sdl3Canvas.drawNativeText` stacks wrapped
  lines by `styledLineCellHeight(...)` instead of uniform lineHeight —
  `SkiaCanvas.drawNativeText` / SkiaTextRenderer's line loop needs the same
  (currently uniform `vLineH` per index).
- **SpanStyle.background**: fill rect behind the run — x = run start advance,
  w = run advance, full line band height.
- **underline / lineThrough**: SDL bakes TTF_STYLE bits; Skia: draw 1–2px
  rects from font metrics (underlinePosition/underlineThickness are on
  Skia's FontMetrics) in the RUN's colour, after the glyphs.
- **Base italic/decoration**: `SkiaCanvas.drawNativeText` now receives
  `inBaseItalic: Boolean, inTextDecoration: TextDecoration?` (added blind —
  **verify it compiles**, `SkiaCanvas.kt:202`) and currently ignores them.
  Forward into SkiaTextRenderer like Sdl3Canvas does (underline/lineThrough
  booleans + italic), then into `lineColorRuns(..., inBaseItalic =,
  inBaseUnderline =, inBaseLineThrough =)` — the shared run builder folds
  base flags into every run.
- Faux italic already exists on Skia (`skewX = -0.2f` when `run.italic`).
- Cache keys: whatever Skia caches per (family,size,variations) must include
  the run size; check `TypefaceKey` usage.

New `ColorRun` fields available to read: `background: Color`,
`underline: Boolean`, `lineThrough: Boolean`, `fontSize: TextUnit` — plus the
shared helpers `resolveRunPx`, `runVariations`, `spansAffectMetrics`,
`styledSliceWidth`, `styledLineCellHeight` (all in ColorRun.kt, commonMain).

Verify: `--screen=AnnotatedString` (all six cards: mixed colour+weight+
underline, weight sweep, per-run fontSize "small medium LARGE" hugging its
card, background tint, hyperlink, line-through) at `--height=1400`.

## 2. Offscreen ImageBitmap + ColorFilter tint (HIGH — ImageVectors blank on Skia?)

SDL commit `ca94be8`: the vendored VectorPainter/DrawCache rasterises
ImageVectors into an offscreen ImageBitmap and blits it back tinted. The
`OffscreenRenderer` hook (`compose/ui/src/nativeMain/.../graphics/Offscreen.kt`)
is registered by the SDL backend only — **Skia kept the stub**, so
material3-internal vector icons (SegmentedButton checkmark, dropdown/date
picker arrows, snackbar close…) presumably render blank on Skia builds.
Implement the hook with a Skia Surface (render-to-texture) or route
ActualImageBitmap/ActualCanvas to Skia Bitmap/Canvas natively, then check the
SegmentedButton checkmark on `--screen=ButtonsExtra`.

## 3. Blind edits to verify compile (from Windows sessions)

- `SkiaCanvas.drawNativeText` signature (+2 params) — this session.
- `SkiaTextRenderer` width-cache cap port (commit `37ecaeb`, PLAN_OPTIM
  phase-2 audit) — COMPILE-UNVERIFIED.
- `SkiaTextRenderer`/`SkiaDrawScope` `when(TextAlign/StrokeCap)` else-branches
  from the fidelity clean-vendor pass.

## 4. Runtime spot-checks accumulated for a Skia host (fidelity pass)

One demo run with screenshots covers all: colours (real sRGB decode — check
against SDL build), text sizing (TextUnit vendored), scroll, a Canvas stroke
(StrokeCap), Constraints packing in measure (no assert).

## 5. Not applicable on Skia (don't port)

- SDL_ttf variable-font axis patch (Skia honours axes natively).
- White-glyph colormod / texture LRU caches (SDL-specific perf).
- fix(ui.sdl) border-ring stroke series (Skia strokes shapes natively).
- SDL3FrameClock/BroadcastFrameClock fix, Dialog animations, PopupExitHandle —
  shared `:window`/`:ui` code, already active on Skia builds; just re-run the
  probe suite (`--dialoganimtest`, `--animvistest`, …) once on the mac.
