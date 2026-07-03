# Phase 9 — Continuation Guide

State of the `phase9` branch after the current sprint (116 commits ahead of main,
Buttons runtime hash `ce15decb83c3bb7ba44660cd9002408c` preserved throughout).

Full history / rationale lives in [`NODE_ENGINE_PORT.md`](NODE_ENGINE_PORT.md).
This file is the shorter “here’s what a fresh session should read + do first”.

## Latest sprint — interaction + selection fully vendored & runtime-verified

- **Full interaction engine is upstream**: vendored `foundation/Clickable.kt`,
  `Focusable.kt`, hoverable, the whole pointer-input engine (`HitPathTracker` +
  `PointerInputEventProcessor` + `SuspendingPointerInputFilter`) and the ~25-file
  focus engine (`FocusOwnerImpl`/`FocusTargetNode`/…). Project `Clickable`/`Focusable`/
  `FontVariation`/pointer shims **deleted**. `clickable`/`hoverable`/`focusable` are now
  upstream `Modifier.Node`s driven by the processor.
- **foundation/selection vendored**: `Toggleable` + `Selectable` + `SelectableGroup`.
  Material `Switch`/`Checkbox`/`TriStateCheckbox`/`RadioButton` migrated off ad-hoc
  `.clickable{}` onto `toggleable`/`triStateToggleable`/`selectable` (proper a11y roles).
- **Full scroll system vendored**: `Modifier.verticalScroll`/`horizontalScroll` + `ScrollState`
  + `Modifier.scrollable`/`Draggable` + the scrolling-logic files + `foundation/relocation/*`
  (nested-scroll + overscroll were already vendored). Retired the project scroll system
  (`Scroll.kt`, `ScrollAnimator.kt`, `Vertical/HorizontalScrollNode`, B6a `onWheel`). Mouse
  wheel now flows `AppEvent.MouseWheel → host.onWheel → feedScrollToProcessor` (a
  `PointerEventType.Scroll` event) → `MouseWheelScrollingLogic`. Native actuals in
  `Scrollable.native.kt` (`platformScrollConfig` = dp-per-notch; fling = `splineBasedDecay`).
  Project `LazyColumn` was already built on `verticalScroll`, so it rides the vendored engine.
- **FULL lazy system vendored** (55 files) — `foundation/lazy/layout/*` (LazyLayout engine) +
  `foundation/lazy/*` (LazyList/LazyListState/Measure/…) + `foundation/lazy/grid/*` + the snapping
  providers. Retired the project `LazyColumn`/`LazyRow`/`LazyVerticalGrid` + `ScrollStateExtensions`
  — **all demo/apidemo call sites resolved with zero migration** (project API was upstream-shaped).
  The snapping providers also brought `FinalSnappingItem`, so `AnchoredDraggable` + `SnapFlingBehavior`
  are now vendored too. Impl-when-needed: no-op `LocalPlatformPrefetchScheduler` + `LocalScrollCaptureInProgress`,
  `defaultLazyListBeyondBoundsItemCount=0` native actual, `placeRelativeWithLayer(IntOffset,…)` overloads,
  lazy semantics in SemanticsShim, `-opt-in=ExperimentalFoundationApi`. Real virtualized lists render.
- **compose.foundation.text — partial**: vendored the config leaves (`KeyboardOptions`,
  `KeyboardActions`, `InlineTextContent`). **`BasicText`/`BasicTextField` CANNOT be vendored on the
  SDL/mingw target** — they need `ui.text.MultiParagraph`/`Paragraph`, whose only `actual`s are
  skiko (Skia) / android; there is no Skia on mingwX64. They stay the intentional-custom
  `TextMeasurer` render-bridge impls. Unblocking them would require writing a native `Paragraph`
  actual over SDL_ttf/FreeType (~15 expect fns — a dedicated effort). ui.text DATA types are already
  vendored (AnnotatedString/SpanStyle/ParagraphStyle/font/input).
- **SubcomposeLayout + BoxWithConstraints vendored** (keystone) — the real
  `SubcomposeLayout.kt` (incl. `LayoutNodeSubcompositionsState`) compiled 0-error against the
  vendored LayoutNode engine + `createSubcomposition`. Retired `SubcompositionStubs.shim`.
  Unblocks lazy-layout / pager next.
- **Gesture family + ContextualFlowLayout vendored** — `Transformable` (pinch/zoom/rotate),
  `Draggable2D`, `Scrollable2D`, `ContextualFlowLayout` (retired `ContextualFlowLayoutStubs.shim`).
  `AnchoredDraggable` deferred (pulls `FinalSnappingItem` from the upstream-lazy SnapLayoutInfoProviders).
- **Animation transitions vendored** — `EnterExitTransition` (fadeIn/Out, slideIn/Out, expand/shrink,
  scaleIn/Out), `AnimatedVisibility`, `AnimatedContent`, `Modifier.animateContentSize`. Retired the
  project `ExperimentalAnimationApi.kt` stub.
- **Node-animation frame clock**: `ComposeOwner.coroutineContext` now carries a
  `BroadcastFrameClock` (`animationFrameClock`), pumped each frame by ComposeWindow via
  `ComposeRootHost.sendAnimationFrame`. Node-level animations (scroll fling,
  `animateScrollToItem`, `Animatable` inside `Modifier.Node`s) await `withFrameNanos` on it —
  without it they hang (this is why wheel smooth-scroll needs it; wheel is currently immediate).
- **B6b keyboard + text input now works** (was a hard regression — `ComposeWindow` dropped
  `AppEvent.Key`/`TextInput` in `else->{}`, so text fields received nothing). Wired the full
  focus path: `host.dispatchKeyEvent` → `focusOwner.dispatchKeyEvent` (onKeyEvent chain);
  `host.dispatchTextInput` → focused node's project `OnTextInputNode`; **focus-on-click** in
  `ComposeRootHost.onPointer` (`requestFocus` the `FocusTargetNode` under the cursor).
- **Runtime-verified end-to-end** (not just compile): demo injection probes push synthetic SDL
  events through the *real* pipeline (`injectMouseEvent`/`injectTextInput`/`injectKey` via
  `SDL_PushEvent` → `pollEvents` → host):
  - `demo.exe --clicktest` → upstream `clickable` fires (**PASS**)
  - `demo.exe --toggletest` → Material `Switch` toggles via `toggleable` (**PASS**)
  - `demo.exe --keytest` → real `BasicTextField`: click-to-focus + type "AB" + Backspace = "A" (**PASS**)
  - `demo.exe --scrolltest` → wheel scrolls a `Modifier.verticalScroll` Column to max (**PASS**)
  - `demo.exe --inputtest` → project `pressable` + processor no-crash

### Focus engine wiring — four things that must ALL be true (each was missing)

The vendored focus engine (`FocusOwnerImpl` etc.) needs the owner to wire it up. If focus/typing
silently does nothing, check these in order:
1. **`root.modifier = focusOwner.modifier`** (in `ComposeRootHost.attach`) — installs the focus
   tree root. Upstream AndroidComposeView does `.then(focusOwner.modifier)`. Missing → no focus root,
   `requestFocus` can't build a path, key dispatch throws "Cannot obtain node coordinator".
2. **`registerOnEndApplyChangesListener` + `onEndApplyChanges` implemented** (in `ComposeOwner`,
   were no-ops) and **pumped each frame** (from `ComposeRootHost.measureAndLayout`) — the
   `FocusInvalidationManager` defers its flush here; without it focus changes never apply.
3. **`PlatformFocusOwner.requestOwnerFocus()` returns `true`** (was `false`) — `performRequestFocus`
   denies the very first grant (`previousActiveNode == null && !requestOwnerFocus()`) otherwise.
4. **`Dispatchers.setMain` before `ComposeOwner` construction** — see gotcha #1 below (gesture scopes).

### Two runtime gotchas that cost real debugging (keep in mind)

1. **`Dispatchers.setMain` must run BEFORE `ComposeOwner` is constructed.** The owner
   captures `Dispatchers.Main` *eagerly* (`override val coroutineContext = Dispatchers.Main`)
   for its per-node gesture coroutine scopes. Install the `Sdl3MainDispatcher` first, or
   every gesture launch dies with `MissingMainDispatcher`. (Fixed in `ComposeWindow.kt`.)
2. **Synthesized `PointerInputEvent`s must carry `buttons`.** Upstream `isChangedToDown`
   (`TapGestureDetector`, `firstDownRefersToPrimaryMouseButtonOnly()==true` on native)
   rejects a mouse down unless `buttons.isPrimaryPressed`. `PointerEventBridge.native.kt`
   tracks held primary/secondary/tertiary across events so drags keep the button set on
   Move too. `onPointerRaw`/`feedPointerToProcessor` take `inButton`.

## Where we are

- Upstream `androidx.compose.ui.node.LayoutNode` engine is fully in charge.
  `ProjectLayoutNode` and its parallel-world adapters are **deleted**.
- Renderer pivoted to `RenderBackend.drawRoot(host)` → `LayoutNode.draw` →
  `NodeCoordinator.draw` → `DrawModifierNode.draw(ContentDrawScope)` →
  `CanvasDrawScope` → per-platform `Canvas`.
- **SDL Canvas** (`Sdl3Canvas`, in `core/src/sdlRendererMain/…`) is done.
- **Skia Canvas** (`SkiaCanvas`, in `core/src/skikoRendererMain/…`) is done —
  macOS default (Skia Metal) renders through the upstream pipeline.
  Gradient shaders currently fall back to solid color; other draw ops match
  Sdl3Canvas scope.
- **Scroll** works via the **vendored upstream** `Modifier.verticalScroll`/`scrollable`
  (the project `Vertical/HorizontalScrollNode` were retired — see Latest sprint). Mouse
  wheel routes through the pointer processor → `MouseWheelScrollingLogic`.
- **Pink-screen bugs fixed twice**: per-frame clear at physical DPR scale
  before the DPR transform, on both SDL and Skia backends.

### Counts

| Metric | Start of Phase 9 | Now |
| --- | ---: | ---: |
| `core/src/commonMain/**/*.kt` | 100 | **58** |
| `core/src/commonMain/**/*.shim.kt` | 30 | **16** |
| `core/src/vendor/**/*.kt` | 591 | **753** |
| `core/src/nativeMain/**/*.kt` | 48 | **59** |

Full mingwX64 (SDL) + macOS-Skia + macOS-sdl3 graph is compile-green.

## First actions for the next session

1. **Sync vendor** — the vendored tree is gitignored:
   ```bash
   CMP_REF=../cmp-ref bash tools/compose-fork/sync.sh
   ```
2. **Sanity build**:
   ```bash
   ./gradlew :apidemo:linkDebugExecutableMacosArm64      # Skia
   ./gradlew :demo:linkDebugExecutableMacosArm64 -Prenderer=sdl3
   ```
3. **Baseline screenshot** — confirm Buttons hash matches:
   ```bash
   demo/build/bin/macosArm64/debugExecutable/demo.kexe --screen=Buttons --screenshot=/tmp/x.bmp
   md5 /tmp/x.bmp  # expect ce15decb83c3bb7ba44660cd9002408c
   ```

## The remaining commonMain

### 16 shims still in place

| Shim | Blocks / retires when… |
| --- | --- |
| `ContextualFlowLayoutStubs.shim.kt` (111L) | SubcomposeLayout lands. |
| `DragAndDropNode.shim.kt` (18L) | Real upstream DragAndDropNode (492L) — needs full DnD engine. |
| `RegisterOnLayoutRectChanged.shim.kt` | RectManager + RelativeLayoutBounds land. |
| `ApproachModifierStubs.shim.kt`, `ApproachMeasureScope.shim.kt`, `ApproachMeasureScopeImpl.shim.kt` | Approach/lookahead pipeline (out of scope). |
| `SubcompositionStubs.shim.kt` (8L) | SubcomposeLayout lands. |
| `ViewConfigurationStub.shim.kt` | Not really a shim — the real `ViewConfiguration` interface is vendored; this provides `DefaultViewConfiguration + LocalViewConfiguration`. Rename to non-`.shim` when convenient. |
| `LocalGraphicsContext.shim.kt` | Similar — small `staticCompositionLocalOf`. Rename. |
| `SoftwareKeyboardController.shim.kt` (10L) | Just `PlatformTextInputSessionScope` marker — full text-input session engine unvendored. |
| `SemanticsNode.shim.kt`, `SemanticsOwner.shim.kt`, `SemanticsModifierStub.shim.kt`, `SemanticsInfo.shim.kt` | Semantics engine (out of scope for now). `SemanticsShim.kt` (non-`.shim`) is the accept-and-discard property surface — grows as vendored files reference new props (latest: selection/toggle props for Toggleable/Selectable). |
| `RectManager.shim.kt` | Real RectManager (spatial rect tracker, ~600L). |
| `RetainedValuesStore.shim.kt` (13L) | Compose runtime version 1.11.1 doesn't include the `retain` package; retires when we bump runtime or vendor upstream. |
| `TextInputService.shim.kt` (23L) | Real text-input session engine. |
| `PainterStubs.shim.kt` (34L) | Just holds BitmapPainter stub — retires when ImageBitmap engine vendors. |
| `ShadowContext.shim.kt` (23L) | Retires with upstream `shadow/ShadowContext.kt` (58L) — needs Shape-based DropShadowPainter/InnerShadowPainter ctor (both currently no-arg stubs). |
| `VectorPainterStubs.shim.kt` (29L) | ImageVector (704L) + VectorPainter engines vendor. |

### Non-shim project files still in `commonMain`

Groups roughly by blocker:

- **DONE this sprint** — `Clickable.kt`, `Focusable.kt`, hoverable, the pointer +
  focus engines, and `foundation/selection/{Toggleable,Selectable,SelectableGroup}`
  are all vendored. `ClickableModifier`/`ClickableNode` retired from the project.
- **Foundation composables not yet vendored** — need one of {scrollable/draggable
  engine, text engine, SubcomposeLayout}:
  - `Scroll.kt` — project `ScrollState` already implements the vendored
    `gestures.ScrollableState`; blocked on `Scrollable.kt`+`Draggable.kt`+overscroll+
    nested-scroll (~2000L). Still driven by the B6a wheel dispatch + `ScrollAnimator`.
  - `text/BasicText.kt`, `text/BasicTextField.kt`, `text/TextSelectionHelpers.kt`
  - `text/selection/{Selection.kt, SelectionContainer.kt, SelectionRect.kt}`
  - `lazy/{LazyColumn.kt, LazyRow.kt}`, `lazy/grid/LazyVerticalGrid.kt`
- **Project modifier system** — `com/…/element/ModifierElements.kt` hosts
  BackgroundModifier / BorderModifier / PressableModifier / OnDragModifier /
  OnPressedModifier / Secondary+MiddleClick / Vertical+HorizontalScroll — the
  still-project modifiers B6a (`ComposeRootHost.onPointer`) routes. `clickable`/
  `hoverable` are gone from here (upstream now). Retires further as scroll/gesture
  vendoring lands.
- **Renderer/host glue** (per-platform actuals in `nativeMain` when possible):
  - `com/…/node/ComposeRootHost.kt` (the public facade `:window` drives),
    `NodeApplier.kt`.
  - `com/…/text/{TextMeasurer.kt, TextDrawModifier.kt, ColorRun.kt,
    TextRendererCapabilities.kt}` — Skia+SDL text bridge.
  - `com/…/graphics/{PathCommand.kt, DrawShape.kt, PainterDrawModifier.kt,
    GradientBridge.kt, ColorExtensions.kt}` — project draw glue.
  - `com/…/window/PopupHost.kt`, `com/…/widgets/SplitPane.kt`,
    `com/…/scrollbar/Scrollbar.kt` — project widgets (Scrollbar has no
    upstream equivalent; SplitPane / PopupHost do partially).
- **Other**: `ui/text/font/{Font.kt, FontTypes.kt, FontVariation.kt}`,
  `ui/text/input/TextFieldValue.kt`, `ui/graphics/Outline?` (deleted, uses
  vendored), `ui/graphics/GraphicsLayer.kt` (project GraphicsLayerScope
  interface).

## Suggested next attacks (ordered by ROI)

### 1. Vendor upstream `GraphicsLayerScope.kt` (511L)

- Retires `DefaultCameraDistance.shim.kt`.
- Blocks: our project `androidx.compose.ui.graphics.GraphicsLayer.kt` (185L)
  declares its own `GraphicsLayerScope` + `ReusableGraphicsLayerScope`. Needs
  removed / reduced to project extras (Modifier.graphicsLayer factory) with
  the real interface coming from upstream.
- Reward: unblocks upstream Shadow.kt, upstream animation files (many use
  `graphicsLayer{}` builder).

### 2. Vendor upstream `Shape.createOutline` (upstream Shape interface)

- Blocks: project `Shape.outline(w, h)` is different from
  `createOutline(size, layoutDirection, density)`. Renderers read
  `Outline.Rectangle(Rect) / Rounded(RoundRect) / Generic(Path)` — the
  outline shape is already vendored (`Outline.kt`), only Shape isn't.
- Reward: unblocks upstream Border/Background surface parity, cleaner clip.

### 3. Split renderer-common code from `commonMain` → `nativeMain`

Per user request: “common of both renderer or only for sdl3 windowing/input/
non-rendering can be in `.native`.” Files that don't need to be visible from
`material` / `apidemo` / `demo` (i.e. only consumed within `:core`) can
migrate. Candidates:
- `com/…/text/{TextDrawModifier.kt, ColorRun.kt}` (used only inside :core
  renderers + BasicText).
- `com/…/graphics/{PathCommand.kt, PainterDrawModifier.kt, GradientBridge.kt,
  DrawShape.kt}` (only :core renderers touch these).
- The full `input/PointerInput.kt` + `element/ModifierElements.kt` are
  consumed by :window (which sees `commonMain` via `api(project(":core"))`),
  so they stay in `commonMain` until material/apidemo/demo migrate off them.

### 4. Implement SkiaCanvas gradient shaders

Currently `SkiaCanvas.toSkiaPaint` uses `Paint.color` only. Upstream Skia
`Shader.makeLinearGradient` takes a `Gradient` descriptor — need to construct
one from `LinearGradient` / `RadialGradient` / `SweepGradient`. Mirror
`Sdl3DrawScope`’s sampler math but call Skia's real shader factory. Reward:
gradient buttons / TabRow indicators paint gradients on Skia (currently they
show solid colour).

### 5. Retire `DrawAndDropNode.shim` by vendoring upstream (492L)

Would unlock real drag-and-drop pipeline. Then wire up SDL_EVENT_DROP_* in
`nativeMain` — see the TODO in `core/src/nativeMain/…/DragAndDrop.native.kt`.

### 6. Migrate `Modifier.hoverable` / `focusable` / `clickable` off project
callbacks to upstream `InteractionSource` shape

- Every call site in material / apidemo / demo passes `(Boolean) -> Unit`
  (project shape). Upstream takes `MutableInteractionSource`.
- Grunt work: sweep every `.hoverable { ... }` / `.clickable { ... }` /
  `.focusable(...)`. Once done, vendor upstream `Clickable.kt` (2219L),
  `Focusable.kt` (330L), `Hoverable.kt` (126L).

### 7. Vendor text engine (biggest remaining holdout)

- `Paragraph` / `ParagraphIntrinsics` are `expect class` and upstream ships
  ONLY a **skiko** actual (`ActualParagraph.skiko.kt`).
- On Skia targets we may be able to reuse skiko’s Paragraph → then
  `BasicText.kt` / `BasicTextField.kt` / `TextMeasurer.kt` all vendor.
- On the SDL target we need a native `Paragraph` actual over SDL3_ttf /
  FreeType (from-scratch shaping / line-break / layout engine — its own
  sprint).

### 8. Real `PointerIconService` wiring

`core/src/nativeMain/…/PointerIcon.native.kt` currently has 4 marker
`SdlCursor("default"/"crosshair"/"text"/"hand")` objects. To actually change
the cursor on hover:
1. Install a real `PointerIconService` on the `ComposeOwner` (currently
   returns a no-op).
2. That service maps `PointerIcon` identity → `SDL_SYSTEM_CURSOR_*` and
   calls `SDL_SetCursor(SDL_CreateSystemCursor(kind))` when the hovered
   pointer icon changes.
3. Hook into the ComposeWindow event loop / hover dispatch.

### 9. Real SDL3 drag-and-drop (user-noted)

Now that DragAndDropEvent / DragAndDropTransferData are vendored (no-op
private ctors), wire up the SDL_EVENT_DROP_FILE / _TEXT / _BEGIN / _COMPLETE
event dispatch → construct a DragAndDropEvent → route to vendored
DragAndDropManager.registerTargetInterest / requestDragAndDropTransfer.
See `core/src/nativeMain/…/DragAndDrop.native.kt` for the TODO comment.

## Verifier cheatsheet

```bash
# Vendor sync (idempotent)
CMP_REF=../cmp-ref bash tools/compose-fork/sync.sh

# Compile all five build paths
./gradlew :core:compileKotlinMacosArm64                       # Skia
./gradlew :core:compileKotlinMacosArm64 -Prenderer=sdl3       # SDL macOS
./gradlew :core:compileKotlinLinuxX64                         # Skia Linux (compile-only)
./gradlew :core:compileKotlinLinuxArm64                       # Skia Linux (compile-only)
./gradlew :core:compileKotlinMingwX64                         # SDL Windows (compile-only)

# Runtime — Skia + SDL screenshots
./gradlew :demo:linkDebugExecutableMacosArm64                 # Skia (macOS)
./gradlew :demo:linkDebugExecutableMacosArm64 -Prenderer=sdl3 # SDL (macOS)
demo/build/bin/macosArm64/debugExecutable/demo.kexe --screen=Buttons --screenshot=/tmp/x.bmp
md5 /tmp/x.bmp   # expect ce15decb83c3bb7ba44660cd9002408c

# apidemo
./gradlew :apidemo:linkDebugExecutableMacosArm64
apidemo/build/bin/macosArm64/debugExecutable/apidemo.kexe
```

## Conventions

- `commonMain` = intended API surface visible to material / apidemo / demo.
- `nativeMain` = platform actuals + `:core`-internal glue.
- `sdlRendererMain` / `skikoRendererMain` = platform renderer code only.
- `vendor/` is **gitignored** — sync from cmp-ref via
  `tools/compose-fork/sync.sh`. Everything under vendor is upstream Compose
  Multiplatform BYTE-FOR-BYTE. Never hand-edit; edit
  `tools/compose-fork/manifest.txt` instead.
- Kotlin naming: standard camelCase (no `f/in/v` prefixes in Kotlin, that
  scheme is C-only per CLAUDE.md).

## Known gotchas / lessons

- **Deleting the legacy renderer wipes the frame clear.** `Sdl3Renderer.draw`
  and `SkiaRenderer.draw` used to call `SDL_RenderClear` /
  `canvas.clear(...)` — when they went, add the clear back into
  `beginFrame`. Buttons demo hides regressions because Material Surface
  overpaints the whole viewport each frame; apidemo doesn't → pink screen.
- **Order matters on retina Metal**: clear at `SDL_SetRenderScale(1, 1)`
  BEFORE applying the DPR scale, or you clear only 1/DPR of the target and
  the rest shows uninitialized GPU memory (pink).
- **Modifier.Node.update lifecycle** isn't called on the same node instance
  twice unless the element compares equal — check the `equals` on each
  `ModifierNodeElement` when a modifier lambda captures state.
- **Vendoring an `expect class`** requires a native actual — pick the closest
  upstream actual (macos / skiko / nonJvm) as a starting point; some (like
  StringHelpers.nonJvm) call JVM-only APIs and need a project-side rewrite.
- **`git stash -u`** to include untracked files (freshly created vendor
  files, new shims) when comparing before/after.

## Progress 2026-07-03 (continuation session, Windows)

Vendor tree was stale on this machine (gitignored) — re-synced to 630 files. Then, per the
"vendor everything / commonMain empty" mandate + "follow the Compose way":

- **Vendored `GraphicsLayerScope.kt`** (ROI #1) — retired `DefaultCameraDistance.shim.kt`; removed
  the project GraphicsLayerScope/ReusableGraphicsLayerScope/GraphicsLayerScopeImpl, kept the
  ui.graphics `CompositingStrategy` + `Modifier.graphicsLayer` factories (block form uses the
  vendored ReusableGraphicsLayerScope). Unblocks upstream Shadow + animation graphicsLayer{}.
- **Vendored `TextFieldValue.kt`** — clean (AnnotatedString + TextRange already vendored).
- **Vendored `FontVariation.kt` + migrated the axis API the Compose way** — `List<FontVariation>` →
  `List<FontVariation.Setting>` everywhere; FreeType renderers use `Setting.axisName` +
  `toVariationValue(density)` (was project `axisTag`/`value`); material Icon/Text build axes via
  `FontVariation.Setting("FILL"/"wght"/"GRAD"/"opsz", v)`. Icons screen verified (weight/fill/grade/opsz
  all render correctly through FreeType).
- **Vendored `Popup.kt` + wrote `Popup.native.kt` actual** — expect `PopupProperties` (2 ctors) +
  `Popup` x2 overloads; actual ports the project PopupHost-backed overlay. (Did the impl, didn't revert.)

Counts: `#2 Shape.createOutline` was already done (Shape vendored). commonMain **82 → 78 .kt**,
shims **25 → 24**, vendor **626 → 630**. Full mingw graph green; Buttons/Icons render; `main` untouched.
Rejected: enabling FontVariation/Popup as plain vendors without the migration/actual (needed real work).

## Interaction / pointer-engine scope (measured 2026-07-03)

`Clickable`/`Focusable`/`Hoverable` can't be vendored yet — they're `PointerInputModifierNode`s driven
by synthesized Enter/Exit/press `PointerEvent`s. The interaction *source* types
(`InteractionSource`/`MutableInteractionSource`/`Press`/`Hover`/`FocusInteraction`) + `PointerInputModifierNode`
+ `PointerEvent`/`PointerInputChange` ARE vendored, but the DRIVER is not:
- Missing: `PointerInputEventProcessor`, `HitPathTracker`, `SuspendingPointerInputFilter`.
- **Measured**: enabling those 3 (+ the `.native` actual) → **126-error cascade** — `SuspendingPointerInputFilter`
  needs `InternalPointerEvent`/`PointerInputEventData` + coroutine plumbing (`Continuation`/`coroutineScope`)
  + `PointerEventTimeoutCancellationException` expect + Owner integration. Reverted.
- Additionally `Clickable`/`Focusable` need the **focus engine** (`FocusRequesterModifierNode`/`Focusability`/
  `requestFocus`) + `IndirectPointerInputModifierNode`.
So interaction = a dedicated sprint: vendor the pointer-input engine (3 big files + InternalPointerEvent
family + nonJvm actuals) → wire `PointerInputEventProcessor` into ComposeWindow's event loop + `ComposeOwner`
(replacing the B6a hit-test bridge) → then vendor Hoverable, then the focus engine → Clickable/Focusable.
Call sites are few (hoverable 1, clickable 8, focusable 4), so the widget migration is small once the engine lands.

The clean leaf-vendors are exhausted — every remaining project commonMain `androidx.compose.*` file is either
engine-blocked (pointer/focus/text-layout/measure) or intentional-custom project glue.

## Pointer-input engine sprint DONE (2026-07-03) — hover on the upstream engine ✅

Vendored the pointer DISPATCH engine + wired it + migrated hover to it:
- **HitPathTracker + PointerInputEventProcessor** vendored (retires `PositionCalculator.shim` — the
  processor defines PositionCalculator/MatrixPositionCalculator). The key was deleting that shim
  (it duplicated the processor's declarations → 126-err cascade before).
- **Wired live**: `ComposeOwner` holds `PointerInputEventProcessor(root)` + `processPointerInput()`;
  `ComposeRootHost.onPointerRaw` builds the internal PointerInputEvent via a nativeMain expect/actual
  (`feedPointerToProcessor`, since the expect PointerInputEvent has no commonMain ctor); `ComposeWindow`
  drives it from AppEvent.Pointer alongside the B6a project dispatch. `ComposeOwner.coroutineContext =
  Dispatchers.Main` so HoverableNode.emitEnter/Exit launches run.
- **Vendored Hoverable** — retired project `Modifier.hoverable(onChange)` + HoverableModifier/Node +
  the B6a hover dispatch. Migrated ALL 21 call sites (material ×9 + apidemo ×9 + demo ×3 incl.
  Scrollbar/SplitPane) to `hoverable(interactionSource)` + `collectIsHoveredAsState`.
- Verified: `--inputtest` PASS (click) + pointer processor no-crash; Buttons renders.

Counts: commonMain **78 → 77**, shims **24 → 23**, vendor **630 → 633**.

### Deferred within the sprint
- **SuspendingPointerInputFilter** (the `pointerInput{}` coroutine layer) — 122-err cascade
  (inner-class coroutine plumbing: needs its nonJvm actual's `PointerEventTimeoutCancellationException`
  expect + the AwaitPointerEventScope inner-class supertypes to resolve). Blocks vendoring gesture
  detectors + the project's `pointerInput`/PointerInputNode. Next.
- **Focus engine** (FocusTargetNode/FocusOwnerImpl/…) → then **Clickable** (needs both gesture + focus)
  + **Focusable**. Clickable call sites are few (8).
