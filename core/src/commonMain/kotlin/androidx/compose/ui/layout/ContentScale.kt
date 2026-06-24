package androidx.compose.ui.layout

import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

// ==================
// MARK: ScaleFactor
// ==================

/* Scale to apply along each axis. Official Compose models this as a value
   class over a packed Long; this is a reduced float-pair stand-in. */
data class ScaleFactor(val scaleX: Float, val scaleY: Float)

// ==================
// MARK: ContentScale
// ==================

/* How an Image's intrinsic content is scaled into its (post-layout) bounds.
   The renderer reads the value off the LayoutNode; consumers pick one of the
   companion constants. Matches official Compose's ContentScale interface. */
interface ContentScale {
	fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor

	companion object {
		// Uniformly scale to fill the bounds (overflow clipped).
		val Crop: ContentScale = object : ContentScale {
			override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
				val s = computeFillMaxDimension(srcSize, dstSize)
				return ScaleFactor(s, s)
			}
		}

		// Uniformly scale so the whole image fits inside the bounds.
		val Fit: ContentScale = object : ContentScale {
			override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
				val s = computeFillMinDimension(srcSize, dstSize)
				return ScaleFactor(s, s)
			}
		}

		val FillHeight: ContentScale = object : ContentScale {
			override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
				val s = computeFillHeight(srcSize, dstSize)
				return ScaleFactor(s, s)
			}
		}

		val FillWidth: ContentScale = object : ContentScale {
			override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
				val s = computeFillWidth(srcSize, dstSize)
				return ScaleFactor(s, s)
			}
		}

		// Like Fit, but never upscale past the intrinsic size.
		val Inside: ContentScale = object : ContentScale {
			override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor =
				if (srcSize.width <= dstSize.width && srcSize.height <= dstSize.height) {
					ScaleFactor(1f, 1f)
				} else {
					val s = computeFillMinDimension(srcSize, dstSize)
					ScaleFactor(s, s)
				}
		}

		// Non-uniform stretch to exactly fill the bounds.
		val FillBounds: ContentScale = object : ContentScale {
			override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor =
				ScaleFactor(computeFillWidth(srcSize, dstSize), computeFillHeight(srcSize, dstSize))
		}

		// Draw at intrinsic size (no scaling), centred in the bounds.
		val None: ContentScale = FixedScale(1f)
	}
}

// ==================
// MARK: FixedScale
// ==================

data class FixedScale(val value: Float) : ContentScale {
	override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor =
		ScaleFactor(value, value)
}

private fun computeFillMaxDimension(srcSize: Size, dstSize: Size): Float =
	max(computeFillWidth(srcSize, dstSize), computeFillHeight(srcSize, dstSize))

private fun computeFillMinDimension(srcSize: Size, dstSize: Size): Float =
	min(computeFillWidth(srcSize, dstSize), computeFillHeight(srcSize, dstSize))

private fun computeFillWidth(srcSize: Size, dstSize: Size): Float =
	dstSize.width / srcSize.width

private fun computeFillHeight(srcSize: Size, dstSize: Size): Float =
	dstSize.height / srcSize.height
