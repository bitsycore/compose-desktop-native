package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.compose.desktop.native.res.Res
import composeresources.generated.compose_logo
import composeresources.generated.heart
import composeresources.generated.notice
import composeresources.generated.photo
import composeresources.generated.star

// Native actuals: the generated typed accessors (from generateComposeResAccessors,
// wired into nativeMain) back the bundled drawables / files in data.kres.
@Composable
actual fun demoPainter(id: DemoDrawable): Painter = when (id) {
    DemoDrawable.ComposeLogo -> Res.drawable.compose_logo
    DemoDrawable.Photo -> Res.drawable.photo
    DemoDrawable.Star -> Res.drawable.star
    DemoDrawable.Heart -> Res.drawable.heart
}

actual fun demoReadBytes(id: DemoFile): ByteArray? = when (id) {
    DemoFile.Notice -> Res.readBytes(Res.files.notice)
}
