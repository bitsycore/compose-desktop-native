package androidx.compose.foundation.text

// ==================
// MARK: platformDefaultKeyMapping — native actual
// ==================

/*
 Actual for vendored KeyMapping.kt's expect val. Upstream ships this per
 platform (macos: createMacOsDefaultKeyMapping, ios: create.../etc). Desktop
 doesn't know Mac vs Linux vs Windows at compile time, so we route through
 the vendored DefaultSkikoKeyMapping which uses Ctrl-based bindings — same
 as upstream's Windows/Linux default. TODO: platform detection via
 SDL_GetPlatform() at ComposeWindow startup, swap in createMacOsDefaultKeyMapping
 when SDL reports macOS.
*/
internal actual val platformDefaultKeyMapping: KeyMapping = DefaultSkikoKeyMapping
