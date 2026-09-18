package com.compose.sdl.renderer.skia

import com.compose.sdl.*

import kotlinx.cinterop.*
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import sdl3.SDL_GL_MakeCurrent
import sdl3.SDL_GL_SwapWindow

// ==================
// MARK: SkiaGLBridge - Skia GPU on top of an SDL3 OpenGL context.
// ==================

/** Requires the SDL window to have been created with SDL_WINDOW_OPENGL and
   a GL context current. Each frame: clear via Canvas → draw → flush GL
   commands → SDL_GL_SwapWindow.

   Resize: tear down and rebuild the BackendRenderTarget + Surface; the
   DirectContext is reused. The default GL framebuffer (id 0) is wrapped
   directly; we don't manage an offscreen FBO. */
internal class SkiaGLBridge(private val backend: Sdl3Backend) : SkiaBridge {
    private var mContext: DirectContext? = null
    private var mRT: BackendRenderTarget? = null
    private var mSurface: Surface? = null
    private var mWidth = 0
    private var mHeight = 0

    /**
     Binds THIS window's GL context to the calling thread.

     A GL context is per-window but "current" is per-THREAD state, so with more
     than one window open the last one to touch GL owns the thread. SDL makes a
     context current once at creation (Sdl3Backend.init) and nothing re-bound it
     per frame, so window A issued its draw and swap against window B's context:
     silent corruption while both live, and a hard crash (SIGSEGV in present)
     the moment B is destroyed and the current context is left dangling.

     Every entry point that touches GL calls this first. Cheap when already
     current - SDL short-circuits a redundant bind, so the single-window path
     pays nothing.
     */
    private fun makeCurrent() {
        val vWindow = backend.window ?: return
        val vContext = backend.glContext ?: return
        SDL_GL_MakeCurrent(vWindow.reinterpret(), vContext.reinterpret())
    }

    fun init(): Boolean {
        return try {
            makeCurrent()
            mContext = DirectContext.makeGL()
            mContext != null
        } catch (t: Throwable) {
            println("SkiaGLBridge: DirectContext.makeGL failed: ${t.message}")
            false
        }
    }

    override val canvas: Canvas
        get() = requireNotNull(mSurface) { "SkiaGLBridge not initialised" }.canvas

    override fun ensureSize(inWidth: Int, inHeight: Int): Boolean {
        // BEFORE the unchanged-size early return: this runs once per frame and is
        // what re-binds this window's context for the draw phase that follows.
        makeCurrent()
        if (inWidth == mWidth && inHeight == mHeight && mSurface != null) return true
        if (inWidth <= 0 || inHeight <= 0) return false
        val vContext = mContext ?: return false

        mSurface?.close()
        mRT?.close()
        mSurface = null
        mRT = null

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
        mRT = vRT
        mSurface = vSurface
        mWidth = inWidth
        mHeight = inHeight
        return true
    }

    override fun present() {
        val vSurface = mSurface ?: return
        val vWindow = backend.window ?: return
        makeCurrent()
        vSurface.flushAndSubmit()
        SDL_GL_SwapWindow(vWindow.reinterpret())
    }

    override fun snapshot(): Image? = mSurface?.makeImageSnapshot()

    override fun snapshotBgra(): Triple<Int, Int, ByteArray>? =
        readBackBgra(mSurface, mWidth, mHeight)

    override fun destroy() {
        // Free this window's GPU objects against its OWN context, not whichever
        // window happens to be current.
        makeCurrent()
        mSurface?.close()
        mRT?.close()
        mContext?.close()
        mSurface = null
        mRT = null
        mContext = null
    }
}
