package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager

// ==================
// MARK: LocalInputModeManager
// ==================

/*
 CompositionLocal for the active InputModeManager. Upstream declares this at
 the bottom of `CompositionLocals.kt` (which we don't vendor because it's
 platform-View heavy); ours is a standalone file so vendored `Indication` /
 `Focusability` can `currentValueOf(LocalInputModeManager)` without pulling
 the whole CompositionLocals object.

 Default: always Keyboard mode (desktop). ComposeWindow can install a real
 manager if it needs to switch on user input.
*/
private val kDefaultInputModeManager = object : InputModeManager {
	override val inputMode: InputMode = InputMode.Keyboard
	override fun requestInputMode(mode: InputMode): Boolean = true
}

val LocalInputModeManager = staticCompositionLocalOf<InputModeManager> { kDefaultInputModeManager }
