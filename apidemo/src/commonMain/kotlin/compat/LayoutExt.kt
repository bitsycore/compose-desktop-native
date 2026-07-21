package apidemo

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow

// COPY of the port's com.compose.sdl.layout LayoutCoordinates sugar so the
// same helpers resolve on the jvm parity target (the port modules are
// native-only). Guards `isAttached`: a menu/tooltip anchor can be read while
// its node is mid-recycle, and positionInWindow() throws on a detached
// coordinator.

/** Logical-point X of this layout in the window. */
val LayoutCoordinates.x: Int get() = if (isAttached) positionInWindow().x.toInt() else 0

/** Logical-point Y of this layout in the window. */
val LayoutCoordinates.y: Int get() = if (isAttached) positionInWindow().y.toInt() else 0
