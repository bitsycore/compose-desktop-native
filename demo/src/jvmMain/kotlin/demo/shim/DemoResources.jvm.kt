package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

// JVM actuals: the demo's bundled drawables / files are on the classpath (the
// build points jvmMain resources at demo/src/nativeMain/composeResources). Compose
// Desktop's painterResource decodes PNG / JPG / SVG / Android-XML by extension.
@Composable
actual fun demoPainter(id: DemoDrawable): Painter = painterResource(
    when (id) {
        DemoDrawable.ComposeLogo -> "drawable/compose_logo.png"
        DemoDrawable.Photo -> "drawable/photo.jpg"
        DemoDrawable.Star -> "drawable/star.svg"
        DemoDrawable.Heart -> "drawable/heart.xml"
    },
)

actual fun demoReadBytes(id: DemoFile): ByteArray? = when (id) {
    DemoFile.Notice -> readClasspath("files/notice.txt")
}

private fun readClasspath(path: String): ByteArray? =
    Thread.currentThread().contextClassLoader?.getResourceAsStream(path)?.use { it.readBytes() }
