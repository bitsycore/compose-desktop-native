package androidx.compose.ui.layout

// ==================
// MARK: Ruler + RulerScope shims
// ==================

/**
 * Phase 4a shims for upstream `androidx.compose.ui.layout.Ruler` +
 * `RulerScope` (Ruler.kt, 163 lines upstream).
 *
 * Real upstream Ruler is a sealed class with VerticalRuler /
 * HorizontalRuler subclasses + a calculation lambda that runs in a
 * PlacementScope. Vendored MeasureResult references Ruler in three
 * defaulted member types (`rulers: (RulerScope.() -> Unit)?`,
 * `isRulerProvided: ((Ruler) -> Boolean)?`, `rulerProvider:
 * (RulerScope.(Ruler) -> Unit)?`). Our renderers never read these —
 * they call `MeasureResult.placeChildren()` and ignore the rest — so
 * the shims just need to exist as types.
 *
 * Delete in Phase 4 proper when the real Ruler.kt + PlacementScope
 * reshape lands.
 */
sealed class Ruler

class VerticalRuler internal constructor() : Ruler()

class HorizontalRuler internal constructor() : Ruler()

/**
 * Minimal `RulerScope`. Upstream extends `Density` + `Placeable.PlacementScope`
 * and exposes `provides` / `providesRelative` etc. We expose nothing —
 * no Phase 4a code constructs a RulerScope.
 */
interface RulerScope
