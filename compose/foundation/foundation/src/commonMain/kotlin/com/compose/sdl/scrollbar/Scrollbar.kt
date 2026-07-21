package com.compose.sdl.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ==================
// MARK: Scrollbar (Compose Desktop-style)
// ==================

/** A faithful subset of Compose Desktop's scrollbar (androidx.compose.foundation).
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

/** Upstream default: an 8dp black thumb that's faint until hovered. Dark UIs
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

/** Bridges a scrollable's state to the scrollbar in pixels. scrollOffset /
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

/** Adapter for a virtualized LazyColumn / LazyRow. A lazy list only knows the
   size of its VISIBLE items, so exact pixel geometry of off-screen content is
   unknowable — we estimate it from the average visible item size × total item
   count (the same approach as Compose Desktop). Accurate enough for a thumb
   whose length/position track the scroll position; the estimate self-corrects
   as items of different sizes scroll through the viewport. */
@Composable
fun rememberScrollbarAdapter(scrollState: LazyListState): ScrollbarAdapter =
    remember(scrollState) { LazyListScrollbarAdapter(scrollState) }

private class LazyListScrollbarAdapter(private val state: LazyListState) : ScrollbarAdapter {
    // Mean main-axis size of the currently-visible items, in px (>= 1 to avoid /0).
    private fun averageItemSize(): Double {
        val vVisible = state.layoutInfo.visibleItemsInfo
        if (vVisible.isEmpty()) return 0.0
        val vSpan = (vVisible.last().offset + vVisible.last().size) - vVisible.first().offset
        return (vSpan.toDouble() / vVisible.size).coerceAtLeast(1.0)
    }

    override val scrollOffset: Double
        get() = averageItemSize() * state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset

    override val contentSize: Double
        get() = averageItemSize() * state.layoutInfo.totalItemsCount

    override val viewportSize: Double
        get() = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset).toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        val vDistance = scrollOffset - this.scrollOffset
        // Within a viewport, scroll by EXACT pixels so a drag stays smooth. A lazy
        // list's pixel geometry is only ESTIMATED (average visible item size), and
        // that estimate drifts as differently-sized items scroll through the
        // viewport — so index-snapping every frame makes the thumb jump. scrollBy
        // sidesteps the estimate entirely. This is what Compose Desktop's own
        // LazyListScrollbarAdapter does, and for the same reason.
        if (abs(vDistance) <= viewportSize) {
            state.scrollBy(vDistance.toFloat())
            return
        }
        // Far jump (track-click paging past a viewport): no exact geometry to lean
        // on, so estimate an index/offset from the average and snap to it.
        val vAvg = averageItemSize()
        if (vAvg <= 0.0) return
        val vTarget = scrollOffset.coerceAtLeast(0.0)
        val vTotal = state.layoutInfo.totalItemsCount
        val vIndex = (vTarget / vAvg).toInt().coerceIn(0, (vTotal - 1).coerceAtLeast(0))
        val vItemOffset = (vTarget - vIndex * vAvg).toInt().coerceAtLeast(0)
        state.scrollToItem(vIndex, vItemOffset)
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

/** Shared impl. The root Box is the track (fixed thickness on the cross axis,
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
    // onSizeChanged reports PHYSICAL pixels (Option-B density flow), so every
    // int/px carried through here — vTrackPx, vThumbLen, vThumbPos — is in
    // physical pixels. Passing them into `.dp` doubles again through
    // density.toPx() on Retina, which visibly bloated the thumb (filled the
    // whole track) and made drag/track-click positions land 2× too far down.
    // Route pixel-sized modifiers through the current density so `.dp` and the
    // pixel value stay in the same coord space.
    val vDensity = LocalDensity.current

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

        val vThumbLenDp = with(vDensity) { vThumbLen.toDp() }
        val vThumbMod = (
            if (isVertical) Modifier.offset { IntOffset(0, vThumbPos) }.width(style.thickness).height(vThumbLenDp)
            else Modifier.offset { IntOffset(vThumbPos, 0) }.height(style.thickness).width(vThumbLenDp)
            )
            .clip(style.shape)
            .background(vColor)

        Box(modifier = vThumbMod)

        // ============
        //  Drag the thumb / page on track click — handled on the track so the
        //  coordinates have a stable (non-moving) origin. The press is CONSUMED:
        //  a scrollbar owns the pointer input it handles, so content sharing this
        //  region (the scrollable beneath, or a parent that also reads pointer
        //  input) never reacts to the same press. Geometry is read LIVE from the
        //  adapter at press time — not from the captured render metrics above,
        //  which go stale between pointerInput relaunches — so a grab after a
        //  wheel-scroll still hit-tests against the thumb's current position.
        Box(
            modifier = Modifier
                .matchTrackSize(isVertical, with(vDensity) { vTrackPx.toDp() }, style.thickness)
                .pointerInput(isVertical, adapter, vTrackPx, reverseLayout) {
                    awaitEachGesture {
                        val vDown = awaitFirstDown(requireUnconsumed = false)
                        vDown.consume()

                        // Live geometry — stable for the drag's duration (content /
                        // viewport / track sizes don't change mid-drag).
                        val vContentPx = adapter.contentSize
                        val vViewportPx = adapter.viewportSize
                        val vMaxScrollPx = vContentPx - vViewportPx
                        if (vMaxScrollPx <= 0.0 || vTrackPx <= 0) return@awaitEachGesture
                        val vMinLen = style.minimalHeight.value.toInt()
                        val vLen = (vTrackPx * (vViewportPx / vContentPx)).roundToInt()
                            .coerceIn(vMinLen.coerceAtMost(vTrackPx), vTrackPx)
                        val vTravelPx = vTrackPx - vLen
                        val vRaw = if (vTravelPx <= 0) 0
                                   else (vTravelPx * (adapter.scrollOffset / vMaxScrollPx)).roundToInt()
                        val vPos = (if (reverseLayout) vTravelPx - vRaw else vRaw).coerceIn(0, vTravelPx)

                        // Map a track-relative thumb-top to a scroll offset. The
                        // target uses the LIVE max scroll (read fresh each move),
                        // not the press-time value — the adapter compares it against
                        // its live scrollOffset, and mixing a stale scale with a live
                        // one makes the delta run away (grab at the bottom, drag up,
                        // and the list overshoots to the middle).
                        fun scrollToTrackPos(inThumbTop: Int) {
                            if (vTravelPx <= 0) return
                            val vLiveMax = adapter.contentSize - adapter.viewportSize
                            if (vLiveMax <= 0.0) return
                            val vClamped = inThumbTop.coerceIn(0, vTravelPx)
                            val vFrac = (if (reverseLayout) vTravelPx - vClamped else vClamped).toDouble() / vTravelPx
                            vScope.launch { adapter.scrollTo(vFrac * vLiveMax) }
                        }

                        val vPress = (if (isVertical) vDown.position.y else vDown.position.x).toInt()
                        val vGrab: Int
                        if (vPress in vPos..(vPos + vLen)) {
                            vDraggingThumb = true
                            vGrab = vPress - vPos
                        } else {
                            // Page toward the click by one viewport.
                            vDraggingThumb = false
                            vGrab = 0
                            val vDir = if (vPress < vPos) -1.0 else 1.0
                            vScope.launch { adapter.scrollTo(adapter.scrollOffset + vDir * vViewportPx) }
                        }

                        // Follow the pressed pointer to release, consuming every
                        // change so the interaction can't leak. Only a thumb grab
                        // drags-to-scroll; a track press just consumes.
                        do {
                            val vEvent = awaitPointerEvent()
                            val vChange = vEvent.changes.firstOrNull { it.id == vDown.id } ?: break
                            if (vDraggingThumb) {
                                val vCur = (if (isVertical) vChange.position.y else vChange.position.x).toInt()
                                scrollToTrackPos(vCur - vGrab)
                            }
                            vChange.consume()
                        } while (vEvent.changes.any { it.id == vDown.id && it.pressed })

                        vDraggingThumb = false
                    }
                }
        )
    }
}

/** Sizes the invisible drag-capture overlay to the full track. `inTrackDp`
   comes from the caller pre-converted via LocalDensity (Option-B: layout
   coords are physical px, so px-to-dp goes through the current density). */
private fun Modifier.matchTrackSize(inVertical: Boolean, inTrackDp: Dp, inThickness: Dp): Modifier =
    if (inVertical) this.width(inThickness).height(inTrackDp)
    else this.height(inThickness).width(inTrackDp)
