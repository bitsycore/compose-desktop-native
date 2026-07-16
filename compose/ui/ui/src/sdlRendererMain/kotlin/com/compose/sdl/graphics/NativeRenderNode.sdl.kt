package com.compose.sdl.graphics

// ==================
// MARK: createNativeRenderNode — SDL3 renderer actual
// ==================

/* Returns the shared, renderer-agnostic DeferredRenderNode (replay-the-block).
   Phase 2b will swap this for an SDL caching node (offscreen texture /
   cached-geometry display list) so replay stops re-tessellating — see
   RENDERER_REFACTOR.md §4b. That swap is local to this actual; GraphicsLayer /
   GraphicsLayerOwnerLayer don't change. */
internal actual fun createNativeRenderNode(context: NativeRenderNodeContext): NativeRenderNode =
	DeferredRenderNode()
