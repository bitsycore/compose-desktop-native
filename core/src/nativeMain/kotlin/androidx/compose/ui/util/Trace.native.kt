package androidx.compose.ui.util

// ==================
// MARK: trace native actuals
// ==================

// No-op trace actuals — this build has no tracing backend.

/** Run [block] without any tracing instrumentation. */
actual inline fun <T> trace(sectionName: String, block: () -> T): T = block()

/** Discard the trace value — no tracing backend installed. */
actual fun traceValue(tag: String, value: Long) {}
