package compose.material

import androidx.compose.runtime.Composable
import compose.foundation.BasicText
import compose.ui.Color
import compose.ui.Modifier

// ==================
// MARK: Text (Material)
// ==================

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onBackground,
    fontSize: Int = 16
) {
    BasicText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize
    )
}
