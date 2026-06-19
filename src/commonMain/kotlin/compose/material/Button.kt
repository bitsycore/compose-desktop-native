package compose.material

import androidx.compose.runtime.Composable
import compose.foundation.layout.Box
import compose.foundation.layout.Row
import compose.ui.*

// ==================
// MARK: Button
// ==================

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colors.primary,
    contentColor: Color = MaterialTheme.colors.onPrimary,
    content: @Composable () -> Unit
) {
    val bgColor = if (enabled) color else Color.Gray
    Box(
        modifier = modifier
            .background(bgColor)
            .padding(horizontal = 16, vertical = 8)
            .clickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}
