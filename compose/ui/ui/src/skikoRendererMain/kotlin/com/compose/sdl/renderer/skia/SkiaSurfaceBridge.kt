package com.compose.sdl.renderer.skia

import com.compose.sdl.*

import kotlinx.cinterop.*
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import sdl3.*

// ==================
// MARK: SkiaSurfaceBridge
// ==================

/* Owns the CPU pixel buffer Skia draws into, plus the streaming SDL_Texture
   used to push that buffer onto the window. All three (buffer, Skia surface,
   SDL texture) get reallocated when the window resizes. */
class SkiaSurfaceBridge(private val backend: SDL3Backend) : SkiaBridge {
    private var fWidth = 0
    private var fHeight = 0
    private var fPixels: CPointer<UByteVar>? = null
    private var fSurface: Surface? = null
    private var fTexture: COpaquePointer? = null

    override val canvas: Canvas
        get() = requireNotNull(fSurface) { "SkiaSurfaceBridge not initialised" }.canvas

    val width: Int get() = fWidth
    val height: Int get() = fHeight

    override fun ensureSize(inWidth: Int, inHeight: Int): Boolean {
        if (inWidth == fWidth && inHeight == fHeight && fSurface != null) return true
        if (inWidth <= 0 || inHeight <= 0) return false
        destroy()

        val vRenderer = backend.renderer ?: return false
        val vBytes = inWidth * inHeight * 4
        val vPixels = nativeHeap.allocArray<UByteVar>(vBytes)

        val vSurface = Surface.makeRasterDirect(
            ImageInfo(inWidth, inHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
            vPixels.rawValue,
            inWidth * 4
        )

        val vTexture = SDL_CreateTexture(
            vRenderer.reinterpret(),
            SDL_PIXELFORMAT_RGBA32,
            SDL_TextureAccess.SDL_TEXTUREACCESS_STREAMING,
            inWidth,
            inHeight
        )
        if (vTexture == null) {
            vSurface.close()
            nativeHeap.free(vPixels)
            return false
        }
        SDL_SetTextureBlendMode(vTexture.reinterpret(), SDL_BLENDMODE_NONE)

        fPixels = vPixels
        fSurface = vSurface
        fTexture = vTexture
        fWidth = inWidth
        fHeight = inHeight
        return true
    }

    /* Skia → SDL_Texture → screen, once per frame. */
    override fun present() {
        val vRenderer = backend.renderer ?: return
        val vTexture = fTexture ?: return
        val vSurface = fSurface ?: return
        val vPixels = fPixels ?: return

        vSurface.flushAndSubmit()
        SDL_UpdateTexture(vTexture.reinterpret(), null, vPixels, fWidth * 4)
        SDL_RenderTexture(vRenderer.reinterpret(), vTexture.reinterpret(), null, null)
        SDL_RenderPresent(vRenderer.reinterpret())
    }

    override fun snapshot(): Image? = fSurface?.makeImageSnapshot()

    /* CPU bridge already has the raw pixels in fPixels (RGBA8888 premul).
       Repack to BGRA so we share one BMP writer with the GPU bridges. */
    override fun snapshotBgra(): Triple<Int, Int, ByteArray>? {
        val vPixels = fPixels ?: return null
        val vCount = fWidth * fHeight
        val vBytes = ByteArray(vCount * 4)
        for (i in 0 until vCount) {
            val vR = vPixels[i * 4 + 0]
            val vG = vPixels[i * 4 + 1]
            val vB = vPixels[i * 4 + 2]
            val vA = vPixels[i * 4 + 3]
            vBytes[i * 4 + 0] = vB.toByte()
            vBytes[i * 4 + 1] = vG.toByte()
            vBytes[i * 4 + 2] = vR.toByte()
            vBytes[i * 4 + 3] = vA.toByte()
        }
        return Triple(fWidth, fHeight, vBytes)
    }

    override fun destroy() {
        fTexture?.let { SDL_DestroyTexture(it.reinterpret()) }
        fSurface?.close()
        fPixels?.let { nativeHeap.free(it) }
        fTexture = null
        fSurface = null
        fPixels = null
        fWidth = 0
        fHeight = 0
    }
}
