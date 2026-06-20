package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.HorizontalScrollModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.VerticalScrollModifier

// ==================
// MARK: ScrollState
// ==================

/* Holds a scroll offset and its currently-known max. Both are MutableState
   so any composable reading value / maxValue recomposes when they change.
   The owning Modifier.verticalScroll (or horizontalScroll) sets maxValue
   from the actual content height / width during layout. */
class ScrollState(initial: Int = 0) {
    private var _value by mutableStateOf(initial.coerceAtLeast(0))
    private var _maxValue by mutableStateOf(Int.MAX_VALUE)

    val value: Int get() = _value
    val maxValue: Int get() = _maxValue

    /* Internal: the layout sets the max each frame as content / viewport sizes change. */
    fun setMaxInternal(inMax: Int) {
        val vClamped = inMax.coerceAtLeast(0)
        _maxValue = vClamped
        if (_value > vClamped) _value = vClamped
    }

    fun scrollBy(inDelta: Int) {
        _value = (_value + inDelta).coerceIn(0, _maxValue)
    }

    fun scrollTo(inPosition: Int) {
        _value = inPosition.coerceIn(0, _maxValue)
    }
}

@Composable
fun rememberScrollState(initial: Int = 0): ScrollState =
    remember { ScrollState(initial) }

// ==================
// MARK: Modifier.verticalScroll / horizontalScroll
// ==================

/* Adds vertical scrolling to the node. Children are measured with
   unbounded height, the node's own bounds clamp to incoming maxHeight,
   children are visually translated by -state.value, and the node clips
   children to its bounds. Mouse wheel events over this node call
   state.scrollBy. */
fun Modifier.verticalScroll(state: ScrollState): Modifier =
    then(VerticalScrollModifier(state))

fun Modifier.horizontalScroll(state: ScrollState): Modifier =
    then(HorizontalScrollModifier(state))
