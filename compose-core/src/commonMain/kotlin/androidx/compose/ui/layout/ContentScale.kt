package androidx.compose.ui.layout

// ==================
// MARK: ContentScale
// ==================

/* How an Image's intrinsic content is scaled into its (post-layout) bounds.
   The renderer reads this off the LayoutNode and computes the source/dest
   rectangles accordingly. Matches the common subset of Compose's ContentScale. */
enum class ContentScale {
	Fit,         // scale uniformly so the whole image fits inside the bounds (letterboxed)
	Crop,        // scale uniformly so the image fills the bounds (overflow clipped to bounds)
	FillBounds,  // stretch non-uniformly to exactly fill the bounds
	Inside,      // like Fit, but never upscale past the intrinsic size
	None,        // draw at intrinsic size, centred in the bounds
}
