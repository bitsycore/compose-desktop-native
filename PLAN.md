# ROAD TO 1.0.0

**Objective:** get ComposeNativeSDL3 **as close to upstream Compose Multiplatform as
possible** — vendor every file we can instead of rewriting, stay performant, and look
faithful (ideally pixel-identical across macOS / Linux / Windows). The port is already at
99% upstream API coverage with maximal vendoring and no steady-state perf gap on
macOS/Metal; the remaining 1.0.0 work is **cross-platform rendering fidelity** (Windows
skiko-fork divergences), **completing native-actual stubs**, and the release mechanics
(1.12.0-stable re-pin + WIN-SMOKE).

**Status / handoff:** the macOS/Linux-verifiable work is done and committed on `main` (see
**"Landed toward 1.0.0"** below — don't redo it). The remaining blockers need a **Windows host**
→ jump to **"▶ Continuing on Windows (mingwX64) — START HERE"**. This file is self-contained: it
has the build/run commands, the exact confirm steps, the macOS baseline data to diff against, and
per-task fixes. So on Windows you can say **"continue PLAN.md"** and have everything.

## Definition of done for 1.0.0

- [x] No text tofu on any platform — Windows tab fix **CONFIRMED on-device** (§1a, 2026-08-04:
      literal `\t`→space, no `.notdef`); CJK / color-emoji / control chars render clean too (§1c).
- [x] Fork-vs-official divergence surface (FontMgr, gamma, ICU) **audited on native Windows**
      (2026-08-04): metrics == JVM (§1b), FontMgr fallback works (CJK + color emoji, §1c), ICU
      classification works (§1c). Net — the fork's Skia matches upstream; the only deltas are its
      thin extern-C bindings, handled in shared Kotlin (LineMetrics reconstruction). No fork-side
      fix needed for 1.0.
- [~] Native-actual fidelity blockers closed: float pointer coords **DONE**, screen-reader
      no-op **DONE**, text context menu **RECONCILED — already works** via the legacy path
      (§2). Date/time localization is P1 polish (formatter already works).
- [x] Vendor hygiene stays clean: zero drift (all 10 manual vendors match pin), zero
      commonMain rule-1 violations, zero vendored files touched this session — verified via
      `check-vendor-drift.py`.
- [~] SDL static lib slimmed to the used subsystem surface (§3) — **DONE + verified on
      macOS/Metal**; other hosts pending WIN-SMOKE.
- [ ] Refs re-pinned to Compose **1.12.0 stable**, JVM parity versions bumped, WIN-SMOKE
      fidelity pass green on a real Windows host.
- [x] `CLAUDE.md` documentation map consistent with tree — line-1 typo fixed; the stale
      historical docs `RENDERER.md` + `SKIKO-MINGW-FEASIBILITY.md` removed (current renderer
      essentials inlined into CLAUDE.md, the rest is in git history); all dead doc links
      (TODO.md / RENDERER.md / SKIKO) removed or repointed to PLAN.md.

## Landed toward 1.0.0 (this pass — all build + run verified on macOS/Metal)

Everything below is committed; details + file refs are in the sections that follow.

1. **Tab tofu fix** (§1a P0) — normalize `\t`→space before shaping so the Windows fork
   can't render `.notdef`. Platform-independent, length-preserving.
2. **HiDPI caret quantization fix** (§2 P0) — pointer/wheel coords carried as `Float`
   end-to-end instead of truncated to `Int`.
3. **Screen-reader no-op** (§2 P0) — `LocalPlatformScreenReader` defaults to inactive
   instead of throwing.
4. **SDL slimming** (§3) — 10 unused subsystems disabled in `build-all.py`; 6 now-dead
   macOS frameworks dropped from `sdl3.def`. Rebuilt; demo + apidemo link, demo runs.
5. **Context menu reconciled** (§2 P0) — confirmed already working via the vendored legacy
   path; the 3 "NOP" seams are vestigial (disabled new API) — misleading TODOs corrected.
6. **`CDN_TEXT_METRICS` diagnostic** (§1b) — env-gated line-metrics dump; macOS baseline
   captured, hypothesis sharpened (fork likely drops lineHeight leading).
7. **Doc hygiene** (§5) — removed the stale historical `RENDERER.md` +
   `SKIKO-MINGW-FEASIBILITY.md` (renderer essentials folded into `CLAUDE.md`; rest in git
   history), fixed the `CLAUDE.md` typo + all dead doc links; extracted the fork
   FreeType-scaler lead into §1c before deleting. Vendor drift verified clean.

**Windows pass (2026-08-04, this host — DPR 1).** On a real Windows host at last: (8) tab fix
**CONFIRMED on-device** (§1a); (9) vertical metrics **RESOLVED** — native `--metricsprobe` heights
== JVM `--metrics` exactly, feared "dropped leading" DISPROVEN (§1b); (10) fork `LineMetrics.
ascent/descent` mis-decode **FIXED in shared Kotlin** (`SkiaParagraphOps.lineMetrics()`, guarded
no-op on official skiko) — repairs Windows caret/selection; (11) fork FontMgr/ICU **AUDITED** — CJK
+ color-emoji + control-char fallback all work → the "empty FreeType stub" fear DISPROVEN (§1c);
(12) `FontRasterizationSettings.native.kt` verified to mirror upstream VERBATIM + given a
`VENDOR-BASE` provenance line; rasterization stays upstream-faithful per-OS (user decision). Net:
the fork's Skia matches upstream — the only deltas are its thin extern-C bindings, fixed in shared
Kotlin, **zero fork rebuild**.

**Still not done — WIN-SMOKE interactive + release** (§5): interactive caret-height check (the one
behavioral consequence of the LineMetrics fix), the `PrintWindow` probe, `compileCommonMainKotlin
Metadata`, apiDump + publish FROM Windows, and the Compose 1.12.0-stable re-pin. **Deliberately
deferred** (large/structural, rationale in-section): RTL, `PlatformFontLoader`, grapheme source-set
move, date CLDR, brush/gradient text — plus the big **vendor-the-upstream-skiko-text-engine
refactor (§6)**, sequenced after these fixes land.

---

## ▶ Continuing on Windows (mingwX64) — START HERE

**Read this before doing anything.** This file is the single source of truth for the road to
1.0.0. Everything in **"Landed toward 1.0.0"** above is **already implemented and committed on
`main`** — do NOT redo it, just verify it on this host. Every open `[ ]` / `[~]` below is
remaining work. The macOS/Linux side is done and verified; the remaining blockers need a **real
Windows host** — the mingwX64 build cross-compiled from mac/Linux fails at cinterop, and the
bitsycore skiko **fork** can only be rebuilt on Windows.

**The Windows fidelity reference is skiko-on-JVM Compose Desktop on THIS Windows host**
(`gradlew :demo:run` — also DirectWrite/system fonts), NOT macOS. mac↔Windows metric deltas are
expected in upstream Compose Desktop too, so "matches Windows-JVM" is the acceptance bar, not
"matches macOS".

### Build / run (Windows shell, repo root)

```bat
gradlew.bat :demo:runDebugExecutableMingwX64      :: native demo (the port under test)
gradlew.bat :apidemo:runDebugExecutableMingwX64   :: native apidemo (has the Session/Override buttons)
gradlew :demo:run                                 :: JVM Compose Desktop = the FIDELITY REFERENCE
gradlew :apidemo:run                              :: JVM apidemo reference
```

- The bridge plugin auto-drops `skiko-windows-x64.dll` next to the exe (`installWindowsSkiaDll`).
  Fork coords `com.bitsycore.skiko:skiko:0.150.1-mingw.1` (override `-PskikoMingwVersion`).
- `:demo` CLI flags (native AND jvm): `--screen=<Name>`, `--screenshot=<path.bmp>` (capture at
  quiescence then quit), `--gpu=auto|software|skia.opengl`. `CDN_TEXT_METRICS=1` env var dumps
  text metrics (§1b). `CDN_PROFILE=1` frame profiler. `:apidemo` has NO screenshot CLI — drive it
  by hand for the button-metrics visual.
- If an IC-cache error appears after any module churn: delete `demo\build\kotlin-native-ic-cache`.

### ✅ Windows session results (2026-08-04, this host — DPR 1.0)

Items 1–3 below are **DONE + verified on the shipped mingwX64 demo binary**; findings folded into
§1a/§1b/§1c. Only 4–5 (WIN-SMOKE interactive gate + apiDump/publish) remain. TL;DR:

- **§1a tab fix — CONFIRMED.** `--screen=BasicText` with `"start>a\tb<end\tcols\tand\tthere"`
  rendered `start>a b<end cols and there` — every `\t` a SPACE, zero `.notdef`. (Structural proof
  too: the whole module has exactly 2 `.addText(` sites, both `shapedText`.)
- **§1b vertical metrics — RESOLVED, the feared bug is DISPROVEN.** Native `--metricsprobe` vs JVM
  `--metrics` (same host, NotoSans, density 1): **every paragraph height + non-M3 baseline matches
  JVM EXACTLY** (size 11→24). Leading is NOT dropped. Two residual deltas found (details in §1b):
  (a) the fork's `LineMetrics.ascent/descent` are mis-decoded — **fixed in Kotlin** this session;
  (b) M3 baseline drifts ≤0.77px (halfLeading, a shared-engine omission on ALL platforms, sub-pixel).
- **§1c FontMgr/ICU — the "empty FreeType stub" fear is DISPROVEN.** Native render of CJK
  (你好世界 日本語 한국어) + **full-COLOR emoji** (😀🎉🚀❤) + control chars (nbsp/zwsp/en-dash) = zero
  tofu. Color emoji + CJK fallback prove `FontMgr.default` is a real DirectWrite-backed system
  manager, not an empty custom FreeType one. Remaining §1c work is doc-only (capture `args.gn`).

Reference commands actually used (both probes print `metrics:` lines; native runs a real window):
```bat
demo\build\bin\mingwX64\debugExecutable\demo.exe --metricsprobe    :: native/fork metrics
gradlew :demo:run --args="--metrics"                               :: JVM/upstream metrics
set CDN_TEXT_METRICS=1 && ...demo.exe --screen=Buttons             :: raw per-paragraph dump
```
(The JVM leg's screenshot flag is `--screenshot-all=<dir>`, not the native `--screenshot=<file>`.)

### Ordered Windows work (do top-to-bottom; full detail in the numbered sections below)

1. **[§1a] Confirm the tab fix — ✅ DONE** (see session results above). Rendered a literal-tab
   string on the fork; all `\t`→space, no tofu. The `SkiaParagraphEngine.shapedText` normalization
   holds; no bypass path exists (2 `.addText(` sites, both `shapedText`).

2. **[§1b] Confirm/resolve vertical metrics — ✅ RESOLVED (feared bug DISPROVEN).** Used the
   `--metricsprobe` (native) vs `--metrics` (JVM) pair, NOT the screenshot diff (the JVM leg has no
   `--screenshot=<file>`; it dumps the same `metrics:` table). Every paragraph HEIGHT matches JVM
   exactly (leading applied identically) → correct-by-reference. Only a ≤0.77px M3-baseline drift
   remains (halfLeading, all platforms). Separately fixed the fork's broken `LineMetrics.ascent/
   descent` in Kotlin. Full data in §1b.

3. **[§1c] Audit the fork FontMgr / gamma / ICU — ✅ AUDITED (fallback WORKS).** CJK + color emoji +
   control chars all render with zero tofu on the fork → `FontMgr.default` is a real system
   (DirectWrite) manager with working glyph fallback, NOT an empty FreeType stub. Remaining: capture
   the fork's REAL `args.gn` (`SK_GAMMA_*`, ICU packaging, freetype-vs-dwrite) into the fork repo so
   drift is auditable (doc-only, not a code blocker).

4. **[§5] WIN-SMOKE gate** (pre-ship): (1) the §1b metrics dump, (2) tab/control-char render clean,
   (3) the Windows-only `PrintWindow` probe (`scripts/probe/`), (4) `gradlew
   :<module>:compileCommonMainKotlinMetadata` (only the Windows job compiles common metadata).

5. **[§5] apiDump + publish FROM Windows** (host-specific; only the Windows job carries the full
   mingwX64 variant table). Then, when Compose **1.12.0 stable** ships, do the re-pin + version
   bump. Release runbook: [TOOLING.md](TOOLING.md).

---

## 1. Cross-platform FIDELITY (headline)

All Windows divergences live **below Kotlin** — the mingw source set reuses
`src/skikoRendererMain/kotlin` byte-for-byte (`compose/ui/ui/build.gradle.kts:102-113`),
the only mingw-unique file is `PlatformGpu.mingw.kt` (13 lines, GPU only). So every
divergence is in the **fork's Skia binary**, in **`FontMgr.default`'s per-OS impl**, or in
the **GPU backend**. The fork is external (`bitsycore/skiko`,
`com.bitsycore.skiko:skiko:0.150.1-mingw.1`) and its GN args are **not verifiable from this
tree** — recovering them is a prerequisite for several fixes below.

**Fidelity reference for Windows is skiko-on-JVM Compose Desktop on Windows** (also
DirectWrite), NOT macOS/Linux. macOS↔Windows metric deltas partly exist in upstream
Compose Desktop too (per-platform font backends). Reframe V-items as "match JVM-Windows"
acceptance tests, not "bugs vs macOS".

### 1a. Windows tab-character tofu — HIGH CONFIDENCE (root cause traced)

Kotlin sets `ParagraphStyle.replaceTabCharacters = true`
(`compose/ui/ui-text/src/skikoRendererMain/kotlin/.../renderer/skia/SkiaParagraphEngine.kt:234-236`,
exact mirror of upstream `ParagraphBuilder.skiko.kt:625`, CMP-6589). Works on
macOS/Linux (official skiko honors the flag → `U+0009` replaced by space before shaping).
On Windows the same call runs against the fork's flat extern-C DLL, which evidently does
**not** wire the setter → raw `\t` reaches HarfBuzz → NotoSans has no tab glyph → `.notdef`
box. Font fallback is a **red herring**: no font has a tab glyph, only tab→space
replacement cures it.

- [x] **P0** Confirm — ✅ DONE (Windows 2026-08-04): rendered a literal-tab string on the fork
      (`--screen=BasicText`); every `\t` shows a SPACE, no `.notdef`. The fork renders everything
      else clean too, so tab→space was the only gap. Structural backstop: the module has exactly 2
      `.addText(` sites, both `shapedText` — no raw tab can reach skiko.
- [x] **P0** Fix (ship now, author project code): in
      `.../SkiaParagraphEngine.kt`, feed skiko a normalized copy — added
      `private val shapedText = if (text.indexOf('\t') >= 0) text.replace('\t', ' ') else text`,
      used at `addText` (no-span path) and via `shapedText.substring(segStart, segEnd)`
      (span path); `text` kept for ALL length/index queries (`getRectsForRange`,
      `getCursorRect`, `wordBoundary`, `lineMetrics`, span cut-points). Length-preserving so
      every offset consumer stays correct; this is exactly what Skia's flag does internally →
      upstream-faithful; makes behavior platform-independent (no fork rebuild needed).
      Kept `replaceTabCharacters = true` (harmless/redundant elsewhere).
      **DONE** — compiles clean on macosArm64. Windows tofu render still needs on-device
      confirmation (WIN-SMOKE §5).
- [ ] **P2** Long-term true fix (fork, out of tree): wire
      `ParagraphStyle::setReplaceTabCharacters` through the fork's extern-C surface,
      rebuild/republish `skiko-windows-x64.dll`, bump `-PskikoMingwVersion`. Do this *in
      addition to* the Kotlin fix, not instead — it keeps the port thin. Can't be verified
      in-tree.

### 1b. macOS-vs-Windows vertical text metrics — ✅ RESOLVED (Windows 2026-08-04): heights match JVM, feared bug DISPROVEN

**WINDOWS RESULT (2026-08-04).** All targets render through the SAME Skia, so metrics are expected
to be similar — and they are. Native `--metricsprobe` (fork) vs JVM `--metrics` (upstream skiko) on
this host (NotoSans, density 1) print IDENTICAL paragraph heights and non-M3 baselines for every
size 11→24 (`cell`/`one`/`three`/`oneM3`/`threeM3`, `base1`/`base3` all match). The fork does NOT
drop lineHeight leading — the hypothesis below is disproven. Two small deltas remained, both
handled in SHARED Kotlin (no fork rebuild, keeping fork-reliance minimal):

  1. **Fork `LineMetrics.ascent/descent` mis-decode → FIXED in Kotlin (this session).**
     `CDN_TEXT_METRICS` showed the fork's per-line `ascent`/`descent` constant (18.837/5.163)
     regardless of `fontPx`, while `baseline`/`height`/`width` are correct (mac scales them:
     37.674@30px). This is the fork's flat extern-C LineMetrics binding, NOT a Skia difference.
     Lines stack contiguously so true ascent = `baseline - lineTop`; `SkiaParagraphOps.lineMetrics()`
     now rebuilds ascent/descent from the reliable baseline+height when they're internally
     inconsistent (guard `|ascent-(baseline-lineTop)|>0.5` = no-op on official skiko). Repairs caret
     height / getLineTop/Bottom / vertical hit-test on Windows. **Behavioral caret check → WIN-SMOKE.**
  2. **M3 baseline drift ≤0.77px (halfLeading) — SHARED-engine omission, all platforms.** `base1M3`
     differs native-vs-JVM by ≤0.77px in the 11→24 sweep (size 14 → 0.28px, sub-pixel), 2.19px in
     the contrived `boundary 24/25` case. Root cause is NOT the fork: `SkiaParagraphEngine` maps
     `LineHeightStyle.trim`→HeightMode but never wires `halfLeading`/`LineHeightStyle.Alignment`, so
     macOS-native drifts from JVM by the same amount (parity tolerated it as sub-pixel). Fixable in
     shared Kotlin (`textStyle.halfLeading` from the alignment); deferred pending a cross-platform
     parity re-check (can't run mac parity from a Windows host).

Native-fork metricsprobe vs JVM `--metrics` (this host) — identical except `base?M3`:
```
size=14 lh=20 cell=19 one=19 three=59 base1=14.898 base3=54.898 oneM3=20 threeM3=60 base1M3: 15.682 fork / 15.398 jvm
size=24 lh=30 cell=33 one=33 three=93 base1=25.968 base3=85.968 oneM3=30 threeM3=90 base1M3: 23.546 fork / 24.312 jvm
boundary 24/25 m3=25  base: 19.622 fork / 21.812 jvm
```

The vertical-layout code path is **identical** on all targets. The lineHeight/HeightMode
path is a byte-faithful port of upstream and is NOT the cause: m3 `labelLarge` has
lineHeight 20sp > fontSize 14sp with `LineHeightStyle.Default.trim == Both` →
`HeightMode.DISABLE_ALL`, which collapses the single-line box to `fontAscent + fontDescent`
of the font itself (`SkiaParagraphEngine.kt:244-249`, matches upstream
`ParagraphBuilder.skiko.kt:631-646`). So box height is driven entirely by the font's
reported ascent/descent.

Root cause: **`FontMgr.default` resolves to a different Skia font scaler per build** for
the *same* `font/NotoSans.ttf` bytes (`SkiaFonts.kt:38,44,50`) — CoreText on macOS,
fontconfig/FreeType on Linux, DirectWrite (or FreeType, unverified) in the fork on Windows.
Different backends select different metric tables (hhea `ascender/descender` vs OS/2
`sTypoAscender/Descender` vs `usWinAscent/Descent`) and honor the OS/2 `USE_TYPO_METRICS`
fs_selection bit differently. NotoSans is the textbook case (large top-heavy ascent).
`SkiaParagraphEngine.kt:147-148` read `-defaultFont.metrics.ascent`/`.descent`;
`:150-169 lineMetrics()` forwards skiko's numbers verbatim; `SkiaParagraph.native.kt:116,148-153`
consume them. Contributors V2 (hinting Normal on Windows grid-fits vertically, CoreText
ignores it) and V3 (subpixel/edging) are secondary and per-scaler.

**Hypothesis, not confirmed:** whether Windows-native actually *diverges from
Windows-JVM*. If it matches JVM-Windows it's correct-by-reference (not a bug), and the
macOS↔Windows delta is expected upstream behavior.

- [x] **P0** Confirm FIRST — ✅ RESOLVED (Windows 2026-08-04; heights == JVM, see WINDOWS RESULT
      above). Diagnostic **LANDED**: `SkiaParagraphEngine.kt` dumps
      `defaultFont.metrics.{ascent,descent,leading}`, `paragraph.height`,
      `lineMetrics[0].{ascent,descent,baseline,height}`, `familyName` + `fontPx` per built
      paragraph when `CDN_TEXT_METRICS=1` (mirrors the `CDN_PROFILE` env-flag convention).
      Run `CDN_TEXT_METRICS=1 <app> --screen=Buttons` (native) and the JVM parity app on the
      SAME host, diff the lines. macOS baseline captured (see below). **REMAINING (Windows
      host):** compare Windows-native vs **skiko-JVM-Windows**. Expectation: `fontPx` +
      lineHeight + heightMode identical, `metrics.ascent` differs by scaler.
      **Acceptance test:** Windows-native == skiko-JVM-Windows (→ correct-by-reference, not a
      bug); if they differ, it's a fork scaler fix (next item).

      **macOS baseline captured (`--screen=Buttons`, DPR 2):** for the fontPx=30 button
      labels ("Filled Button" / "OutlinedButton" / "TextButton"): font metrics
      `ascent=-32.07 descent=8.79 leading=0` (font box = **40.86px**), but the line box is
      `line0[ascent=37.67 descent=10.33 height=48.0]`, `paraHeight=48`. So macOS DOES apply
      the lineHeight leading (48 > 40.86) and distributes it **asymmetrically — ~5.6px above,
      ~1.5px below** → exactly the "extra space on top of the text" the report describes.
      **SHARPENED HYPOTHESIS:** this is NOT a mac bug — it's correct lineHeight distribution.
      The likely Windows story is the MIRROR of §1a: the fork's flat extern-C surface doesn't
      wire the `ParagraphStyle` height/strut path, so Windows renders **tight to the font box
      (~41px, no leading)** = the "fitted to text" look. **Sharpened WIN-SMOKE check:** for the
      SAME label, compare `paraHeight` + `line0.height`. If macOS/JVM = 48 but Windows-native
      ≈ 41, the fork drops lineHeight leading (fork fix: wire `heightMode`/strut, like
      `replaceTabCharacters`); a Kotlin fallback is a manual `StrutStyle`. If Windows-native
      == Windows-JVM, it's correct-by-reference.

      **macOS baseline raw lines** (`CDN_TEXT_METRICS=1 demo --screen=Buttons`, DPR 2) — diff
      Windows-native + Windows-JVM against these; the fontPx=30 rows are the cleanest signal:
      ```
      text='Filled Button'  fontPx=30.0  ascent=-32.070007 descent=8.789978 leading=0.0  paraHeight=48.0  line0[ascent=37.674 descent=10.326 baseline=37.674 height=48.0]
      text='OutlinedButton' fontPx=30.0  ascent=-32.070007 descent=8.789978 leading=0.0  paraHeight=48.0  line0[ascent=37.674 descent=10.326 baseline=37.674 height=48.0]
      text='TextButton'     fontPx=30.0  ascent=-32.070007 descent=8.789978 leading=0.0  paraHeight=48.0  line0[ascent=37.674 descent=10.326 baseline=37.674 height=48.0]
      text='Outlined'       fontPx=28.0  ascent=-29.932007 descent=8.203980 leading=0.0  paraHeight=40.0  line0[ascent=31.395 descent=8.605  baseline=31.364 height=40.0]
      ```
      (family='Noto Sans' on all.) Expectation if correct: `fontPx`, `paraHeight`, `line0.height`
      match JVM-Windows exactly; the raw `ascent`/`descent` may differ from these macOS numbers
      because the Windows scaler is FreeType (§1c), but the LINE BOX must still match JVM-Windows.
- [x] **P1** ~~If Windows-native != Windows-JVM: fix in the fork~~ — **N/A (2026-08-04):** heights
      MATCH JVM-Windows exactly, so no fork scaler/FontMgr change is needed for vertical metrics.
      The `makeFromData` typefaces already get the same metric-table selection as skiko-JVM-Windows.
      The one residual (M3 halfLeading) is a shared-Kotlin fix, not a fork one.
- [ ] **P2** (Optional, STRONGER than upstream) If cross-platform pixel-identity is
      wanted over matching each host's stock Compose Desktop: build a single
      FreeType/`SkFontMgr_Custom_Empty`-backed `SkFontMgr` used on ALL native targets for
      the bundled fonts, so NotoSans yields identical ascent/descent everywhere.
      Intentional departure from per-platform upstream behavior — decide deliberately.
- [ ] **DO NOT** "fix" by injecting a default `LineHeightStyle` or clamping ascent in
      `SkiaParagraphEngine.kt` — that desyncs from upstream `DISABLE_ALL` and masks, not
      resolves, the metric divergence.

### 1c. Other fork-vs-official divergences

- [x] **P1** ~~`FontMgr.default` may be an empty/stub manager on the fork~~ — **DISPROVEN
      (Windows 2026-08-04).** Native render of CJK (你好世界 日本語 한국어) + **full-COLOR emoji**
      (😀🎉🚀❤) + control chars = zero tofu. Color emoji + CJK fallback are impossible with an empty
      custom FreeType manager, so the fork's `FontMgr.default` IS a real DirectWrite-backed system
      manager with working glyph enumeration/fallback. No fork change needed. `SkiaFonts.kt` wiring
      stays as-is.
- [x] **P1** `FontMgrWithFallback` — ANALYZED + fallback now VERIFIED on Windows. The port
      does `setDefaultFontManager(fontMgr)` [system] + `setAssetFontManager(provider)`
      [bundled] (`SkiaFonts.kt:49-52`). This is **functionally equivalent** for missing-glyph
      fallback on macOS/Linux/Windows-JVM: skiko's shaper tries the asset provider (bundled
      Noto + aliases) first, then the default/system manager for CJK/emoji fallback, which
      resolves because the system FontMgr has those fonts. Rewriting to
      `setDefaultFontManager(FontMgrWithFallback(provider))` risks a macOS fallback
      regression with no observable gain, and I can't visually verify CJK/emoji fallback on
      this host. **The real gap was thought to be Windows-fork-only** (fork `FontMgr.default`
      empty stub → no system fallback) — but that's **DISPROVEN (2026-08-04):** CJK + color-emoji
      fallback works on native Windows too (item above). So the setDefault/setAsset wiring is fine
      on ALL targets. **Decision:** leave the Kotlin as-is; nothing to fix in the fork here. Re-open
      only if a CJK/emoji fallback bug is actually observed.
- [x] **P2** ~~ICU/unicode data packaged differently in the fork~~ — **VERIFIED OK (2026-08-04).**
      Rendered U+00A0 (nbsp → visible space), U+200B (zwsp → zero-width/collapsed), en-dash, and
      U+0009 (tab, pre-replacement) on native Windows: all classified/handled correctly, no tofu.
      The force-exported `uloc_*_skiko` symbols do their job; skunicode classification works. (This
      is also why HarfBuzz shaping + skparagraph bidi work at all — ICU data is present via
      icudtl.dat.)
- [~] **P2** Text gamma/AA edges: a Skia **build-time constant**
      (`SK_GAMMA_EXPONENT`/`SK_GAMMA_CONTRAST`/`SK_GAMMA_APPLY_TO_A8`), NOT a GL-vs-Metal
      runtime difference. **Note (2026-08-04):** native Windows text AA looked clean in the render
      (no visible AA anomaly), so no fork gamma fix is pursued for 1.0. Still worth capturing the
      fork's values in the fork repo (see the args.gn item below). **Kotlin-side rasterization
      settled:** the `RASTER_EDGING/HINTING/SUBPIXEL` in `SkiaParagraphEngine` come from
      `FontRasterizationSettings.PlatformDefault`, which was verified (2026-08-04) to reproduce
      upstream Compose Desktop's per-OS defaults VERBATIM (Win/mac=Normal hinting, Linux=Slight).
      **User decision:** keep the upstream-faithful per-OS behavior (match each platform's stock
      CMP), NOT a forced uniform cross-platform look. So there is no SDL_TTF-era gamma hack left to
      strip — Skia is configured exactly as upstream does it.
- [x] **GL-vs-Metal AA/gamma/color-space — RULED OUT as a primary cause.** Both bridges
      create the surface `colorSpace = null` (`SkiaGLBridge.kt:68-74`,
      `SkiaMetalBridge.kt:133-139`) — identical un-color-managed legacy blending. Only
      deltas are cosmetic-correct: `RGBA_8888`/`BOTTOM_LEFT` (GL) vs `BGRA_8888`/`TOP_LEFT`
      (Metal) — channel order + Y-flip, matched to buffers. Windows uses OpenGL
      (`PlatformGpu.kt:20`), same backend as Linux, so GL-vs-Metal can't explain a
      Windows-vs-Linux gap at all. Deprioritized — no action.
- [~] **P1** The fork's Skia build config (GN args). **Lead extracted from git history**
      (the old `SKIKO-MINGW-FEASIBILITY.md`, deleted as stale — its build recipe lives in
      `git show bdb5c64d^:SKIKO-MINGW-FEASIBILITY.md`): the fork's Route-1a recipe builds Skia
      with **`skia_use_freetype`**, i.e. the Windows text scaler is **FreeType**, not
      DirectWrite and not macOS CoreText — a concrete reason the SAME NotoSans bytes yield
      different ascent/descent on Windows (different metric-table selection) AND why fork
      `FontMgr.default` may not enumerate Windows system fonts for glyph fallback (FreeType
      has no system fontmgr without fontconfig). **CAVEAT:** that's the *recommended recipe*,
      not a verified dump of the shipped `0.150.1-mingw.1` GN args. **REMAINING:** whoever
      rebuilds the fork should capture the real `args.gn` (`SK_GAMMA_*`, ICU packaging,
      freetype-vs-dwrite) alongside the fork sources so drift is auditable — NOT as a doc in
      this repo (kept lean); a comment in the fork repo or `build-sdl.properties`-style pin.

---

## 2. VENDORING & upstream-fidelity debt

Vendor hygiene is **release-ready**: `check-vendor-drift.py` reports all 10 manual vendors
match pin `v1.12.0-beta03+dev4483`; zero commonMain rule-1 violations (the 3 authored
`androidx.compose.*` files in commonMain are all provenance-tracked Rule-3 vendors). The
macosMain DIAGNOSTIC GAP families (`ui/ui` 12, `foundation` 23: `CoreTextField.macos.kt`,
`SelectionManager.macos.kt`, `PlatformClipboard.macos.kt`, …) are **correctly excluded**
(AppKit/NSView; the port uses SDL) — keep them in gaps. **Do not spend 1.0.0 effort
re-vendoring.** The debt is **completing native-actual stubs**.

### P0 — blocks fidelity/correctness

- [~] **P0** Text context menu (right-click copy/paste/select-all) — **RECONCILED: already
      works, no reimplementation.** Static trace confirmed the menu is wired end-to-end
      through the vendored LEGACY path: text-level `ContextMenuArea` (`ContextMenu.native.kt`)
      → `CommonContextMenuArea` (`vendor/common/.../text/CommonContextMenuArea.kt`) → (native
      `ComposeFoundationFlags.isNewContextMenuEnabled = false`, so the legacy branch) →
      `contextmenu.ContextMenuArea` → `contextMenuGestures`/`onRightClickDown`
      (`isSecondaryPressed`) → `ContextMenuPopup` → `Popup.native.kt` (hosted by
      `LocalPopupHost` at the app root). Items are real: `TextFieldSelectionManager` /
      `SelectionManager.contextMenuBuilder` emit Cut/Copy/Paste/SelectAll with real actions.
      SDL right-click → `PointerButton.Secondary` (`SDL3EventMapper.kt`) →
      `isSecondaryPressed` (`PointerEventBridge`). The three "NOP" seams belong to the
      DISABLED *new* context-menu API and are unreachable — **DONE:** their misleading
      `TODO(CMP-7819)` comments corrected to say so, in all three files.
      **REMAINING:** an interactive right-click smoke check (no headless driver here — fold
      into manual/WIN-SMOKE). Known limitation, not a breakage: Paste enablement is
      plain-text-only (`ClipboardPasteState.hasClip = hasText`, see P2 below).
- [ ] **P1** DatePicker/TimePicker localization — VERIFIED current state:
      `material3/.../internal/PlatformDateFormat.native.kt` is a real kotlinx-datetime
      formatter that DOES honor CLDR patterns/skeletons (renders "Jul 29, 2026" /
      "July 2026" correctly — the agent's "always yyyy-MM-dd" finding was stale). Genuine
      remaining gaps, all narrower than a correctness blocker: field NAMES English-only
      (`:29 weekdayNames`, `:147-154 MONTH_NAMES/ABBR` — no CLDR data bundled), `:27
      firstDayOfWeek=1` fixed Sunday, `:67 is24HourFormat()=true` fixed, `:46 parse()`
      ISO-8601-only. Upstream `darwinMain` uses `NSDateFormatter` (not portable). **Fix
      (author):** bundle a CLDR subset or K/N i18n lib for localized names + locale-aware
      first-day/24h; also unblocks `CalendarLocale.native.kt:20` (fixed `"en"`). Ships
      readable English dates today — polish, not a P0 gate.
- [x] **P0** Float pointer coordinates — `SDL3EventMapper.kt` `.toInt()`-truncated SDL's
      Float `mb.x`/`mb.y`/`mm.x`/`mm.y` and the wheel `mouse_x/y` before the DPR multiply in
      `ComposeWindow.kt`. On 2× displays a click at logical 100.9 → physical 200 not ~201,
      quantizing the caret to 2px steps near glyph edges. **DONE:** widened
      `LegacyPointerEvent.x/y` and `AppEvent.MouseWheel.x/y` to `Float`, dropped the
      `.toInt()` at the three mapper construction sites, and removed the now-redundant
      `.toFloat()` at the two `ComposeWindow` read sites. Consumers fully contained (verified
      by grep). Builds + runs clean on macosArm64.

### P1 — quality / parity

- [ ] **P1** RTL text unsupported — `ui-text/.../SkiaParagraph.native.kt:112`
      `textDirection = ResolvedTextDirection.Ltr` fixed field; `getParagraphDirection()`
      (`:202`) always Ltr. Bidi run direction (`:204`) does read box direction (partial
      machinery). Root cause: `text/intl/Locale.native.kt isRtl()` unimplemented. Fix
      (author) tied to P1.2 engine.
- [ ] **P1** `PlatformFontLoader` NOP — `ui-text/.../font/FontFamilyResolver.native.kt`
      `SdlPlatformFontLoader.loadBlocking`/`awaitLoad` NOP;
      `PlatformFontFamilyTypefaceAdapter.resolve` returns `Immutable(Unit)`. Raw androidx
      `Font(bytes)`/`ResourceFont` don't load (compose-resources `Font()` works). Upstream
      `SkiaFontLoader.skiko.kt` is the vendorable reference. This is the long-deferred P3.2
      font resolver — **high-risk L**, parity harness wouldn't exercise it. Scope
      deliberately for 1.0.0 (accept-and-document vs attempt). **NOW ALSO GATES §6** — the
      2026-08-04 spike found the verbatim engine-vendor routes font resolution through this NOP, so
      implementing this loader (so `SkiaFonts`' icon/variable-axis model rides upstream's seam) and
      the §6 engine-vendor are ONE effort. Do them together, with macOS/Linux verification.
- [ ] **P1** `CharHelpers.skiko.kt` grapheme-break logic — the ONE cheap selective-vendor
      win from the hand-rolled text engine. Fixes `findPrecedingBreak`/`findFollowingBreak`
      splitting emoji/combining marks in `StringHelpers.native.kt:31` +
      `CharHelpers.native.kt:14`. (Giving `:foundation` a `skikoRenderer` source set would
      unblock this + `DragAndDropSource.skiko` — L-effort structural move, per §13a.)
- [~] **P1** `Serif`/`Cursive` generic families — RE-SCOPED after reading `SkiaFonts.kt`.
      They do NOT silently collapse to sans: `baseTypeface()` routes `generic:serif` /
      `generic:cursive` through `resolveGeneric()` → `fontMgr.matchFamilyStyle()` over a
      per-OS candidate list (`genericFamilyAliases`: Times/Noto Serif/… on each host), only
      falling back to the bundled default if NONE is installed. So serif/cursive already
      render as a real serif/cursive wherever the OS ships one (all three target OSes do).
      The only true gap is **cross-platform pixel-identity** / a host with no serif installed,
      which needs a BUNDLED Noto Serif — an app-level opt-in (buildSrc `downloadNotoFonts` +
      data.kres size cost), not a library correctness bug. **Decision:** leave library
      resolution as-is; offer bundled-serif as an opt-in later if an app needs it.
- [x] **P1** Document the hand-rolled text engine as an **accepted architectural
      deviation** — **DONE:** rationale header added to the top of
      `ui-text/compose-fork.txt` (the exact place someone would go to "fill" the gaps),
      explaining the reduced local engine, the flat-source-set reason, and that RTL /
      stroke-DrawStyle / grapheme reductions are tracked here (§2), not fixed by vendoring.
      Also reflected in `SkiaParagraphEngine.kt`'s header and CLAUDE.md's renderer summary.

### P1/P2 — reduced-coverage + accessibility

- [ ] **P1** Text paint path handles only `SolidColor`
      (`SkiaParagraph.native.kt:253-256`) — no brush/gradient text fill, `drawStyle`
      (stroke), or non-`SrcOver` blendMode. Part of the reduced style coverage; extend on
      the local engine.
- [ ] **P1** `SkiaParagraphEngine.kt:150-164` span segmentation drops partial `SpanStyle`
      overlaps (keeps only fully-covering `start<=segStart && end>=segEnd`). Documented
      divergence (`b63-upstream-text-mingw.md`) — widen to true interval segmentation.
- [~] **P1** Accessibility absent — `ComposeOwner.kt:~340` builds a `semanticsOwner` never
      traversed to any OS a11y API; `SemanticsRegion.native.kt intersect()/difference()`
      hardcode `false`. Not vendorable (NSAccessibility/UIA/AT-SPI). **Decision for 1.0.0:
      out of scope for desktop.** The full a11y pipeline stays P2/out.
      - [x] **P0** No-op `PlatformScreenReader` — `CompositionLocals.native.kt`
        `LocalPlatformScreenReader` default no longer throws (`error(...)`); it returns an
        `InactivePlatformScreenReader` (`isActive = false`) so a11y-gated vendored code
        degrades to "no reader present" instead of crashing. **DONE** — builds + runs clean.
- [ ] **P2** `SkiaFonts.kt` fidelity: `resolveCache` unbounded (font-cache half of P3.4
      deferred); variation key passes `null` density (`:65,78`) so density-dependent `opsz`
      in sp resolves wrong. `NamedFont.equals/hashCode` axis identity reportedly fixed —
      re-verify.

### P2 — cosmetic / edge

- [ ] **P2** `BlendMode.Multiply` renders wrong — opaque cyan×yellow reads back blue;
      likely Metal premultiply in graphics-layer flatten, not the `toSkia()` map. Isolate on
      the Skia draw path.
- [ ] **P2** Drag-OUT of window NOP — `ui/.../draganddrop/Sdl3DragAndDropOwner.kt:39`
      `requestDragAndDropTransfer`; `DragAndDropSource.native.kt:36` no drag-shadow. SDL3
      has no portable start-drag → needs NSDraggingSession/DoDragDrop/XDND per-OS.
      Drop-INTO works. Accepted 1.0 gap (SDL platform limit) — document.
- [ ] **P2** `NativeStringDelegate.native.kt:17` `toUpper/toLowerCase` ignore `locale`
      (Turkish i). Upstream `darwin` unvendorable (NSString).
- [ ] **P2** Prefetch scheduler NOP — `platform/PrefetchLocals.native.kt:18`, lazy lists
      skip ahead-of-time composition (scroll-in jank).
- [ ] **P2** `ClipboardPasteState.hasClip` aliased to `hasText`
      (`TextFieldSelectionState.native.kt:43`) — image-only clipboard undetected.
- [ ] **P2** `ComposeOwner.kt` gaps: `autofill`/`autofillManager` null (`:324`),
      `hapticFeedBack` NOP (`:279`), deprecated `clipboardManager` NOP (`:287`, real
      `LocalClipboard` works), `textToolbar` stub (`:311`, touch-gated). No multi-monitor
      enumeration (`SDL_GetDisplays` unused). Most match upstream desktop — verify, then
      leave or fill.
- [x] **P2** `installGlobals()` → `registerGenericFonts()`: VERIFIED not a hot-path cost —
      `registerGenericFonts()` already early-returns on a `fRegistered` flag
      (`GenericFonts.kt:20-26`), so the per-event call is a single boolean check, not a
      re-register. Original premise inaccurate; no change needed. (Minor residue: the
      `ImeBridge.onSessionActiveChange` lambda is reassigned per event — negligible, left
      as-is since it repoints per active window.)

---

## 3. SDL slimming

Static lib built from source per host (`scripts/build-sdl/build-all.py`, ref
`release-3.4.12`); linked into the exe via `sdl3.def` (`staticLibraries=libSDL3.a`). Today
only tests/examples + Windows D3D12/GPU are off (`build-all.py:311-319`). `SDL_Init` uses
**`SDL_INIT_VIDEO` only** (`SDL3Backend.kt:49`).

**USED (keep ON):** Video/window, Events, Clipboard, Dialog (file open/save — no cheaper
substitute; Linux uses portal/zenity, keep deps), OpenGL + Metal contexts, Render (2D CPU-
raster blit path: `SkiaSurfaceBridge` uploads to `SDL_Texture` + `SDL_RenderPresent`),
Filesystem, Cursor, Locale, System theme, Text input/IME, OpenURL, timing/hints.

**UNUSED (disable — zero references, no transitive need):** Audio, Joystick (→disables
Gamepad), Haptic, HIDAPI, Sensor, Power, Camera, GPU API, Offscreen video driver, virtual
joystick.

- [x] **P1** Added to `build-all.py` shared `vExtra`: `-DSDL_AUDIO=OFF`,
      `-DSDL_JOYSTICK=OFF`, `-DSDL_HAPTIC=OFF`, `-DSDL_HIDAPI=OFF`, `-DSDL_SENSOR=OFF`,
      `-DSDL_POWER=OFF`, `-DSDL_CAMERA=OFF`, `-DSDL_GPU=OFF`, `-DSDL_OFFSCREEN=OFF`,
      `-DSDL_VIRTUAL_JOYSTICK=OFF`. Promoted `-DSDL_GPU=OFF` from the Windows-only branch to
      the shared list; kept the per-host `-DSDL_RENDER_D3D12=OFF` (Windows). (Skipped
      `-DSDL_DISABLE_INSTALL_DOCS` — not a real SDL3 option; the build's `cmake --install`
      is relied upon.) All ten confirmed zero-reference by grep before disabling.
- [~] **P1** After the flag change, rebuild + run apps to confirm no regression.
      **DONE on macOS/Metal:** `build-all.py` rebuilt libSDL3.a clean with the new flags;
      `:demo` links + runs to a settled screenshot (TextField screen, no visual regression);
      `:apidemo` links clean too. **PENDING:** both apps on Linux + Windows hosts (fold into
      WIN-SMOKE §5). If audio/gamepad is ever needed by a consumer app, re-enable the flag
      and rebuild the static lib (build-time only).
- [x] **P2** Prune now-unreferenced macOS frameworks from `sdl3.def`. **DONE:** verified
      against the regenerated `libs/SDL3/lib/pkgconfig/sdl3.pc` `Libs:` line (source of
      truth), which after slimming no longer references `CoreAudio`, `AudioToolbox`,
      `AVFoundation`, `GameController`, `ForceFeedback`, or weak `CoreHaptics` — removed all
      six from `linkerOpts.osx` (kept `CoreMedia`, which the `.pc` still lists). Demo relinks
      clean. (Left Linux `linkerOpts` alone — those `-l` entries serve Skia/GL/X11, not the
      disabled SDL subsystems.)
- [ ] **P2** (higher-risk, flag-don't-apply) Render-driver pruning to software-only. Only
      the CPU-raster fallback uses `SDL_Render`; GL/Metal go direct. But
      `SDL_CreateRenderer(window, null)` (`SDL3Backend.kt:117`) lets SDL pick the first
      driver — with only software present it picks software (fine) but couples the fallback
      to that assumption. **Conservative: leave render drivers alone.**

**Do NOT** add `-DSDL_DISABLE_INSTALL` — the build relies on `cmake --install`
(`build-all.py:342`) to stage `libSDL3.a` + headers. `SDL_DYNAMIC_API` auto-disables for
static builds (no flag needed). **Risk:** a future consumer app needing SDL audio/gamepad
requires a static-lib rebuild (build-time, not a code change).

---

## 4. Performance

Prior PLAN Phases 0–3 landed; §12 measurement showed **no steady-state perf gap on
macOS/Metal**. Remaining perf question (if any) is Windows-GL or heavy-interaction
specific. Retained-layer engine is byte-for-byte upstream — not the gap.

- [ ] **P2** WON'T-FIX confirmed, keep as-is (don't relitigate): P0.4 per-frame full-surface
      clear + P0.6 per-frame Metal drawable reacquire (`CAMetalDrawable` single-use per
      frame, matches upstream `MetalRedrawer`).
- [ ] **P2** P2.2 upstream `GlobalSnapshotManager` invalidation-driven scheduling —
      deferred; coalescing half done by P1.3; full manager is skiko-windowing-coupled
      (an explicit renderer non-goal — skiko-windowing-coupled). Leave deferred unless a
      Windows-GL perf gap surfaces.
- [ ] **P2** `SkiaImageCache` font-cache half of P3.4 — evicted typefaces stay referenced by
      live paragraphs + `TypefaceFontProvider` (no clean unregister) so eviction frees no
      memory. Native-resource lifecycle backstop is the periodic `GC.collect()` nudge (top-
      level `Paragraph` a live `Text` holds has no Compose dispose seam). Accept for 1.0 or
      add a provider-unregister path.
- [ ] **P2** P4.1 second-renderer manual-vendor reversal (restore upstream RenderNode
      shadows via `SkiaGraphicsContext.setLightingInfo` + relocate
      `prepareTransformationMatrix`; deltas D2–D6). Buys vendoring cleanliness at
      shadow-lighting/hit-test regression risk (current `NativeShadowCanvas` shadows render,
      draw=0.08ms). Low ROI — deferred, needs full verify-mac + parity.
- [ ] **P2** Profile the shipped **Windows-GL** binary (`CDN_PROFILE=1`) under heavy
      interaction before ship (present phase is vsync-capped by display refresh — profile on
      the target monitor). Only open perf unknown.

---

## 5. API coverage / release mechanics

API coverage vs upstream is **99%** (8665/8751 decls via `compose-coverage.py`). Misses are
AppKit/UIKit/web host actuals (N/A), `ImageComposeScene`/`renderComposeScene` (test util),
and the `ui-text.platform` font layer (= P3.2 font resolver). The 1076 "extra" decls are
umbrella-repo modules the tool can't compare + version skew, not invented surface.

- [ ] **P0** Re-pin refs to Compose **1.12.0 stable** when it ships (currently
      `v1.12.0-beta03+dev4483` — no clean beta03 tag on Maven). Bump both
      `scripts/compose-fork/compose.properties` refs, re-sync (`scripts/compose-fork/sync.sh`),
      let the build surface breakage.
- [ ] **P0** Bump `vComposeJvmVersion` in `:demo` / `:apidemo` / `:material-symbols` (JVM
      parity leg currently forced to beta02 by documented skew — native leads).
- [ ] **P1** Run `check-vendor-drift.py` after the ref bump — re-stamp `VENDOR-BASE` on the
      10 manual vendors, hand-reconcile any that actually changed base..pin.
- [~] **P0** WIN-SMOKE fidelity pass (Windows host only, pre-ship gate): the Mac runbook
      cannot cover the shipped mingwX64 binary. Assert: (1) NotoSans `FontMetrics` dump
      native-vs-JVM-Windows (§1b acceptance) — **✅ DONE 2026-08-04 (`--metricsprobe`==`--metrics`)**;
      (2) `\t`/control-char render clean (§1a) — **✅ DONE**; (3) the Windows-only `PrintWindow`
      probe — pending (interactive); (4) the common-metadata publish job — compile gate now
      **✅ GREEN (2026-08-04, see the fix below)**; the actual publish + downstream-consume smoke
      still owed. Remaining: (3) `PrintWindow` probe + the interactive caret-height check for the
      §1b LineMetrics fix.
- [ ] **P0** apiDump is **host-specific** — do NOT commit macOS dumps. Only the **Windows
      publish job compiles common metadata** (owns the root KotlinMultiplatform publications
      — the only host declaring every target, so only its `.module` files carry the full
      variant table; macOS-published roots left v0.1.15 without mingwX64 variants). Test
      `gradlew :<module>:compileCommonMainKotlinMetadata` before tagging; publish from
      Windows.
- [x] **P0** **FIXED (2026-08-04) — common-metadata compile is now GREEN** (`compileCommonMainKotlin
      Metadata` across all modules → BUILD SUCCESSFUL). Was RED, blocking the Windows publish.
      Running `:material3:compileCommonMainKotlinMetadata` (or the aggregate) had failed at
      `:foundation:compileNativeMainKotlinMetadata` (pulled in via `:foundation:allMetadataJar`):
      `Scrollbar.skiko.kt` + `v2/Scrollbar.skiko.kt` — `Declaration annotated with
      '@OptionalExpectation' can only be used in common module sources`
      (`OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE`). **Root cause:** upstream keeps these
      `.skiko.kt` files in `skikoMain`, which INCLUDES the JVM-desktop target, so `@JvmName`/
      `@JvmStatic`/`@file:JvmName` resolve; the port vendors them into **native-only `nativeMain`**
      (no JVM target under it), so those JVM annotations are orphaned and the *metadata* compile
      rejects them (per-target compile tolerates them — that's why the APPS build fine, but the
      publish's `allMetadataJar` recompiles nativeMain metadata and dies). `:ui` and below pass;
      `:foundation` is the first break. **Blast radius:** 14 vendored native files use `@Jvm*`
      (`grep -rE "@file:JvmName|@JvmName|@JvmStatic" .../src/vendor/native .../src/nativeMain`); 11
      are in the GITIGNORED, sync-regenerated `src/vendor/native/` tree, so per-file edits DON'T
      survive a re-sync. **Fix (build/sync-level, not per-file):** either (a) a `sync.sh`
      post-step that injects `@file:Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")`
      (or strips `@Jvm*`, which are no-ops with no JVM library target) into vendored native files —
      there's precedent: `components-resources/…/ResourceState.blocking.kt` already carries that
      suppress; or (b) a KGP-level relaxation of the native metadata compilation. Needs a real
      publish + downstream-consume verification after. Dedicated effort — likely THE reason the
      Windows metadata publish never went green.
      **FIX APPLIED (2026-08-04):** the root turned out narrower — sync.py ALREADY injects the K2
      `@file:Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE", "LESS_VISIBLE_TYPE_ACCESS_IN_INLINE")`
      into every `src/vendor/` .kt (`sync.py:313`), so the regenerated tree was fine. The only gap
      was the two COMMITTED manual vendors in `src/nativeMain` that sync doesn't regenerate:
      `foundation/…/Scrollbar.skiko.kt` + `v2/Scrollbar.skiko.kt`. Added the identical `@file:Suppress`
      to both (provenance comment updated; `check-vendor-drift` still clean). Full
      `compileCommonMainKotlinMetadata` now green. **Remaining before publish:** run the actual
      publish + a downstream-consume smoke (the compile gate is necessary, not proof the `.module`
      variant table is complete).
- [ ] **P1** Version bump to `1.0.0` across published coords once the above are green.
- [x] **P0** Doc-hygiene blocker: `CLAUDE.md` documentation map referenced `RENDERER.md`,
      `SKIKO-MINGW-FEASIBILITY.md`, and `TODO.md`. **DONE:** removed `RENDERER.md` +
      `SKIKO-MINGW-FEASIBILITY.md` (stale/historical — current renderer essentials inlined in
      CLAUDE.md's doc map, the rest recoverable from git history); fixed the `CLAUDE.md`
      line-1 `ean` typo; dropped the `TODO.md` link (never committed) → its content is PLAN.md
      §2. `git grep` confirms no remaining links to any deleted doc.

---

## 6. Vendor the upstream skiko text engine (next major refactor — approved 2026-08-04)

**Goal:** honour "VENDOR ALL WE CAN". Delete the hand-rolled reduced text engine
(`SkiaParagraph.native.kt` + `NativeParagraphOps` seam + `SkiaParagraphEngine.kt` +
`FontRasterizationSettings.native.kt` port) and vendor upstream's skiko text subsystem VERBATIM.

**Why it was hand-rolled (the REAL reason, not SDL_TTF):** `Paragraph` is `expect sealed interface`
(`vendor/common/…/Paragraph.kt:56`) whose `actual` sits in the **skiko-FREE `nativeMain`**, so a
sealed implementer can't touch skiko → the hand seam. `nativeMain` is skiko-free because it's the
shared parent of the **official-skiko** (mac/linux, `libs.skiko`) and **fork-skiko** (mingw,
`com.bitsycore.skiko:skiko`) legs — two different artifacts, so the shared parent can carry neither.

**Why it's feasible NOW (all-skiko + srcDir reuse):** every native leg already reaches
`src/skikoRendererMain/kotlin` (mingw via `kotlin.srcDir(...)` in `skikoRendererMingwSharedMain`,
`ui-text/build.gradle.kts:54`), and the fork exposes the SAME `org.jetbrains.skia.*` Kotlin API. So
the `Paragraph` **actual + engine can move DOWN into the skiko source set** (served to all targets)
instead of skiko-free `nativeMain` — which lets upstream's skiko files compile as-is.

**⚠️ SPIKE FINDING (2026-08-04) — the real gate is the FONT LOADER, not the source-set move.**
A read-only feasibility map (no code changed) found two layers:
- **Layer 1 — source-set / sealed move: FEASIBLE.** The `actual sealed interface Paragraph`
  (`vendor/native/Paragraph.native.kt:36`, signature-only mirror) + its implementer + the 11 factory
  actuals can move to `skikoRendererMain` (all leaves reach it; sealed same-source-set holds). Just
  mechanical.
- **Layer 2 — font model: THIS is the blocker, and it's the SAME task as §2-P1 `PlatformFontLoader`.**
  Upstream's engine (`SkiaParagraphIntrinsics.skiko.kt:38,61`) resolves typefaces through
  `FontFamily.Resolver` → `PlatformFontLoader`. The port's `SdlPlatformFontLoader`
  (`font/FontFamilyResolver.native.kt:30-49`) is a **NOP** (`loadBlocking`/`awaitLoad`→`Unit`,
  `resolve`→`Immutable(Unit)`). The port's hand-rolled engine exists precisely to BYPASS that NOP by
  resolving through `SkiaFonts` (icon fonts + variable axes + data.kres bytes). So vendoring the
  upstream engine verbatim routes font resolution through the NOP → **all text goes blank/tofu**
  until the loader is real.
- **Consequence:** the verbatim engine-vendor REQUIRES first implementing upstream's font-loader path
  (vendor `SkiaFontLoader.skiko.kt` as the reference) so the port's `SkiaFonts` icon/variable-axis
  model rides upstream's `PlatformFontLoader`/`FontLoadResult` seam. This is the deferred high-risk P1
  in §2 — it and this §6 are one effort. **Intermediate option:** vendor the engine files but keep
  `SkiaFonts` via a Rule-3 edit at the one resolve call site (vendors most files verbatim, 1–2 stay
  hand-reconciled). **Verification needs macOS + Linux hosts** (parity), so this is a dedicated
  effort, not a Windows-only session — do it with mac access, on its own branch.

**Plan (spike on a branch, verify on THIS Windows host — build + render mingwX64):**
1. Move the `Paragraph`/`ParagraphIntrinsics` **actuals** into `skikoRendererMain` (reused by mingw
   via the existing `srcDir`). Confirm the `expect sealed` actual is accepted there for every leaf
   target (the b63 memo hit a sealed-in-`nativeMain` wall — test whether hosting the actual in the
   skiko set clears it; the sealed same-module rule is per-target-compilation, which should hold).
2. Vendor VERBATIM into `src/vendor/skikoRenderer/`: `SkiaParagraph.skiko.kt`,
   `ParagraphBuilder.skiko.kt`, `SkiaParagraphIntrinsics.skiko.kt`, `SkiaTextPaint.skiko.kt`,
   `ParagraphLayouter.skiko.kt`, `PlatformFont.skiko.kt`, `FontRasterizationSettings.skiko.kt`,
   `TextStyle.skiko.kt`; add them to `ui-text/compose-fork.txt`; delete the hand-rolled files.
3. Reconcile the ~7 hierarchy-crossing `expect/actual` pairs the inverted layout needed; keep the
   `SkiaFonts` family/variable-axis model (it's the port's real value-add) behind upstream's seams.
4. **Fork-only Rule-3 edit:** upstream's code calls `paragraph.lineMetrics` — the fork's extern-C
   binding mis-decodes ascent/descent (§1b finding). Either carry the guarded reconstruction (from
   `SkiaParagraphOps.lineMetrics()`) into the vendored `SkiaParagraph.skiko.kt` as a Rule-3 edit, or
   fix it in the fork DLL. **Bonus:** vendoring `ParagraphBuilder.skiko.kt` verbatim wires
   `halfLeading` → the §1b M3-baseline drift disappears for free.
5. Verify: mingwX64 build + `--screen=BasicText/Buttons/AnnotatedString` render clean +
   `--metricsprobe` == JVM `--metrics`. Then macOS/Linux verify + parity (needs those hosts).

**Sequencing:** AFTER the current shared-Kotlin fixes land (this session). If the sealed-actual move
proves infeasible, fall back to keeping the seam but at least converting the hand files to tracked
Rule-3 vendors (like `FontRasterizationSettings.native.kt` now has a `VENDOR-BASE` line).

## Accepted 1.0.0 gaps (documented, not fixed)

Drag-OUT of window (SDL platform limit; drop-IN works), full accessibility pipeline (out of
scope for desktop 1.0 — but ship the non-throwing `PlatformScreenReader` no-op), the
hand-rolled text engine as an architectural deviation (§2 P1), per-focus
`SDL_StartTextInput/StopTextInput`, `loadImageBitmap`/`loadSvgPainter` (JVM `InputStream`
signatures, N/A on K/N).
