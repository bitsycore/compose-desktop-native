package com.compose.sdl.graphics

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

// ==================
// MARK: ProjectPaint
// ==================

/**
 * Concrete native impl of the upstream [Paint] interface. Stores all
 * configurable fields in plain vars matching the upstream defaults; the
 * renderer does not consume Paint today (Brush / DrawScope path), so this
 * is a passive state holder.
 */
class ProjectPaint : Paint {
	override var alpha: Float = 1f
	override var isAntiAlias: Boolean = true
	override var color: Color = Color.Black
	override var blendMode: BlendMode = BlendMode.SrcOver
	override var style: PaintingStyle = PaintingStyle.Fill
	override var strokeWidth: Float = 0f
	override var strokeCap: StrokeCap = StrokeCap.Butt
	override var strokeJoin: StrokeJoin = StrokeJoin.Miter
	override var strokeMiterLimit: Float = 4f
	override var filterQuality: FilterQuality = FilterQuality.Low
	override var shader: Shader? = null
	override var colorFilter: ColorFilter? = null
	override var pathEffect: PathEffect? = null
}
