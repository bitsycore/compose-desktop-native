package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import demo.generated.resources.Res
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource

// Official org.jetbrains.compose.resources on BOTH sides: the native targets
// resolve it against the vendored :components-resources (data.kres reader +
// SDL3_image decode / common XML-vector parser), the JVM target against the
// official Maven artifact (classpath reader + Skia decode). Same generated
// accessors, same relative paths — this file is IDENTICAL in both actuals.
@Composable
actual fun demoPainter(id: DemoDrawable): Painter = painterResource(
	when (id) {
		DemoDrawable.ComposeLogo -> Res.drawable.compose_logo
		DemoDrawable.Photo -> Res.drawable.photo
		DemoDrawable.Star -> Res.drawable.star
		DemoDrawable.Heart -> Res.drawable.heart
	},
)

actual fun demoReadBytes(id: DemoFile): ByteArray? = runBlocking {
	runCatching { Res.readBytes(Res.files.notice) }.getOrNull()
}
