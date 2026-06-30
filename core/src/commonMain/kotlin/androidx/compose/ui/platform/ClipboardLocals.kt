package androidx.compose.ui.platform

// ==================
// MARK: Clipboard composition locals + global shims (non-official)
// ==================

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString

/**
 * `LocalClipboardManager.current` — in-composition accessor for the
 * vendored [ClipboardManager] (synchronous, AnnotatedString-typed).
 * Defaults to the SDL3-backed manager (the native backend installs it
 * automatically when composeWindow boots), so call sites never have to
 * wrap compositions in a Provider.
 *
 * Upstream's `LocalClipboardManager` lives in `CompositionLocals.kt` and
 * defaults to `noLocalProvidedFor("LocalClipboardManager")`. We default
 * to the platform manager here — same shape, friendlier for native
 * single-window apps.
 */
val LocalClipboardManager = staticCompositionLocalOf<ClipboardManager> { defaultClipboardManager }

/**
 * `LocalClipboard.current` — suspend-API variant. Returns the SDL3-backed
 * [Clipboard] by default.
 */
val LocalClipboard = staticCompositionLocalOf<Clipboard> { defaultClipboard }

// ==================
// MARK: Non-composable access (project glue)
// ==================

/**
 * Project glue: the SDL3-backed [ClipboardManager] picked up from the
 * native source set. Non-composable call sites (event handlers,
 * coroutines, plain functions) use this; inside composition prefer
 * `LocalClipboardManager.current`.
 *
 * Declared `internal` to nudge call sites toward the official locals.
 */
internal expect val defaultClipboardManager: ClipboardManager

/** Project glue: the SDL3-backed suspend [Clipboard]. */
internal expect val defaultClipboard: Clipboard

/**
 * Back-compat alias for the project's old global `currentClipboard:
 * Clipboard` — kept so the renderer + foundation call sites keep
 * compiling. Now points at the SDL3-backed [ClipboardManager] (which is
 * the synchronous API the call sites actually need).
 */
val currentClipboard: ClipboardManager get() = defaultClipboardManager
