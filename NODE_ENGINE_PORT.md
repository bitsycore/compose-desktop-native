# Node-engine port plan

Roadmap for replacing the project's hand-written layout / modifier pipeline
(`com.compose.desktop.native.node.LayoutNode` + `Modifier.Element` data
classes read via `foldIn`) with the upstream `androidx.compose.ui.node`
engine (`Modifier.Node` + `DelegatableNode` + `NodeCoordinator` + upstream
`LayoutNode` + …).

This is a multi-session sprint. Each commit must end with a green build on
**all five paths**: macOS-arm64 Skia, macOS-arm64 SDL3, Linux x64 (both
renderers, compile-only), Linux arm64 (both renderers, compile-only),
mingwX64 (SDL3 only, compile-only) — plus Skia + SDL3 `--screenshot=Buttons`
hashes byte-identical to the running baseline (`c6bc8f7…` / `1844ac4…`).

## Why this is hard

`androidx.compose.ui.node` is **15,000+ lines across ~43 unvendored files**
in upstream. Its top-level types are densely interconnected — there is no
clean "leaf interface" order. `DelegatableNode.kt` alone imports 18
cross-package types, several of which import back, and most of which pull
tens of thousands of lines of further engine code.

Examples that block naïve bottom-up vendoring:

- `DelegatableNode` ↔ `BeyondBoundsLayout` — mutual import, must land
  together (now both vendored, with `Nodes.BeyondBoundsLayout` in shim).
- `DelegatableNode` ↔ `ModifierLocalModifierNode` — same (both vendored).
- `DelegatableNode.requireSemanticsInfo()` casts our `LayoutNode` to
  `SemanticsInfo` → upstream's `SemanticsInfo` extends `LayoutInfo` (~11
  members), plus a real impl would pull `SemanticsConfiguration` (209) →
  `SemanticsProperties` (1739) → `TextLayoutResult` → the entire paragraph
  engine + autofill (`ContentDataType` / `ContentType` / `FillableData`).
- `DelegatableNode.requireGraphicsContext()` returns `GraphicsContext` →
  real `GraphicsContext` constructs `GraphicsLayer` (155+ lines) +
  `PlatformShadowContext` + `ShadowContext` — each its own subtree.
- Upstream `LayoutNode` is **~5000 lines** of layout state machine,
  modifier node chain (`.nodes.head`), `Owner` wiring, etc. Cannot be
  vendored without first replacing every project-side reader.

Because of this density, "one file per commit" stops being viable past
the leaf-interface zone. Past that line, each commit has to be a
self-consistent batch.

## Tracking

- `tools/compose-fork/manifest.txt` — one line per vendored file
  (`<upstream-path> <repo-dest>`). Coverage-map section at the bottom
  shows unvendored files grouped by skip reason.
- `tools/compose-fork/sync.sh` — copies each manifest entry byte-for-
  byte from `$CMP_REF` (default `../cmp-ref`) into
  `core/src/vendor/{common,native,skikoRenderer}/kotlin/`.
- `find core/src/vendor -name "*.kt" | wc -l` — vendored file count
  (currently **404**).
- `find core/src -name '*.shim.kt' | wc -l` — shim file count
  (currently **11**).
- After every change: build all five paths + run demo `--screen=Buttons
  --screenshot=…` on Skia and SDL3, check `md5` matches baseline.

## Phase status

### Phase 0 — Bottom-up leaf vendoring (DONE)

Pre-plan groundwork. Vendored leaf data types + their actuals:
`Modifier` runtime support (Stable / @Composable), `androidx.collection`,
`Color` / `Offset` / `Size` / `IntSize` / `IntOffset` / `Rect` /
`CornerRadius` / `Constraints`, the `androidx.compose.ui.text.font`
cluster, `MutatorMutex`, `BorderStroke`, gestures (`DragGestureDetector`,
`TapGestureDetector`, `TransformGestureDetector`, snapping), `Interaction`
hierarchy, `ScrollableState` / `ScrollableState2D` / `FlingBehavior`,
`Ref`, `WeakReference`, `OutOfFrameExecutor`, `SortedSet`,
`MutableVectorWithMutationTracking`. All in `core/src/vendor/`.

### Phase 1 — Hand-written shim layer + `Modifier.kt` + `DelegatableNode.kt` (DONE)

Atomic commit `c2152f3` + `b9f79aa`: ~10 project shim files (named
`*.shim.kt`) provide the *minimum* surface vendored `Modifier.kt` +
`DelegatableNode.kt` need to compile.

### Phase 2 — Small `*ModifierNode` interface vendors (DONE)

Commits `a052052`, `f7128af`, `ccb655b`, `9211104`, `ad29243`: pure
interface files extending `DelegatableNode`. Vendored:
- `DrawModifierNode` (56), `LayoutAwareModifierNode` (61),
  `ObserverModifierNode` (63), `CompositionLocalConsumerModifierNode`
  (76), `ParentDataModifierNode` (44),
  `GlobalPositionAwareModifierNode` (44),
  `MeasuredSizeAwareModifierNode` (39),
  `UnplacedAwareModifierNode` (41), `TraversableNode` (217).
- Input modifier-node cluster: `KeyInputModifierNode` (47),
  `SoftKeyboardInterceptionModifierNode` (51),
  `RotaryInputModifierNode` (46) + `RotaryScrollEvent` expect/actual,
  `IndirectPointerInputModifierNode` (44) + `IndirectPointerEvent` (145).
- `ModifierLocalModifierNode` (236) — retires
  `ModifierLocalModifierNode.shim.kt`.
- `BeyondBoundsLayout` (133) — retires `BeyondBoundsLayout.shim.kt`.
- `DelegatingNode` (278) — retires `DelegatingNode.shim.kt`, needed
  `Int.contains(NodeKind<*>)` operator + `autoInvalidate*` no-op stubs +
  `NodeChain.syncCoordinators()` no-op stub.

### Phase 3 — `ModifierNodeElement` (DONE)

Commit `f7128af`: `ModifierNodeElement` (105) + `Expect.kt` + native
actuals + hand-written `ExpectActuals.native.kt` for the 4 nonJvmMain +
skikoMain expect-actuals that collide in our single nativeMain. After
this, every project modifier could migrate to the upstream factory
pattern (done in Phase 4h).

### Phase 4 — Reshape layout interfaces + Modifier.Node lifecycle (DONE)

Multi-step.

- **4a** (`40d9b67`) `MeasureResult` reshaped to upstream shape and
  vendored.
- **4b** (`b99979d`) Vendored full `Ruler.kt` (163) +
  `LayoutCoordinates.localPositionOf` + `PlacementScope.Ruler.current`.
- **4c** (`6f3780b`) `MeasureScope` interface reshape to upstream
  (added `density` / `fontScale` / `layoutDirection` /
  `isLookingAhead` + alignmentLines).
- **4d** (`66c4d99`) `Placeable.PlacementScope` reshape to upstream
  (extends Density, carries `parentWidth` / `parentLayoutDirection`).
- **4e** (`3e9a1e2`) `MeasureScope.kt` vendored verbatim. Extracted
  `MeasureScopeImpl.kt` for our project use.
- **4f** (`65ac0d3`) Upstream-shape `place()` / `placeRelative()`
  extension overloads on PlacementScope.
- **4g** (`d2353b2`) API parity overloads — `border(brush, …)`,
  `background(brush, …)`, `padding(PaddingValues)`,
  `absolutePadding(left, top, right, bottom)`.
- **4h** (`a99dcbc`) **All 17 modifier-element classes in
  `com.compose.desktop.native.element.ModifierElements.kt` migrated**
  to `ModifierNodeElement<XxxNode>` factory pattern, each paired with
  a `Modifier.Node` impl (+ `DrawModifierNode` where they paint).
- **4i** (`9458f50`) **Real `NodeChain` + `NodeCoordinator`** —
  retired `NodeChain.shim.kt` + `NodeCoordinator.shim.kt`. NodeChain
  builds a per-LayoutNode chain of Modifier.Node instances via
  `ModifierNodeElement.create()` / `update()`. NodeCoordinator owns
  the chain + real `LayoutCoordinates` view onto the owning LayoutNode.
- (`3f08816`) Retired `AnchorNode.shim.kt` — Phase 4h's concrete
  Modifier.Node subclasses give K2 enough reachability for the `is
  DrawModifierNode` smart-casts in vendored code.
- (`6aa72f0`) Migrated 6 remaining hand-written `Modifier.Element`
  classes outside ModifierElements.kt:
  - `ZIndexModifier` → `ZIndexElement` (matches upstream name) +
    `ZIndexNode : LayoutModifierNode`
  - `FocusRequesterModifier` → `ModifierNodeElement<FocusRequesterNode>`
  - `OnFocusChangedModifier` → `ModifierNodeElement<OnFocusChangedNode>`
  - `LayoutModifierElement` → `ModifierNodeElement<LayoutModifierNodeImpl>`
  - `PointerInputElement` → `ModifierNodeElement<PointerInputNode>`
  - `GraphicsLayerModifier` → `ModifierNodeElement<GraphicsLayerNode>`
- **4j** (`5b62dc9`) **`Modifier.Node` lifecycle now driven**:
  `NodeChain.update()` runs `updateCoordinator(coordinator) →
  markAsAttached() → runAttachLifecycle()` on created nodes;
  `runDetachLifecycle() → markAsDetached()` on removed nodes. Two
  passes head-to-tail mirror upstream's "mark all then run" contract.
  Reused-in-place nodes (same instance or class via `update(node)`)
  skip the lifecycle.
- **4k** (`136a98b`, `c23dda2`) Vendored
  `ModifierLocalModifierNode.kt` + `TraversableNode.kt` +
  `BeyondBoundsLayout.kt`. Added `Nodes.Traversable` entry +
  `calculateNodeKindSetFrom(node)` helper to NodeKind shim.
- (`5ac63d7`) `kindSet` set on newly-created Modifier.Nodes →
  `isKind(kind)` returns true → vendored `visitAncestors(Nodes.X)` /
  `visitChildren(Nodes.X)` / `visitSubtreeIf(Nodes.X)` traversals
  actually find matching nodes instead of short-circuiting.
- (`2990571`) **First renderer migration to chain walk** — vendored
  `OnRemeasuredModifier.kt`; removed hand-written
  `Modifier.onSizeChanged` extension + project `OnSizeChangedModifier`
  + `is OnSizeChangedModifier` foldIn reader. `LayoutNode.measure()`
  now walks `nodes` chain calling `onRemeasured(IntSize)` on every
  `MeasuredSizeAwareModifierNode`.
- (`3c3aa9b`, `142b2c3`) Vendored `OnPlacedModifier.kt`; second
  renderer migration to chain walk — `dispatchGloballyPositioned()`
  fires `onPlaced(coordinates)` on every `LayoutAwareModifierNode`.

After Phase 4 the project's modifier pipeline is **double-running**: the
renderer's `Modifier.foldIn { is XxxModifier }` reader continues to read
every modifier (works because `ModifierNodeElement IS-A
Modifier.Element`), AND the parallel Modifier.Node chain is built +
attached + has its kindSet set + fires lifecycle hooks. Two renderer
sites (onSizeChanged + onPlaced) have crossed over to chain-walk
dispatch; the rest still use foldIn.

### Phase 4 supporting vendors (DONE)

- `VectorComposable` (annotation marker)
- Default platform stubs: `DefaultHapticFeedback`, `DefaultTextToolbar`,
  `DefaultAccessibilityManager` (all skiko actuals — pure no-ops)
- `EmptyLayout` (skiko helper)

## Where we are now

**Vendor count: 404. Shim count: 11.**

### Shims still in place (with concrete unblock requirements)

| Shim | Lines | Why it stays |
| --- | --- | --- |
| `node/NodeKind.shim.kt` | 95 | Upstream NodeKind.kt (440) imports `FocusEventModifierNode`, `FocusPropertiesModifierNode`, `FocusTargetNode`, `SemanticsModifierNode`, `BringIntoViewModifierNode`, `BackwardsCompatNode`, `androidx.collection.mutableObjectIntMapOf`. Every one is heavy. **Stays as project shim; expand by adding new `Nodes.X` constants + `calculateNodeKindSetFrom` cases as we vendor more interfaces.** |
| `node/Owner.shim.kt` | 38 | Upstream Owner.kt (412) pulls 30+ subsystems: autofill, drag-and-drop, focus, graphics-layer, haptic feedback, input mode, pointer icon, modifier-local manager, accessibility, clipboard, platform text input, software keyboard, text toolbar, view configuration, window info, semantics owner, rect manager, font, locale. **Stays.** |
| `node/OwnerSnapshotObserver.shim.kt` | 32 | Real OwnerSnapshotObserver (157) wraps `SnapshotStateObserver` + needs upstream LayoutNode's `requestRelayout` / `requestRemeasure` / `requestLookaheadRemeasure` / `invalidateSemantics` / `isValidOwnerScope`. **Stays until LayoutNode replacement (Phase 6+).** |
| `semantics/SemanticsInfo.shim.kt` | 22 | Real `SemanticsInfo` (103) pulls `SemanticsConfiguration` (1700+ lines) + the whole semantics engine. **Stays.** |
| `graphics/GraphicsContext.shim.kt` | 13 | Real GraphicsContext requires `GraphicsLayer` subtree + `ShadowContext` + `PlatformShadowContext`. **Stays.** |
| `node/LayoutModifierNode.shim.kt` | 13 | Upstream interface (417 lines including measure overloads) pulls `ApproachMeasureScope` cluster + `NodeMeasuringIntrinsics`. Phase 5 territory. **Stays.** |
| `node/CheckMeasuredSize.shim.kt` | 27 | Helper defined inside 700-line `LookaheadDelegate.kt`. Can't pull just the helper without the rest. **Stays.** |
| `layout/RegisterOnLayoutRectChanged.shim.kt` | 25 | Real `registerOnLayoutRectChanged` needs `RectManager` + `RelativeLayoutBounds` + `RegistrationHandle` + Owner. **Stays.** |
| `node/LayoutNode.shim.kt` (typealias) | 21 | `internal typealias LayoutNode = com.compose.desktop.native.node.LayoutNode`. **Retires when project LayoutNode → upstream LayoutNode (Phase 6).** |
| `node/ListForEachReversed.shim.kt` | 20 | `List<T>.forEachReversed` extension — vendored DelegatableNode calls `.forEachReversed { … }` on `getChildren(zOrder)`. Upstream returns `MutableVector` (built-in extension); ours returns `List`. **Retires when LayoutNode.getChildren returns MutableVector or when upstream LayoutNode lands.** |
| `node/LookaheadCapablePlaceable.shim.kt` | 31 | K2 smart-cast anchor for `this@MeasureScope is LookaheadCapablePlaceable` check inside vendored `MeasureScope.layout()` default impl. Real type is base class for `NodeCoordinator` + `LookaheadDelegate` (700 lines). **Stays; cheap anchor.** |

## What remains — phased plan

Each phase is sized to land on a feature branch with all 5 build paths
green + screenshots byte-identical, then merge to main.

### Phase 5 — LayoutModifierNode upgrade + chain measure pipeline (DONE)

Retired `LayoutModifierNode.shim.kt` (commit `a60fcf8`) — real interface
matching upstream's non-Approach surface (`measure` + 4 intrinsic
defaults). Added project mirrors of upstream's internal types
(`NodeMeasuringIntrinsics`, `IntrinsicMinMax`, `IntrinsicWidthHeight`,
`LargeDimension`, `DefaultIntrinsicMeasurable`, `FixedSizeIntrinsicsPlaceable`,
`IntrinsicsMeasureScope`). `ZIndexNode` + `LayoutModifierNodeImpl` now
implement real `measure()` bodies.

**Per-modifier chain measure pipeline** added in `LayoutNode.measure()`:

- `cachedLayoutModifierNodes: List<LayoutModifierNode>` (commit `c19ec21`) —
  populated by `recomputeChainCaches()` walking the live Modifier.Node
  chain. When non-empty, `measure(constraints)` builds inside-out
  wrapping `Measurable`s and dispatches each `LayoutModifierNode.measure()`
  outermost-first; the leaf wrapper re-enters `measure()` with a
  layout-modifier guard for the project's natural measurePolicy path.
- `contentOffsetX/Y` + `pendingChainResult` (commit `0eed731`) —
  `ChainStepPlaceable.placeAt(x, y)` accumulates (x, y) into
  `LayoutNode.contentOffsetX/Y` rather than overwriting the node's own
  position. `place(x, y)` resets contentOffset and replays
  `pendingChainResult.placeChildren()`. `absoluteX/Y` adds
  `parent.contentOffsetX/Y` so children render at the offset.

This is the **per-modifier coordinator pattern in miniature** — without
upstream's full per-modifier NodeCoordinator graph, but correct for
"vendored LayoutModifierNode's measure() runs + its placement effect
lands as inner offset". Validates that upstream Padding / Offset /
WrapContent semantics work when the demo doesn't depend on the project's
deviating behavior.

Files vendored against this pipeline:
- `AspectRatio.kt` (commit `66407ac`) — first upstream Modifier with
  measure logic + intrinsic defaults; position-pass-through (places at
  0,0), no project-side conflict.
- `Intrinsic.kt` (commit `f327677`) — `IntrinsicSize.Min/Max` +
  `Modifier.width(IntrinsicSize)` / `height(IntrinsicSize)` family;
  position-pass-through; different param type from
  `Modifier.width(Dp)` so no overload conflict.

### Phase 6 — Padding migration (DONE)

`Padding.kt` is now vendored verbatim. The project's hand-written
`Modifier.padding(...)` extensions, `PaddingValues.kt`, the
`PaddingModifier` element, and `LayoutNode.cachedPaddingLeft/Top/Right/
Bottom` are all gone. Padding flows through the LayoutModifierNode
chain as `PaddingNode` — inner offset accumulates via
`contentOffsetX/Y` in the chain's deferred `placeChildren` walk
(`LayoutNode.place(x, y)`).

Box/Row/Column measure policies simplified: they no longer read padding
from the node, because constraints reach them already reduced by the
chain. Verified end-to-end on Skia (Metal) and SDL3 (Auto) — demo
sidebar + content panel render correctly.

**Canonical semantics now in effect** (matches upstream):

| Modifier chain | Result |
| --- | --- |
| `.width(180).padding(8)` | 196 wide, child placed at +8 |
| `.padding(8).width(180)` | 196 wide, content at 180 |

If a demo layout depends on the old "padding insets a fixed-width box"
semantics, reorder to `.padding(8).width(180)`. (The current demo's
sidebar uses `.width(180).fillMaxHeight().background().verticalScroll().
padding(...)` and renders fine — that pattern was never load-bearing on
the non-canonical interpretation in practice.)

Same playbook still applies for Size.kt (`fillMaxWidth/Height/Size`,
`wrapContentSize`, `requiredSize`, etc.) and Offset.kt — see Phase 6b.

### Phase 6b — Other modifier alignments (open)

**Goal**: vendor more upstream modifier files that have project
hand-written equivalents — each migration removes one hand-written
modifier in favor of upstream-shape. Same pattern as the Padding/Size/Offset
migration in Phase 6 above, but smaller scope per call.

Candidates (each is one self-contained PR-sized commit):

- `androidx.compose.ui.layout.OnGloballyPositionedModifier.kt` —
  upstream takes `(LayoutCoordinates) -> Unit`, project's takes
  `(IntOffset) -> Unit`. Migration: bulk-update every call site to
  use `LayoutCoordinates` (or fish an IntOffset out of it). Touches
  BasicText, BasicTextField, SelectionContainer, RequestTabStrip,
  Tooltip, DropdownMenu, TlsChainDialog, Sidebar. After migration,
  retire `com.compose.desktop.native.element.GloballyPositionedModifier`
  + the project's `Modifier.onGloballyPositioned`. Third renderer
  site moves to chain walk (`dispatchGloballyPositioned` walks
  `LayoutAwareModifierNode.onPlaced` already; this would walk
  `GlobalPositionAwareModifierNode.onGloballyPositioned` too).
- `androidx.compose.ui.input.pointer.HoverableModifier` /
  `PointerHoverIconModifierNode` — depends on PointerInputModifier
  hierarchy, partial vendor. Maybe skip until Phase 8 (foundation
  widget rewrite).
- `androidx.compose.foundation.Hoverable.kt` + `Focusable.kt` +
  `Clickable.kt` — these all currently use project-local elements +
  the foldIn pipeline. Upstream variants pull `InteractionSource`
  (vendored) + `FocusTargetNode` (huge). Skip until Phase 8.

**Acceptance**: progressive — each commit independently shippable.

### Phase 7 — Renderer pipeline cleanup: complete the foldIn → chain walk migration

**Goal**: every `Modifier.foldIn { e is XxxModifier }` in
`LayoutNode.kt` (currently ~27 sites) migrates to either:
(a) walking `nodes.head.child → … → tail` and matching by node class
or upstream interface (the path onSizeChanged + onPlaced took), OR
(b) reading a single cached property on LayoutNode populated during
NodeChain.update().

This decouples the renderer from `Modifier.Element` chain order —
the chain becomes a query / dispatch artifact rather than a data carrier.
Visible side effect: zero. Sets the stage for Phase 9 (replace project
LayoutNode with upstream's, which exposes `nodes.head` but not
`modifier.foldIn`).

**Concrete site list** (in priority order):

1. `paddingLeft/Top/Right/Bottom` getters — currently 4 separate
   foldIns. Hand-roll a single `cachedPadding: IntInsets` that's
   populated when modifier changes (in the modifier setter).
2. `nodeAlpha`, `zIndex`, `offsetX/Y` — same pattern, one fold per
   property, easy to cache.
3. `scrollOffsetX/Y`, `findVerticalScroll/HorizontalScroll`,
   `applyModifierConstraints(SizeModifier)`, `findLayoutModifier()`
   — all single-pass foldIns that can be cached.
4. `graphicsLayer` getter — already isolated, switch to chain walk.
5. The renderer-side `is BackgroundModifier` / `is BorderModifier` /
   etc. in the Skia + SDL3 renderers — also foldIn-based. These are
   the harder ones because they live in the drawing hot path and need
   to dispatch via `DrawModifierNode.draw(ContentDrawScope)` ideally.
   Phase 8 territory.

**Acceptance**: `grep "foldIn" core/src/commonMain/.../node/` returns
≤ 3 hits (only at modifier-change-time, not per-frame). Demo screen
hashes unchanged.

### Phase 8 — Drive `DrawModifierNode.draw` from the renderer

**Goal**: stop reading individual `is BackgroundModifier` /
`is BorderModifier` / `is GraphicsLayerModifier` from renderers. Each
modifier's `Modifier.Node` impl gets a real `draw(ContentDrawScope)`
body, and the renderer's per-node draw loop walks the chain calling
`(node as DrawModifierNode).draw(scope)` in chain order.

**Why it's a separate phase**: requires `ContentDrawScope` (vendored,
upstream interface) to actually be implementable on top of our
renderer's `DrawScope`. Skia path already gets a real DrawScope.
SDL3 path has its own DrawScope-shaped surface. Both renderers need
a `ContentDrawScope` adapter that satisfies upstream's interface —
one of the bigger renderer changes.

**After this**: third-party Modifier.Node implementations that paint
(by implementing `DrawModifierNode`) work in our renderer. The
project's `BackgroundNode.draw()` / `BorderNode.draw()` etc. become
real (currently they're `drawContent()` stubs). The renderer no
longer needs to know about `BackgroundModifier` by name.

**Acceptance**: Phase 4h's `Node` classes' `draw()` bodies do real
drawing; renderer's `is XxxModifier` reads are gone. Visual hashes
unchanged.

### Phase 9 — Vendor upstream `LayoutNode` + replace project LayoutNode

**The big one** — multi-session, feature branch.

Vendor:
- `node/LayoutNode.kt` (~2000+ lines)
- `node/NodeChain.kt` (upstream version, replaces our project one;
  ours is a simplified subset)
- `node/NodeCoordinator.kt` (1796 — upstream per-modifier chain)
- `node/LayoutModifierNodeCoordinator.kt`
- `node/InnerNodeCoordinator.kt`
- `node/Owner.kt` + project native actual / impl
- `node/LayoutNodeLayoutDelegate.kt` (the layout state machine)
- `node/MeasurePassDelegate.kt` + `LookaheadPassDelegate.kt`
- `node/MeasureAndLayoutDelegate.kt`
- `node/OwnerSnapshotObserver.kt` (real one — retires the shim)

Migrate:
- `:window`'s composition setup — currently uses
  `ComposeNode<LayoutNode, NodeApplier>` with our project applier.
  Switch to upstream's `DefaultUiApplier` + upstream LayoutNode.
- Every project reader of LayoutNode internals (width / height / x /
  y / children / measurePolicy / drawer field / scroll / focus /
  hit-test) updates to upstream's API.
- Skia + SDL3 renderers — both walk our LayoutNode tree directly,
  reading `.children` / `.x` / `.y` / `.width` / `.height` / `.drawer`.
  Switch each to walk upstream's tree via `NodeCoordinator`.

After this phase:
- Retires `LayoutNode.shim.kt` (typealias) + `ListForEachReversed.shim.kt`
  + `OwnerSnapshotObserver.shim.kt`. Shim count 8 → 5.
- Modifier.Node draw / measure / layout pipeline is upstream-native.
- Project's `com.compose.desktop.native.node.{LayoutNode, NodeApplier}`
  are deleted.

**This is destabilising** — every behavior change is a regression to
hunt down. Plan: feature branch, commit per renderer subsystem,
golden screenshot suite across all 30+ demo screens.

### Phase 10 — Vendor `Layout` + `Box` / `Column` / `Row`

Once Phase 9 lands, upstream `Layout` (419 lines) becomes vendorable.
Replaces hand-written `Layout.kt` + the bespoke `RowMeasurePolicy` /
`ColumnMeasurePolicy` / `BoxMeasurePolicy` with upstream's:
- `androidx.compose.ui.layout.Layout.kt` (419)
- `androidx.compose.ui.layout.Placeable.kt` (585 — replaces our slim
  version)
- `androidx.compose.foundation.layout.Box.kt` (332)
- `androidx.compose.foundation.layout.Row.kt` + `Column.kt`
- `androidx.compose.foundation.layout.RowColumnMeasurePolicy.kt` (279)

After this, **only project-glue / non-official surface remains
hand-written** (window setup, renderer code, the SplitPane custom
widget, etc.).

### Phase 11+ — Foundation widget rewrite (foundation-level upstream alignment)

`Clickable.kt`, `Focusable.kt`, `Hoverable.kt`, `BasicText`,
`BasicTextField` — currently project-rewritten. Vendor upstream
versions, retire project versions. This is largely independent of
node-engine and can run in parallel.

Out of scope for the node-engine port; tracked in `FIDELITY.md`.

## What's explicitly out of scope

- **Semantics engine** — vendoring `SemanticsConfiguration` +
  `SemanticsProperties` + `SemanticsOwner` + `SemanticsNode` is its
  own multi-session sprint. Accessibility / autofill follow.
- **Lookahead pass** — `LookaheadDelegate` (700 lines), the
  `Approach*` measure pipeline, and `LookaheadLayoutCoordinates`.
  Required for some shared-element transitions; not required for
  the current demo.
- **Per-modifier `LayoutModifierNodeCoordinator` chain** — upstream
  threads one coordinator per layout modifier; our simplified setup
  collapses to one coordinator per LayoutNode. Could be added in
  Phase 9 but isn't required for correctness.
- **`RectManager` + `OnLayoutRectChangedModifier` + `OnVisibilityChanged`**
  — needs the spatial rect tracker.
- **Drag and drop** — `DragAndDropManager`, `DragAndDropNode`.
- **`FrameRate` API** — upstream uses `NodeCoordinator.layer:
  OwnedLayer?` which our coordinator doesn't have.

## Tools

```bash
# vendor sync (idempotent, re-runnable)
CMP_REF=../cmp-ref bash tools/compose-fork/sync.sh

# fidelity check vs upstream klib API
./gradlew apiDump && python3 scripts/compose-fidelity-check.py

# build all five paths
./gradlew :demo:linkDebugExecutableMacosArm64
./gradlew :demo:linkDebugExecutableMacosArm64 -Prenderer=sdl3
./gradlew :demo:compileKotlinLinuxX64
./gradlew :demo:compileKotlinLinuxArm64
./gradlew :demo:compileKotlinMingwX64

# screenshot regression (run twice — first is cold-start variance)
demo/build/bin/macosArm64/debugExecutable/demo.kexe \
  --screen=Buttons --screenshot=/tmp/x.png
md5 /tmp/x.png   # expect c6bc8f7… (Skia) or 1844ac4… (SDL3)
```

## Counts

| Marker | Start of session N-3 | End of session N-2 | End of session N-1 | End of session N |
| --- | ---: | ---: | ---: | ---: |
| Vendor files | 365 | 396 | 404 | 411 |
| Shim files | 22 | 11 | 11 | 10 |
| Modifier.Node lifecycle | none | dormant | driven + kindSet | + chain measure pipeline (Phase 5) |
| Renderer foldIn sites | 27+ | 27+ | 0 (all cached) | 0 + 3 chain-walk dispatch sites |
| LayoutModifierNode chain measure | — | — | — | with per-modifier inner-offset (commit `0eed731`) |
| Vendored Modifier files using chain | — | — | — | AspectRatio, Intrinsic (position-pass-through only) |
