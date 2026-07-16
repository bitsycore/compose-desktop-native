package com.compose.sdl.graphics

import com.compose.sdl.renderer.sdl.SdlRenderNode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

// ==================
// MARK: createNativeRenderNode — SDL3 renderer actual
// ==================

/* CDN_LAYERCACHE=1 opts into the retained texture-cached SdlRenderNode (leaves
   stop re-tessellating); default is the safe replay-the-block DeferredRenderNode.
   Flag-gated while the caching node is verified pixel-equal against the default
   (RENDERER_REFACTOR.md §4b). Flip the default once the parity sweep is clean. */
@OptIn(ExperimentalForeignApi::class)
private val layerCacheEnabled: Boolean by lazy {
	platform.posix.getenv("CDN_LAYERCACHE")?.toKString() == "1"
}

internal actual fun createNativeRenderNode(context: NativeRenderNodeContext): NativeRenderNode =
	if (layerCacheEnabled) SdlRenderNode() else DeferredRenderNode()
