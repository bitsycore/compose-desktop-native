package com.compose.desktop.native.renderer.skia

import com.compose.desktop.native.*
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.objcPtr
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import platform.CoreGraphics.CGSizeMake
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.QuartzCore.CAMetalDrawableProtocol
import platform.QuartzCore.CAMetalLayer
import sdl3.SDL_Metal_GetLayer

// ==================
// MARK: SkiaMetalBridge — Skia GPU on top of an SDL3 Metal view.
// ==================

/* Per-frame flow:
   ensureSize → resize CAMetalLayer (if dimensions changed) and acquire the
   next drawable, wrap the drawable's texture as a Skia BackendRenderTarget
   and build a Surface around it.
   canvas → exposes the Surface's canvas to SkiaRenderer.
   present → flushAndSubmit() pushes Skia GPU work into the Metal command
   stream, then a fresh command buffer presents the drawable.

   Unlike GL where the same FBO is reused, Metal hands out a fresh drawable
   per frame, so we tear the BackendRenderTarget + Surface down every frame
   (cheap — the DirectContext + device + queue persist). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class SkiaMetalBridge(private val backend: SDL3Backend) : SkiaBridge {
    private var fDevice: MTLDeviceProtocol? = null
    private var fQueue: MTLCommandQueueProtocol? = null
    private var fLayer: CAMetalLayer? = null
    private var fContext: DirectContext? = null
    private var fSurface: Surface? = null
    private var fRT: BackendRenderTarget? = null
    private var fDrawable: CAMetalDrawableProtocol? = null
    private var fWidth = 0
    private var fHeight = 0

    fun init(): Boolean {
        val vView = backend.metalView ?: run {
            println("SkiaMetalBridge: SDL_Metal_CreateView wasn't called")
            return false
        }
        val vDevice = MTLCreateSystemDefaultDevice() ?: run {
            println("SkiaMetalBridge: MTLCreateSystemDefaultDevice returned null")
            return false
        }
        val vQueue = vDevice.newCommandQueue() ?: run {
            println("SkiaMetalBridge: newCommandQueue returned null")
            return false
        }
        val vLayerPtr = SDL_Metal_GetLayer(vView) ?: run {
            println("SkiaMetalBridge: SDL_Metal_GetLayer returned null")
            return false
        }
        val vLayer = interpretObjCPointer<CAMetalLayer>(vLayerPtr.rawValue)
        // CAMetalLayer.device is typed via a forward-declared MTLDevice in
        // QuartzCore headers (objcnames.protocols.MTLDeviceProtocol), which
        // Kotlin/Native can't unify with platform.Metal.MTLDeviceProtocol —
        // bridge through the pointer.
        vLayer.device = interpretObjCPointer<objcnames.protocols.MTLDeviceProtocol>(vDevice.objcPtr())
        vLayer.pixelFormat = MTLPixelFormatBGRA8Unorm
        // contentsScale matches the SDL backbuffer (HIGH_PIXEL_DENSITY): on
        // Retina the drawable is 2× the logical bounds, ComposeWindow scales
        // the Skia canvas by the same DPR, and pixels end up 1:1 on screen.
        vLayer.contentsScale = backend.pixelDensity.toDouble()

        val vContext = try {
            DirectContext.makeMetal(vDevice.objcPtr(), vQueue.objcPtr())
        } catch (t: Throwable) {
            println("SkiaMetalBridge: DirectContext.makeMetal failed: ${t.message}")
            return false
        }

        fDevice = vDevice
        fQueue = vQueue
        fLayer = vLayer
        fContext = vContext
        return true
    }

    override val canvas: Canvas
        get() = requireNotNull(fSurface) { "SkiaMetalBridge not initialised" }.canvas

    override fun ensureSize(inWidth: Int, inHeight: Int): Boolean {
        if (inWidth <= 0 || inHeight <= 0) return false
        val vLayer = fLayer ?: return false
        val vContext = fContext ?: return false

        if (inWidth != fWidth || inHeight != fHeight) {
            vLayer.drawableSize = CGSizeMake(inWidth.toDouble(), inHeight.toDouble())
            // Keep contentsScale in lock-step with the live backing density.
            // Setting it only in init() can leave it stale at 1.0 when the
            // window hadn't acquired its Retina backing yet: the physical-px
            // drawable then gets downscaled to the layer's logical backing and
            // re-upscaled by the display, softening text and aliasing edges.
            vLayer.contentsScale = backend.pixelDensity.toDouble()
            fWidth = inWidth
            fHeight = inHeight
            println("SkiaMetalBridge: drawable ${inWidth}x${inHeight} @ contentsScale ${backend.pixelDensity}")
        }

        // Per-frame: drop the previous frame's drawable wrappers and grab a
        // fresh one. nextDrawable() may block (~16ms vsync) when 3 drawables
        // are already in flight — that's the natural pacing mechanism.
        fSurface?.close()
        fRT?.close()
        fSurface = null
        fRT = null

        val vDrawable = vLayer.nextDrawable() ?: run {
            println("SkiaMetalBridge: nextDrawable returned null")
            return false
        }
        val vRT = try {
            BackendRenderTarget.makeMetal(inWidth, inHeight, vDrawable.texture.objcPtr())
        } catch (t: Throwable) {
            println("SkiaMetalBridge: BackendRenderTarget.makeMetal failed: ${t.message}")
            return false
        }
        val vSurface = Surface.makeFromBackendRenderTarget(
            vContext,
            vRT,
            SurfaceOrigin.TOP_LEFT,
            SurfaceColorFormat.BGRA_8888,
            null,
        )
        if (vSurface == null) {
            vRT.close()
            println("SkiaMetalBridge: Surface.makeFromBackendRenderTarget returned null")
            return false
        }
        fRT = vRT
        fSurface = vSurface
        fDrawable = vDrawable
        return true
    }

    override fun present() {
        val vSurface = fSurface ?: return
        val vQueue = fQueue ?: return
        val vDrawable = fDrawable ?: return
        vSurface.flushAndSubmit()
        val vCommandBuffer = vQueue.commandBuffer() ?: return
        vCommandBuffer.presentDrawable(vDrawable)
        vCommandBuffer.commit()
        fDrawable = null
    }

    override fun snapshot(): Image? = fSurface?.makeImageSnapshot()

    override fun snapshotBgra(): Triple<Int, Int, ByteArray>? =
        readBackBgra(fSurface, fWidth, fHeight)

    override fun destroy() {
        fSurface?.close()
        fRT?.close()
        fContext?.close()
        fSurface = null
        fRT = null
        fContext = null
        fLayer = null
        fQueue = null
        fDevice = null
        fDrawable = null
    }
}
