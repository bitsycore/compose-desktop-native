package com.compose.sdl.graphics

import com.compose.sdl.renderer.sdl.SdlDisplayListRenderNode
import com.compose.sdl.renderer.sdl.SdlRenderNode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

// ==================
// MARK: createNativeRenderNode — SDL3 renderer actual
// ==================

/* CDN_LAYERCACHE selects the retained node (default = safe replay-the-block):
     geo → SdlDisplayListRenderNode  (Phase 4 cached-geometry — crisp/bit-exact, no
                                      texture; the intended eventual default)
     1   → SdlRenderNode             (texture cache — fast for static leaves but
                                      timing-nondeterministic on complex screens)
     *   → DeferredRenderNode        (replay the block every frame; no caching)
   Flag-gated while the caching nodes are verified; flip the default once the geo
   node's capture covers text/image/clip and a parity sweep is clean. See
   RENDERER_REFACTOR.md §4b/§13. */
@OptIn(ExperimentalForeignApi::class)
private val layerCacheMode: String by lazy {
	platform.posix.getenv("CDN_LAYERCACHE")?.toKString() ?: ""
}

internal actual fun createNativeRenderNode(context: NativeRenderNodeContext): NativeRenderNode =
	when (layerCacheMode) {
		"geo" -> SdlDisplayListRenderNode()
		"1" -> SdlRenderNode()
		else -> DeferredRenderNode()
	}
