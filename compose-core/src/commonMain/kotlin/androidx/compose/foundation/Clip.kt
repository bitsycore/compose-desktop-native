package androidx.compose.foundation

import androidx.compose.ui.ClipModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

// ==================
// MARK: Modifier.clip()
// ==================

/* Clips all descendants of this node to the given shape. Use it on
   containers whose contents would otherwise overflow rounded corners
   (e.g. an image inside a RoundedCornerShape Button). */
fun Modifier.clip(shape: Shape) = then(ClipModifier(shape))
