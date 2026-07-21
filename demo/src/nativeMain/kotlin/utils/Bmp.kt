package utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

// ==================
// MARK: BMP save
// ==================

@OptIn(ExperimentalForeignApi::class)
internal fun writeFile(inPath: String, inBytes: ByteArray) {
    val vFile = fopen(inPath, "wb") ?: run {
        println("fopen failed for $inPath")
        return
    }
    fwrite(inBytes.refTo(0), 1u, inBytes.size.toULong(), vFile)
    fclose(vFile)
}

/** Minimal BMP writer: 24-bit BGR (drop alpha) with the classic 40-byte
   BITMAPINFOHEADER. Universally readable, including `sips`. Rows are
   bottom-up (positive height) and padded to 4 bytes. */
internal fun encodeBmpBgra32(inWidth: Int, inHeight: Int, inBgra: ByteArray): ByteArray {
    val kFileHeader = 14
    val kInfoHeader = 40
    val vRowBytes = inWidth * 3
    val vRowPad = (4 - vRowBytes % 4) % 4
    val vStride = vRowBytes + vRowPad
    val vPixelBytes = vStride * inHeight
    val vTotal = kFileHeader + kInfoHeader + vPixelBytes
    val vOut = ByteArray(vTotal)

    fun putU16LE(off: Int, v: Int) {
        vOut[off]     = (v and 0xFF).toByte()
        vOut[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }
    fun putU32LE(off: Int, v: Int) {
        vOut[off]     = (v and 0xFF).toByte()
        vOut[off + 1] = ((v ushr 8) and 0xFF).toByte()
        vOut[off + 2] = ((v ushr 16) and 0xFF).toByte()
        vOut[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    // BITMAPFILEHEADER
    vOut[0] = 'B'.code.toByte(); vOut[1] = 'M'.code.toByte()
    putU32LE(2, vTotal)
    putU32LE(10, kFileHeader + kInfoHeader)

    // BITMAPINFOHEADER
    val info = kFileHeader
    putU32LE(info + 0,  kInfoHeader)
    putU32LE(info + 4,  inWidth)
    putU32LE(info + 8,  inHeight)              // positive → bottom-up
    putU16LE(info + 12, 1)
    putU16LE(info + 14, 24)
    putU32LE(info + 16, 0)                     // BI_RGB
    putU32LE(info + 20, vPixelBytes)
    putU32LE(info + 24, 2835)
    putU32LE(info + 28, 2835)
    putU32LE(info + 32, 0)
    putU32LE(info + 36, 0)

    // Source is top-down BGRA; convert to bottom-up 24-bit BGR.
    val vPixelOffset = kFileHeader + kInfoHeader
    for (y in 0 until inHeight) {
        val vSrcRow = (inHeight - 1 - y) * inWidth * 4
        val vDstRow = vPixelOffset + y * vStride
        for (x in 0 until inWidth) {
            vOut[vDstRow + x * 3 + 0] = inBgra[vSrcRow + x * 4 + 0]  // B
            vOut[vDstRow + x * 3 + 1] = inBgra[vSrcRow + x * 4 + 1]  // G
            vOut[vDstRow + x * 3 + 2] = inBgra[vSrcRow + x * 4 + 2]  // R
        }
    }
    return vOut
}
