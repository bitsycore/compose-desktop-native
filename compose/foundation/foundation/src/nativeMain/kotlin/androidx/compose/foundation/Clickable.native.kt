package androidx.compose.foundation

import androidx.compose.ui.node.DelegatableNode

// ==================
// MARK: Clickable — native actuals
// ==================

/** Desktop/SDL values (mirror upstream desktop): no tap-indication delay, and the
   compose root is never inside a platform scrollable container. */
internal actual val TapIndicationDelay: Long = 0L

internal actual fun DelegatableNode.isComposeRootInScrollableContainer(): Boolean = false
