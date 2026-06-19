package compose.material

import androidx.compose.runtime.Composable
import compose.foundation.layout.Box
import compose.ui.*

// ==================
// MARK: Surface
// ==================

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.surface,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.background(color),
        content = content
    )
}
