package androidx.compose.ui.semantics

import androidx.compose.ui.layout.LayoutInfo

// Phase 9 stub — upstream SemanticsInfo (semantics tree view onto a LayoutNode).
// Vendored LayoutNode implements it + overrides these; defaults keep it compiling
// without the semantics/a11y runtime.
internal interface SemanticsInfo : LayoutInfo {
	val semanticsConfiguration: SemanticsConfiguration?
		get() = null
	fun isTransparent(): Boolean = false
	val childrenInfo: List<SemanticsInfo>
		get() = emptyList()
}

// Monotonic semantics-id generator (upstream is atomic; single-threaded here).
private var vSemanticsIdCounter: Int = 0
fun generateSemanticsId(): Int = ++vSemanticsIdCounter
