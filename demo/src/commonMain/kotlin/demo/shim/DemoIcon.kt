package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined

// ==================
// MARK: DemoIcon — platform-neutral icon token
// ==================

/** A platform-neutral icon reference for the shared demo screens. Now that
   :material-symbols exposes its API from commonMain (native draws through the
   port's IconFont pipeline, JVM through upstream variable-font text), the
   token resolves to a Material Symbols codepoint on BOTH stacks — the same
   glyphs render everywhere, which is exactly what the parity build compares. */
enum class DemoIcon {
    Home, Search, Person, Settings, Add, Edit, Favorite, Star, Close, Image,
    Delete, Share, Check, MoreVert, ContentCopy, KeyboardArrowDown, Bookmark,
    Notifications, Menu, ArrowBack, Save, Refresh, Download, Upload,
    ExpandMore, ExpandLess,
}

/** Renders [icon] via MaterialSymbolsOutlined — the portable stand-in for
   material3's Icon(imageVector, …). Color.Unspecified tint falls back to
   material3's LocalContentColor inside MaterialSymbolsOutlined. */
@Composable
fun DemoIcon(
    icon: DemoIcon,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
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
    MaterialSymbolsOutlined(
        icon = codepoint,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
    )
}
