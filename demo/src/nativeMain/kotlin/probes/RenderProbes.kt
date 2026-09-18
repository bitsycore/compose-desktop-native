import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.dp
import com.compose.sdl.nativeComposeWindow
import demo.registry.allCategories
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

// ==================
// MARK: Renderer probes - the soak run and encoded-image decoding.
// ==================
//
// Split out of MainNative.kt, which held the entry point plus 20 scenarios in
// 1240 lines. Behaviour is unchanged; each probe is `internal` so main()'s
// argument dispatch still reaches it.

/** P2.2 soak - cycle through EVERY registered screen kCycles times in ONE process,
   disposing each via a changing key() so composition + layer (skiko RenderNode)
   allocate-and-release is exercised repeatedly. After each full cycle, GC then
   record peak RSS (getrusage ru_maxrss). Peak RSS is monotonic, so after cycle 1 it
   already reflects visiting every screen once; if there is NO leak it plateaus, if
   there IS one it keeps climbing each cycle. PASS iff last-cycle peak stays within a
   ceiling of the first-cycle peak. */
@OptIn(kotlin.native.runtime.NativeRuntimeApi::class, ExperimentalForeignApi::class)
internal fun runSoakTest() {
    // Disable never-settling animations so RSS reflects composition/layer lifetime,
    // not live animation-state churn (same seed the screenshot path uses).
    com.compose.sdl.disableInfiniteAnimations = true
    com.compose.sdl.useVirtualFrameTime = true
    val vAll = allCategories().flatMap { it.screens }
    // CDN_SOAK_SCREEN=<name> repeats ONE screen 40x/cycle (bisect a specific screen);
    // default cycles through every screen.
    val vTarget = platform.posix.getenv("CDN_SOAK_SCREEN")?.toKString()
    val vScreens = if (vTarget != null) { val vS = vAll.first { it.name.equals(vTarget, ignoreCase = true) }; List(40) { vS } }
        else vAll
    val vIndex = mutableStateOf(0)
    // CDN_SOAK_STATIC=1: mount ONE screen once, never remount - measure RSS every 120 frames.
    // Isolates a per-FRAME leak (RSS climbs with no remounts) from a per-MOUNT leak.
    val vStatic = platform.posix.getenv("CDN_SOAK_STATIC")?.toKString() == "1"
    val kFramesPerScreen = if (vStatic) 120 else 3
    val kCycles = platform.posix.getenv("CDN_SOAK_CYCLES")?.toKString()?.toIntOrNull() ?: 3
    val vRssPerCycleMb = mutableListOf<Long>()
    var vShown = 0

    nativeComposeWindow(
        title = "soaktest",
        width = 1000,
        height = 700,
        onFrame = { _, vFrame ->
            if (vFrame > 0 && vFrame % kFramesPerScreen == 0) {
                if (vStatic) {
                    // No remount - just pump frames on the one mounted screen.
                    kotlin.native.runtime.GC.collect()
                    vRssPerCycleMb.add(currentResidentMb())
                    println("soaktest[static]: measure ${vRssPerCycleMb.size}: currentRSS=${vRssPerCycleMb.last()}MB")
                } else {
                    vShown++
                    vIndex.value = vShown % vScreens.size
                    if (vShown % vScreens.size == 0) {
                        kotlin.native.runtime.GC.collect()
                        vRssPerCycleMb.add(currentResidentMb())
                        println("soaktest: cycle ${vRssPerCycleMb.size}: currentRSS=${vRssPerCycleMb.last()}MB")
                    }
                }
            }
            if (vRssPerCycleMb.size >= kCycles) {
                val vFirst = vRssPerCycleMb.first()
                val vLast = vRssPerCycleMb.last()
                val vGrowth = vLast - vFirst
                val vCeiling = maxOf(48L, vFirst / 4)  // 25% or 48MB, whichever larger
                println("soaktest: currentRSS/cycle(MB)=$vRssPerCycleMb (${vScreens.size} screens x $kCycles cycles)")
                if (vGrowth <= vCeiling) {
                    println("soaktest: PASS (current RSS grew ${vGrowth}MB over cycles 1->$kCycles, within ${vCeiling}MB ceiling)")
                } else {
                    println("soaktest: FAIL (current RSS grew ${vGrowth}MB > ${vCeiling}MB ceiling - possible leak)")
                }
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val vScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(vScroll)
                    .padding(24.dp),
            ) {
                // key(vShown) forces a FULL dispose+recompose each advance (even when the
                // target screen repeats) - the allocate/release churn we soak. Reading
                // vIndex.value (a State) is what triggers the recomposition each mount.
                key(vShown) {
                    vScreens[vIndex.value].content()
                }
            }
        }
    }
}

/** CURRENT resident set (MB): current RSS can DROP after GC, so it distinguishes a
   true leak (ratchets up) from K/N allocator high-water, unlike getrusage's
   monotonic peak. posix reads it from `ps`; mingw has no `ps`/popen and returns
   -1 (the soak gate runs on macOS/Linux - see scripts/verify-mac.sh). */
internal expect fun currentResidentMb(): Long

/** --imagebytestest: decodes an in-memory 2x2 BMP via ByteArray.decodeToImageBitmap()
   (routes to createImageBitmap(bytes)) through Skia's image decoder. Needs a window
   so the render backend installs the encoded-image decoder. */
internal fun runImageBytesTest() {
    nativeComposeWindow(
        title = "imagebytestest",
        width = 200,
        height = 200,
        onFrame = { _, vFrame ->
            if (vFrame >= 2) {
                try {
                    val vBitmap = tinyRedBmp().decodeToImageBitmap()
                    println("imagebytestest: decoded ${vBitmap.width}x${vBitmap.height}")
                    println(if (vBitmap.width == 2 && vBitmap.height == 2) "imagebytestest: PASS" else "imagebytestest: FAIL (wrong size)")
                } catch (e: Throwable) {
                    println("imagebytestest: FAIL (${e::class.simpleName}: ${e.message})")
                }
                false
            } else true
        },
    ) {}
}

// A minimal 2x2 24bpp red BMP (no compression) - valid decoder input.
internal fun tinyRedBmp(): ByteArray {
    fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )
    val vPixel = byteArrayOf(0, 0, 0xFF.toByte())          // BGR red
    val vRow = vPixel + vPixel + byteArrayOf(0, 0)          // 2px + 2 pad = 8 bytes (4-byte aligned)
    val vPixels = vRow + vRow                               // 2 rows = 16 bytes
    val vFileHeader = byteArrayOf('B'.code.toByte(), 'M'.code.toByte()) + le32(70) + le32(0) + le32(54)
    val vDib = le32(40) + le32(2) + le32(2) + le16(1) + le16(24) + le32(0) + le32(16) +
        le32(2835) + le32(2835) + le32(0) + le32(0)
    return vFileHeader + vDib + vPixels
}
