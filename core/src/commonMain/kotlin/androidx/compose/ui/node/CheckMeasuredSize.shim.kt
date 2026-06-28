package androidx.compose.ui.node

import androidx.compose.ui.internal.checkPrecondition

// ==================
// MARK: checkMeasuredSize shim
// ==================

/**
 * Phase 4e shim for upstream `checkMeasuredSize` (defined inside
 * `node/LookaheadDelegate.kt` line 554). Vendored `MeasureScope.kt`
 * calls it from the default `layout()` impl. Pulling LookaheadDelegate.kt
 * verbatim is multi-session work (700 lines + transitive engine deps),
 * so we ship just this 4-line helper.
 *
 * Body matches upstream byte-for-byte.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun checkMeasuredSize(width: Int, height: Int) {
	checkPrecondition(width and kMaxLayoutMask == 0 && height and kMaxLayoutMask == 0) {
		"Size($width x $height) is out of range. Each dimension must be between 0 and " +
			"${(1 shl 24) - 1}."
	}
}

/** Upstream LookaheadDelegate.kt:551 `private const val MaxLayoutMask`. */
private const val kMaxLayoutMask: Int = 0xFF00_0000.toInt()
