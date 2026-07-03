package com.compose.desktop.native.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compose.desktop.native.modifier.onDrag
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ==================
// MARK: Scrollbar (Compose Desktop-style)
// ==================

/* A faithful subset of Compose Desktop's scrollbar (androidx.compose.foundation).
   You overlay a VerticalScrollbar / HorizontalScrollbar on a scrollable and feed
   it a ScrollbarAdapter built from the same ScrollState. This project's Box has
   no BoxScope (so no Modifier.align) — pin the bar with the Box's
   contentAlignment; the fillMaxSize content fills the rest:

       Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
           val state = rememberScrollState()
           Column(Modifier.fillMaxSize().verticalScroll(state)) { ... }
           VerticalScrollbar(
               adapter = rememberScrollbarAdapter(state),
               modifier = Modifier.fillMaxHeight(),
           )
       }

   Always visible (no auto-hide); the thumb tweens unhoverColor -> hoverColor on
   hover over hoverDurationMillis, matching upstream. Drag the thumb to scroll;
   click the track to page. */

@Immutable
class ScrollbarStyle(
    val minimalHeight: Dp,
    val thickness: Dp,
    val shape: Shape,
    val hoverDurationMillis: Int,
    val unhoverColor: Color,
    val hoverColor: Color,
)

/* Upstream default: an 8dp black thumb that's faint until hovered. Dark UIs
   provide a white-tinted style via LocalScrollbarStyle or the `style` param. */
fun defaultScrollbarStyle(): ScrollbarStyle = ScrollbarStyle(
    minimalHeight = 16.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 300,
    unhoverColor = Color.Black.copy(alpha = 0.12f),
    hoverColor = Color.Black.copy(alpha = 0.50f),
)

val LocalScrollbarStyle = compositionLocalOf { defaultScrollbarStyle() }

// ==================
// MARK: ScrollbarAdapter
// ==================

/* Bridges a scrollable's state to the scrollbar in pixels. scrollOffset /
   contentSize / viewportSize are along the scroll axis; the thumb is sized by
   viewportSize / contentSize and positioned by scrollOffset / (content - viewport). */
interface ScrollbarAdapter {
    val scrollOffset: Double
    val contentSize: Double
    val viewportSize: Double
    suspend fun scrollTo(scrollOffset: Double)
}

@Composable
fun rememberScrollbarAdapter(scrollState: ScrollState): ScrollbarAdapter =
    remember(scrollState) { ScrollStateScrollbarAdapter(scrollState) }

private class ScrollStateScrollbarAdapter(private val state: ScrollState) : ScrollbarAdapter {
    override val scrollOffset: Double get() = state.value.toDouble()
    override val contentSize: Double get() = state.contentSize.toDouble()
    override val viewportSize: Double get() = state.viewportSize.toDouble()
    override suspend fun scrollTo(scrollOffset: Double) {
        // Scrollbar thumb drag → instant scroll-to. dispatchRawDelta takes a
        // delta in pixels; convert the target offset into a delta from where
        // we are now (clamping at the scroll edges is the dispatchRawDelta
        // contract, so we don't have to coerce here).
        state.dispatchRawDelta((scrollOffset - state.value.toDouble()).toFloat())
    }
}

// ==================
// MARK: VerticalScrollbar / HorizontalScrollbar
// ==================

@Composable
fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    style: ScrollbarStyle = LocalScrollbarStyle.current,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = Scrollbar(adapter, modifier, reverseLayout, style, interactionSource, isVertical = true)

@Composable
fun HorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    style: ScrollbarStyle = LocalScrollbarStyle.current,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = Scrollbar(adapter, modifier, reverseLayout, style, interactionSource, isVertical = false)

/* Shared impl. The root Box is the track (fixed thickness on the cross axis,
   filling the main axis from the caller's modifier); a child Box is the thumb,
   positioned with an offset. Drag + track-click are handled on the track in
   track-relative coordinates (a stable origin), so the grab point stays under
   the pointer with no feedback from the thumb moving. interactionSource is
   accepted for API parity; hover here is driven by Modifier.hoverable. */
@Composable
private fun Scrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier,
    reverseLayout: Boolean,
    style: ScrollbarStyle,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource,
    isVertical: Boolean,
) {
    val vScope = rememberCoroutineScope()
    var vTrackPx by remember { mutableStateOf(0) }
    val vHoverSource = remember { MutableInteractionSource() }
    val vHovered by vHoverSource.collectIsHoveredAsState()
    var vDraggingThumb by remember { mutableStateOf(false) }
    var vGrab by remember { mutableStateOf(0) }

    val vContent = adapter.contentSize
    val vViewport = adapter.viewportSize
    val vOffset = adapter.scrollOffset
    val vMaxScroll = vContent - vViewport
    val vScrollable = vMaxScroll > 0.5 && vViewport > 0.0

    val vColor by animateColorAsState(
        targetValue = if (vHovered || vDraggingThumb) style.hoverColor else style.unhoverColor,
        animationSpec = tween(style.hoverDurationMillis),
    )

    val vRoot = (if (isVertical) modifier.width(style.thickness) else modifier.height(style.thickness))
        .onSizeChanged { vTrackPx = if (isVertical) it.height else it.width }
        .hoverable(vHoverSource)

    Box(modifier = vRoot) {
        if (!vScrollable || vTrackPx <= 0) return@Box

        // ============
        //  Thumb metrics (px)
        val vMinLen = style.minimalHeight.value.toInt()
        val vThumbLen = (vTrackPx * (vViewport / vContent)).roundToInt()
            .coerceIn(vMinLen.coerceAtMost(vTrackPx), vTrackPx)
        val vTravel = vTrackPx - vThumbLen
        val vRawPos = if (vTravel <= 0) 0 else (vTravel * (vOffset / vMaxScroll)).roundToInt()
        val vThumbPos = (if (reverseLayout) vTravel - vRawPos else vRawPos).coerceIn(0, vTravel)

        // ============
        //  Map a track-relative position to a scroll offset and apply it.
        fun scrollToTrackPos(inThumbTop: Int) {
            if (vTravel <= 0) return
            val vPos = inThumbTop.coerceIn(0, vTravel)
            val vFrac = (if (reverseLayout) vTravel - vPos else vPos).toDouble() / vTravel
            vScope.launch { adapter.scrollTo(vFrac * vMaxScroll) }
        }

        val vThumbMod = (
            if (isVertical) Modifier.offset(y = vThumbPos.dp).width(style.thickness).height(vThumbLen.dp)
            else Modifier.offset(x = vThumbPos.dp).height(style.thickness).width(vThumbLen.dp)
            )
            .clip(style.shape)
            .background(vColor)

        Box(modifier = vThumbMod)

        // ============
        //  Drag the thumb / page on track click — handled on the track so the
        //  coordinates have a stable (non-moving) origin.
        Box(
            modifier = Modifier
                .matchTrackSize(isVertical, vTrackPx, style.thickness)
                .onDrag(
                    onStart = { rx, ry ->
                        val vPress = if (isVertical) ry else rx
                        if (vPress in vThumbPos..(vThumbPos + vThumbLen)) {
                            vDraggingThumb = true
                            vGrab = vPress - vThumbPos
                        } else {
                            // Page toward the click by one viewport.
                            vDraggingThumb = false
                            val vDir = if (vPress < vThumbPos) -1.0 else 1.0
                            vScope.launch { adapter.scrollTo(vOffset + vDir * vViewport) }
                        }
                    },
                    onDrag = { rx, ry ->
                        if (vDraggingThumb) {
                            val vCur = if (isVertical) ry else rx
                            scrollToTrackPos(vCur - vGrab)
                        }
                    },
                    onEnd = { vDraggingThumb = false },
                )
        )
    }
}

/* Sizes the invisible drag-capture overlay to the full track. */
private fun Modifier.matchTrackSize(inVertical: Boolean, inTrackPx: Int, inThickness: Dp): Modifier =
    if (inVertical) this.width(inThickness).height(inTrackPx.dp)
    else this.height(inThickness).width(inTrackPx.dp)
