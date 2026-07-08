package androidx.compose.ui.graphics

import com.compose.sdl.graphics.ProjectPath
import com.compose.sdl.graphics.ProjectPathIterator

// ==================
// MARK: Path + PathIterator — SDL3 renderer actuals
// ==================

/*
 * SDL3 counterpart of upstream `SkiaBackedPath.skiko.kt`. Vendored Skia path
 * is backed by `org.jetbrains.skia.Path`; SDL3 has no native path type, so
 * the vendored `expect fun Path()` and `expect fun PathIterator(...)`
 * factories return project-side types from `com.compose.sdl.graphics`
 * (a PathCommand-list impl the SDL3 renderer walks).
 */
actual fun Path(): Path = ProjectPath()

actual fun PathIterator(
	path: Path,
	conicEvaluation: PathIterator.ConicEvaluation,
	tolerance: Float,
): PathIterator = ProjectPathIterator(path as? ProjectPath ?: ProjectPath(), conicEvaluation)
