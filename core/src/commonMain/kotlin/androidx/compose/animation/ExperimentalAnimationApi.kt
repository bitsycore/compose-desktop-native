package androidx.compose.animation

// Stand-in for upstream's `ExperimentalAnimationApi` opt-in annotation.
//
// Why it's hand-written instead of vendored: upstream declares this
// annotation INLINE in `EnterExitTransition.kt` (line 66 of a 1598-line
// file), not as a standalone file. Our vendor pipeline copies whole files
// byte-for-byte from `tools/compose-fork/manifest.txt`, so the annotation
// can't be vendored standalone — and EnterExitTransition.kt as a whole
// can't be vendored yet because it depends on the Modifier.Node
// infrastructure (`ModifierNodeElement` / `LayoutAwareModifierNode` /
// `DrawModifierNode` / `ApproachLayoutModifierNode`) our renderer doesn't
// implement.
//
// The declaration below is BYTE-FOR-BYTE identical to upstream lines
// 66–75 of EnterExitTransition.kt — when that file becomes vendorable,
// this file should be deleted (the vendored copy supersedes it).

@RequiresOptIn(message = "This is an experimental animation API.")
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
)
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalAnimationApi
