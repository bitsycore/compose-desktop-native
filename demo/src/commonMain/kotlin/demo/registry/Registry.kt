package demo.registry

import androidx.compose.runtime.Composable
import screens.*

// ==================
// MARK: Screen / category registry
// ==================

/* One showcase screen: a display name (also the `--screen=<name>` CLI key) and
   its content composable. */
data class DemoScreen(val name: String, val content: @Composable () -> Unit)

/* A sidebar group. [id] is stable across platforms so a platform-specific screen
   can be folded into a shared category (e.g. a native-only Core screen adds to
   the common "core" group), while platform-only groups (Native on native) use a
   fresh id. */
data class DemoCategory(val id: String, val label: String, val screens: List<DemoScreen>)

/* Shared, platform-neutral registries — these compile against upstream Compose
   on a future jvm target unchanged. Screens migrate into these lists as they're
   moved to commonMain. */
val CoreScreens: List<DemoScreen> = listOf(
    // Text primitives first (BasicText → BasicTextField), then layout, graphics,
    // lists, gestures/interaction, state/runtime, animation.
    DemoScreen("BasicText") { BasicTextScreen() },
    DemoScreen("BasicTextField") { BasicTextFieldScreen() },
    DemoScreen("Layout") { LayoutScreen() },
    DemoScreen("CustomLayout") { CustomLayoutScreen() },
    DemoScreen("FlowLayout") { FlowLayoutScreen() },
    DemoScreen("Modifiers") { ModifiersScreen() },
    DemoScreen("ModShortcuts") { ModifierShortcutsScreen() },
    DemoScreen("Shapes") { ShapesScreen() },
    DemoScreen("Path") { PathScreen() },
    DemoScreen("Canvas") { CanvasScreen() },
    DemoScreen("Brushes") { BrushScreen() },
    DemoScreen("GraphicsLayer") { GraphicsLayerScreen() },
    DemoScreen("Shadows") { ShadowScreen() },
    DemoScreen("Colors") { ColorsScreen() },
    DemoScreen("Images") { ImagesScreen() },
    DemoScreen("Scroll") { ScrollScreen() },
    DemoScreen("LazyColumn") { LazyColumnScreen() },
    DemoScreen("LazyGrid") { LazyGridScreen() },
    DemoScreen("LazyExtra") { LazyExtraScreen() },
    DemoScreen("GridsExtra") { GridsExtraScreen() },
    DemoScreen("Pager") { PagerScreen() },
    DemoScreen("Gestures") { GestureScreen() },
    DemoScreen("Interaction") { InteractionScreen() },
    DemoScreen("InteractionSource") { InteractionSourceScreen() },
    DemoScreen("FocusRequester") { FocusRequesterScreen() },
    DemoScreen("AnnotatedString") { AnnotatedStringScreen() },
    DemoScreen("Remember") { StateScreen() },
    DemoScreen("Counter") { CounterScreen() },
    DemoScreen("Recomposition") { RecompositionScreen() },
    DemoScreen("Animation") { AnimationScreen() },
)
val Material3Screens: List<DemoScreen> = listOf(
    // Ordered by family: typography, buttons, inputs, containers, navigation, overlays.
    DemoScreen("Text") { TextScreen() },
    DemoScreen("Buttons") { ButtonsScreen() },
    DemoScreen("ButtonsExtra") { M3ButtonsExtraScreen() },
    DemoScreen("Fab") { FabScreen() },
    DemoScreen("FabExtra") { M3FabExtraScreen() },
    DemoScreen("TextField") { TextFieldScreen() },
    DemoScreen("Widgets") { WidgetsScreen() },
    DemoScreen("Cards") { CardsScreen() },
    DemoScreen("Chips") { ChipsScreen() },
    DemoScreen("Lists") { ListItemsScreen() },
    DemoScreen("Sheets") { M3SheetsScreen() },
    DemoScreen("Drawers") { M3DrawersScreen() },
    DemoScreen("AppBars") { M3AppBarsScreen() },
    DemoScreen("NavRails") { M3RailsScreen() },
    DemoScreen("Navigation") { NavigationScreen() },
    DemoScreen("Tabs") { M3TabsScreen() },
    DemoScreen("Search") { M3SearchScreen() },
    DemoScreen("Pickers") { M3PickersScreen() },
    DemoScreen("Carousel") { M3CarouselScreen() },
    DemoScreen("M3Misc") { M3MiscScreen() },
)

val commonCategories: List<DemoCategory> = listOf(
    DemoCategory("core", "Core", CoreScreens),
    DemoCategory("material3", "Material 3", Material3Screens),
)

/* Platform extras that fill the dropdown dynamically: screens that can't be
   common (project-only APIs) folded into their category by id, plus any
   platform-only category (Native on native; a jvm target would add its own or
   return an empty list). */
expect fun getPlatformCategories(): List<DemoCategory>

/* commonCategories merged with getPlatformCategories(), by id: matching ids
   concatenate their screens (common first); brand-new platform ids append at the
   end. This is what the sidebar dropdown iterates. */
fun allCategories(): List<DemoCategory> {
    val platform = getPlatformCategories()
    val out = ArrayList<DemoCategory>(commonCategories.size + platform.size)
    val seen = HashSet<String>()
    for (category in commonCategories) {
        val extra = platform.filter { it.id == category.id }.flatMap { it.screens }
        out += DemoCategory(category.id, category.label, category.screens + extra)
        seen += category.id
    }
    for (platformCategory in platform) if (seen.add(platformCategory.id)) out += platformCategory
    return out
}
