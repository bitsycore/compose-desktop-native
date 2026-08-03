# ROAD TO 1.0.0

**Objective:** get ComposeNativeSDL3 **as close to upstream Compose Multiplatform as
possible** — vendor every file we can instead of rewriting, stay performant, and look
faithful (ideally pixel-identical across macOS / Linux / Windows). The port is already at
99% upstream API coverage with maximal vendoring and no steady-state perf gap on
macOS/Metal; the remaining 1.0.0 work is **cross-platform rendering fidelity** (Windows
skiko-fork divergences), **completing native-actual stubs**, and the release mechanics
(1.12.0-stable re-pin + WIN-SMOKE).

## Definition of done for 1.0.0

- [~] No text tofu on any platform — Windows tab regression fixed in Kotlin (§1a);
      needs on-Windows confirmation (WIN-SMOKE).
- [ ] Text vertical metrics on Windows match the Windows fidelity reference
      (skiko-on-JVM Compose Desktop), not a bug vs it.
- [ ] Fork-vs-official divergence surface (FontMgr, gamma, ICU) documented + audited,
      each item either fixed or accepted-with-rationale.
- [~] Native-actual fidelity blockers closed: float pointer coords **DONE** (§2); text
      context menu still open (P0). Date/time localization is P1 polish (formatter already
      works — see §2). Screen-reader no-op **DONE** (§2).
- [ ] Vendor hygiene stays clean: zero drift, zero commonMain rule-1 violations
      (already true — keep it true through the ref bump).
- [~] SDL static lib slimmed to the used subsystem surface (§3) — **DONE + verified on
      macOS/Metal**; other hosts pending WIN-SMOKE.
- [ ] Refs re-pinned to Compose **1.12.0 stable**, JVM parity versions bumped, WIN-SMOKE
      fidelity pass green on a real Windows host.
- [x] `CLAUDE.md` documentation map consistent with tree — RENDERER.md restored, line-1
      typo fixed, TODO.md link repointed to §2.

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

- [ ] **P0** Confirm: on the fork, render a literal `\t` string and check for the box;
      confirm `replaceTabCharacters` either isn't exported or its ParagraphStyle field
      offset doesn't match m150.
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

### 1b. macOS-vs-Windows vertical text metrics — HIGH CONFIDENCE root cause, "is it a bug?" UNCONFIRMED

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

- [~] **P0** Confirm FIRST — diagnostic **LANDED**: `SkiaParagraphEngine.kt` now dumps
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
- [ ] **P1** If Windows-native != Windows-JVM: fix in the fork (out of tree) — build its
      `FontMgr.default`/scaler to match official skiko-windows (DirectWrite:
      `skia_use_dwrite` / system fontmgr) so `makeFromData` typefaces get the same
      metric-table selection. Then Windows-native matches Windows-JVM, mac stays matching
      mac-JVM — both upstream-faithful.
- [ ] **P2** (Optional, STRONGER than upstream) If cross-platform pixel-identity is
      wanted over matching each host's stock Compose Desktop: build a single
      FreeType/`SkFontMgr_Custom_Empty`-backed `SkFontMgr` used on ALL native targets for
      the bundled fonts, so NotoSans yields identical ascent/descent everywhere.
      Intentional departure from per-platform upstream behavior — decide deliberately.
- [ ] **DO NOT** "fix" by injecting a default `LineHeightStyle` or clamping ascent in
      `SkiaParagraphEngine.kt` — that desyncs from upstream `DISABLE_ALL` and masks, not
      resolves, the metric divergence.

### 1c. Other fork-vs-official divergences

- [ ] **P1** `FontMgr.default` may be an empty/stub manager on the fork (T1/V1 root).
      `SkiaFonts.kt:38,44,50` wire it as both the paragraph default manager and the loader.
      An empty manager breaks glyph FALLBACK for missing codepoints (CJK/emoji on Windows),
      distinct from the tab bug. **Confirm** which `SkFontMgr` the fork's `FontMgr.default`
      returns on native Windows (the feasibility doc noted the fork hand-wrote windowsMain
      actuals because "linux is a stub"). **Fix (fork):** ensure it returns a real
      DirectWrite manager matching skiko-JVM-Windows.
- [~] **P1** `FontMgrWithFallback` — ANALYZED, deliberately NOT changing blindly. The port
      does `setDefaultFontManager(fontMgr)` [system] + `setAssetFontManager(provider)`
      [bundled] (`SkiaFonts.kt:49-52`). This is **functionally equivalent** for missing-glyph
      fallback on macOS/Linux/Windows-JVM: skiko's shaper tries the asset provider (bundled
      Noto + aliases) first, then the default/system manager for CJK/emoji fallback, which
      resolves because the system FontMgr has those fonts. Rewriting to
      `setDefaultFontManager(FontMgrWithFallback(provider))` risks a macOS fallback
      regression with no observable gain, and I can't visually verify CJK/emoji fallback on
      this host. **The real gap is Windows-fork-only** (fork `FontMgr.default` may be an empty
      stub → no system fallback), which is the item below and is not fixable in Kotlin.
      **Decision:** leave the Kotlin as-is; fix the fork's `FontMgr.default`. Re-open only if
      a CJK/emoji fallback bug is actually observed on macOS/Linux.
- [ ] **P2** ICU/unicode data packaged differently in the fork (T3). Recovered feasibility
      doc notes it had to force-export `uloc_getDefault_skiko` / `uloc_toLanguageTag_skiko`.
      Control-char/whitespace classification (U+00A0, U+200B, U+0009 pre-replacement) can
      diverge. **Confirm:** render those control codepoints, Windows-only tofu check.
- [ ] **P2** Text gamma/AA edges: a Skia **build-time constant**
      (`SK_GAMMA_EXPONENT`/`SK_GAMMA_CONTRAST`/`SK_GAMMA_APPLY_TO_A8`), NOT a GL-vs-Metal
      runtime difference. If Windows AA looks different it's the fork's Skia gamma flags.
      Document the fork's values; align to official skiko-windows.
- [x] **GL-vs-Metal AA/gamma/color-space — RULED OUT as a primary cause.** Both bridges
      create the surface `colorSpace = null` (`SkiaGLBridge.kt:68-74`,
      `SkiaMetalBridge.kt:133-139`) — identical un-color-managed legacy blending. Only
      deltas are cosmetic-correct: `RGBA_8888`/`BOTTOM_LEFT` (GL) vs `BGRA_8888`/`TOP_LEFT`
      (Metal) — channel order + Y-flip, matched to buffers. Windows uses OpenGL
      (`PlatformGpu.kt:20`), same backend as Linux, so GL-vs-Metal can't explain a
      Windows-vs-Linux gap at all. Deprioritized — no action.
- [x] **P1** Recover + re-commit the fork's Skia build config. **DONE:**
      `SKIKO-MINGW-FEASIBILITY.md` (402 lines, deleted in `bdb5c64d`) restored to the tree —
      also fixes the broken `CLAUDE.md:37` link to it. **Key lead for 1b/1c:** the fork's
      Route-1a recipe builds Skia with **`skia_use_freetype`** (`:220`; `:209` pairs it with
      FreeType), i.e. the Windows text scaler is **FreeType**, not DirectWrite and not macOS
      CoreText — a concrete reason the SAME NotoSans bytes yield different ascent/descent on
      Windows (different metric-table selection) AND why fork `FontMgr.default` may not
      enumerate Windows system fonts for glyph fallback (FreeType has no system fontmgr
      without fontconfig). **CAVEAT:** this is the *recommended recipe* in the doc, not a
      verified dump of the shipped `0.150.1-mingw.1` GN args — confirm against the actual
      fork build (`SK_GAMMA_*`, ICU packaging still unlisted). Whoever rebuilds the fork
      should paste the real `args.gn` into this doc so drift is auditable.

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
      deliberately for 1.0.0 (accept-and-document vs attempt).
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
- [ ] **P1** Document the hand-rolled text engine (`SkiaParagraphEngine.kt`) as an
      **accepted architectural deviation** — the 17-file upstream `skikoMain` text stack is
      unselected in `ui-text/compose-fork.txt` (all in DIAGNOSTIC GAPS), forced by the flat
      nativeMain source-set layout (MEMORY note). It's the single biggest "not upstream"
      surface and the root of RTL / stroke-DrawStyle / grapheme stubs. Record rationale so
      nobody "fixes" the gap list.

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
      (RENDERER §8 non-goal). Leave deferred unless a Windows-GL perf gap surfaces.
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
- [ ] **P0** WIN-SMOKE fidelity pass (Windows host only, pre-ship gate): the Mac runbook
      cannot cover the shipped mingwX64 binary. Assert: (1) NotoSans `FontMetrics` dump
      native-vs-JVM-Windows (§1b acceptance), (2) `\t`/control-char render clean (§1a),
      (3) the Windows-only `PrintWindow` probe, (4) the common-metadata publish job.
- [ ] **P0** apiDump is **host-specific** — do NOT commit macOS dumps. Only the **Windows
      publish job compiles common metadata** (owns the root KotlinMultiplatform publications
      — the only host declaring every target, so only its `.module` files carry the full
      variant table; macOS-published roots left v0.1.15 without mingwX64 variants). Test
      `gradlew :<module>:compileCommonMainKotlinMetadata` before tagging; publish from
      Windows.
- [ ] **P1** Version bump to `1.0.0` across published coords once the above are green.
- [x] **P0** Doc-hygiene blocker: `CLAUDE.md` documentation map links `PLAN.md` (this file
      — restored), `RENDERER.md`, and `TODO.md`. **DONE:** `RENDERER.md` restored from
      history; `CLAUDE.md` line-1 `ean` typo fixed; the `TODO.md` link (never committed at
      HEAD) repointed to PLAN.md §2, which subsumes the stub audit.

---

## Accepted 1.0.0 gaps (documented, not fixed)

Drag-OUT of window (SDL platform limit; drop-IN works), full accessibility pipeline (out of
scope for desktop 1.0 — but ship the non-throwing `PlatformScreenReader` no-op), the
hand-rolled text engine as an architectural deviation (§2 P1), per-focus
`SDL_StartTextInput/StopTextInput`, `loadImageBitmap`/`loadSvgPainter` (JVM `InputStream`
signatures, N/A on K/N).
