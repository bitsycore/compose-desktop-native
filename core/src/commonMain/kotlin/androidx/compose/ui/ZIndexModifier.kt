package androidx.compose.ui

import androidx.compose.runtime.Stable

// ==================
// MARK: zIndex
// ==================

/* Override draw ordering within a parent. Higher z draws on top of lower z;
   siblings without zIndex have implicit z = 0. The renderer sorts children by
   zIndex right before painting them. */
data class ZIndexModifier(val zIndex: Float) : Modifier.Element

@Stable
fun Modifier.zIndex(zIndex: Float): Modifier =
	if (zIndex == 0f) this else this.then(ZIndexModifier(zIndex))
