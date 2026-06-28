package androidx.compose.ui.input.pointer

// PointerInputChange is now vendored verbatim from upstream — see
// core/src/vendor/.../PointerEvent.kt (it's defined inside that file,
// not its own).

// ==================
// MARK: Scopes
// ==================

/* Scope passed to the suspending block of Modifier.pointerInput. Inside
   you typically open an `awaitPointerEventScope { ... }` and loop on
   `awaitPointerEvent()`. */
interface PointerInputScope {

	suspend fun <R> awaitPointerEventScope(
		block: suspend AwaitPointerEventScope.() -> R,
	): R
}

/* The scope inside awaitPointerEventScope where you can suspend on
   pointer events. Returns the vendored upstream `PointerEvent`
   (multi-touch — list of PointerInputChange). */
interface AwaitPointerEventScope {

	suspend fun awaitPointerEvent(pass: PointerEventPass = PointerEventPass.Main): PointerEvent
}

// PointerInputElement / PointerInputScopeImpl (the render-bridge
// implementations) live in com.compose.desktop.native.input per FIDELITY
// relocate rule — no official upstream equivalent.
