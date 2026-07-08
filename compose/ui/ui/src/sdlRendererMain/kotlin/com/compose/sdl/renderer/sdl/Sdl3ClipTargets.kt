package com.compose.sdl.renderer.sdl

import kotlinx.cinterop.*
import sdl3.*

// ==================
// MARK: Sdl3ClipTargets
// ==================

/* Persistent pool of offscreen render-target textures used by Sdl3Canvas to
   implement rounded-shape clipping (SDL has only a rectangular render clip, so
   a rounded / circular clip is done by drawing the clipped subtree into an
   offscreen target, zeroing the corners outside the rounded outline, then
   compositing that target back).

   Sdl3Canvas is recreated every frame, but GPU render targets are expensive to
   allocate, so this pool lives on Sdl3RenderBackend and is reused frame to
   frame. One target per clip-nesting depth (grown on demand); every target is
   sized to the full render output and recreated when the output size changes.

   Targets carry PREMULTIPLIED-alpha blend for the composite step. Content is
   drawn into a target with ordinary BLEND over a transparent (zeroed) surface,
   which leaves the target holding premultiplied colours (RGB already scaled by
   coverage, coverage in alpha). Compositing that back with plain BLEND would
   multiply by alpha a SECOND time — thinning/fading text and other partial-alpha
   content; BLEND_PREMULTIPLIED (dstRGBA = srcRGBA + dst*(1-srcA)) composites it
   correctly. */
@OptIn(ExperimentalForeignApi::class)
internal class Sdl3ClipTargets(private val fRenderer: COpaquePointer) {

	private var fWidth = 0
	private var fHeight = 0
	private val fTargets = mutableListOf<COpaquePointer>()

	/* Returns a render-target texture for clip nesting [inDepth], sized to
	   (inWidth, inHeight). Recreates the whole pool if the output size changed.
	   Returns null if the texture could not be created (caller falls back to a
	   plain rectangular clip). */
	fun target(inDepth: Int, inWidth: Int, inHeight: Int): COpaquePointer? {
		if (inWidth <= 0 || inHeight <= 0) return null
		if (inWidth != fWidth || inHeight != fHeight) {
			releaseAll()
			fWidth = inWidth
			fHeight = inHeight
		}
		while (fTargets.size <= inDepth) {
			val vTex = SDL_CreateTexture(
				fRenderer.reinterpret(),
				SDL_PIXELFORMAT_RGBA32,
				SDL_TextureAccess.SDL_TEXTUREACCESS_TARGET,
				fWidth,
				fHeight,
			) ?: return null
			SDL_SetTextureBlendMode(vTex.reinterpret(), SDL_BLENDMODE_BLEND_PREMULTIPLIED)
			fTargets.add(vTex)
		}
		return fTargets[inDepth]
	}

	private fun releaseAll() {
		for (vTex in fTargets) SDL_DestroyTexture(vTex.reinterpret())
		fTargets.clear()
	}

	fun destroy() = releaseAll()
}
