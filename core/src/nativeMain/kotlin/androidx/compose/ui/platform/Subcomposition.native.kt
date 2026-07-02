package androidx.compose.ui.platform

import androidx.compose.runtime.AbstractApplier
import com.compose.desktop.native.node.ProjectLayoutNode
import com.compose.desktop.native.node.NodeApplier

// Native actual for vendored commonMain Subcomposition.kt. Upstream's
// skiko actual returns a `DefaultUiApplier(container)`; ours returns
// the project's `NodeApplier` which is the same shape — an
// `AbstractApplier<ProjectLayoutNode>` driving a ProjectLayoutNode tree. Retires when
// upstream ProjectLayoutNode + DefaultUiApplier are vendored (Phase 9).
internal actual fun createApplier(container: ProjectLayoutNode): AbstractApplier<ProjectLayoutNode> =
    NodeApplier(container)
