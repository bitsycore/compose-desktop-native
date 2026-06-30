package androidx.compose.ui.layout

// ==================
// MARK: WindowInsetsRulers — native no-op actuals
// ==================

/**
 * Desktop builds have no platform window-insets (no status bars / display
 * cutouts / IME insets). Both expect functions return empty / no-anim
 * defaults so the rest of WindowInsetsRulers.kt compiles. If desktop
 * window-insets ever land, this is where the real wiring goes.
 */

internal actual fun findDisplayCutouts(
	placementScope: Placeable.PlacementScope,
): List<RectRulers> = emptyList()

internal actual fun findInsetsAnimationProperties(
	placementScope: Placeable.PlacementScope,
	windowInsetsRulers: WindowInsetsRulers,
): WindowInsetsAnimation = NoWindowInsetsAnimation
