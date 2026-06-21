# PLAN.md

Roadmap for bringing this Kotlin/Native subset of Compose closer to the
upstream `androidx.compose.foundation` / `.ui` / `.animation` surface.
Items are tackled in order; each one is its own commit. Strike through
when done.

## ✅ Step 0 — Modularisation: split Material out of :core

`:core` should be the "foundation + ui + animation" base of Compose.
Material widgets (`androidx.compose.material.*`) currently live in
`:core` but conceptually belong on top.

- New module `:material` (Gradle artifact id
  `compose-desktop-native-material`). Depends on `:core` via `api`.
- Move every file under
  `core/src/commonMain/kotlin/androidx/compose/material/` into the new
  module, preserving package names (so callers' imports don't change).
- Make `:window` re-export `:material` via `api` so existing app code
  that just depends on `:window` keeps working.
- Update `settings.gradle.kts` to include the new module.
- Verify the demo builds with both Skia and SDL3 renderers and that
  screenshots are pixel-identical to pre-split.

## Audit — what's missing (informs steps 1-10)

### `androidx.compose.foundation`

**Tier A (common patterns block on these)**
- `RowScope.weight()` / `ColumnScope.weight()` — confirmed absent.
- `pointerInput` + gesture DSL (`awaitPointerEventScope`,
  `awaitFirstDown`, `detectTapGestures`, `detectDragGestures`,
  `detectTransformGestures`). Currently only one-shot callbacks
  (`clickable`, `onPressed`, `onDrag`).
- `combinedClickable` — long-press / double-tap.
- `AnnotatedString` + `SpanStyle` / `ParagraphStyle` /
  `buildAnnotatedString` — no rich text today.
- `TextStyle` / `FontFamily` / `FontWeight` declarative font
  resolution — today `fontFamily: String?` is a raw name and
  `fontSize: Int`.
- `InteractionSource` / `Indication` — no shared press/hover/focus
  state object, no swappable visual feedback (ripple, focus ring).
- `LazyRow` — only LazyColumn exists.
- `LazyVerticalGrid` / `LazyHorizontalGrid` / `LazyStaggeredGrid` —
  also absent.

**Tier B**
- `Pager` / `HorizontalPager` / `VerticalPager` — page snapping.
- `stickyHeader` / `animateItemPlacement` inside LazyColumn.
- `SelectionContainer` — text selection across composables.
- `TextOverflow` — clip / ellipsis / visible.
- `VisualTransformation` — password masking, formatted input.
- `KeyboardOptions` / `KeyboardActions` — soft-keyboard hints.
- `draggable` / `scrollable` / `anchoredDraggable` — standalone gesture
  modifiers separate from scroll containers.

### `androidx.compose.ui`

**Tier A**
- `Modifier.layout { measurable, constraints -> ... }` — the
  foundational "write a custom layout from one composable" hook.
- Public `Layout(content, measurePolicy)` composable + the
  `MeasurePolicy` / `MeasureScope` / `Placeable` / `IntrinsicMeasurable`
  public API. Internals exist (LayoutNode) but aren't exposed.
- `Modifier.composed { ... }` — composition-aware modifier factory.
- `Path` (with `moveTo` / `lineTo` / `cubicTo` / `quadraticBezierTo` /
  `arcTo` / `close`) and `DrawScope.drawPath` / `clipPath`. Custom
  shapes are stuck on Rectangle / RoundedRect today.
- `Modifier.aspectRatio`, `.zIndex`, `.shadow`, `.rotate`, `.scale`,
  `.offset(IntOffset)` variants. `.rotate` / `.scale` are trivial
  wrappers over `graphicsLayer`.
- `FocusRequester` + `LocalFocusManager` — programmatic focus.
- `Modifier.onFocusChanged` / `onFocusEvent` as standalone modifiers.

**Tier B**
- DrawScope completeness: `drawOval`, `drawRoundRect`, `drawPath`,
  `drawText`, `drawImage`, `drawPoints`, plus transform DSL
  (`withTransform`, `rotate`, `translate`, `scale`, `inset`,
  `clipRect`, `clipPath`).
- `CutCornerShape`, `GenericShape`, `AbsoluteRoundedCornerShape`.
- `ColorFilter`, `BlendMode`, `RenderEffect` (Skia supports natively).
- `Modifier.drawWithCache`, `drawWithContent`, `paint(Painter)`.
- `Modifier.semantics`, `contentDescription`, `Role`, `testTag` (a11y +
  UI testing).
- `rememberSaveable` + `Saver` / `MapSaver` / `ListSaver`.
- `WindowInsets` (desktop has analogs — menubar inset, traffic-light
  overlap on macOS).
- `Rect`, `RoundRect`, `CornerRadius` — only `Offset` / `Size` exposed
  today.

### `androidx.compose.animation`

**Entire package — not implemented.** Biggest single gap.

- `animateFloatAsState`, `animateDpAsState`, `animateColorAsState`,
  `animateIntAsState`, `animateValueAsState<T>` — bread-and-butter.
- `Animatable<T>` — imperative handle for `snap` / `animateTo` /
  `animateDecay` / `stop`.
- `AnimationSpec` + `tween`, `spring`, `snap`, `keyframes`,
  `repeatable`, `infiniteRepeatable`.
- `InfiniteTransition` + `rememberInfiniteTransition`.
- `Transition`, `updateTransition` — coordinated multi-property
  animations.
- `Easing` + `LinearEasing`, `FastOutSlowInEasing`, `CubicBezierEasing`.
- `AnimatedVisibility`, `AnimatedContent`, `Crossfade`.
- `Modifier.animateContentSize`.

## Steps (ordered by impact / unblocking)

- [x] **Step 1 — `RowScope.weight()` / `ColumnScope.weight()`.** Small,
  blocks many layouts. Today there's no way to say "split the leftover
  space 1:2 between these two children". Add the standard upstream
  semantics: children with a `weight` first take their measured size of
  the cross axis, then split the remaining main axis proportionally;
  `fill = false` lets them stay at their min content size while still
  claiming weight share.

- [x] **Step 2 — Public `Layout` composable + `MeasurePolicy` API +
  `Modifier.layout { ... }`.** Opens the door to user-written custom
  layouts without forking `:core`. LayoutNode already does measure /
  place; this step is mostly exposing the right interfaces
  (`Measurable`, `Placeable`, `MeasureScope`) and a thin
  `Layout(content, measurePolicy)` composable that builds a LayoutNode
  with the provided policy. `Modifier.layout` wraps a child's measure
  in a user lambda.

- [x] **Step 3 — `androidx.compose.animation` core.** Biggest gap by
  user-facing impact. Eliminates the hand-rolled `LaunchedEffect {
  while(true) ... delay(16) }` loops (e.g. the M3 spinner). Concretely:
  `Animatable<T>`, `AnimationSpec` (`tween`, `spring`, `snap`,
  `keyframes`, `repeatable`, `infiniteRepeatable`), `Easing`
  (`LinearEasing`, `FastOutSlowInEasing`, `CubicBezierEasing`), the
  `animate*AsState` family for `Float` / `Dp` / `Int` / `Color`,
  `InfiniteTransition` / `rememberInfiniteTransition`. Driven by the
  existing `SDL3FrameClock` (already a `MonotonicFrameClock`).
  Wrappers like `AnimatedVisibility`, `Crossfade`, and
  `Modifier.animateContentSize` come after the core.

- [x] **Step 4 — `pointerInput` + gesture DSL.** Unlocks custom
  interactions. `Modifier.pointerInput(*keys) { /* PointerInputScope */
  }`, `awaitPointerEventScope`, `awaitFirstDown`,
  `waitForUpOrCancellation`, `detectTapGestures(onPress, onDoubleTap,
  onLongPress, onTap)`, `detectDragGestures(onDragStart, onDrag,
  onDragEnd)`, `detectTransformGestures(onGesture)`,
  `combinedClickable`. Routed off the existing `ComposeWindow` event
  dispatch.

- [ ] **Step 5 — `Path` + `drawPath` + DrawScope completion +
  `CutCornerShape` / `GenericShape`.** Add `Path` with `moveTo`,
  `lineTo`, `quadraticBezierTo`, `cubicTo`, `arcTo`, `relativeLineTo`
  etc.; `DrawScope.drawPath` / `clipPath` / `drawOval` /
  `drawRoundRect` / `drawImage` / `drawText` / `drawPoints`; transform
  DSL (`withTransform { rotate / translate / scale / inset / clipRect /
  clipPath }`). Skia maps almost 1:1; SDL3 tessellates paths into
  triangle fans.

- [ ] **Step 6 — `AnnotatedString` + `TextStyle`.** Mixed-style runs,
  inline icons, hyperlink-style spans. `AnnotatedString` with
  `SpanStyle` / `ParagraphStyle`, `buildAnnotatedString`, `TextStyle`
  aggregate (`fontWeight`, `fontStyle`, `letterSpacing`, `lineHeight`,
  `textDecoration`), declarative `FontFamily` / `FontWeight`,
  `TextOverflow` (clip / ellipsis / visible).

- [ ] **Step 7 — Thin Modifier convenience wrappers.** Small commit,
  immediately useful. `Modifier.rotate(degrees)`, `.scale(scaleX,
  scaleY)`, `.aspectRatio(ratio, matchHeightConstraintsFirst)`,
  `.zIndex(value)`, `.shadow(elevation, shape, clip)`,
  `.offset(IntOffset)`. Most are one-line wrappers over the existing
  `graphicsLayer`; shadow needs a Skia drop-shadow paint + an SDL3
  blurred-quad fallback.

- [ ] **Step 8 — `FocusRequester` / `FocusManager`.** Programmatic
  focus control (today focus only follows clicks). `FocusRequester`
  with `.requestFocus()`, `LocalFocusManager`,
  `focusProperties { next = ..., previous = ... }`, `onFocusChanged` /
  `onFocusEvent` standalone modifiers.

- [ ] **Step 9 — `LazyRow` + `LazyVerticalGrid`.** Extends the lazy
  story. `LazyRow` reuses LazyListState; `LazyVerticalGrid` adds
  `GridCells.Fixed(count)` and `GridCells.Adaptive(minSize)`.
  `stickyHeader` inside `LazyListScope` as a follow-up.

- [ ] **Step 10 — `InteractionSource` + `Indication`.**
  `MutableInteractionSource` (Press / Hover / Focus emissions),
  `Indication` interface, default ripple indication, default
  focus-ring indication. Wire into `clickable` / `focusable` /
  `hoverable` so visual feedback is uniform and pluggable.

## How each step lands

1. One commit per step (no bundling).
2. Each step adds a small demo screen (where visually relevant) so the
   feature is exercised end-to-end on both Skia and SDL3.
3. After each step, run `--screenshot` on both renderers and confirm
   parity with what changed.
4. CLAUDE.md gets a short note for any new public API surface that
   reshapes how downstream code consumes the library.
