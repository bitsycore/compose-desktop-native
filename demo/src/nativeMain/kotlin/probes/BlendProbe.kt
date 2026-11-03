import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.compose.sdl.nativeComposeWindow
import utils.encodeBmpBgra32

/* Advanced-blend regression probe (issue #7).

   No demo screen uses an advanced BlendMode, so the normal --screen sweep can
   never make Skia emit `layout(blend_support_all_equations)` - the whole suite
   ran clean on the AMD driver that was spamming the reporter. This draws every
   KHR_blend_equation_advanced mode so that path is definitely taken.

   Run `--blendtest --gpu=skia.opengl` and check stdout is EMPTY. Any "Shader
   compilation error" means the GL context has regressed to a version whose
   driver mis-compiles advanced blend (see kGlMajorPreferred in Sdl3Backend).

   `--blendshot=<path.bmp>` dumps the render; it should be pixel-identical to
   the same run under `--gpu=software`, which is how the 4.1 fix was verified
   to blend correctly rather than merely fail quietly. */
internal fun runBlendTest(inShot: String? = null) {
	val vModes = listOf(
		BlendMode.Multiply, BlendMode.Screen, BlendMode.Overlay, BlendMode.Darken,
		BlendMode.Lighten, BlendMode.ColorDodge, BlendMode.ColorBurn,
		BlendMode.Hardlight, BlendMode.Softlight, BlendMode.Difference,
		BlendMode.Exclusion, BlendMode.Hue, BlendMode.Saturation,
		BlendMode.Color, BlendMode.Luminosity,
	)
	var vFrames = 0
	nativeComposeWindow(
		title = "blendtest",
		width = 600,
		height = 400,
		onFrame = { vBridge, _ ->
			vFrames++
			// Give Skia a few frames to build + fail every program, then quit.
			if (vFrames >= 5 && inShot != null) {
				val vSnap = vBridge.snapshotBgra()
				if (vSnap != null) {
					val (vW, vH, vBgra) = vSnap
					utils.writeFile(inShot, encodeBmpBgra32(vW, vH, vBgra))
					println("[blendtest] wrote $inShot (${vW}x$vH)")
				}
				false
			} else vFrames < 10
		},
	) {
		Box(
			Modifier
				.size(600.dp, 400.dp)
				.background(Color(0xFF3366AA))
				.drawBehind {
					vModes.forEachIndexed { vIndex, vMode ->
						drawIntoCanvas { vCanvas ->
							val vPaint = Paint().apply {
								color = Color(0xFFCC4422)
								blendMode = vMode
							}
							val vX = 10f + (vIndex % 5) * 70f
							val vY = 10f + (vIndex / 5) * 70f
							vCanvas.drawRect(
								left = vX, top = vY, right = vX + 60f, bottom = vY + 60f,
								paint = vPaint,
							)
							// A rounded rect too - the reporter's failing shader is the
							// analytic RRect coverage one (uinnerRect_S1 / uradiusPlusHalf_S1).
							vCanvas.drawRoundRect(
								left = vX, top = vY + 200f, right = vX + 60f, bottom = vY + 260f,
								radiusX = 12f, radiusY = 12f,
								paint = vPaint,
							)
						}
					}
				}
		)
	}
	println("[blendtest] rendered ${vModes.size} advanced blend modes over $vFrames frames")
}
