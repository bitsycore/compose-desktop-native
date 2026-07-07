package demo.shim

import androidx.compose.ui.Modifier
import com.compose.desktop.native.modifier.pressable

actual fun Modifier.demoPressable(onChange: (Boolean) -> Unit): Modifier = this.pressable(onChange)
