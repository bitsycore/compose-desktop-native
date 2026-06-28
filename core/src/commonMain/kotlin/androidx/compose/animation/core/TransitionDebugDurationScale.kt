package androidx.compose.animation.core

// `AnimationDebugDurationScale` is defined in upstream's Transition.kt
// (line 215) as an internal const. SuspendAnimation.kt reads it. We don't
// vendor Transition.kt (2415 lines, big bite, separate pass), so expose the
// same constant here. Upstream value is 1; bump for slow-motion debugging.
internal const val AnimationDebugDurationScale = 1
