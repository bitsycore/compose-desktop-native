package apidemo

import com.compose.desktop.native.icons.IconFont
import com.compose.desktop.native.loadComposeResourceBytes

// ==================
// MARK: Monospace body font
// ==================

// Family name the body editor passes to Text / BasicTextField for monospace
// text. Resolves to the bundled Noto Sans Mono once registered; null means the
// font isn't in data.kres, in which case the body falls back to the default
// (proportional Noto Sans).
const val kMonoFamily = "noto-mono"

// Registers Noto Sans Mono (bundled in data.kres under font/) with IconFont on
// first access and returns kMonoFamily; null if the font is missing. Lazy so
// it runs after the window / resource layer is up — loadComposeResourceBytes
// reads data.kres via SDL_GetBasePath(), which is only valid post-init.
val monoFontFamily: String? by lazy {
    val vBytes = loadComposeResourceBytes("font/NotoSansMono.ttf")
    if (vBytes == null) {
        println("apidemo: NotoSansMono.ttf not bundled — body uses the default font")
        null
    } else {
        IconFont.register(kMonoFamily, vBytes)
        kMonoFamily
    }
}
