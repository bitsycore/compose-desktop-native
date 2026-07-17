package com.compose.sdl.graphics

import androidx.compose.ui.graphics.Matrix
import kotlin.math.abs

// Layer transform matrix builder for GraphicsLayerOwnerLayer (hit-testing /
// coordinate mapping). Body vendored from compose-multiplatform-core
// Matrices.skiko.kt `prepareTransformationMatrix` — pure Compose `Matrix` math,
// no Skia. It lives HERE in nativeMain (shared by both renderer legs) under a
// distinct name so it is visible to GraphicsLayerOwnerLayer (also nativeMain);
// the upstream `prepareTransformationMatrix` stays skikoRenderer-only in the
// vendored Matrices.skiko.kt and would not be visible up here. Distinct name
// avoids clashing with that copy on the Skia leg. (RENDERER_CONVERGE.md P1.5 will
// reverse this rename once B2 vendors Matrices.skiko onto the Skia leg.)
// VENDOR-BASE: compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/graphics/Matrices.skiko.kt @ v1.12.0-beta03+dev4483

// Builds the layer transform (translation / rotation / scale / camera about a
// pivot) into [matrix]. Matches the skiko copy so hit-testing agrees across legs.
internal fun prepareLayerTransformationMatrix(
	matrix: Matrix,
	pivotX: Float,
	pivotY: Float,
	translationX: Float,
	translationY: Float,
	rotationX: Float,
	rotationY: Float,
	rotationZ: Float,
	scaleX: Float,
	scaleY: Float,
	cameraDistance: Float,
) {
	matrix.reset()
	matrix.translate(x = -pivotX, y = -pivotY)
	matrix *= Matrix().apply {
		rotateZ(rotationZ)
		rotateY(rotationY)
		rotateX(rotationX)
		scale(scaleX, scaleY)
	}
	// Perspective transform should be applied only in case of rotations to avoid
	// multiply application in hierarchies.
	// See Android's frameworks/base/libs/hwui/RenderProperties.cpp for reference
	if (!rotationX.isZero() || !rotationY.isZero()) {
		matrix *= Matrix().apply {
			// The camera location is passed in inches, set in pt
			val depth = cameraDistance * 72f
			this[2, 3] = -1f / depth
		}
	}
	matrix *= Matrix().apply {
		translate(x = pivotX + translationX, y = pivotY + translationY)
	}

	// Third column and row are irrelevant for 2D space.
	// Zeroing required to get correct inverse transformation matrix.
	matrix[2, 0] = 0f
	matrix[2, 1] = 0f
	matrix[2, 3] = 0f
	matrix[0, 2] = 0f
	matrix[1, 2] = 0f
	matrix[3, 2] = 0f
}

private const val NON_ZERO_EPSILON = 0.001f

@Suppress("NOTHING_TO_INLINE")
private inline fun Float.isZero(): Boolean = abs(this) <= NON_ZERO_EPSILON
