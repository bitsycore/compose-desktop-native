import androidx.compose.runtime.RememberObserver

// ==================
// MARK: log helper
// ==================

// ==================
// MARK: RecompositionTracker
// ==================

/* A per-call-site recomposition counter that also observes its own composition
   lifecycle via Compose's RememberObserver. Because it's stored with remember,
   the runtime calls:
     onRemembered — when this instance first enters the composition,
     onForgotten  — when it leaves (scope removed, or `tag` changed), and on
                    composition.dispose(),
     onAbandoned  — if the composition that produced it is rolled back before
                    being applied.
   record() bumps the recomposition count; it's a plain Int (not snapshot
   state) so logging it from a SideEffect doesn't itself trigger recomposition. */
class RecompositionTracker(private val tag: String) : RememberObserver {
	var count: Int = 0
		private set

	fun record() {
        ++count
    	println("$tag: Recomposed: #$count")
	}

	override fun onRemembered() {
		println("[$tag] entered composition")
	}

	override fun onForgotten() {
		println("[$tag] left composition (after $count recompositions)")
	}

	override fun onAbandoned() {
		println("[$tag] abandoned (composition rolled back before commit)")
	}
}

