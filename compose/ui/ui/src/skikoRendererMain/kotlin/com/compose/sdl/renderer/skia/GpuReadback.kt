package com.compose.sdl.renderer.skia

import com.compose.sdl.*

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

// ==================
// MARK: GPU surface pixel readback
// ==================

/* Allocates a host Bitmap with BGRA8888 layout and asks Skia to copy the
   given GPU Surface's pixels into it. Returns (w, h, bytes) on success.

   Returns null if anything along the way fails: the Surface may not yet
   exist (first frame), the GPU readback may fail, or bitmap.readPixels()
   may not be exposed in this Skiko build. */
internal fun readBackBgra(inSurface: Surface?, inWidth: Int, inHeight: Int): Triple<Int, Int, ByteArray>? {
    val vSurface = inSurface ?: return null
    if (inWidth <= 0 || inHeight <= 0) return null
    val vBitmap = Bitmap()
    val vInfo = ImageInfo(inWidth, inHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    if (!vBitmap.allocPixels(vInfo)) {
        vBitmap.close()
        return null
    }
    val vCopied = vSurface.readPixels(vBitmap, 0, 0)
    if (!vCopied) {
        vBitmap.close()
        return null
    }
    val vBytes = vBitmap.readPixels()
    vBitmap.close()
    if (vBytes == null) return null
    return Triple(inWidth, inHeight, vBytes)
}
