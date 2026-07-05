package com.compose.desktop.native.renderer.skia

import com.compose.desktop.native.*

import kotlinx.cinterop.*
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import sdl3.SDL_GL_SwapWindow

// ==================
// MARK: SkiaGLBridge — Skia GPU on top of an SDL3 OpenGL context.
// ==================

/* Requires the SDL window to have been created with SDL_WINDOW_OPENGL and
   a GL context current. Each frame: clear via Canvas → draw → flush GL
   commands → SDL_GL_SwapWindow.

   Resize: tear down and rebuild the BackendRenderTarget + Surface; the
   DirectContext is reused. The default GL framebuffer (id 0) is wrapped
   directly; we don't manage an offscreen FBO. */
internal class SkiaGLBridge(private val backend: SDL3Backend) : SkiaBridge {
    private var fContext: DirectContext? = null
    private var fRT: BackendRenderTarget? = null
    private var fSurface: Surface? = null
    private var fWidth = 0
    private var fHeight = 0

    fun init(): Boolean {
        return try {
            fContext = DirectContext.makeGL()
            fContext != null
        } catch (t: Throwable) {
            println("SkiaGLBridge: DirectContext.makeGL failed: ${t.message}")
            false
        }
    }

    override val canvas: Canvas
        get() = requireNotNull(fSurface) { "SkiaGLBridge not initialised" }.canvas

    override fun ensureSize(inWidth: Int, inHeight: Int): Boolean {
        if (inWidth == fWidth && inHeight == fHeight && fSurface != null) return true
        if (inWidth <= 0 || inHeight <= 0) return false
        val vContext = fContext ?: return false

        fSurface?.close()
        fRT?.close()
        fSurface = null
        fRT = null

        // GL_RGBA8 = 0x8058. fbId = 0 binds the default framebuffer.
        // sampleCnt = 0 means no MSAA; stencilBits = 8 is what Skia recommends.
        val vRT = try {
            BackendRenderTarget.makeGL(inWidth, inHeight, 0, 8, 0, 0x8058)
        } catch (t: Throwable) {
            println("SkiaGLBridge: BackendRenderTarget.makeGL failed: ${t.message}")
            return false
        }
        val vSurface = Surface.makeFromBackendRenderTarget(
            vContext,
            vRT,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            null,
        )
        if (vSurface == null) {
            vRT.close()
            println("SkiaGLBridge: Surface.makeFromBackendRenderTarget returned null")
            return false
        }
        fRT = vRT
        fSurface = vSurface
        fWidth = inWidth
        fHeight = inHeight
        return true
    }

    override fun present() {
        val vSurface = fSurface ?: return
        val vWindow = backend.window ?: return
        vSurface.flushAndSubmit()
        SDL_GL_SwapWindow(vWindow.reinterpret())
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
    }
}
