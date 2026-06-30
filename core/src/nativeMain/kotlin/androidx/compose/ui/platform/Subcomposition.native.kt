package androidx.compose.ui.platform

import androidx.compose.runtime.AbstractApplier
import androidx.compose.ui.node.LayoutNode
import com.compose.desktop.native.node.NodeApplier

// Native actual for vendored commonMain Subcomposition.kt. Upstream's
// skiko actual returns a `DefaultUiApplier(container)`; ours returns
// the project's `NodeApplier` which is the same shape — an
// `AbstractApplier<LayoutNode>` driving a LayoutNode tree. Retires when
// upstream LayoutNode + DefaultUiApplier are vendored (Phase 9).
internal actual fun createApplier(container: LayoutNode): AbstractApplier<LayoutNode> =
    NodeApplier(container)
