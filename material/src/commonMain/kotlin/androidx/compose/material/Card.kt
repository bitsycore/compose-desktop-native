package androidx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ==================
// MARK: Card
// ==================

/* Material's surface-with-rounded-corners container. Defaults to a 12.dp
   corner radius and the theme's surface colour. Pass a BorderStroke for
   an outlined variant. Real Compose Material also has elevation here;
   we don't have a shadow primitive yet, so it's flat. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.Shape,
    backgroundColor: Color = MaterialTheme.colors.surface,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        border = border,
        content = content,
    )
}

object CardDefaults {
    val Shape: Shape = RoundedCornerShape(12.dp)
}
