# TODO.md

Everything the port currently leaves as a no-op, stub, hardcode, or partial
implementation, plus the gaps worth closing before a stable 1.12 release. This
is an audit of PROJECT code only (the `*.native.kt` / `*.sdl.kt` /
`com.compose.sdl.*` actuals); vendored upstream under `src/vendor/` is out of
scope.

Severity is a rough guide, not a mandate:

- **Blocker** for stable: a capability a typical desktop app expects that is
  currently absent or broken.
- **Nice-to-have**: real gap, but the app works without it.
- **Cosmetic**: minor fidelity or edge-case behavior.

Context for prioritizing: the project goal is **G1, cheap upstream-tracking**
(see [RENDERER.md](RENDERER.md)). The **JVM Compose Desktop target is the
documented Windows fidelity/feature tier**, so SDL-leg visual gaps (section J)
are lower priority than the cross-cutting platform gaps (input, locale, a11y,
cursor) that affect every backend including Skia on macOS/Linux.

Renderer-internal cleanups (the deferred D2 shadow-lighting split, D3 matrix
dedupe) live in [RENDERER.md](RENDERER.md#6-remaining-and-future-work), not
here.

---

## Release-blocking shortlist for stable 1.12

The cross-cutting items to weigh first (all detailed below):

1. ~~Locale hardcoded to `en-US`~~ **DONE** (section E) — `Locale.current` /
   `LocaleList.current` now read the OS preferred locales via SDL, unlocking the
   40+ Material 3 translations. The date/number-format tail (needs CLDR) remains.
2. **No IME / text composition** (section B). Only committed Latin text works;
   CJK, dead keys, and accented input do not.
3. **No cursor changes** (section D). The OS cursor never becomes an I-beam over
   text or a hand over clickables.
4. **No copy/paste context menu** (section C). Keyboard copy works; there is no
   right-click or selection toolbar.
5. **Accessibility is entirely absent** (section A). Decide whether stable
   requires any screen-reader support at all.
6. **Standard `Font(...)` / resource-font loading is a no-op** (section F). Only
   pre-registered named fonts render. A blocker only if apps use the standard
   font APIs.

---

## A. Accessibility (entirely absent)

No accessibility bridge exists. A real `SemanticsOwner` is built but never
surfaced to any OS accessibility API (no NSAccessibility / UIA / AT-SPI, no SDL
a11y). Screen readers get nothing.

- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:340` · `semanticsOwner` is instantiated but never read; no traversal into a platform a11y tree. A real a11y controller would walk the semantics tree and map roles/labels/actions to a native AT bridge. **Blocker for a11y.**
- `compose/ui/ui/src/nativeMain/.../platform/CompositionLocals.native.kt:31` · `LocalPlatformScreenReader` default throws (`error(...)`); never provided by any window. Screen-reader-active queries are unavailable. **Blocker for a11y.**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:297` · `accessibilityManager` implements only `calculateRecommendedTimeoutMillis` (passthrough); no announce/focus/event surface. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../semantics/SemanticsRegion.native.kt:14` · `StubSemanticsRegion.intersect()`/`difference()` hardcode `false`; only `set`/`bounds` work. Region math for semantics culling is inert. **Nice-to-have.**

## B. Text input / IME

Committed text, arrow/backspace/enter editing, and mouse-drag selection work.
Composition (preedit) does not.

- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:407` · `textInputSession` awaits cancellation forever (`awaitCancellation()` at :414); no real `PlatformTextInputSession`. The modern `PlatformTextInputModifierNode` pipeline (BasicTextField2) has no IME start/stop, composition, or cursor-rect reporting. **Blocker.**
- `compose/ui/ui/src/nativeMain/.../SDL3EventMapper.kt:126` · maps `SDL_EVENT_TEXT_INPUT` (committed) only; `SDL_EVENT_TEXT_EDITING` (preedit/composition) is never mapped, so there is no underlined composing region for dead keys / CJK. **Blocker for CJK and dead-key input.**
- `compose/ui/ui/src/nativeMain/.../SDL3Backend.kt:111` · `SDL_StartTextInput` is called once at window init and never scoped to the focused field; `SDL_SetTextInputArea` is never called, so the IME candidate window renders at the wrong location. **Nice-to-have.**
- `compose/ui/ui/src/commonMain/.../text/input/NoOpPlatformTextInputService.kt:24,32` · legacy `PlatformTextInputService` `startInput`/`stopInput`/`updateState` + software-keyboard toggles are all NOP. Superseded by the modern path above. **Nice-to-have / cosmetic (desktop).**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:328` · `softwareKeyboardController.show/hide` NOP. **Cosmetic (desktop).**

## C. Text toolbar and context menus (copy / paste / select-all)

- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:311` · `textToolbar` is a stub: `showMenu`/`hide` NOP, `status` always `Hidden`. No selection or right-click copy/paste popup anywhere. Keyboard copy still works. **Blocker for text UX.**
- `compose/foundation/.../text/selection/SelectionActuals.native.kt:42` · `addSelectionContainerTextContextMenuComponents` NOP (upstream TODO CMP-7819). No copy/select-all menu for `SelectionContainer`. **Nice-to-have.**
- `compose/foundation/.../text/selection/TextFieldSelectionManager.native.kt:24` and `compose/foundation/.../text/input/internal/selection/TextFieldSelectionState.native.kt:36` · `addBasicTextFieldTextContextMenuComponents` NOP for both the legacy and state-based `BasicTextField`. **Nice-to-have.**

## D. Pointer / cursor icons

The OS cursor never changes. No SDL cursor API is called anywhere in the port.

- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:333` · `pointerIconService.getIcon()` always returns `Default`; `setIcon()` body is empty. This is the hook `Modifier.pointerHoverIcon` drives, so hover cursor changes are swallowed. Should store the requested icon and call `SDL_SetCursor`. **Blocker for UX.**
- `compose/ui/ui/src/nativeMain/.../input/pointer/PointerIcon.native.kt:18` · `pointerIconDefault|Crosshair|Text|Hand` are inert marker objects carrying only a name; nothing maps them to `SDL_SYSTEM_CURSOR_*`. Needs `SDL_CreateSystemCursor` + `SDL_SetCursor` (and cursor caching in the `:window` loop). **Blocker (paired with above).**

## E. Locale and internationalization

**DONE — `Locale.current` / `LocaleList.current` now read the OS preferred
locales via SDL.** A shared reader (`com.compose.sdl.text.systemPreferredLocaleTags`,
backed by `SDL_GetPreferredLocales()`, cached after first non-empty read) feeds
both actuals. This is what keys Material 3 string translation, so all 40+
vendored `material3/internal/l10n/*.kt` tables now resolve. Verified with the
`demo --localetest` probe: `-AppleLanguages "(fr-FR)"` renders the DatePicker
headline as "Sélectionner une date"; `(de-DE, fr-FR, en)` returns the full
ordered `LocaleList` (`de-DE` first). en-US fallback before SDL init.

- [x] `Locale.native.kt` · `Locale.current` reads the OS locale (first preferred), en-US fallback. **Done.**
- [x] `PlatformLocale.native.kt` · `createPlatformLocaleDelegate().current` returns the full ordered `LocaleList` from SDL. **Done.**

The rest of section E is the i18n **tail** that needs locale-aware DATA
(month/weekday names, date/number patterns), which Kotlin/Native has no ICU for.
It is NOT unlocked by the SDL wiring above:

- `compose/material3/.../CalendarLocale.native.kt:20` · locale-agnostic stub: `toString()` fixed `"en"`, single shared instance, `equals` treats all instances as equal. A separate type from `ui.text.intl.Locale`; needs its own wiring so `CalendarModel`/`PlatformDateFormat` can vary by locale. Inert until `PlatformDateFormat` below is localized. **Nice-to-have (DatePicker/TimePicker).**
- `compose/material3/.../internal/PlatformDateFormat.native.kt` · multiple hardcodes:
  - `:27` `weekdayNames` hardcoded English full/short names (DatePicker shows English day initials regardless of locale). **Blocker.**
  - `:32` `formatWithPattern`/`formatWithSkeleton` ignore the pattern/skeleton and always emit ISO `yyyy-MM-dd`. **Blocker.**
  - `:25` `firstDayOfWeek` hardcoded `1` (Sunday); locale-dependent. **Nice-to-have.**
  - `:44` `parse()` only accepts ISO `yyyy-MM-dd`, ignoring pattern/locale. **Nice-to-have.**
  - `:62` `getDateInputFormat()` hardcoded `yyyy-MM-dd` / `'-'`. **Nice-to-have.**
  - `:65` `is24HourFormat()` hardcoded `true`. **Cosmetic.**
- `compose/ui/ui/src/nativeMain/.../text/platform/NativeStringDelegate.native.kt:17` · `toUpperCase`/`toLowerCase` use locale-independent stdlib casing; the `locale` param is ignored (wrong for Turkish dotted/dotless i, etc.). **Cosmetic.**
- `compose/ui/ui/src/nativeMain/.../text/intl/Locale.native.kt:17` · `Locale.isRtl()` not implemented; the pipeline is LTR-only (`SdlParagraph.native.kt:333` `getBidiRunDirection` always returns `Ltr`; owners/DrawScopes default `LayoutDirection.Ltr`). No RTL shaping. **Nice-to-have.**
- No `NumberFormat` / currency / decimal-grouping actual exists; numbers render via `toString()` with `.` decimal and no grouping regardless of locale. **Cosmetic.**

**Beyond a real `getLocale`:** SDL reports language + country only (no script subtag, no calendar/clock/number preferences), and Kotlin/Native bundles no ICU. Localized month/weekday name tables, date/number/currency patterns, first-day-of-week, locale-aware casing, and RTL all still require a bundled CLDR subset or a third-party K/N i18n library. The SDL wiring is the cheap 80%; these are the expensive tail.

## F. Font resolution

- `compose/ui/ui/src/nativeMain/.../text/font/FontFamilyResolver.native.kt:32` · `SdlPlatformFontLoader.loadBlocking`/`awaitLoad` are NOP (return `Unit`); font bytes for `ResourceFont` / `Font(bytes)` / `LoadedFontFamily` are never loaded. Only pre-registered `NamedFont` names reach the renderer. **Blocker if the app uses standard `Font(...)` / resource-font APIs; otherwise nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../text/font/FontFamilyResolver.native.kt:38` · `PlatformFontFamilyTypefaceAdapter.resolve` returns `TypefaceResult.Immutable(Unit)`; any code reading the resolved typeface gets `Unit` (the renderer bypasses this via name lookup, so usually harmless). **Nice-to-have.**
- `compose/ui/ui/src/commonMain/.../text/NamedFont.kt:62` · `FontFamily.projectFontName` resolves only a `FontListFontFamily` whose first font is a `NamedFont`; returns `null` for `Serif`/`SansSerif`/`Monospace`/`Cursive`/`Default`, `LoadedFontFamily`, and resource-backed families, so all of those collapse to the single default font (no serif/mono/cursive distinction). **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../text/font/FontFamilyResolver.native.kt:69` · deprecated `createFontFamilyResolver(fontResourceLoader)` ignores the supplied loader. **Cosmetic.**

## G. Text rendering details

- `compose/ui/ui/src/nativeMain/.../text/SdlParagraph.native.kt:376` · `drawNativeText` accepts and ignores text `Shadow`, stroke `DrawStyle`, and non-`SrcOver` blend, so those never render on text. **Cosmetic.**
- `compose/foundation/.../text/StringHelpers.native.kt:31` and `compose/ui/ui/src/nativeMain/.../text/CharHelpers.native.kt:14` · `findPrecedingBreak`/`findFollowingBreak` (and `offsetByCodePoints`) do a codepoint/surrogate walk only, with no ICU grapheme clusters, so caret movement and backspace split combining marks and emoji ZWJ sequences. **Nice-to-have.**

## H. Drag and drop

Drop INTO the window (files + text) is fully wired and works. Drag OUT does not.

- `compose/ui/ui/src/nativeMain/.../draganddrop/Sdl3DragAndDropOwner.kt:38` · `requestDragAndDropTransfer` is a NOP and `isRequestDragAndDropTransferRequired` returns `false`; `Modifier.dragAndDropSource` compiles but no OS-level drag leaves the window. Needs per-OS native work (NSDraggingSession / DoDragDrop / XDND); SDL3 has no portable start-drag. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../draganddrop/Sdl3DragAndDropOwner.kt:119` · incoming transfer carries only file paths + a text blob; no image/URL/custom-MIME payloads. **Cosmetic.**
- `compose/foundation/.../draganddrop/DragAndDropSource.native.kt:36` · no drag-shadow / drag-image feedback for internal DnD (upstream skiko renders one). **Cosmetic.**

## I. Window and platform info

- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:354` · `windowInfo.containerSize`/`containerDpSize` return `IntSize.Zero`/`DpSize.Zero`; `LocalWindowInfo.containerSize` readers (popup sizing, window-size-aware layout) get 0. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:353` · `windowInfo.isWindowFocused` hardcoded `true`; not fed by SDL focus events (which the window already tracks). Focus-reactive UI will not react. **Nice-to-have.**
- Multi-monitor: no display enumeration or per-monitor placement anywhere (`SDL_GetDisplays`/`SDL_GetDisplayBounds`/`SDL_GetDisplayForWindow` unused). **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../platform/PrefetchLocals.native.kt:18` · `LocalPlatformPrefetchScheduler` default is a NOP scheduler; lazy lists skip ahead-of-time item composition (correctness fine, possible scroll-in jank). **Nice-to-have (perf).**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:324` · `autofill`/`autofillManager` are null; `requestAutofill` NOP. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:279` · `hapticFeedBack.performHapticFeedback` NOP (no desktop pointer haptics API). **Cosmetic.**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:287` · deprecated `clipboardManager` `setText`/`getText` NOP, and `clipboard.getClipEntry` null. Harmless: the real `LocalClipboard` (text + PNG image via SDL3) works. **Cosmetic.**
- `compose/foundation/.../text/input/internal/selection/TextFieldSelectionState.native.kt:44` · `ClipboardPasteState.hasClip` aliased to `hasText`, so image-only clipboard is not detected for the paste affordance. **Cosmetic.**

## J. SDL renderer graphics (Windows / `-Prenderer=sdl3` only)

These affect only the SDL leg. On macOS/Linux the Skia leg implements them
correctly, and JVM is the documented Windows fidelity tier, so these are lower
priority under G1. Listed because SDL is the shipped Windows renderer.

- `compose/ui/ui/src/sdlRendererMain/.../graphics/PathEffect.sdl.kt:15` · `dash`/`corner`/`chain`/`stamped` path effects are all NOP; dashed/dotted strokes render solid. **Nice-to-have.**
- `compose/ui/ui/src/sdlRendererMain/.../graphics/RenderEffect.sdl.kt:16` · `RenderEffect.isSupported()` is `false`; `Modifier.blur()` and `graphicsLayer{renderEffect=...}` do nothing. `compose/ui/ui/src/sdlRendererMain/.../graphics/shadow/Blur.native.kt:33` · `Paint.setBlurFilter` NOP. **Nice-to-have (Windows visual parity).**
- Gradients render real ramps, but `TileMode` is dropped: `t` is clamped, so `Repeated`/`Mirror`/`Decal` all behave as `Clamp` (`Sdl3DrawScope.kt:1070` samplers; `CanvasPaintActuals.native.kt:66`). **Nice-to-have.**
- `compose/ui/ui/src/sdlRendererMain/.../graphics/CanvasPaintActuals.native.kt:106` · `ActualImageShader`/`ActualCompositeShader` are stubs; image/composite `ShaderBrush` degrades to solid fill (white/black). **Nice-to-have.**
- `compose/ui/ui/src/sdlRendererMain/.../graphics/ColorFilter.sdl.kt:13` · tint/color-matrix/lighting color filters are empty stubs; only `BlendModeColorFilter` tint on image blits is honored (`Sdl3Canvas.kt:1251`). ColorMatrix (grayscale/saturation) and all filters on shapes are inert. **Nice-to-have.**
- BlendMode: only `SrcOver` (plus a `Clear` special case) composites; `Multiply`/`Screen`/`Plus`/etc. are ignored (`Sdl3DrawScope.kt:206`, `Sdl3Canvas.kt:926`). Note `BlendMode.isSupported()` misleadingly returns `true`. **Nice-to-have.**
- `compose/ui/ui/src/sdlRendererMain/.../renderer/sdl/Sdl3Canvas.kt:436` · `saveLayer` has no offscreen buffer; layer alpha is multiplied into each primitive, so overlapping content double-composites (wrong group opacity) and the layer paint's colorFilter/blendMode/renderEffect are dropped. **Nice-to-have (blocker for correct group-alpha over overlapping content).**
- `compose/ui/ui/src/sdlRendererMain/.../renderer/sdl/Sdl3Canvas.kt:509` · `clipPath` degrades an arbitrary path to its bounding box; rotated/sheared `clipRect` collapses to an AABB. Non-rect clips leak. (Rounded/difference clips are real, with feathered AA.) **Nice-to-have.**
- Stroke joins (`StrokeJoin`) are never applied, so polyline corners notch; `StrokeCap.Square` falls back to butt (`Sdl3DrawScope.kt:380,342`). **Nice-to-have / cosmetic.**
- `PathFillType` (NonZero vs EvenOdd) is mostly ignored beyond a 2-contour border case; interior holes and self-intersections are not cut out, and concave fills self-overlap (`Sdl3DrawScope.kt:366,717`). **Nice-to-have.**
- `compose/ui/ui/src/sdlRendererMain/.../renderer/sdl/Sdl3Canvas.kt:1276` · `drawPoints`/`drawRawPoints`/`drawVertices` are NOP; point-mode drawing and custom vertex meshes render nothing. **Nice-to-have.**
- Images under a rotated layer are not rotated (`SDL_RenderTexture` is axis-aligned, `Sdl3Canvas.kt:1264`); stroked non-square ovals stroke as a circular ring (`Sdl3DrawScope.kt:527`); `SdlImageBitmap.readPixels` is a no-op (`Sdl3Offscreen.kt:117`). **Cosmetic / nice-to-have.**
- Drop shadow is approximated (9-slice / stacked rings, not a gaussian blur; ambient vs spot largely collapsed) at `Sdl3Canvas.kt:785`; AA is a ~1px geometry fringe rather than analytic coverage. **Cosmetic.**

## K. GraphicsLayer / image

- `compose/ui/ui/src/sdlRendererMain/.../graphics/CanvasPaintActuals.native.kt:151` · `createImageBitmap(bytes)` throws `UnsupportedOperationException`. (`GraphicsLayer.toImageBitmap` and `snapshotBgra` do work on both renderers.) **Nice-to-have.**
