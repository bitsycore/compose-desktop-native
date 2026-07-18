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
documented Windows fidelity/feature tier**, so SDL-leg visual gaps (section H)
are lower priority than the cross-cutting platform gaps that affect every
backend including Skia on macOS/Linux.

Renderer-internal cleanups (the deferred D2 shadow-lighting split, D3 matrix
dedupe) live in [RENDERER.md](RENDERER.md#6-remaining-and-future-work), not
here.

## Completed

- **Locale / i18n** (`5d03711f`) — `Locale.current` / `LocaleList.current` read the
  OS locale via SDL, unlocking the 40+ Material 3 translations. The date/number
  format tail remains (section C).
- **IME text input** (`500c49e4`) — a real `PlatformTextInputSession` routes SDL
  committed + composing text to the focused field (commit replaces the
  composition); `SDL_SetTextInputArea` positions the OS candidate window.
- **Cursor icons** (`1fcc0828`) — `Modifier.pointerHoverIcon` drives the OS cursor
  via SDL (I-beam over text, hand over links).
- **WindowInfo** (`bad23073`) — `isWindowFocused` + `containerSize` / `containerDpSize`
  report real values (were hardcoded `true` / `Zero`).
- **ImageBitmap from bytes** (SDL leg) — `ByteArray.decodeToImageBitmap()` /
  `createImageBitmap(bytes)` decodes via SDL3_image; was
  `UnsupportedOperationException`. Verified `demo --imagebytestest` on both legs.

---

## Release-blocking shortlist for stable 1.12

The cross-cutting items to weigh first (all detailed below):

1. **No copy/paste context menu** (section B). Keyboard copy works; there is no
   right-click or selection toolbar (the desktop path is the right-click context
   menu, upstream TODO CMP-7819).
2. **Accessibility is entirely absent** (section A). Decide whether stable
   requires any screen-reader support at all.
3. Fonts (section D): compose-resources `Font(Res.font.x)` works on both legs, and
   `FontFamily.Monospace` now renders NotoSansMono. Remaining: `Serif`/`Cursive`
   generics (no bundled font) and the androidx `Font(bytes)`/`PlatformFontLoader`
   path. Nice-to-have, not a blocker.

---

## A. Accessibility (entirely absent)

No accessibility bridge exists. A real `SemanticsOwner` is built but never
surfaced to any OS accessibility API (no NSAccessibility / UIA / AT-SPI, no SDL
a11y). Screen readers get nothing.

- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:340` · `semanticsOwner` is instantiated but never read; no traversal into a platform a11y tree. A real a11y controller would walk the semantics tree and map roles/labels/actions to a native AT bridge. **Blocker for a11y.**
- `compose/ui/ui/src/nativeMain/.../platform/CompositionLocals.native.kt:31` · `LocalPlatformScreenReader` default throws (`error(...)`); never provided by any window. Screen-reader-active queries are unavailable. **Blocker for a11y.**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:297` · `accessibilityManager` implements only `calculateRecommendedTimeoutMillis` (passthrough); no announce/focus/event surface. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../semantics/SemanticsRegion.native.kt:14` · `StubSemanticsRegion.intersect()`/`difference()` hardcode `false`; only `set`/`bounds` work. Region math for semantics culling is inert. **Nice-to-have.**

## B. Text toolbar and context menus (copy / paste / select-all)

- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:311` · `textToolbar` is a stub: `showMenu`/`hide` NOP, `status` always `Hidden`. NOTE: the floating toolbar is gated on `isInTouchMode`, so on desktop (mouse) it never shows anyway — the desktop path is the right-click context menu below. **Nice-to-have (touch only).**
- `compose/foundation/.../text/selection/SelectionActuals.native.kt:42` · `addSelectionContainerTextContextMenuComponents` NOP (upstream TODO CMP-7819). No right-click copy/select-all menu for `SelectionContainer`. **Blocker for desktop text UX.**
- `compose/foundation/.../text/selection/TextFieldSelectionManager.native.kt:24` and `compose/foundation/.../text/input/internal/selection/TextFieldSelectionState.native.kt:36` · `addBasicTextFieldTextContextMenuComponents` NOP for both the legacy and state-based `BasicTextField`. **Blocker for desktop text UX.**

## C. Locale / i18n tail (needs locale-aware DATA, no ICU on K/N)

`Locale.current` / `LocaleList.current` are wired to the OS (see Completed). The
rest needs month/weekday name tables and date/number patterns, which Kotlin/Native
has no ICU for; SDL reports language + country only (no script subtag, no
calendar/clock/number prefs). These require a bundled CLDR subset or a K/N i18n lib.

- `compose/material3/.../CalendarLocale.native.kt:20` · locale-agnostic stub: `toString()` fixed `"en"`, single shared instance, `equals` treats all instances as equal. A separate type from `ui.text.intl.Locale`; needs its own wiring so `CalendarModel`/`PlatformDateFormat` can vary by locale. Inert until `PlatformDateFormat` is localized. **Nice-to-have (DatePicker/TimePicker).**
- `compose/material3/.../internal/PlatformDateFormat.native.kt` · multiple hardcodes:
  - `:27` `weekdayNames` hardcoded English full/short names (DatePicker shows English day initials regardless of locale). **Blocker for localized DatePicker.**
  - `:32` `formatWithPattern`/`formatWithSkeleton` ignore the pattern/skeleton and always emit ISO `yyyy-MM-dd`. **Blocker for localized DatePicker.**
  - `:25` `firstDayOfWeek` hardcoded `1` (Sunday); locale-dependent. **Nice-to-have.**
  - `:44` `parse()` only accepts ISO `yyyy-MM-dd`, ignoring pattern/locale. **Nice-to-have.**
  - `:62` `getDateInputFormat()` hardcoded `yyyy-MM-dd` / `'-'`. **Nice-to-have.**
  - `:65` `is24HourFormat()` hardcoded `true`. **Cosmetic.**
- `compose/ui/ui/src/nativeMain/.../text/platform/NativeStringDelegate.native.kt:17` · `toUpperCase`/`toLowerCase` use locale-independent stdlib casing; the `locale` param is ignored (wrong for Turkish dotted/dotless i, etc.). **Cosmetic.**
- `compose/ui/ui/src/nativeMain/.../text/intl/Locale.native.kt` · `Locale.isRtl()` not implemented; the pipeline is LTR-only (`SdlParagraph.native.kt` `getBidiRunDirection` always returns `Ltr`; owners/DrawScopes default `LayoutDirection.Ltr`). No RTL shaping. **Nice-to-have.**
- No `NumberFormat` / currency / decimal-grouping actual exists; numbers render via `toString()` with `.` decimal and no grouping regardless of locale. **Cosmetic.**

## D. Font resolution

**Custom fonts via compose-resources work.** `org.jetbrains.compose.resources.Font(Res.font.x)`
loads the bytes and registers them with the project font registry (IconFont → NamedFont)
on both renderer legs (`FontResources.sdl.kt`, shared by the skiko leg via a srcDir
alias), so the standard CMP way to bundle a font renders correctly.

- [x] **`FontFamily.Monospace` DONE** — `NamedFont.projectFontName` maps generic families to
  `"generic:<name>"`; `com.compose.sdl.text.registerGenericFonts` (called from
  `installGlobals`) registers the bundled NotoSansMono under `generic:monospace`. The demo
  Zip task bundles the font only when the app references `FontFamily.Monospace` (mirrors the
  Material Symbols scan). Falls back to the default sans if not bundled. Verified
  `demo --fonttest` on both legs.
- `FontFamily.Serif` / `FontFamily.Cursive` still collapse to the default sans — no bundled
  serif/cursive font yet (`downloadNotoFonts` fetches Sans + SansMono only). Register one under
  `generic:serif` / `generic:cursive` the same way to enable them. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../text/font/FontFamilyResolver.native.kt:32` · `SdlPlatformFontLoader.loadBlocking`/`awaitLoad` are NOP; the androidx `PlatformFontLoader` path (`ResourceFont` / `Font(bytes)` / `LoadedFontFamily`) doesn't load. Use the working compose-resources `Font()` instead. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../text/font/FontFamilyResolver.native.kt:38` · `PlatformFontFamilyTypefaceAdapter.resolve` returns `TypefaceResult.Immutable(Unit)` (the renderer resolves by name, so harmless). **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../text/font/FontFamilyResolver.native.kt:69` · deprecated `createFontFamilyResolver(fontResourceLoader)` ignores the supplied loader. **Cosmetic.**

## E. Text rendering details

- `compose/ui/ui/src/nativeMain/.../text/SdlParagraph.native.kt:376` · `drawNativeText` accepts and ignores text `Shadow`, stroke `DrawStyle`, and non-`SrcOver` blend, so those never render on text. **Cosmetic.**
- `compose/foundation/.../text/StringHelpers.native.kt:31` and `compose/ui/ui/src/nativeMain/.../text/CharHelpers.native.kt:14` · `findPrecedingBreak`/`findFollowingBreak` (and `offsetByCodePoints`) do a codepoint/surrogate walk only, with no ICU grapheme clusters, so caret movement and backspace split combining marks and emoji ZWJ sequences. **Nice-to-have.**

## F. Drag and drop

Drop INTO the window (files + text) is fully wired and works. Drag OUT does not.

- `compose/ui/ui/src/nativeMain/.../draganddrop/Sdl3DragAndDropOwner.kt:38` · `requestDragAndDropTransfer` is a NOP and `isRequestDragAndDropTransferRequired` returns `false`; `Modifier.dragAndDropSource` compiles but no OS-level drag leaves the window. Needs per-OS native work (NSDraggingSession / DoDragDrop / XDND); SDL3 has no portable start-drag. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../draganddrop/Sdl3DragAndDropOwner.kt:119` · incoming transfer carries only file paths + a text blob; no image/URL/custom-MIME payloads. **Cosmetic.**
- `compose/foundation/.../draganddrop/DragAndDropSource.native.kt:36` · no drag-shadow / drag-image feedback for internal DnD (upstream skiko renders one). **Cosmetic.**

## G. Window and platform info

- Multi-monitor: no display enumeration or per-monitor placement anywhere (`SDL_GetDisplays`/`SDL_GetDisplayBounds`/`SDL_GetDisplayForWindow` unused). **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../platform/PrefetchLocals.native.kt:18` · `LocalPlatformPrefetchScheduler` default is a NOP scheduler; lazy lists skip ahead-of-time item composition (correctness fine, possible scroll-in jank). **Nice-to-have (perf).**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:324` · `autofill`/`autofillManager` are null; `requestAutofill` NOP. **Nice-to-have.**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:279` · `hapticFeedBack.performHapticFeedback` NOP (no desktop pointer haptics API). **Cosmetic.**
- `compose/ui/ui/src/nativeMain/.../node/impl/ComposeOwner.kt:287` · deprecated `clipboardManager` `setText`/`getText` NOP, and `clipboard.getClipEntry` null. Harmless: the real `LocalClipboard` (text + PNG image via SDL3) works. **Cosmetic.**
- `compose/foundation/.../text/input/internal/selection/TextFieldSelectionState.native.kt:44` · `ClipboardPasteState.hasClip` aliased to `hasText`, so image-only clipboard is not detected for the paste affordance. **Cosmetic.**

## H. SDL renderer graphics (Windows / `-Prenderer=sdl3` only)

These affect only the SDL leg. On macOS/Linux the Skia leg implements them
correctly, and JVM is the documented Windows fidelity tier, so these are lower
priority under G1. Listed because SDL is the shipped Windows renderer.

- `compose/ui/ui/src/sdlRendererMain/.../graphics/PathEffect.sdl.kt` · **DASH DONE** — `dashPathEffect` now carries its pattern and `Sdl3DrawScope.dashPolyline` splits stroked geometry into dashes for `drawLine` and `drawPath(Stroke)` (paint `pathEffect` threaded through `Sdl3Canvas.drawLine`/`styleFor`). Verified `demo --dashtest`. `corner`/`chain`/`stamped` remain NOP. **Nice-to-have.**
- `compose/ui/ui/src/sdlRendererMain/.../graphics/RenderEffect.sdl.kt:16` · `RenderEffect.isSupported()` is `false`; `Modifier.blur()` and `graphicsLayer{renderEffect=...}` do nothing. `compose/ui/ui/src/sdlRendererMain/.../graphics/shadow/Blur.native.kt:33` · `Paint.setBlurFilter` NOP. **Nice-to-have (Windows visual parity).**
- **DONE** — gradient `TileMode` now honoured: `Sdl3DrawScope`'s linear/radial samplers apply `Repeated` (wrap), `Mirror` (reflect), `Decal` (transparent outside), `Clamp` unchanged (`tileT` + `gradientTileMode`). Verified `demo --tilemodetest`. **Nice-to-have.**
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
