# Node-engine port plan

Roadmap for replacing the project's hand-written layout / modifier pipeline
(`com.compose.desktop.native.node.LayoutNode` + `Modifier.Element` data
classes read via `foldIn`) with the upstream
`androidx.compose.ui.node` engine (`Modifier.Node` + `DelegatableNode` +
`NodeCoordinator` + `LayoutNode` + ...).

This is a multi-session sprint. Each session must end with a green build
across **all five paths**: macOS-arm64 Skia, macOS-arm64 SDL3, Linux x64
(both renderers, compile-only), Linux arm64 (both renderers, compile-only),
mingwX64 (SDL3 only, compile-only).

## Why this is hard

`androidx.compose.ui.node` is **15,183 lines across 43 unvendored files**
in upstream. Its top-level types are densely interconnected — there is no
clean "leaf interface" order. The central node `DelegatableNode.kt`
(632 lines) alone imports 18 cross-package types, several of which import
DelegatableNode back, and most of which transitively pull tens of
thousands of lines of other engine code.

Concrete examples of why incremental bottom-up vendoring stalls:

- `DelegatableNode` ↔ `androidx.compose.ui.layout.BeyondBoundsLayout` —
  mutual import. Have to land in one commit.
- `DelegatableNode` ↔ `androidx.compose.ui.modifier.ModifierLocalModifierNode`
  — same.
- `DelegatableNode` needs `androidx.compose.ui.semantics.SemanticsInfo`
  → which needs `SemanticsConfiguration` (209 lines) → which needs
  `SemanticsProperties` (1739 lines) → which needs
  `androidx.compose.ui.text.TextLayoutResult` (the entire paragraph engine)
  + the autofill engine (`ContentDataType` / `ContentType` /
  `FillableData`).
- `DelegatableNode` needs `androidx.compose.ui.graphics.GraphicsContext`
  → which needs `GraphicsLayer` (155+ lines) + `PlatformShadowContext`
  + `ShadowContext` (each its own subtree).
- `DelegatableNode.requireSemanticsInfo()` casts `requireLayoutNode()` to
  `SemanticsInfo` — upstream's `LayoutNode` **implements** `SemanticsInfo`.
  Replacing our hand-written `LayoutNode` with upstream's is itself a
  ~5000-line port involving the whole layout state machine, the modifier
  node chain (`.nodes.head`), `Owner`, etc.

Because of this density, "vendor one file per commit" stops being viable
once you cross out of the leaf-interface zone. Past that line, each commit
has to be a self-consistent batch: vendor the whole tight cluster, AND
update any code that calls into it.

## Where we are now (committed)

Already vendored from the engine fringe:

- `androidx.compose.ui.node.{Ref, WeakReference, InternalCoreApi,
  OwnerScope, OutOfFrameExecutor, SortedSet, MutableVectorWithMutationTracking,
  MeasureBlocks}`
- `androidx.compose.ui.layout.{IntrinsicMeasurable, Measurable,
  IntrinsicMeasureScope, Measured, RemeasurementModifier}` — our
  hand-written `Measurable.kt` has been replaced; `LayoutNodeMeasurable`
  now implements the upstream interface
- `androidx.compose.ui.modifier.{ModifierLocal, ModifierLocalConsumer,
  ModifierLocalProvider}`
- `androidx.compose.ui.focus.PlatformFocusOwner`
- `androidx.compose.ui.internal.{InlineClassHelper (checkPrecondition
  family), JvmDefaultWithCompatibility, PlatformOptimizedCancellationException,
  Threading}`
- `androidx.compose.ui.platform.{DebugUtils (simpleIdentityToString),
  NativeActuals, InspectableValue, ViewConfiguration, ...}`

All of this satisfies *some* of `DelegatableNode`'s imports, but the
heavy ones (Section "Why this is hard" above) are still blockers.

## Phased plan

### Phase 1 — Hand-written shim layer (1 focused session, ~2000 lines)

Hand-write project shims under `core/src/commonMain/kotlin/androidx/compose/ui/`
that provide the *minimum* surface for `DelegatableNode` + `Modifier.kt`
+ the small `*ModifierNode` interfaces to compile verbatim. **These are
project files (not vendor), explicitly named `*.shim.kt`, and will be
deleted as their real implementations get vendored later.**

Shims needed (with the minimum surface DelegatableNode actually exercises
— see `git log -p e0b51c6` for the survey):

| Symbol | Shim surface |
| --- | --- |
| `semantics.SemanticsInfo` | `internal interface SemanticsInfo : LayoutInfo` (empty body — DelegatableNode only uses it as a cast target) |
| `semantics.SemanticsConfiguration` | empty `class SemanticsConfiguration` |
| `graphics.GraphicsContext` | `interface GraphicsContext { fun createGraphicsLayer(): Any; fun releaseGraphicsLayer(layer: Any) }` |
| `graphics.layer.GraphicsLayer` | `class GraphicsLayer` (opaque) |
| `node.Owner` | `interface Owner { val graphicsContext: GraphicsContext; fun dispatchOnScrollChanged(delta: Offset) }` |
| `node.Nodes` | `object Nodes { val Any: Int; val Layout: Int; val Draw: Int; val Locals: Int; val BeyondBoundsLayout: Int; ... }` (each is a `NodeKind<*>` upstream — shim as bit-mask ints) |
| `node.NodeKind` | `class NodeKind<T : Modifier.Node>(val mask: Int)` |
| `node.NodeCoordinator` | empty `internal class NodeCoordinator` |
| `node.ObserverNodeOwnerScope` | empty `internal class ObserverNodeOwnerScope` |
| `layout.BeyondBoundsLayout` | `interface BeyondBoundsLayout { fun <T> layout(...): T? }` |
| `layout.BeyondBoundsLayoutProviderModifierNode` | `interface BeyondBoundsLayoutProviderModifierNode : DelegatableNode { val boundsLayout: BeyondBoundsLayout? }` |
| `layout.ModifierLocalBeyondBoundsLayout` | `val ModifierLocalBeyondBoundsLayout = modifierLocalOf<BeyondBoundsLayout?> { null }` |
| `modifier.ModifierLocalModifierNode` | `interface ModifierLocalModifierNode : ModifierLocalReadScope, ModifierLocalProvider2, DelegatableNode` (where `ModifierLocalProvider2` is also a shim) |
| `Modifier.kt` extension funs `visitAncestors`, `requireCoordinator`, `invalidateSubtree`, etc. | hand-write throwing stubs (`error("not in this build")`) |

Once the shim layer compiles, vendor:

- `androidx.compose.ui.Modifier.kt` verbatim (replacing our hand-written
  `Modifier.kt` — the upstream version has `Modifier.Node` inner class +
  `CombinedModifier` + same `Element` + same `Companion` we already have).
- `androidx.compose.ui.node.DelegatableNode.kt` verbatim.

**Acceptance**: build green on all five paths, no behavior change
(nothing creates `Modifier.Node` instances yet so the lifecycle code in
DelegatableNode is dormant). The hand-written `Modifier.kt` is gone;
our existing `Modifier.Element` data classes (`PaddingModifier`,
`BorderModifier`, etc.) still work unchanged because the vendored
Modifier.kt has the same `Element` interface.

### Phase 2 — *ModifierNode interface vendors (1 session, ~10 small files)

With `DelegatableNode` available as a vendored type, the small interfaces
that extend it become cheap drop-ins:

- `node/DrawModifierNode.kt` (56)
- `node/LayoutAwareModifierNode.kt` (61)
- `node/ObserverModifierNode.kt` (63)
- `node/CompositionLocalConsumerModifierNode.kt` (76)
- `node/ParentDataModifierNode.kt` (44)
- `node/GlobalPositionAwareModifierNode.kt` (44)
- `node/MeasuredSizeAwareModifierNode.kt` (39)
- `node/UnplacedAwareModifierNode.kt` (41)

Each is a pure interface extending `DelegatableNode`. No behavior change.

### Phase 3 — `ModifierNodeElement` + LayoutModifierNode (1 session)

Vendor `node/ModifierNodeElement.kt` (105) and `node/LayoutModifierNode.kt`
(the big one — let me put it last in the phase as a stretch). These
are the *factory* and *layout-modifying* contracts that downstream
Compose code uses to declare modifiers.

After this phase, **upstream Compose code that uses `ModifierNodeElement`
will compile against our build**. It won't run yet because we still
haven't ported `NodeCoordinator` or replaced `LayoutNode`, but the
classpath is right.

### Phase 4 — Replace project LayoutNode (multi-session)

The big one. Replace `com.compose.desktop.native.node.LayoutNode` with
upstream's `androidx.compose.ui.node.LayoutNode`. This requires:

- Vendor `node/LayoutNode.kt` (huge — likely ~2000+ lines)
- Vendor `node/NodeChain.kt`, `node/NodeCoordinator.kt` (1796!),
  `node/LayoutModifierNodeCoordinator.kt`, `node/InnerNodeCoordinator.kt`
- Vendor `node/Owner.kt` + provide a project `actual` for Owner
- Migrate `:window`'s composition setup (it currently uses our
  hand-written `LayoutNode` + `NodeApplier` directly via
  `ComposeNode<LayoutNode, NodeApplier>`). Switch to upstream
  `LayoutNode` + `DefaultUiApplier`.
- Migrate every `Modifier.Element` data class in our codebase
  (`PaddingModifier`, `BorderModifier`, `BackgroundModifier`, etc.) to
  `ModifierNodeElement` + a `Modifier.Node` impl. Each modifier needs:
  - An `Element` class extending `ModifierNodeElement<MyNode>` with
    `create()` and `update()`
  - A `Node` class extending `Modifier.Node` implementing
    `DrawModifierNode` (or `LayoutModifierNode`) with the drawing
    behavior we currently express via `foldIn` in renderers
- Migrate both renderers (`SkiaRenderer` + `Sdl3Renderer`) to walk the
  modifier-node chain via `NodeCoordinator` instead of `foldIn`-ing
  over `Modifier.Element` list.

This phase rewrites the entire layout + render pipeline. It will land
in a feature branch and ship as one atomic merge to `main` after every
demo screenshot matches.

### Phase 5 — Vendor `Layout` composable + `Box` / `Column` / `Row`

Once phase 4 lands, upstream `Layout` (419 lines) becomes vendorable:

- Vendor `androidx.compose.ui.layout.Layout.kt`
- Vendor `androidx.compose.ui.layout.Placeable.kt` (585 lines)
- Vendor `androidx.compose.foundation.layout.Box.kt` (332)
- Vendor `androidx.compose.foundation.layout.Row.kt` + `Column.kt`
  (similar size; both currently 110-line hand-written files)
- Delete our hand-written `Box.kt` / `Row.kt` / `Column.kt`

After this, **only foundation-level widgets / project glue remain
hand-written**.

## Tooling

To track progress:

- `tools/compose-fork/manifest.txt` — running list of vendored files.
  Coverage map section at the bottom shows what's still unvendored,
  grouped by skip reason.
- `find core/src/vendor -name "*.kt" | wc -l` — vendored file count.
  Was 365 before this plan; will be ~410 after Phase 1, ~420 after
  Phase 2/3, etc.
- Each phase: spawn a feature branch, commit per-file when possible,
  squash-merge to main only when the whole phase is green.

## What this plan is NOT

- A promise that everything Compose Multiplatform does will work.
  Several engines are explicitly out of scope (semantics, autofill,
  accessibility, full text input — vendoring them is its own multi-session
  effort).
- A single-session task. Each phase is days of focused work, each one is
  destabilizing the build for hours at a time.
- A guarantee that the project's renderers won't need significant changes.
  Phase 4 in particular will require rewriting both `SkiaRenderer` and
  `Sdl3Renderer`'s draw loops.
