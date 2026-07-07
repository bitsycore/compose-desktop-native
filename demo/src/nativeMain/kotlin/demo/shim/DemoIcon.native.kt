package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import com.compose.desktop.native.icons.MaterialSymbols
import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined

// Native actual: resolve the token to a Material Symbols codepoint and draw it
// through the Outlined variable font (SDL3_ttf / FreeType or Skia).
@Composable
actual fun DemoIcon(
    icon: DemoIcon,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    size: Dp,
) {
    val codepoint = when (icon) {
        DemoIcon.Home -> MaterialSymbols.Home
        DemoIcon.Search -> MaterialSymbols.Search
        DemoIcon.Person -> MaterialSymbols.Person
        DemoIcon.Settings -> MaterialSymbols.Settings
        DemoIcon.Add -> MaterialSymbols.Add
        DemoIcon.Edit -> MaterialSymbols.Edit
        DemoIcon.Favorite -> MaterialSymbols.Favorite
        DemoIcon.Star -> MaterialSymbols.Star
        DemoIcon.Close -> MaterialSymbols.Close
        DemoIcon.Image -> MaterialSymbols.Image
        DemoIcon.Delete -> MaterialSymbols.Delete
        DemoIcon.Share -> MaterialSymbols.Share
        DemoIcon.Check -> MaterialSymbols.Check
        DemoIcon.MoreVert -> MaterialSymbols.MoreVert
        DemoIcon.ContentCopy -> MaterialSymbols.ContentCopy
        DemoIcon.KeyboardArrowDown -> MaterialSymbols.KeyboardArrowDown
        DemoIcon.Bookmark -> MaterialSymbols.Bookmark
        DemoIcon.Notifications -> MaterialSymbols.Notifications
        DemoIcon.Menu -> MaterialSymbols.Menu
        DemoIcon.ArrowBack -> MaterialSymbols.ArrowBack
        DemoIcon.Save -> MaterialSymbols.Save
        DemoIcon.Refresh -> MaterialSymbols.Refresh
        DemoIcon.Download -> MaterialSymbols.Download
        DemoIcon.Upload -> MaterialSymbols.Upload
        DemoIcon.ExpandMore -> MaterialSymbols.ExpandMore
        DemoIcon.ExpandLess -> MaterialSymbols.ExpandLess
    }
    // Color.Unspecified → let MaterialSymbolsOutlined fall back to LocalContentColor.
    if (tint.isSpecified) {
        MaterialSymbolsOutlined(codepoint, contentDescription, modifier, tint = tint, size = size)
    } else {
        MaterialSymbolsOutlined(codepoint, contentDescription, modifier, size = size)
    }
}
