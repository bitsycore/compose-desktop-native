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
        // Icons demos the variable-font axes + Outlined/Rounded/Sharp families — project-only,
        // no upstream analog, so it can't be common.
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
        DemoScreen("Navigation3") { Navigation3Screen() },
    )),
)
