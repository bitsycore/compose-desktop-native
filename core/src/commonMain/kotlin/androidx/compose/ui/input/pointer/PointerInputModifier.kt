package androidx.compose.ui.input.pointer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

// ==================
// MARK: Modifier.pointerInput
// ==================

/* Attach a suspending pointer-input block to this modifier chain. The
   block runs in a coroutine launched at composition time and restarted
   whenever any of `keys` changes (same semantics as upstream).

   Inside the block you usually open `awaitPointerEventScope { while
   (true) awaitPointerEvent() }`, or use the higher-level helpers
   detectTapGestures / detectDragGestures.

   Implemented as a @Composable extension (rather than a plain Modifier
   extension) so we can `remember` the scope and drive it with a
   LaunchedEffect tied to the user's keys — same shape as upstream's
   composed { } implementation. */
@Composable
fun Modifier.pointerInput(
	key1: Any?,
	block: suspend PointerInputScope.() -> Unit,
): Modifier {
	val vScope = remember { PointerInputScopeImpl() }
	LaunchedEffect(key1) { vScope.block() }
	return this.then(PointerInputElement(vScope))
}

@Composable
fun Modifier.pointerInput(
	key1: Any?,
	key2: Any?,
	block: suspend PointerInputScope.() -> Unit,
): Modifier {
	val vScope = remember { PointerInputScopeImpl() }
	LaunchedEffect(key1, key2) { vScope.block() }
	return this.then(PointerInputElement(vScope))
}

@Composable
fun Modifier.pointerInput(
	vararg keys: Any?,
	block: suspend PointerInputScope.() -> Unit,
): Modifier {
	val vScope = remember { PointerInputScopeImpl() }
	LaunchedEffect(*keys) { vScope.block() }
	return this.then(PointerInputElement(vScope))
}
