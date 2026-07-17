package com.compose.sdl.graphics

import com.compose.sdl.renderer.sdl.SdlDisplayListRenderNode
import com.compose.sdl.renderer.sdl.SdlRenderNode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

// ==================
// MARK: createNativeRenderNode — SDL3 renderer actual
// ==================

/* CDN_LAYERCACHE selects the retained node. The DEFAULT is now the cached-geometry
   node (Phase 4): it records each leaf's tessellated geometry + plain text runs ONCE
   and replays them under the layer transform — crisp/bit-exact at any transform, no
   render-target state, deterministic. A full 57-screen geo-vs-block-replay sweep is
   clean (55 at 0.000%; GraphicsLayer/ModShortcuts <0.13% cosmetic rotated-edge AA)
   and the former Carousel/Pickers diffs are gone, so it graduates from opt-in to
   default. See RENDERER_REFACTOR.md §4b/§13.

   Escape hatches (env override):
     off / defer / 0  → DeferredRenderNode  (replay the block every frame; no caching —
                                             the previous default, kept as a fallback)
     1 / texture      → SdlRenderNode       (offscreen-texture cache — legacy; fast for
                                             static leaves but timing-nondeterministic
                                             on complex screens, so not the default)
     *                → SdlDisplayListRenderNode (geo — the default) */
@OptIn(ExperimentalForeignApi::class)
private val layerCacheMode: String by lazy {
	platform.posix.getenv("CDN_LAYERCACHE")?.toKString() ?: ""
}

internal fun createNativeRenderNode(context: NativeRenderNodeContext): NativeRenderNode =
	when (layerCacheMode) {
		"off", "defer", "0" -> DeferredRenderNode()
		"1", "texture" -> SdlRenderNode()
		else -> SdlDisplayListRenderNode()
	}
