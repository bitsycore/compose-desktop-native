package androidx.compose.ui.layout

// Phase 9 stub — lookahead pipeline unvendored; NodeCoordinator reads `.coordinator`.
internal interface LookaheadLayoutCoordinates : LayoutCoordinates {
	val coordinator: androidx.compose.ui.node.NodeCoordinator
}
