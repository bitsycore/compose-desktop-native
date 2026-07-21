package androidx.compose.ui

import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==================
// MARK: Expect actuals — project shim
// ==================

/*
   Hand-written actuals for the 4 expects in vendored
   `androidx.compose.ui.Expect.kt` that aren't covered by vendored
   `Actuals.native.kt` (which only provides areObjectsOfSameType +
   currentTimeMillis).

   Upstream splits these across nonJvmMain + skikoMain. Both would end
   up in our single nativeMain source set and conflict — so we vendor
   only `Actuals.native.kt` and hand-write the rest here. Each impl
   mirrors the upstream skikoMain / nonJvmMain behaviour.

   Delete when we grow proper separate skikoMain / nonJvmMain source
   sets in :core.
*/

/** classKeyForObject — upstream nonJvm. Used by Compose UI's internal
   class-based caching. Mirrors upstream verbatim. */
internal actual fun classKeyForObject(a: Any): Any = a::class

/** tryPopulateReflectively — upstream nonJvm no-op. The Layout Inspector
   tooling reflectively populates ModifierNodeElement inspection info on
   JVM; non-JVM doesn't have kotlin-reflect, so this is empty. */
internal actual fun InspectorInfo.tryPopulateReflectively(
	@Suppress("UNUSED_PARAMETER") element: ModifierNodeElement<*>,
) { /* no-op on native */ }

/** postDelayed — upstream skikoMain. Launches a coroutine that delays by
   inDelayMillis and runs inBlock. Returns the Job as a token so
   removePost can cancel. */
@OptIn(DelicateCoroutinesApi::class)
internal actual fun postDelayed(delayMillis: Long, block: () -> Unit): Any =
	GlobalScope.launch(Dispatchers.Main) {
		delay(delayMillis)
		block()
	}

/** removePost — upstream skikoMain. Cancels the Job returned by postDelayed. */
internal actual fun removePost(token: Any?) {
	(token as? Job?)?.cancel()
}
