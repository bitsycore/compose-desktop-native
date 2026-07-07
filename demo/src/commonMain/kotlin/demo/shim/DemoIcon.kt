package demo.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================
// MARK: DemoIcon — platform-neutral icon token
// ==================

/* A platform-neutral icon reference for the shared demo screens. On native it
   maps to a Material Symbols codepoint rendered by the project icon-font engine
   (com.compose.desktop.native.icons); on a future jvm target it maps to an
   androidx.compose.material.icons ImageVector. Shared screens reference icons
   ONLY through this enum, so the exact same code compiles on both stacks. */
enum class DemoIcon {
    Home, Search, Person, Settings, Add, Edit, Favorite, Star, Close, Image,
    Delete, Share, Check, MoreVert, ContentCopy, KeyboardArrowDown, Bookmark,
    Notifications, Menu, ArrowBack, Save, Refresh, Download, Upload,
    ExpandMore, ExpandLess,
}

/* Renders [icon]. The portable stand-in for material3's Icon(imageVector, …) /
   the project's MaterialSymbolsOutlined(codepoint, …). Deliberately exposes no
   variable-font axes (fill / weight / grade / opticalSize) — those have no
   upstream analog. */
@Composable
expect fun DemoIcon(
    icon: DemoIcon,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
)
