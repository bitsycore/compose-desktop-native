package androidx.compose.ui.graphics

import com.compose.desktop.native.graphics.ProjectPath
import com.compose.desktop.native.graphics.ProjectPathIterator

// ==================
// MARK: Path + PathIterator native actuals
// ==================

/* Thin actuals for the vendored `expect fun Path(): Path` and
   `expect fun PathIterator(...)` factory functions. The concrete impls
   live in com.compose.desktop.native.graphics per FIDELITY relocate rule —
   they are project-only types backing the upstream Path / PathIterator
   interfaces with our PathCommand-list representation. */

actual fun Path(): Path = ProjectPath()

actual fun PathIterator(
	path: Path,
	conicEvaluation: PathIterator.ConicEvaluation,
	tolerance: Float,
): PathIterator = ProjectPathIterator(path as? ProjectPath ?: ProjectPath(), conicEvaluation)
