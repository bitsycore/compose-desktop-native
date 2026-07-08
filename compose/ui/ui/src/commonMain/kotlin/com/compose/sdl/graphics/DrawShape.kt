package com.compose.sdl.graphics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// ==================
// MARK: Shape drawing helpers
// ==================

/**
 * Internal helper invoked by the project's `BackgroundNode.draw()` body
 * (Phase 8 chain-driven draw). Resolves the [Shape] into a project
 * [Outline] and dispatches to the appropriate `DrawScope.drawXxx`. The
 * receiver [DrawScope] is the renderer's wrap scope (Skia or SDL3) —
 * any of its `drawRect / drawRoundRect / drawPath` calls becomes a
 * concrete paint operation on the backend canvas.
 *
 * Note `Shape.createOutline` is the upstream three-arg signature
 * (`size`, `layoutDirection`, `density`); project shapes implement it
 * and ignore the latter two parameters today.
 */
fun DrawScope.drawBackgroundShape(inColor: Color, inShape: Shape) {
	val vOutline = inShape.createOutline(size, LayoutDirection.Ltr, kDensity1)
	when (vOutline) {
		is Outline.Rectangle ->
			drawRect(inColor, Offset.Zero, size)
		is Outline.Rounded -> {
			val vR = vOutline.roundRect
			drawRoundRect(
				color = inColor,
				topLeft = Offset(vR.left, vR.top),
				size = Size(vR.width, vR.height),
				cornerRadius = vR.topLeftCornerRadius,
			)
		}
		is Outline.Generic ->
			drawPath(vOutline.path, inColor)
	}
}

/**
 * Mirror of [drawBackgroundShape] for borders — stroked instead of
 * filled. Inset by half the stroke width so the visual edge stays
 * inside the laid-out bounds.
 */
fun DrawScope.drawBorderShape(inWidth: Int, inColor: Color, inShape: Shape) {
	val vW = inWidth.toFloat()
	val vInset = vW / 2f
	val vOutline = inShape.createOutline(size, LayoutDirection.Ltr, kDensity1)
	val vStroke = Stroke(width = vW)
	when (vOutline) {
		is Outline.Rectangle ->
			drawRect(
				color = inColor,
				topLeft = Offset(vInset, vInset),
				size = Size(size.width - vW, size.height - vW),
				style = vStroke,
			)
		is Outline.Rounded -> {
			val vR = vOutline.roundRect
			drawRoundRect(
				color = inColor,
				topLeft = Offset(vR.left + vInset, vR.top + vInset),
				size = Size(vR.width - vW, vR.height - vW),
				cornerRadius = CornerRadius((vR.topLeftCornerRadius.x - vInset).coerceAtLeast(0f)),
				style = vStroke,
			)
		}
		is Outline.Generic ->
			drawPath(vOutline.path, inColor, style = vStroke)
	}
}

private val kDensity1: Density = Density(1f, 1f)
