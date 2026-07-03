@file:Suppress("UNUSED")

package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// ==================
// MARK: CompositionLocals — minimal project shim (non-official)
// ==================

/*
 * Stub of selected entries from upstream's
 * `androidx.compose.ui.platform.CompositionLocals.kt`. The full file
 * pulls Owner / InputModeManager / ViewConfiguration / Autofill /
 * FontFamilyResolver / SoftwareKeyboardController / TextToolbar and
 * other heavy types we don't have yet. Vendored downstream code
 * (Indication, Background, BasicMarquee, SuspendingPointerInputFilter)
 * only reads these locals occasionally, so we expose just the ones
 * with sensible cross-platform defaults.
 *
 * Replace with the full vendored file when its engine deps land.
 */

/**
 * `LocalDensity.current` — the project doesn't bind density per
 * composition (the renderer applies HiDPI scaling separately), so a
 * fixed `Density(1f, 1f)` default is the right reading for layout-time
 * `Dp.toPx()` etc.
 */
val LocalDensity = staticCompositionLocalOf<Density> { Density(1f, 1f) }

/** Layout direction — Ltr until RTL lands in the renderer. */
val LocalLayoutDirection = staticCompositionLocalOf<LayoutDirection> { LayoutDirection.Ltr }

/** Haptic feedback local — no-op default (SDL desktop has no haptics); Clickable long-press reads it. */
val LocalHapticFeedback = staticCompositionLocalOf<androidx.compose.ui.hapticfeedback.HapticFeedback> {
	object : androidx.compose.ui.hapticfeedback.HapticFeedback {
		override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {}
	}
}

/** Platform click/interaction sound (SoundEffect is vendored) — no-op default on SDL desktop. */
val LocalSoundEffect = staticCompositionLocalOf<SoundEffect> {
	object : SoundEffect { override fun playClickSound() {} }
}

/** Whether a scroll-capture (accessibility screenshot scrolling) is in progress. Upstream declares
 *  it in the unvendored CompositionLocals.kt; no scroll-capture on SDL, so always false. */
val LocalScrollCaptureInProgress = staticCompositionLocalOf<Boolean> { false }
