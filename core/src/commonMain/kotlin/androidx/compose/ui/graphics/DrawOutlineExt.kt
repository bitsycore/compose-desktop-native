package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill

// ==================
// MARK: DrawScope.drawOutline extensions
// ==================

/**
 * Mirror of upstream `androidx.compose.ui.graphics.drawOutline(...)`
 * DrawScope extensions, in the project's `DrawScope` shape. Vendored
 * downstream files (Background.kt, Border.kt, …) call these to paint
 * a resolved `Outline` with a color or brush. We dispatch to the
 * project DrawScope's `drawRect / drawRoundRect / drawPath`.
 *
 * Project DrawScope's drawRoundRect / drawCircle don't take blendMode
 * or colorFilter, so those upstream params are accept-and-ignore.
 */
fun DrawScope.drawOutline(
	outline: Outline,
	color: Color,
	@Suppress("UNUSED_PARAMETER") alpha: Float = 1.0f,
	style: DrawStyle = Fill,
	@Suppress("UNUSED_PARAMETER") colorFilter: ColorFilter? = null,
	@Suppress("UNUSED_PARAMETER") blendMode: BlendMode = BlendMode.SrcOver,
) {
	when (outline) {
		is Outline.Rectangle ->
			drawRect(color, Offset(outline.rect.left, outline.rect.top),
				Size(outline.rect.width, outline.rect.height), style = style)
		is Outline.Rounded -> {
			val r = outline.roundRect
			drawRoundRect(
				color = color,
				topLeft = Offset(r.left, r.top),
				size = Size(r.width, r.height),
				cornerRadius = r.topLeftCornerRadius.x,
				style = style,
			)
		}
		is Outline.Generic ->
			drawPath(outline.path, color, style = style)
	}
}

fun DrawScope.drawOutline(
	outline: Outline,
	brush: Brush,
	alpha: Float = 1.0f,
	style: DrawStyle = Fill,
	@Suppress("UNUSED_PARAMETER") colorFilter: ColorFilter? = null,
	@Suppress("UNUSED_PARAMETER") blendMode: BlendMode = BlendMode.SrcOver,
) {
	when (outline) {
		is Outline.Rectangle ->
			drawRect(brush, Offset(outline.rect.left, outline.rect.top),
				Size(outline.rect.width, outline.rect.height), alpha = alpha, style = style)
		is Outline.Rounded -> {
			val r = outline.roundRect
			drawRoundRect(
				brush = brush,
				topLeft = Offset(r.left, r.top),
				size = Size(r.width, r.height),
				cornerRadius = r.topLeftCornerRadius.x,
				alpha = alpha,
				style = style,
			)
		}
		is Outline.Generic ->
			drawPath(outline.path, brush, alpha = alpha, style = style)
	}
}
