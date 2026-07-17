import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import demo.registry.allCategories
import demo.shell.App
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

// The JVM comparison app: the SAME shared App() + screens as :demo, on upstream
// Compose Desktop. Interactive by default; a headless screenshot mode drives
// the parity harness (scripts/parity — compares each screen native vs jvm).
//
//   --screenshot-all=<dir>   render every registered screen to <dir>/<Name>.png
//   --width / --height       viewport size (default 1000 / 700)
//
// The single-screen wrapper MIRRORS MainNative's --screen path (dark theme,
// verticalScroll + 24dp padding) so layout constraints match the native
// screenshots pixel-for-pixel.
fun main(args: Array<String>) {
    val screenshotDir = args.firstOrNull { it.startsWith("--screenshot-all=") }?.substringAfter('=')
    if (screenshotDir != null) {
        screenshotAllScreens(
            outDir = File(screenshotDir),
            width = args.intArg("--width", 1000),
            height = args.intArg("--height", 700),
        )
        return
    }
    if (args.any { it == "--metrics" }) {
        printParagraphMetrics()
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ComposeDesktopNative — JVM (upstream Compose)",
            state = rememberWindowState(width = 1000.dp, height = 700.dp),
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(isJvm = true)
            }
        }
    }
}

private fun Array<String>.intArg(name: String, default: Int): Int =
    firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.toIntOrNull() ?: default

// P0.5 render-to-quiescence (mirrors the native leg): step a VIRTUAL 60fps clock and
// re-render while the scene still has invalidations, so entrance animations settle
// before capture. Infinite animations are CANCELLED via the upstream test
// InfiniteAnimationPolicy (they freeze at their initial value), so looping screens
// settle too. The cap only guards never-settling content the policy can't reach.
private const val FRAME_NANOS = 16_666_667L
private const val MAX_FRAMES = 300

private object CancelInfiniteAnimations : androidx.compose.ui.platform.InfiniteAnimationPolicy {
    override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R =
        throw kotlinx.coroutines.CancellationException("infinite animations disabled for parity screenshots")
}

/* Render each registered screen headlessly (density 1 to match the native
   physical-pixel screenshots) to quiescence and write a PNG per screen. */
@OptIn(ExperimentalComposeUiApi::class)
private fun screenshotAllScreens(outDir: File, width: Int, height: Int) {
    outDir.mkdirs()
    val screens = allCategories().flatMap { it.screens }.distinctBy { it.name }
    for (screen in screens) {
        val scene = ImageComposeScene(
            width, height, density = Density(1f),
            coroutineContext = kotlinx.coroutines.Dispatchers.Unconfined + CancelInfiniteAnimations,
        ) {
            ScreenHost { screen.content() }
        }
        try {
            var nanos = 0L
            var frames = 0
            var image = scene.render(nanos)
            while (scene.hasInvalidations() && frames < MAX_FRAMES) {
                nanos += FRAME_NANOS
                image = scene.render(nanos)
                frames++
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: continue
            File(outDir, "${screen.name}.png").writeBytes(png.bytes)
            println("jvm screenshot: ${screen.name} (settled after $frames frame(s))")
        } finally {
            scene.close()
        }
    }
}

// P0.3 (RENDERER_CONVERGE.md §8): the SAME default font the native leg bundles
// (font/NotoSans.ttf, staged onto the JVM classpath by jvmProcessResources). Loading it
// into the parity JVM leg collapses the font-drift baseline to (near) just rasterizer AA —
// so the parity % measures real divergence, not "different default typeface". Null (→ no
// alignment, harness still runs) if the resource is missing.
private val notoSans: FontFamily? by lazy {
    val bytes = object {}.javaClass.getResourceAsStream("/font/NotoSans.ttf")?.readBytes()
    if (bytes == null) {
        println("parity(jvm): /font/NotoSans.ttf not on classpath — font NOT aligned")
        null
    } else FontFamily(Font(identity = "NotoSans", data = bytes))
}

/* A copy of the M3 Typography with every core style forced to [family] (Typography styles
   carry their own fontFamily, so overriding LocalTextStyle alone wouldn't reach them). */
private fun notoTypography(family: FontFamily): Typography {
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.copy(fontFamily = family),
        displayMedium = b.displayMedium.copy(fontFamily = family),
        displaySmall = b.displaySmall.copy(fontFamily = family),
        headlineLarge = b.headlineLarge.copy(fontFamily = family),
        headlineMedium = b.headlineMedium.copy(fontFamily = family),
        headlineSmall = b.headlineSmall.copy(fontFamily = family),
        titleLarge = b.titleLarge.copy(fontFamily = family),
        titleMedium = b.titleMedium.copy(fontFamily = family),
        titleSmall = b.titleSmall.copy(fontFamily = family),
        bodyLarge = b.bodyLarge.copy(fontFamily = family),
        bodyMedium = b.bodyMedium.copy(fontFamily = family),
        bodySmall = b.bodySmall.copy(fontFamily = family),
        labelLarge = b.labelLarge.copy(fontFamily = family),
        labelMedium = b.labelMedium.copy(fontFamily = family),
        labelSmall = b.labelSmall.copy(fontFamily = family),
    )
}

/* Upstream-Paragraph metrics for a font-size sweep at density 1 (NotoSans — the same
   font the native leg bundles). The JVM half of the metrics-alignment probe: MainNative's
   `--metricsprobe` prints the same table from SdlParagraph; aligning the numbers kills
   the accumulating vertical text drift in parity (P3.1). Runs inside a headless scene so
   LocalFontFamilyResolver supplies the platform resolver. */
@OptIn(ExperimentalComposeUiApi::class)
private fun printParagraphMetrics() {
    val scene = ImageComposeScene(100, 100, density = Density(1f)) {
        val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
        val density = Density(1f)
        fun paragraph(text: String, size: Int, lineHeight: Int?, m3Style: Boolean): androidx.compose.ui.text.Paragraph =
            androidx.compose.ui.text.Paragraph(
                text = text,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = size.sp,
                    lineHeight = lineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                    fontFamily = notoSans,
                    lineHeightStyle = if (m3Style) {
                        androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                        )
                    } else null,
                ),
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = 10_000),
                density = density,
                fontFamilyResolver = resolver,
            )
        // What the demo's Text actually resolves to on this leg — is lineHeightStyle set?
        println("metrics: m3 bodyMedium = ${Typography().bodyMedium.fontSize}/${Typography().bodyMedium.lineHeight} lineHeightStyle=${Typography().bodyMedium.lineHeightStyle}")
        for (size in listOf(11, 12, 14, 16, 22, 24)) {
            val lh = size + 6
            val cell = paragraph("Hg", size, null, false)
            val one = paragraph("Hg", size, lh, false)
            val three = paragraph("Hg\nHg\nHg", size, lh, false)
            val oneM3 = paragraph("Hg", size, lh, true)
            val threeM3 = paragraph("Hg\nHg\nHg", size, lh, true)
            println(
                "metrics: size=$size lh=$lh cell=${cell.height} " +
                    "one=${one.height} three=${three.height} " +
                    "base1=${one.firstBaseline} base3=${three.lastBaseline} " +
                    "oneM3=${oneM3.height} threeM3=${threeM3.height} base1M3=${oneM3.firstBaseline}"
            )
        }
        // h-boundary cases: does upstream apply lineHeight at exactly 1em?
        for ((s, l) in listOf(24 to 24, 24 to 25, 32 to 24, 16 to 16)) {
            val b = paragraph("Hg", s, l, true)
            println("metrics: boundary $s/$l m3=${b.height} base=${b.firstBaseline}")
        }
        // The band-smaller-than-cell case (Counter's fontSize=48 under body lineHeight).
        val big = paragraph("42", 48, 24, false)
        val bigM3 = paragraph("42", 48, 24, true)
        println("metrics: big48/lh24 raw=${big.height} base=${big.firstBaseline} m3=${bigM3.height} baseM3=${bigM3.firstBaseline}")
        println("metrics: DONE")
    }
    try {
        scene.render()
    } finally {
        scene.close()
    }
}

/* Same wrapper as MainNative's --screen path — plus NotoSans font-alignment (P0.3) so the
   parity diff isn't dominated by the JVM's default typeface. */
@Composable
private fun ScreenHost(content: @Composable () -> Unit) {
    val family = notoSans
    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = if (family != null) notoTypography(family) else Typography(),
    ) {
        val base = LocalTextStyle.current
        CompositionLocalProvider(
            LocalTextStyle provides (if (family != null) base.copy(fontFamily = family) else base),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) { content() }
        }
    }
}
