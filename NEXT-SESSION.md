# Next session — renderer caching / retained layers

Starting point after v0.1.19. Context for the perf work the profiling pointed at.

## The problem (measured)

The demo caps at ~70 fps on every screen while apidemo hits 144. Cause is NOT
vsync (both use `SDL_SetRenderVSync(renderer, 1)`) — the demo is **draw-bound**:
`CDN_PROFILE=1` showed draw ~34 ms cold / ~14 ms steady, present only ~1.3 ms.
The renderer is **immediate-mode**: every rendered frame walks the layout tree
and re-tessellates everything (text → glyph quads, shapes → triangles). The
always-present sidebar (30+ text rows + icons) is re-tessellated each frame.

Two open threads:

### FINDING (updated) — the 70-vs-144 is NOT reproducible on a 75 Hz dev box

Instrumented the draw phase (`DrawStats`: geo/verts/masks/text/img) + added
`CDN_FORCERENDER=1`. Measured both apps forced-continuous:
- demo:    draw ~3.7ms, geo=40,  verts=6582,  masks=0,  text=51
- apidemo: draw ~2.9ms, geo=146, verts=18576, masks=74, text=174
BOTH pinned at exactly 150 frames/2s = 75 fps with present ~9ms — i.e. the DEV
DISPLAY is 75 Hz, so everything is present/vsync-bound here and the demo's draw
is actually LIGHTER than apidemo's. The "demo is draw-bound" theory is WRONG on
this hardware; the 70-vs-144 split is specific to the user's 144 Hz monitor and
can't be reproduced/diagnosed from a 75 Hz box.

NEXT STEP FOR THE USER (144 Hz machine): run both apps with
`CDN_FORCERENDER=1 CDN_PROFILE=1` and share the `cdn_profile.log` lines. If demo
shows present ~13ms (75 fps) while apidemo shows present ~7ms (144 fps) at
similar draw times, it's a present/vsync-path difference (driver / swap
interval / DWM), NOT draw cost — a different investigation than caching.

The demo idles correctly when static (verified: 2 frames headless, even while
hovering the sidebar), so there's NO spurious-continuous-render bug — thread A
below is resolved as a non-issue.

### A. Why does the demo render CONTINUOUSLY? (investigate FIRST — likely cheaper)

Headless the demo idled (2 frames); interactively you see a constant 70-71 fps,
so something invalidates every frame. Prime suspect: a **hover self-loop** — the
main loop dispatches a synthetic hover every rendered frame
(`ComposeWindow.renderFrame`: `if (hasMousePos) host.onPointerRaw(...)`), and if
that perpetually re-invalidates a hover-reactive sidebar row, the app never
idles while the cursor is over it. If confirmed, the fix is to stop the spurious
invalidation so static screens idle (→ sidebar stops re-tessellating because
nothing renders) — small and targeted, no caching needed.
- Quick test: does the demo's FPS-title stop updating when the mouse leaves the
  window? If yes → hover loop confirmed.
- Look at: `renderFrame` synthetic hover; how hover state feeds `needsFrame` /
  `hasPendingWork` / `shouldRender()`.

### B. cacheKey / retained-layer texture caching (bigger — the "renderer rewrite")

`Modifier.graphicsLayer(cacheKey=…)` exists as API (`GraphicsLayerModifier` /
`GraphicsLayerNode` in `element/ModifierElements.kt`) but is **dead
scaffolding**: `cacheKey` is stored and never read by the renderer; the node is
a bare `Modifier.Node` with no draw behaviour (comment: "stays dormant until the
renderer rewrite drives it"). The demo's GraphicsLayer "(cached)" section uses a
plain `graphicsLayer()` — the label is aspirational; nothing is cached.

Blocker for real texture caching: it needs to render a subtree's `drawContent()`
into an **offscreen canvas**, and the renderer has no way to redirect
`drawContent()` off the frame canvas. `GraphicsLayer.native.draw()` replays its
recorded block against a canvas via `drawScope.draw(…, canvas, …)`, but
`drawContent()` is bound to the outer `ContentDrawScope`'s canvas, which nothing
swaps. The vector/icon offscreen path works only because it draws an explicit
object, not opaque `drawContent()`.

So the real unit of work is a **content-redirect primitive**: make the
`ContentDrawScope` canvas swappable (or add a coordinator-level "draw this
subtree into canvas X"). That primitive is the foundation for BOTH cacheKey
(retained layers) AND dirty-region rendering. Infrastructure that already
exists to build on: `OffscreenRenderer` (`createImageBitmap` + `createCanvas`),
`SdlImageBitmap` render-target textures + `drawImage` blit-back, and the
`NativeReleaseQueue` for freeing cached textures.

Cleanup to do regardless: either implement `cacheKey` or delete the dead
`GraphicsLayerModifier`/`GraphicsLayerNode` scaffolding and fix the demo's
misleading "(cached)" label.

## Tooling ready for this work

- `CDN_PROFILE=1 <app>` → per-phase timings (layout/draw/present) to a file.
- `scripts/parity/parity.py` → native-vs-JVM screenshot diff (regression net).
- `scripts/probe/probe.py` → drive a native window (click/hover/hold) + capture.
- See ROADMAP.md item 2 (dirty regions + retained layers promoted, with the
  demo evidence) and CLAUDE.md "Tooling".
