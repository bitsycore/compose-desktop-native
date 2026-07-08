package demo.shim

import androidx.compose.ui.Modifier

// JVM actual: upstream has no `pressable`; the call sites that use it also wire a
// clickable + collectIsPressedAsState, so a passthrough is behaviourally fine
// (press-state feedback just isn't driven by this modifier on JVM).
actual fun Modifier.demoPressable(onChange: (Boolean) -> Unit): Modifier = this
