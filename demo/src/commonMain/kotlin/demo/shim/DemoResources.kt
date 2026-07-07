package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

// ==================
// MARK: Resource shims
// ==================

/* Bundled drawables the shared Images screen shows. Native resolves each to a
   generated Res.drawable.* accessor (data.kres); a future jvm target resolves
   them via org.jetbrains.compose.resources. */
enum class DemoDrawable { ComposeLogo, Photo, Star, Heart }

/* Bundled raw files. */
enum class DemoFile { Notice }

@Composable
expect fun demoPainter(id: DemoDrawable): Painter

expect fun demoReadBytes(id: DemoFile): ByteArray?
