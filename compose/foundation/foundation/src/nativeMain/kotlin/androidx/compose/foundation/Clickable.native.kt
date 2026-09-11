// VENDOR-REIMPL: compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/Clickable.skiko.kt @ v1.12.0
// Project REIMPLEMENTATION, not a derived copy: same package + signatures,
// different body (the port replaces upstream's skiko scene/platform layer).
// Upstream edits to the counterpart do NOT need reconciling here - re-check
// only if the expect/actual SIGNATURES change.

package androidx.compose.foundation

import androidx.compose.ui.node.DelegatableNode

// ==================
// MARK: Clickable - native actuals
// ==================

/** Desktop/SDL values (mirror upstream desktop): no tap-indication delay, and the
   compose root is never inside a platform scrollable container. */
internal actual val TapIndicationDelay: Long = 0L

internal actual fun DelegatableNode.isComposeRootInScrollableContainer(): Boolean = false
