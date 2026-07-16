package com.compose.sdl.graphics

// ==================
// MARK: createNativeRenderNode — Skia renderer actual
// ==================

/* Returns the shared, renderer-agnostic DeferredRenderNode (replay-the-block).
   Phase 2a will swap this for a node backed by skiko's
   org.jetbrains.skiko.node.RenderNode (a real display list) so the Skia leg gets
   record-once/replay caching + upstream shadow/clip fidelity — see
   RENDERER_REFACTOR.md §4a. That swap is local to this actual; GraphicsLayer /
   GraphicsLayerOwnerLayer don't change. */
internal actual fun createNativeRenderNode(context: NativeRenderNodeContext): NativeRenderNode =
	DeferredRenderNode()
