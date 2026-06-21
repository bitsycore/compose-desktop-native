package androidx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Sp
import androidx.compose.ui.unit.sp

// ==================
// MARK: Text (Material)
// ==================

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onBackground,
    fontSize: Sp = 16.sp,
    textAlign: TextAlign = TextAlign.Start,
    softWrap: Boolean = true,
    fontFamily: String? = null,
    fontVariationSettings: List<FontVariation>? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        textAlign = textAlign,
        softWrap = softWrap,
        fontFamily = fontFamily,
        fontVariationSettings = fontVariationSettings,
    )
}
