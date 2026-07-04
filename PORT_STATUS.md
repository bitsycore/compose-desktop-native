# PORT_STATUS — vendoring Compose Multiplatform into ComposeNativeSDL3

**Read this first when continuing the port** (on any machine). It is the single
source of truth for: what's done, how to bootstrap a fresh checkout, the runtime
gotchas that cost real debugging, how to verify, and what to do next.

Project overview + conventions live in [`CLAUDE.md`](CLAUDE.md); the fidelity
rules (pull-verbatim / surface-match / intentional-custom) live in
[`FIDELITY.md`](FIDELITY.md); the vendoring tooling in
[`tools/compose-fork/README.md`](tools/compose-fork/README.md).

---

## TL;DR — where we are

- Branch: **`phase9`** (~140 commits ahead of `main`; **`main` is untouched and runnable**).
- The upstream **`androidx.compose.ui.node.LayoutNode` engine is fully in charge**
  (the old hand-written `ProjectLayoutNode` is deleted). The strategy is: vendor as
  much of upstream Compose **byte-for-byte** as possible into `core/src/vendor/`, and
  keep only a thin **platform actual + shim** layer as project code, so
  `core/src/commonMain` trends toward empty.
- **Vendored & runtime-verified** this far: the whole interaction stack (clickable /
  focusable / hoverable / pointer-input engine / focus engine), keyboard+text input
  routing (B6b), scroll (scrollable/draggable + wheel), SubcomposeLayout +
  BoxWithConstraints, the gesture family (+ `TransformableState`), animation
  transitions, the **full lazy system** (LazyList/LazyGrid), the spatial RectManager,
  the **text-paragraph engine Phase 2** (`SdlParagraph` measure + paint bridging
  through `NativeTextCanvas.drawNativeText`), the DnD engine (`DragAndDropNode`
  vendored), and the **approach/lookahead layout pipeline** (`ApproachLayoutModifierNode`
  + `ApproachMeasureScope` + `LookaheadScope`).
- Counts: `core/src/commonMain` **100 → 47** `.kt` (`.shim.kt` **30 → 8**),
  `core/src/vendor` **591 → 1074** (+ real
  `androidx.compose.runtime:runtime-retain` on the classpath —
  `ForgetfulRetainedValuesStore` replaces the project marker).
- **Full CoreTextField + BasicTextField pipeline vendored**: `BasicTextField.kt`
  (upstream, replaces project) + `CoreTextField.kt` (1200+L: LegacyTextFieldState +
  Handle / HandleState + requestFocusAndShowKeyboardIfNeeded) + `TextFieldKeyInput` +
  `TextFieldPointerModifier.common` + `TextFieldCursor` + `TextFieldFocusModifier` +
  `TextFieldDelegate` + all `input/internal/*` modifier chain
  (TextFieldCoreModifier + TextFieldDecoratorModifier + TextFieldTextLayoutModifier +
  TextFieldKeyEventHandler + TextFieldDragAndDropNode + TransformedTextFieldState +
  TextFieldLayoutStateCache + TextLayoutState + CodepointTransformation +
  CoreTextFieldSemanticsModifier + LegacyAdaptingPlatformTextInputModifierNode +
  LegacyPlatformTextInputServiceAdapter + TextInputSession.skiko) + full
  `input/internal/selection/*` (TextFieldSelectionState + TextPreparedSelection +
  TextFieldMagnifier). Plus `BasicSecureTextField` + `TextFieldScrollState.skiko` +
  `TextPointerIcon.skiko`.
- **Full IME platform stack**: `PlatformTextInputModifierNode.kt` +
  `PlatformTextInputMethodRequest.skiko.kt` — real IME session interface with
  all slot properties (value / state / imeOptions / onEditCommand /
  onImeAction / textLayoutResult / focusedRectInRoot / etc).
- Project extracts retired (now provided by vendored files): `HandleExtract`,
  `LegacyTextFieldStateExtract`, `DefaultCursorThicknessExtract`,
  `IsTypedEventExtract`, `PlatformTextInputMethodRequest.native.kt`,
  `TextFieldDecoratorModifier.native.kt`, `OffsetCoerceInExtract`,
  `PlatformTextInputSessionScope.kt`, `TextPointerIcon.native.kt`,
  `CommonContextMenuAreaExtract`, `ContextMenuExtract`, `CeilToIntPx`,
  `TextLayoutHelperExtract`.
- Additional: `TraversableNodeInDrawOrder.skiko` +
  `foundation.text.handwriting.StylusHandwriting` + skiko actual +
  `contextmenu.internal.ProvideDefaultPlatformTextContextMenuProviders` +
  its native no-op actual.
- **BasicText upstream vendor + icon-font decouple — DONE**:
  * project `BasicText` retired; upstream `foundation.text.BasicText.kt` is
    the source of truth. All overloads (String / AnnotatedString /
    maxLines-only / link-inline lambda) match common CMP API 1:1.
  * material `Text` now routes `fontFamily: String?` via
    `TextStyle.fontFamily = FontFamily.Named(name)` — SdlParagraph reads
    `FontFamily.Named` directly and passes to the project TextMeasurer.
  * `com.compose.desktop.native.text.IconText` — new project composable
    that owns the icon-font `fontFamily: String` + `fontVariationSettings`
    axes (upstream `TextStyle` doesn't model per-usage variations).
    material `Icon(codepoint, fontFamily, variations)` uses IconText,
    so BasicText stays byte-signature-clean.
  * `BasicTextField` migrated to build `TextStyle(fontFamily = FontFamily.Named(...))`
    before handing to BasicText.
- **TextFieldSelectionManager upstream vendor — DONE** (1503L): the
  full selection manager for editable TextField (drag / handles / context
  menu). Bridged by `LegacyTextFieldStateExtract.kt` (project stub for
  `LegacyTextFieldState` + `HeightForSingleLineFieldProvider` — the two
  types the manager anchors on; nested inside upstream CoreTextField.kt
  which we don't vendor yet) and `TextFieldSelectionManager.native.kt`
  (5 desktop actuals matching upstream macosMain: textFieldMagnifier /
  addBasicTextFieldTextContextMenuComponents / isSelectionHandleInVisibleBound
  / hasAvailableTextToPaste).
- `ModifierElements.kt` trimmed — 6 dead project modifier pairs deleted
  (Background / Border / DrawBehind / Focusable / LayoutWeight / Alpha —
  all replaced by their vendored upstream equivalents).
- 3 project actuals swapped out for byte-identical upstream: FontSynthesis,
  ContextMenuIcons, InteropViewFactoryHolder.
- **Selection engine — full vendor** (Phases A+B+C, ~25 files): the whole
  registry + manager + container. `Selectable` / `SelectionRegistrar` /
  `SelectionRegistrarImpl` / `Selection` / `SelectionAdjustment` /
  `SelectionHelpers` / `SelectionLayout` / `SelectionMagnifier` / `SimpleLayout`
  / `PlatformSelectionBehaviors` (+.skiko) / `TextSelectionColors` /
  `TextSelectionDelegate` / `MultiWidgetSelectionDelegate` +
  `SelectionContainer` + `SelectionManager` (1804L) + `SelectionState` +
  `SelectionGestures` + `SelectionHandles` + `SelectionModifier{Element,Node}` +
  `SelectionController` + foundation/text/`LongPressTextDragObserver` +
  foundation/text/`TextLinkScope` + `SelectableTextAnnotatedString{Element,Node}`.
  `SelectionActuals.native.kt` bundles the small per-platform actuals
  (isCopyKeyEvent / SelectionHandle / selectionMagnifier /
  addSelectionContainerTextContextMenuComponents / FirstLongPressSelectionAdjustment).
  Not vendored yet: `TextFieldSelectionManager` (1503L) + `TextPreparedSelection`
  (need `LegacyTextFieldState` from unvendored `CoreTextField.kt`) and
  `SelectionMode.kt` (K2 access-scoping bug).
- **Context menu suites — full vendor** (~15 files): all of
  `foundation.contextmenu` (`ContextMenuArea` + `ContextMenuUi` + `ContextMenuState`
  + `ContextMenuGestures` + `ContextMenuPopupPositionProvider` + skiko actuals) +
  all of `foundation.text.contextmenu` (`data.TextContextMenuData` +
  `builder.TextContextMenuBuilderScope` + `modifier.TextContextMenu{,Gestures,ToolbarHandler}Modifier`
  + `provider.{TextContextMenuProvider,BasicTextContextMenuProvider}` +
  `gestures.RightClickGestures`).
- **Text engine spine — full vendor** (~10 files): `TextDelegate` +
  `TextLayoutHelper` + `TextPainter` + `TextMeasurer` + `HeightInLinesModifier`
  + `TextFieldDelegate` + `EditProcessor` + `TextLayoutResultProxy` +
  `TextFieldScroll` (+ native) + `TextFieldSize` + `TextFieldGestureModifiers`
  + `KeyboardActionRunner` + `KeyboardActions`.
- **BasicTextField 2 (input) leaves — vendored**: `TextFieldState` +
  `TextFieldBuffer` + `TextFieldCharSequence` + `TextFieldTextStyles` +
  `TrackedRange` + `UndoState` + `InputTransformation` +
  `OutputTransformation` + `TextUndoManager` + internal `ChangeTracker` +
  `MathUtils` + `IntIntervalTree` (1594L) + `Interval` + `TextStyleBuffer` +
  `undo/TextUndoOperation`.
- **New foundation vendor** — `foundation.pager` (14 files) +
  `foundation.lazy.staggeredgrid` (14 files) + `foundation-layout/FlexBox`
  (2718L) + `foundation-layout/Grid` (2454L) + `foundation/border/BorderLogic`
  + `foundation/content/*` (5 files) + `foundation/OnClick` +
  `foundation/gestures/{cupertino,DragGesture.skiko,TapGesturesDetector.skiko}` +
  `foundation/draganddrop/DragAndDropTarget` + `foundation/selection/*.nonJvm`
  actuals + `foundation/Clickable.nonJvm`.
- **New animation vendor** — SharedElement suite (10 files):
  `AnimateBoundsModifier` + `BoundsAnimation` + `SharedContentNode` +
  `SharedElement` + `SharedElementEntry` + `SharedTransitionScope` +
  `SharedTransitionStateMachine` + `SkipToLookaheadSizeNode` +
  `RenderInTransitionOverlayNodeElement` + `LookaheadAnimationVisualDebugHelper`.
- **New ui/ui vendor** — `ui/layout/{OnGlobalLayoutListener,OnFirstVisibleModifier,OnVisibilityChangedModifier,LayoutBoundsHolder}` +
  `ui/FrameRate`.
- Project extracts/stubs used to bridge (TODO comments included, delete when
  upstream can vendor): `foundation/text/{HandleExtract,CommonContextMenuAreaExtract,
  ContextMenuExtract,DefaultCursorThicknessExtract}` +
  `foundation/text/input/internal/OffsetCoerceInExtract` +
  `ui/draw/ShadowStub` + `ui/platform/CompositionLocalsShim.kt` additions
  (LocalFontFamilyResolver + LocalUriHandler + LocalTextToolbar +
  installDefaultUriHandler wiring).
- Full **mingwX64** (SDL) + **macOS Skia** + **macOS `-Prenderer=sdl3`** graph is
  compile-green. All verification probes PASS (see [Verification](#verification)).

---

## Bootstrap a fresh checkout (DO THIS FIRST on a new computer)

The vendored tree `core/src/vendor/` is **gitignored** (generated, not committed).
You must populate it before the build will compile.

```bash
# 1. Clone this repo, checkout the phase9 branch.
git checkout phase9

# 2. Populate core/src/vendor/ from the pinned upstream ref. This clones a sparse
#    shallow copy of JetBrains/compose-multiplatform-core into ../cmp-ref (override
#    with CMP_REF=<path>) at the SHA in tools/compose-fork/compose-ref.txt
#    (currently 1be9d64… = v1.12.0-beta01+dev4324), then copies every active
#    manifest entry verbatim. Idempotent; re-run after editing manifest.txt.
CMP_REF=../cmp-ref bash tools/compose-fork/sync.sh
```

### Windows native deps (mingwX64 — the primary/only fully-linkable target here)

The SDL3 / SDL3_ttf / SDL3_image / FreeType (+ static libcurl for `:apidemo`) are
built from source as static libs into a gitignored in-repo `libs/`:

```bash
# From Git Bash on Windows. Needs git, cmake, mingw-w64 gcc/g++ on PATH, curl+python.
tools/build-all.sh
```

If you see `fatal error: 'SDL3/SDL.h' file not found` or `cannot find -lSDL3`,
`libs/` is empty/incomplete — run `tools/build-all.sh`.

macOS/Linux use system SDL3 (`brew install sdl3` / `apt install libsdl3-dev`) and
Skiko klibs from Maven; those targets can't cross-link mingwX64 (that's expected —
build the Windows target on Windows).

### Build / run / verify

```bash
# Compile-only the whole graph on Windows (verifies common+native+mingw for
# :core sdlRendererMingwMain + :material + :window + apps, no full link):
./gradlew :demo:compileKotlinMingwX64 :apidemo:compileKotlinMingwX64

# Link + run the demo:
gradlew.bat :demo:runDebugExecutableMingwX64

# macOS default (Skia): ./gradlew :demo:runDebugExecutableMacosArm64
# macOS Skiko-free:     ./gradlew :demo:runDebugExecutableMacosArm64 -Prenderer=sdl3
```

**mingwX64 never sees `skikoRendererMain`** — after renaming a public symbol,
grep `core/src/skikoRendererMain/` + `skikoRenderer{Macos,Linux}Main/` manually
(they only compile on macOS/Linux).

---

## The vendoring workflow (how to add more)

1. Everything upstream is vendored **byte-for-byte** via
   `tools/compose-fork/manifest.txt` (one line: `<upstream-path>  <repo-dest>`).
   Uncommented = vendored; `# `-commented = a not-yet-vendored candidate.
2. To vendor a file: **uncomment its manifest line** (or add it), then run
   `bash tools/compose-fork/sync.sh`. Dest maps source sets: `commonMain →
   core/src/vendor/common/`, `nativeMain`/`nonJvmMain`/`skikoMain →
   core/src/vendor/native/`.
3. **NEVER hand-edit a file under `core/src/vendor/`** — it's overwritten on every
   sync. Hand-written glue (shims, `expect`/`actual` actuals, project code) lives
   OUTSIDE the vendor tree, in `core/src/commonMain/…/*.shim.kt` (or plain `.kt`)
   and `core/src/nativeMain/…/*.native.kt`, and IS committed.
4. Compile → fix the compiler-driven todo list (missing actuals, semantics props,
   CompositionLocals, call-site migrations). This is the whole loop.

Typical error → fix patterns seen throughout the port:
- `Expected X has no actual declaration` → write a `.native.kt` actual.
- `Unresolved reference <semanticsProp>` → add an accept-and-discard prop to
  `SemanticsShim.kt`.
- `Unresolved reference Local<X>` → add a project `staticCompositionLocalOf` (the
  full upstream `CompositionLocals.kt` is platform-View-heavy, so we stub selected
  entries).
- `<file> is experimental` errors on vendored foundation code → the build has
  `-opt-in=androidx.compose.foundation.ExperimentalFoundationApi` in
  `core/build.gradle.kts` (add more opt-ins there if a new area needs them).

---

## What's vendored (subsystems, all runtime-verified)

- **Layout engine**: `androidx.compose.ui.node.*` (LayoutNode, NodeCoordinator,
  NodeChain, MeasureAndLayoutDelegate), driven by the project `ComposeOwner` (the
  real `Owner`) behind the public `ComposeRootHost` facade that `:window` uses.
- **Interaction**: `foundation/Clickable`, `Focusable`, hoverable; the pointer-input
  engine (`HitPathTracker` + `PointerInputEventProcessor` + `SuspendingPointerInputFilter`);
  the ~25-file **focus engine** (`FocusOwnerImpl`/`FocusTargetNode`/…). `clickable`/
  `hoverable`/`focusable` are upstream `Modifier.Node`s driven by the processor.
- **foundation/selection**: `Toggleable`/`Selectable`/`SelectableGroup`. Material
  `Switch`/`Checkbox`/`RadioButton` migrated to `toggleable`/`selectable`.
- **Scroll**: `Modifier.verticalScroll`/`horizontalScroll` + `ScrollState` +
  `Modifier.scrollable`/`Draggable` + scrolling-logic + `relocation/*`. Mouse wheel
  flows `AppEvent.MouseWheel → host.onWheel → feedScrollToProcessor` (a
  `PointerEventType.Scroll` event) → `MouseWheelScrollingLogic`. Native actuals in
  `Scrollable.native.kt`.
- **SubcomposeLayout + BoxWithConstraints** (keystone; unblocked lazy/pager).
- **Gesture family**: `Transformable`, `Draggable2D`, `Scrollable2D`,
  `ContextualFlowLayout`, `AnchoredDraggable`.
- **Animation transitions**: `EnterExitTransition`, `AnimatedVisibility`,
  `AnimatedContent`, `Modifier.animateContentSize`.
- **FULL lazy system**: `foundation/lazy/layout/*` + `foundation/lazy/*` +
  `foundation/lazy/grid/*` + snapping providers. Project `LazyColumn`/`LazyRow`/
  `LazyVerticalGrid` retired — **all call sites resolved with zero migration**.
- **Spatial**: real `RectManager` + `RelativeLayoutBounds` + `ThrottledCallbacks` +
  `RectList` (onLayoutRectChanged/onGloballyPositioned now real, not stubs).
- **Text — Phase 1 (engine only, see below)**: `MultiParagraph`/`Paragraph`/
  `ParagraphIntrinsics`/`TextLayoutResult` + `SdlParagraph` measurement bridge.
  ui.text DATA types (AnnotatedString/SpanStyle/ParagraphStyle/TextStyle/font/input)
  were already vendored.

---

## Runtime gotchas (each cost real debugging — keep in mind)

### Focus engine wiring — four things that must ALL be true

If focus/typing silently does nothing, check in order:
1. **`root.modifier = focusOwner.modifier`** in `ComposeRootHost.attach` (upstream
   AndroidComposeView does `.then(focusOwner.modifier)`). Missing → no focus root →
   `requestFocus` can't build a path; key dispatch throws "Cannot obtain node coordinator".
2. **`registerOnEndApplyChangesListener` + `onEndApplyChanges`** implemented in
   `ComposeOwner` (were no-ops) and **pumped each frame** (from
   `ComposeRootHost.measureAndLayout`) — `FocusInvalidationManager` flushes here.
3. **`PlatformFocusOwner.requestOwnerFocus()` returns `true`** (was `false`) —
   `performRequestFocus` denies the very first grant otherwise.
4. **`Dispatchers.setMain(Sdl3MainDispatcher)` BEFORE `ComposeOwner` is constructed**
   — the owner captures `Dispatchers.Main` *eagerly* for its per-node gesture
   coroutine scopes; install after and every gesture launch dies with
   `MissingMainDispatcher`.

### Other

- **Synthesized `PointerInputEvent`s must carry `buttons`** — upstream
  `isChangedToDown` (native `firstDownRefersToPrimaryMouseButtonOnly()==true`)
  rejects a mouse down unless `buttons.isPrimaryPressed`.
  `PointerEventBridge.native.kt` tracks held primary/secondary/tertiary so drags
  keep the button set on Move too.
- **Node-animation frame clock** — `ComposeOwner.coroutineContext` carries a
  `BroadcastFrameClock` (`animationFrameClock`), pumped each frame via
  `ComposeRootHost.sendAnimationFrame`. Node animations (scroll fling,
  `animateScrollToItem`, node `Animatable`) await `withFrameNanos` on it — without
  it they hang.
- **Snapshot apply** — `Snapshot.sendApplyNotifications()` each frame, or state
  writes never reach the recomposer.

---

## Verification

The demo has injection probes that push synthetic SDL events through the **real**
pipeline (`injectMouseEvent`/`injectTextInput`/`injectKey`/`injectWheel` via
`SDL_PushEvent` → `pollEvents` → host). After any change, link the demo and run:

```bash
EXE=demo/build/bin/mingwX64/debugExecutable/demo.exe
"$EXE" --clicktest      # upstream clickable fires                → PASS
"$EXE" --toggletest     # Material Switch toggles via toggleable  → PASS
"$EXE" --keytest        # real BasicTextField: click-focus+type+backspace → PASS
"$EXE" --scrolltest     # wheel scrolls a verticalScroll Column   → PASS
"$EXE" --paragraphtest  # real width-wrapped Paragraph via SdlParagraph → PASS
"$EXE" --inputtest      # project pressable + processor no-crash
"$EXE" --pipetest=<bmp> # upstream draw pipeline → screenshot

# Screenshot any screen (BMP; convert with PIL). Use Windows temp paths, not /tmp.
"$EXE" --screen=Buttons --screenshot=C:/Users/<you>/AppData/Local/Temp/x.bmp
```

Screens to sanity-check render richly: Buttons, LazyColumn, TextField, Widgets,
Scroll, Animation, Modifiers.

---

## Text engine Phase 2 — DONE

Phase 1 vendored the paragraph engine and wrote `SdlParagraph` — real
measurement (`--paragraphtest` PASS).

**Phase 2 (done)**: `SdlParagraph.paint(canvas, …)` now casts the Compose
Canvas to `NativeTextCanvas` and calls `drawNativeText` with the already-wrapped
text — no re-measure at draw time. All three `paint(…)` overloads
(color / brush / deprecated) route through `paintCore`. `MultiParagraphDraw`
iterates `paragraphInfoList` with per-paragraph `canvas.translate(0, height)`
mirroring upstream's color-based `MultiParagraph.paint`. `BasicText` /
`BasicTextField` are **not yet vendored** — Phase 3 pulls the
foundation.text.modifiers/ ecosystem behind them.

## NEXT WORK — Text engine Phase 3: vendor upstream BasicText

Vendor `foundation.text.modifiers/TextStringSimpleElement.kt` +
`TextStringSimpleNode.kt` + `TextAnnotatedStringElement.kt` +
`TextAnnotatedStringNode.kt` + `SelectableTextAnnotatedStringElement.kt` +
`SelectableTextAnnotatedStringNode.kt` + upstream `BasicText.kt`.
Blocking gaps:
- `StylePhase` / `inheritedTextStyle` / `StyleOuterNode` from
  `foundation/style/*` (unvendored, ~1000L).
- Semantics actions (`text`, `textSubstitution`, `setTextSubstitution`,
  `clearTextSubstitution`, `showTextSubstitution`, `isShowingTextSubstitution`,
  `getTextLayoutResult`) — add these to `SemanticsShim.kt` as accept-and-discard.
- `TextDragObserver` / `MouseSelectionObserver` / `MultiWidgetSelectionDelegate`
  / `SelectionAdjustment` / `awaitSelectionGestures` from
  `foundation/text/selection/*` (needed for SelectableTextAnnotatedString*).

Alternative: vendor upstream BasicText, remove project one, patch call sites
(material Text) — the fontFamily/fontVariationSettings non-upstream params drop,
so per-site migration is needed for icon fonts.

**Vendored this session (post-PORT_STATUS)**: `TextInputService.kt` + `EditCommand.kt`
+ `EditingBuffer.kt` + `GapBuffer.kt` + `LayoutUtils.kt` + `ParagraphLayoutCache.kt`
+ `MinLinesConstrainer.kt` + `InputEventCallback.kt` + `PointerInputEvent.skiko.kt`
+ `TransformableState.kt` + `ApproachLayoutModifierNode.kt` + `ApproachMeasureScope.kt`
+ `LookaheadScope.kt` + `DragAndDropNode.kt` — retired 8 shims (TextInputService,
DragAndDropNode, 3 approach) + 3 rename-off-.shim (ViewConfiguration, LocalGraphicsContext,
PlatformTextInputSessionScope). Added `ceilToIntPx` project helper for text
modifiers that need it without pulling TextDelegate.kt.

Text Phase 2 paint (SdlParagraph.paint through NativeTextCanvas) landed.

---

## Remaining `commonMain` (the 8 shims + project files)

**Blocked on this target** (need Skia or a from-scratch engine):
- `graphics/shadow/ShadowContext.shim.kt`, `graphics/painter/PainterStubs.shim.kt`
  (BitmapPainter), `graphics/vector/VectorPainterStubs.shim.kt` — shadow/bitmap/vector
  painters need Skia blur/raster + `ImageBitmap` engine. (`Modifier.blur` is vendored
  but its render effect is ignored.)

**Deferred / large:**
- `semantics/Semantics{Node,Owner,Info,ModifierStub}.shim.kt` — the a11y engine
  (no backend consumes it here). `SemanticsShim.kt` (non-`.shim`) is the
  accept-and-discard property surface — grows as vendored files reference new props.
- `runtime/retain/RetainedValuesStore.shim.kt` — blocked on a runtime version bump
  (compose-runtime 1.11.1 doesn't yet include the `retain` package).

**Retired this session:**
- `text/input/TextInputService.shim.kt` → real upstream `TextInputService.kt`
  vendored (via `NoOpPlatformTextInputService` for Owner impls).
- `draganddrop/DragAndDropNode.shim.kt` → real upstream `DragAndDropNode.kt` (492L).
- `layout/Approach{MeasureScope,ModifierStubs,MeasureScopeImpl}.shim.kt` → real
  upstream `ApproachLayoutModifierNode.kt` + `ApproachMeasureScope.kt` + `LookaheadScope.kt`.
- Non-shim renames (kept behavior, dropped `.shim.kt`):
  `ViewConfigurationStub.shim → ViewConfigurationLocal`,
  `LocalGraphicsContext.shim → LocalGraphicsContext`,
  `SoftwareKeyboardController.shim → PlatformTextInputSessionScope`.

Irreducible intentional-custom project code (documented in FIDELITY.md): the SDL
`TextMeasurer` bridge + custom `BasicText`/`BasicTextField`, `FontFamily.Named`
(icon fonts), the `ui.res` resource system, the render-bridge `Modifier.Element`
data classes in `com.compose.desktop.native.*`, the project input modifiers
(`pressable`/`onDrag`/`onPressed`/secondary+middle click) routed by
`ComposeRootHost.onPointer` (B6a).

---

## Key files (start here)

- `core/src/commonMain/.../node/ComposeOwner.kt` — the real `Owner` (focus/anim
  clock/rect manager wiring).
- `core/src/commonMain/.../com/compose/desktop/native/node/ComposeRootHost.kt` —
  public facade `:window` drives; input entry points (`onPointer`/`onPointerRaw`/
  `onWheel`/`dispatchKeyEvent`/`dispatchTextInput`).
- `core/src/nativeMain/.../node/PointerEventBridge.native.kt` — builds native
  `PointerInputEvent`s (button state, scroll).
- `core/src/nativeMain/.../ui/text/SdlParagraph.native.kt` +
  `ParagraphFactories.native.kt` — the text-engine bridge (Phase 1).
- `window/src/nativeMain/.../ComposeWindow.kt` — main loop, event routing,
  `Dispatchers.setMain` order, per-frame anim clock + snapshot pumps.
- `core/src/commonMain/.../ui/semantics/SemanticsShim.kt` — accept-and-discard
  semantics props (add here when a vendored file needs a new one).
- `tools/compose-fork/manifest.txt` — the vendor set.
- `demo/src/nativeMain/kotlin/Main.kt` — the `--*test` probes.
