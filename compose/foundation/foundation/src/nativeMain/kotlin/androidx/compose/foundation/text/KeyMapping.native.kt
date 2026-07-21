package androidx.compose.foundation.text

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform

// ==================
// MARK: platformDefaultKeyMapping — native actual
// ==================

/**
 Actual for vendored KeyMapping.kt's expect val. Upstream ships this per
 platform (macos: createMacOsDefaultKeyMapping, ios: create.../etc). We use
 Kotlin/Native's `Platform.osFamily` to pick at startup:

 - MACOSX → `createMacOsDefaultKeyMapping()` — Cmd-based bindings (Cmd+C /
   Cmd+V / Cmd+Z / Cmd+A), Cmd+Home/End for document, Alt-based word/paragraph
   motion, Cmd+Backspace = delete to line start, Alt+Backspace = delete word.
 - anything else → `DefaultSkikoKeyMapping` — Ctrl-based bindings (Ctrl+C /
   Ctrl+V / etc.), matching Windows/Linux desktop conventions.

 Platform.osFamily is compile-time-known per target (Kotlin/Native emits a
 per-target executable), so this is effectively a per-binary choice — no
 runtime SDL_GetPlatform() branch needed.
*/
@OptIn(ExperimentalNativeApi::class)
internal actual val platformDefaultKeyMapping: KeyMapping =
	if (Platform.osFamily == OsFamily.MACOSX) createMacOsDefaultKeyMapping()
	else DefaultSkikoKeyMapping
