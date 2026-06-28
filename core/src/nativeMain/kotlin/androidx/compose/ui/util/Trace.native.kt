package androidx.compose.ui.util

// ==================
// MARK: trace native actuals
// ==================

/* No-op trace actuals — this build has no tracing backend. */

actual inline fun <T> trace(sectionName: String, block: () -> T): T = block()

actual fun traceValue(tag: String, value: Long) {}
