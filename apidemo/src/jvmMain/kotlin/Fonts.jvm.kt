package apidemo

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

// ==================
// MARK: Monospace body font - JVM actuals (classpath font/ staged by
// jvmProcessResources)
// ==================

actual val monoFontFamily: FontFamily? by lazy {
    val vBytes = object {}.javaClass.getResourceAsStream("/font/NotoSansMono.ttf")?.use { it.readBytes() }
    if (vBytes == null) {
        println("apidemo: NotoSansMono.ttf not on the classpath - body uses the default font")
        null
    } else {
        FontFamily(Font(identity = kMonoFamily, data = vBytes))
    }
}

actual val monoFontFamilyName: String? by lazy {
    if (monoFontFamily != null) kMonoFamily else null
}
