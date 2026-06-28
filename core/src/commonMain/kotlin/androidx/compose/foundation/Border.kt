package androidx.compose.foundation

import com.compose.desktop.native.element.BorderModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp

// ==================
// MARK: Modifier.border()
// ==================

// BorderStroke is vendored verbatim from upstream and lives in
// core/src/vendor/.../foundation/BorderStroke.kt. It wraps a Brush; here we
// only act on the SolidColor case for now (the renderers' border paths read a
// flat Color), and silently no-op gradient brushes — match-the-signature
// without yet matching the behaviour.

fun Modifier.border(width: Dp, color: Color, shape: Shape = RectangleShape): Modifier =
    then(BorderModifier(width.value.toInt(), color, shape))

fun Modifier.border(border: BorderStroke, shape: Shape = RectangleShape): Modifier {
    val vColor = (border.brush as? SolidColor)?.color ?: return this
    return then(BorderModifier(border.width.value.toInt(), vColor, shape))
}
