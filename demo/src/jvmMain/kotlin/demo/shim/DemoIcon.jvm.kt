package demo.shim

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

// JVM actual: map the token to an upstream Material Icons vector (material-icons
// -extended) and draw it with material3's Icon — the direct counterpart to the
// native actual's MaterialSymbols codepoint + font glyph.
@Composable
actual fun DemoIcon(
    icon: DemoIcon,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    size: Dp,
) {
    val vector: ImageVector = when (icon) {
        DemoIcon.Home -> Icons.Filled.Home
        DemoIcon.Search -> Icons.Filled.Search
        DemoIcon.Person -> Icons.Filled.Person
        DemoIcon.Settings -> Icons.Filled.Settings
        DemoIcon.Add -> Icons.Filled.Add
        DemoIcon.Edit -> Icons.Filled.Edit
        DemoIcon.Favorite -> Icons.Filled.Favorite
        DemoIcon.Star -> Icons.Filled.Star
        DemoIcon.Close -> Icons.Filled.Close
        DemoIcon.Image -> Icons.Filled.Image
        DemoIcon.Delete -> Icons.Filled.Delete
        DemoIcon.Share -> Icons.Filled.Share
        DemoIcon.Check -> Icons.Filled.Check
        DemoIcon.MoreVert -> Icons.Filled.MoreVert
        DemoIcon.ContentCopy -> Icons.Filled.ContentCopy
        DemoIcon.KeyboardArrowDown -> Icons.Filled.KeyboardArrowDown
        DemoIcon.Bookmark -> Icons.Filled.Bookmark
        DemoIcon.Notifications -> Icons.Filled.Notifications
        DemoIcon.Menu -> Icons.Filled.Menu
        DemoIcon.ArrowBack -> Icons.Filled.ArrowBack
        DemoIcon.Save -> Icons.Filled.Save
        DemoIcon.Refresh -> Icons.Filled.Refresh
        DemoIcon.Download -> Icons.Filled.Download
        DemoIcon.Upload -> Icons.Filled.Upload
        DemoIcon.ExpandMore -> Icons.Filled.ExpandMore
        DemoIcon.ExpandLess -> Icons.Filled.ExpandLess
    }
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (tint.isSpecified) tint else LocalContentColor.current,
    )
}
