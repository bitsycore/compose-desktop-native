package androidx.compose.foundation.text.input.internal

// ==================
// MARK: createTextFieldKeyEventHandler — native actual
// ==================

/*
 Mirrors upstream macosMain / desktopMain / iosMain / webMain — all
 delegate to `createSkikoTextFieldKeyEventHandler` from the already-vendored
 `TextFieldKeyEventHandler.skiko.kt`.
*/
internal actual fun createTextFieldKeyEventHandler(): TextFieldKeyEventHandler =
	createSkikoTextFieldKeyEventHandler()
