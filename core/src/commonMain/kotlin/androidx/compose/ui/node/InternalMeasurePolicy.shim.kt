package androidx.compose.ui.node

// The project's internal node-level measure policy moved to
// com.compose.desktop.native.node (with ProjectLayoutNode). Readers (BasicText,
// Image, LayoutPolicyAdapter) import it as androidx.compose.ui.node.MeasurePolicy;
// this alias keeps that path working without colliding with the vendored
// androidx.compose.ui.layout.MeasurePolicy. SAM construction works through it.
internal typealias MeasurePolicy = com.compose.desktop.native.node.MeasurePolicy
