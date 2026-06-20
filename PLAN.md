# PLAN.md

What's still missing on top of the current ComposeNativeSDL3 implementation.
Phases 0–6 of the original TextField roadmap are done (BasicTextField,
selection, clipboard, undo/redo, multi-line, soft-wrap, Material chrome).
Skia + SDL3 renderers, HiDPI, Window API, and the Sdl3.* driver picker
are all in.

---

## TextField — Phase 7 (IME)

The single biggest feature gap. SDL3 already gives us text-input events;
the IME composition handling isn't wired through.

- `SDL_StartTextInput(window)` on focus, `SDL_StopTextInput(window)` on
  unfocus (currently always on).
- `SDL_EVENT_TEXT_EDITING` → in-progress composition; render with the
  Compose "composition" range styled (underlined).
- Replace composition range on `SDL_EVENT_TEXT_INPUT` commit.
- `SDL_SetTextInputArea(window, rect, cursor)` per frame so the OS IME
  candidate window pops near the cursor.
- Dead keys (´ + e = é) — comes through `SDL_EVENT_TEXT_EDITING` then
  `SDL_EVENT_TEXT_INPUT`.
- CJK composition + candidate windows.
- AnnotatedString-like styling for the composition range underline.

## TextField — Phase 8 (Behaviour polish)

- `KeyboardOptions(imeAction, keyboardType, autoCorrect, capitalization)`
- `KeyboardActions(onDone, onSearch, onNext, …)`
- `visualTransformation` (`PasswordVisualTransformation`)
- `decorationBox` slot for fully custom chrome
- `onTextLayout` callback exposing line metrics + cursor positions
- `interactionSource` (hover / press / focus)
- Cursor-into-view auto-scroll inside tall TextField

## LazyColumn — real virtualization

Currently composes all items each frame; works fine until ~hundreds of
items. Need:

- Skip composition for items outside the viewport.
- Item height caching keyed by index.
- `LazyListState` (firstVisibleItemIndex, scrollOffset) + animations.
- `key { }` for stable item identity across data changes.

## Renderer / backend

- **Sdl3 hinting parity** — Skia uses light hinting + subpixel positioning;
  SDL3_ttf defaults to normal grayscale hinting. Try
  `TTF_SetFontHinting(font, TTF_HINTING_LIGHT)` to bring them closer.
- **Sdl3 rounded-rect borders** — current ring strip is fine at small
  radii, gets visibly faceted at large ones (try
  `RoundedCornerShape(50)`). Bump segment count or move to a stroked
  path approach.
- **Sdl3 shape clipping** — `SDL_SetRenderClipRect` is rectangular only,
  so clipping a `RoundedCornerShape` reveals corners on children that
  draw outside the shape's curve. Need a stencil / mask texture path.
- **Window resize**: bridges (Metal especially) rebuild their drawable
  every frame; CPU + GL rebuild on resize. Make sure no leak under
  rapid resize.

## Build / tooling

- **Linux validation** — code compiles for `linuxX64` but hasn't been
  smoke-tested on a real Linux desktop with `libsdl3-dev` /
  `libsdl3-ttf-dev`. Run the demo, hit a few screens, fix anything broken.
- **Windows validation** — mingwX64 hasn't been smoke-tested either; the
  source compiles on macOS up to the cinterop step (which needs the
  Windows SDL3 headers). When running on Windows, verify:
  - SDL3.dll + SDL3_ttf.dll are next to the .kexe (or on PATH)
  - The fonts/ directory is copied next to the binary
  - `Sdl3.Auto` picks Direct3D 11 or 12, `--gpu=sdl3.d3d12` works
- **Configuration cache compatibility** — most of build.gradle is
  config-cache-friendly, but the gradle property switch for
  `-Prenderer=sdl3` doesn't invalidate the cache when toggled; you have
  to delete `.gradle/configuration-cache/` between switches.

## Smaller stuff

- `Modifier.weight(...)` for Row/Column.
- `Modifier.aspectRatio(...)`.
- `Modifier.alpha(...)` (renderer compositing).
- `LaunchedEffect`/`SideEffect`/`DisposableEffect` — partial coverage; audit.
- Snapshot of the final SDL3-renderer pixels still off by one pixel on
  the right edge of borders in some cases — investigate.
- True PNG screenshot output (currently writes BMP; Skiko Native has no
  encoder).
