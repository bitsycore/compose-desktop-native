# Compose-fidelity handoff

Status + how-to-continue for the ongoing pass that makes core's
`androidx.compose.{foundation,ui,animation}` surface mirror official Compose.
Read this with the **Compose API Fidelity** section of `CLAUDE.md` (rules) — this
file is the *current state*; some "known-diverging / TODO" notes in CLAUDE.md are
now done (see below).

## Strategy (the rule we follow now)

- **Reusable-from-upstream low-level types → vendor verbatim**, kept in their
  official `androidx.compose.*` package (Dp, Color, Offset, TextUnit, …).
- **Anything with no official public equivalent → relocate to
  `com.compose.desktop.native.*`** (the render-bridge / engine / custom desktop
  API). The official extension that *builds* it stays in `androidx.*`.
- Match upstream signatures/representation where a type is official-named.

### Why we can't vendor the Skia renderer wholesale

Upstream's Skia code lives under `compose/ui/ui-graphics/src/skikoMain/` as
`actual` declarations of commonMain `expect` types (`Canvas`, `Paint`, `Path`,
`Shader`, `ImageBitmap`, `Matrix`, `PathIterator`, `RenderEffect`,
`ColorFilter`, `GraphicsContext`, …) — i.e. the *engine* glue. Pulling them in
forces also pulling in the matching commonMain `expect`s and the rest of the
engine (LayoutNode / DrawScope / Modifier-node tree / focus / pointer pipeline)
that our renderer specifically doesn't use. Our `SkiaRenderer` paints into a
raw `org.jetbrains.skia.Canvas` while walking our custom `LayoutNode` tree and
reading our simplified `PathCommand` list — keeping that bridge thin is the
whole point. Conclusion: stay with the hand-written renderers; only vendor
*data* types from the skiko source set (e.g. the `LineBreak` / `TextMotion`
value-class actuals, which are pure data and contain no Skia refs).

### Why we don't put `expect Canvas/Paint` in commonMain and provide a SDL3 actual alongside the Skia one

Tempting idea: vendor `expect class Canvas` etc. into `:core/commonMain`,
then have the Skia and SDL3 renderer source sets each provide their own
`actual`s. Even now that both renderers live in `:core` (so Kotlin's
"actuals must live in the same module" rule is satisfied), it's still the
wrong design:

1. *`skikoRendererMain` and `sdlRendererMain` are peers, not alternatives
   visible from the same compilation.* Only one of them is attached to any
   given target via `dependsOn` (mingwX64 → sdl, macOS/Linux → either,
   chosen by `-Prenderer`). There's no "common" source set that sees both
   at once — so an `expect` in `nativeMain` would need an `actual` in
   **every** target chain, which means we'd duplicate the declaration in
   `skikoRendererMain` and `sdlRendererMain` anyway. Picking one renderer
   per target is a *Gradle source-set wiring* decision, not a Kotlin
   `expect`/`actual` decision.
2. *We don't need it.* Our renderer abstraction is `RenderBackend` (a
   plain interface in `:core/commonMain`) plus a top-level
   `createRenderBackend(...)` defined identically in each renderer source
   set. The linker picks the one from the active source set automatically
   because only one is on the compilation classpath. That's the same
   "select an actual at build time" trick — just at the factory-function
   granularity, not the `Canvas`/`Paint` type granularity. App code never
   holds an `androidx.compose.ui.graphics.Canvas` directly anyway; it draws
   via the official-shaped `DrawScope`, and the renderer (which IS
   renderer-specific code) is the one talking to Skia/SDL3 raw.

## The vendor pipeline — `tools/compose-fork/`

- `compose-ref.txt` — pinned upstream commit of JetBrains/compose-multiplatform-core
  (currently `1be9d64a` = `v1.12.0-beta01+dev4324`).
- `<module>/compose-fork.txt` — per-module manifest (co-located with the
  module's `build.gradle.kts`). Currently only `:core` carries one; a future
  `:material3` module would add its own. Each line is
  `<upstream-path>  <dest-relative-to-this-module>`.
- `sync.sh` — clones the ref (sparse-checkout is the union of upstream modules
  referenced by the selected manifests) to `$CMP_REF` or `../cmp-ref`, then
  copies each manifest entry **byte-for-byte verbatim** into
  `<module>/src/vendor/{common,native}/kotlin/`. `bash sync.sh :core` targets
  one module; `bash sync.sh` (no arg) hits every manifest under the repo.
  Idempotent; `git diff` after a re-sync shows upstream drift.
  **Never hand-edit `**/src/vendor/**`** — see the README under `tools/compose-fork/`.

### On macOS (first time)

```bash
# clone-or-reuse the upstream ref + (re)vendor; clone lands at ../cmp-ref
tools/compose-fork/sync.sh
# fidelity check (auto-finds ../cmp-ref now; or pass a path / set CMP_REF)
./gradlew apiDump && python3 scripts/compose-fidelity-check.py
# run the apps on the default Skia renderer
./gradlew :apidemo:runDebugExecutableMacosArm64
./gradlew :demo:runDebugExecutableMacosArm64
```

## ⚠️ Verify on macOS (Skia path was unreachable on Windows)

`core/src/skikoRendererMain/` is **not** in the mingwX64 build graph (the
`skikoRenderer*` source sets aren't even created for mingwX64), so every
edit below was compile-checked only via the identical sdl3 change — macOS
is the first place Skia actually compiles + runs. Launch `:demo` and
`:apidemo` and check:

- text renders at the right **size + alignment** (TextUnit + the `TextAlign` `else`
  branches added in `SkiaTextRenderer`)
- **colours** correct (vendored `Color` = packed sRGB value class; `r8/g8/b8/a8`
  are now extensions imported in `SkiaTextRenderer`)
- **strokes** (canvas screen) — `StrokeCap` value class + `else` in `SkiaDrawScope`
- **graphicsLayer / alpha / transforms** + general layout (`GraphicsLayerModifier`,
  `LayoutNode` moved packages; `skikoRendererMain` imports updated)
- **selection**: open a large JSON response body in apidemo, drag-select, Ctrl/Cmd+C
  (the selection-aware BasicText fix)

All of the above already verified working on Windows/SDL3.

## Done (divergence 913 → 408; 249 vendor files)

- **Vendored verbatim** (0 divergence): `ui.util` (now incl. `ListUtils`
  giving us `fastFold` / `fastJoinToString` / `fastMap` / `fastAny` /
  `fastForEach` etc.), `ui.geometry`, all `ui.unit` value types
  (Dp/TextUnit/Constraints/Int*/Velocity/Dp{Offset,Size,Rect}) +
  `LayoutDirection`, `ui.graphics.Color` + the whole `colorspace` subsystem +
  Float16, `TileMode`, `StrokeCap`, `StrokeJoin`, `BlendMode`, `ClipOp`,
  `FilterQuality`, `PaintingStyle`, `PointMode`, `VertexMode`, `PathFillType`,
  `PathOperation`, `Degrees`, `ColorMatrix`, `Shadow`, `LayerOutsets`,
  `Bezier` (+ companion `PathSegment`), `Vertices`, `Interpolatable`,
  `ui.text.TextRange`, `TextAlign`/`TextOverflow`/`TextDecoration`,
  `ResolvedTextDirection`/`TextDirection`/`TextGeometricTransform`/`TextMotion`/
  `LineBreak`/`Hyphens`/`BaselineShift`/`TextIndent`, `FontStyle`/`FontWeight`,
  `ui.layout.ScaleFactor`/`ContentScale`/`AlignmentLine`,
  `ui.graphics.RectangleShape` (after the Outline/Shape reshape),
  `ui.Alignment` (incl. `BiasAlignment`, `BiasAbsoluteAlignment`,
  `AbsoluteAlignment`), `ui.UiComposable`, `ui.ComposeUiFlags`,
  `ui.unit.ComposeUiUnitFlags`, `ui.FrameRateCategory`, `ui.state.ToggleableState`,
  `ui.draw.{Alpha, Rotate, Scale}` (one-liners over `graphicsLayer`),
  `foundation.BorderStroke`, the full `foundation.shape` package
  (`CornerSize`, `CornerBasedShape`, `RoundedCornerShape` with per-corner
  `CornerSize` + RTL mirroring + `lerp`, `CircleShape`, `CutCornerShape`,
  `AbsoluteRoundedCornerShape`, `AbsoluteCutCornerShape`, `GenericShape`),
  `foundation.interaction.{Interaction,
  InteractionSource, DragInteraction, HoverInteraction, FocusInteraction}`,
  `foundation.gestures.Orientation`, `foundation.lazy.LazyListItemInfo`,
  `foundation.layout.LayoutScopeMarker`, `animation.core.AnimationEndReason`/
  `Easing`/`EasingFunctions`/`Preconditions`. Plus the experimental/internal
  opt-in annotations: `ExperimentalComposeUiApi`, `ExperimentalGraphicsApi`,
  `ExperimentalFoundationApi`, `InternalFoundationApi`, `InternalAnimationApi`,
  `InternalTextApi`, `ExperimentalTransitionApi`, `ExperimentalAnimationSpecApi`,
  `ExperimentalDeferredTransitionApi`. (`Sp` was migrated to the real
  `TextUnit`.)

  **Note on transitive AndroidX deps**: `androidx.collection:collection:1.5.0`
  and `androidx.annotation:annotation:1.9.1` come for free via
  `org.jetbrains.compose.runtime:runtime`, so we can vendor files using
  `FloatFloatPair`, `MutableScatterSet`, `@RestrictTo`, `@FloatRange`, etc.
  without any extra Gradle wiring.
- **Relocated to `com.compose.desktop.native.*`** (no official equivalent):
  the ~22 `Modifier.Element` classes + `GraphicsLayerModifier` → `.element`;
  `LayoutNode` + `NodeApplier` (+ internal node `MeasurePolicy`) → `.node`;
  `PathCommand` (render-bridge sealed type, no upstream equivalent) →
  `.graphics`; `TextMeasurer` / `WrappedText` / `TextRendererCapabilities` +
  `currentTextMeasurer` / `currentViewportHeight/Width` → `.text` (upstream
  has its own `androidx.compose.ui.text.TextMeasurer` with a different
  shape — class returning `TextLayoutResult`); `PointerInputElement` /
  `PointerInputScopeImpl` / `PointerInputEvent` + `KeyModifiers` → `.input`
  (upstream's `PointerInputEvent` is `internal expect` with different
  shape; modifier system uses Node not Element; `KeyModifiers` has no
  upstream equivalent — modifiers are encoded into `KeyEvent` itself);
  `ScrollAnimator` → `.scroll`;
  Popup host infra → `.window`; `ColorRun` + `SelectableText`/
  `LocalInSelectionContainer` (folded into selection-aware
  BasicText) → `.text`; `InfiniteTransition.animateDp` → `.animation`.
- **Reshaped to match official**: `PaddingValues` → interface
  (`calculate*Padding` + `calculateStart/EndPadding`); `ToggleableState`→`ui.state`,
  `PaddingValues`→`foundation.layout` placement.
- **Kept in androidx as documented exceptions**: `Clipboard` + `currentClipboard`,
  `currentImageLoader`, `currentTextMeasurer` (mutable backend-wiring globals,
  same pattern); the `ui.res` `Res`/`ImageLoader` stand-in.

## Remaining (per the relocate-or-match directive)

**Safe relocations → native.\*** (pure package moves, the proven pattern):
- `foundation.ScrollbarAdapter` stays in `androidx.compose.foundation` —
  upstream's `Scrollbar.skiko.kt` exposes it there too; our hand-written
  impl uses the same package + same names, just with reduced behavior
  (only `ScrollState` adapter, no `LazyListState` / `LazyGridState` /
  `TextFieldScrollState` overloads).
- (bigger, app-facing) the `ui.res` system (`Res`/`ImageLoader`/`ResourceKind`/…)
- (`PathCommand`, `TextMeasurer` package, `PointerInputElement` package
  already relocated.)

**Match-upstream reshapes (done)**: `BorderStroke` wraps a `Brush`;
`Outline`/`Shape`/`Density` — `Shape.createOutline(size, layoutDirection,
density)`, `Outline.Rectangle(rect)`, `Outline.Rounded(roundRect)`,
`Outline.Generic(path)`; `SolidColor.color` → `.value`; Animation specs
(`TweenSpec` / `SnapSpec` / `SpringSpec` / `RepeatableSpec` /
`InfiniteRepeatableSpec`) `data class` → plain class with manual
equals/hashCode; `SpanStyle` / `ParagraphStyle` / `TextStyle` same;
`AnnotatedString.Range<T>` nested + `tag: String` field; `AnimationResult`,
`Stroke`, `FontVariation`, `TextFieldValue`, `KeyEvent`, `PointerEvent`
all `data class` → plain class (drops `component*`/`copy` from the surface).

**Project-only helpers moved to `com.compose.desktop.native.*`** (the
"divergence into extensions" pass): the Color extras
(`r8`/`g8`/`b8`/`a8`/`lighten`/`darken`/`blend`) are now in
`com.compose.desktop.native.graphics.ColorExtensions.kt`; renderers and
material import them from there. Previously they lived in
`androidx.compose.ui.graphics` and inflated that namespace.

**Runtime-critical (done — needs screenshot-test)**: `KeyEvent` /
`PointerEvent` / `PointerEventType` / `PointerButton` enum/data-class →
official value classes. PointerEvent landed in an earlier pass; KeyEvent now
also fully reshaped (Key vendored as value class with 272-key Companion, SDL3
scancode → Key mapping in SDL3EventMapper, KeyEventDispatch wrapper removed —
`Modifier.onKeyEvent` takes `(KeyEvent) -> Boolean` directly).
`BasicText`/`BasicTextField` also reshaped to `style: TextStyle` and
`cursorBrush: Brush` matching upstream signatures.

**Out of scope**: `material` (no cmp-ref material clone), the intentional-custom
`AnimationSpec` lerp-lambda design, `ScrollState`/`Lazy` suspend reshapes.

## Verifying / re-checking

`./gradlew apiDump` regenerates `*/api/*.klib.api`; `apiCheck` guards drift.
`python3 scripts/compose-fidelity-check.py` lists our `androidx.compose.*`
decls not in upstream (the divergence number above). The big remaining buckets
are `material` (294, out of scope) + the surface-match/reshape items above.
