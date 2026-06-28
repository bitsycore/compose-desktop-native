package androidx.compose.animation

// Stand-in for upstream's `ExperimentalAnimationApi` opt-in annotation. The
// upstream declaration lives inside EnterExitTransition.kt (which we don't
// vendor yet — engine-tied), so extracting just the annotation here keeps
// the rest of the animation module (Crossfade, etc.) compilable.
//
// Matches upstream's @RequiresOptIn message + retention + targets.
@RequiresOptIn(message = "This is an experimental animation API.")
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalAnimationApi
