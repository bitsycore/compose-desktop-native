package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.HorizontalScrollModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.VerticalScrollModifier
import kotlin.math.roundToInt

// ==================
// MARK: ScrollState
// ==================

/* Holds a scroll offset and its currently-known max. Both are MutableState
   so any composable reading value / maxValue recomposes when they change.
   The owning Modifier.verticalScroll (or horizontalScroll) sets maxValue
   from the actual content height / width during layout.

   scrollBy / scrollTo move instantly (used by the scrollbar thumb drag);
   smoothScrollBy eases toward a target over several frames (used by the mouse
   wheel) — the window loop drives the easing once per frame via
   ScrollAnimator.tick(). */
class ScrollState(initial: Int = 0) {
    private var _value by mutableStateOf(initial.coerceAtLeast(0))
    private var _maxValue by mutableStateOf(Int.MAX_VALUE)
    private var _viewportSize by mutableStateOf(0)
    // Smooth-scroll target the easing chases; kept == _value when not animating.
    private var _animTarget = initial.coerceAtLeast(0)

    val value: Int get() = _value
    val maxValue: Int get() = _maxValue

    /* Viewport length along the scroll axis, in px — set by layout each frame.
       Used by a Scrollbar to size its thumb (viewport / content). */
    val viewportSize: Int get() = _viewportSize

    /* Total content length along the scroll axis = scroll range + viewport.
       Before the first layout pass (maxValue still Int.MAX_VALUE) this is just
       the viewport so a Scrollbar treats the content as non-scrollable. */
    internal val contentSize: Int get() = if (_maxValue == Int.MAX_VALUE) _viewportSize else _maxValue + _viewportSize

    /* Internal: the layout sets the max + viewport each frame as content /
       viewport sizes change. */
    internal fun setMaxInternal(inMax: Int, inViewport: Int = _viewportSize) {
        val vClamped = inMax.coerceAtLeast(0)
        _maxValue = vClamped
        _viewportSize = inViewport.coerceAtLeast(0)
        if (_value > vClamped) _value = vClamped
        if (_animTarget > vClamped) _animTarget = vClamped
    }

    fun scrollBy(inDelta: Int) {
        _value = (_value + inDelta).coerceIn(0, _maxValue)
        _animTarget = _value
    }

    fun scrollTo(inPosition: Int) {
        _value = inPosition.coerceIn(0, _maxValue)
        _animTarget = _value
    }

    /* Eased scroll: accumulate into a target the frame loop glides toward, so a
       mouse-wheel notch animates instead of jumping. Repeated notches add up
       (momentum). Registers with ScrollAnimator so the loop ticks it. */
    fun smoothScrollBy(inDelta: Int) {
        _animTarget = (_animTarget + inDelta).coerceIn(0, _maxValue)
        if (_animTarget != _value) ScrollAnimator.register(this)
    }

    /* One easing step toward the target; returns true while still animating.
       Called once per frame by ScrollAnimator. Moves ~half the remaining
       distance per frame and snaps the last few px so the glide ends crisply
       (≈80ms settle @60fps) instead of crawling after you stop scrolling. */
    internal fun tickSmooth(): Boolean {
        val vDiff = _animTarget - _value
        if (vDiff == 0) return false
        if (vDiff in -kSnapPx..kSnapPx) { _value = _animTarget; return false }
        val vStep = (vDiff * kSmoothFactor).roundToInt().let { if (it == 0) (if (vDiff > 0) 1 else -1) else it }
        _value = (_value + vStep).coerceIn(0, _maxValue)
        return _value != _animTarget
    }

    companion object {
        private const val kSmoothFactor = 0.5f
        private const val kSnapPx = 2
    }
}

// ==================
// MARK: ScrollAnimator
// ==================

/* Per-frame driver for smoothScrollBy easing. ScrollStates register here when a
   smooth scroll starts; the window's main loop calls tick() once per frame and
   states drop out when they reach their target. No-op (cheap) when idle. */
object ScrollAnimator {
    private val fActive = mutableSetOf<ScrollState>()

    fun register(inState: ScrollState) { fActive.add(inState) }

    fun tick() {
        if (fActive.isEmpty()) return
        val vIt = fActive.iterator()
        while (vIt.hasNext()) {
            if (!vIt.next().tickSmooth()) vIt.remove()
        }
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
