package demo.registry

import screens.*

// ==================
// MARK: Native platform categories
// ==================

// The native build's dropdown extras. During the commonMain migration this holds
// EVERY screen (referencing the composables still in nativeMain package `screens`);
// as screens move to commonMain they leave these lists and join CoreScreens /
// Material3Screens. What remains here at the end: screens that can't be common
// (project-only APIs) folded into "core"/"material3", plus the "native" group.
actual fun getPlatformCategories(): List<DemoCategory> = listOf(
    DemoCategory("core", "Core", listOf(
        // The one Core screen that can't be common: it demos the project's
        // IconFontIcon / IconText glyph engine + desktop scrollbars — no upstream analog.
        DemoScreen("FoundationExtra") { FoundationExtraScreen() },
    )),
    DemoCategory("material3", "Material 3", listOf(
        // Material 3 EXPRESSIVE screens: the project exposes these APIs publicly, but
        // released org.jetbrains.compose keeps ExperimentalMaterial3ExpressiveApi (and
        // ButtonGroup / SplitButton / flexible app bars / WideNavigationRail / carousels
        // / expressive FABs / sheets / drawers) internal — so they can't compile against
        // upstream (:demojvm) and stay native-only.
        DemoScreen("ButtonsExtra") { M3ButtonsExtraScreen() },
        DemoScreen("FabExtra") { M3FabExtraScreen() },
        DemoScreen("Sheets") { M3SheetsScreen() },
        DemoScreen("Drawers") { M3DrawersScreen() },
        DemoScreen("AppBars") { M3AppBarsScreen() },
        DemoScreen("NavRails") { M3RailsScreen() },
        DemoScreen("Search") { M3SearchScreen() },
        DemoScreen("Carousel") { M3CarouselScreen() },
        DemoScreen("M3Misc") { M3MiscScreen() },
        // Icons demos the variable-font axes + Outlined/Rounded/Sharp families — project-only.
        DemoScreen("Icons") { IconsScreen() },
    )),
    DemoCategory("native", "Native", listOf(
        DemoScreen("Window") { WindowScreen() },
        DemoScreen("Clipboard") { ClipboardScreen() },
        DemoScreen("Dispatchers") { DispatchersScreen() },
        DemoScreen("FileDialogs") { FileDialogsScreen() },
        DemoScreen("Desktop integration") { DesktopIntegrationScreen() },
        DemoScreen("PointerInput") { PointerInputScreen() },
        DemoScreen("Desktop widgets") { DesktopWidgetsScreen() },
    )),
)
