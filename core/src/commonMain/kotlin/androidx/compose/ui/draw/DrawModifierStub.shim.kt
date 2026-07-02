package androidx.compose.ui.draw

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// Phase 9 stubs — legacy draw / draw-cache modifier markers (NodeKind kind-set +
// BackwardsCompatNode bridging). BackwardsCompatNode implements BuildDrawCacheParams.
interface DrawModifier : Modifier.Element

interface BuildDrawCacheParams {
	val size: Size
	val layoutDirection: LayoutDirection
	val density: Density
}
interface DrawCacheModifier : DrawModifier {
	fun onBuildCache(params: BuildDrawCacheParams)
}
