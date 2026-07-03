@file:Suppress("UNUSED_PARAMETER")

package androidx.compose.ui.semantics

import androidx.compose.ui.Modifier

// ==================
// MARK: Semantics — minimal project shim (non-official)
// ==================

/*
 * Stub of upstream's `androidx.compose.ui.semantics` surface used by
 * `androidx.compose.foundation.Canvas`, `Image`, `Background`, etc. The
 * full vendored set (SemanticsProperties 1739, SemanticsModifier 232,
 * SemanticsNode 550, SemanticsConfiguration 209) is welded to the
 * accessibility / observation pipeline we don't drive yet. This shim
 * accepts every call and discards the payload — perfect for vendored
 * foundation files that mention semantics but whose runtime
 * accessibility output we never consume.
 *
 * Replace with the vendored upstream files when an accessibility
 * backend lands (Phase 11+).
 */

/** Receiver for the `Modifier.semantics { ... }` block — accept-all. */
interface SemanticsPropertyReceiver {
	operator fun <T> set(key: SemanticsPropertyKey<T>, value: T) = Unit
}

/** Opaque typed key for a single semantics property. */
class SemanticsPropertyKey<T>(val name: String)

private object NoOpSemanticsReceiver : SemanticsPropertyReceiver

/**
 * `Modifier.semantics { ... }` — invokes the block with a no-op receiver
 * and returns the modifier unchanged. The semantics payload is discarded
 * at the modifier boundary; the renderer never reads it.
 */
fun Modifier.semantics(
	mergeDescendants: Boolean = false,
	properties: SemanticsPropertyReceiver.() -> Unit,
): Modifier {
	NoOpSemanticsReceiver.properties()
	return this
}

/** Project shim for upstream's `Modifier.clearAndSetSemantics` — same shape. */
fun Modifier.clearAndSetSemantics(
	properties: SemanticsPropertyReceiver.() -> Unit,
): Modifier = semantics(mergeDescendants = false, properties = properties)

// ============
//  Common property setters (extension setters on SemanticsPropertyReceiver)
//
// Upstream models each semantics property as an
// `SemanticsPropertyKey<T>` value + a typed `var` extension setter. We
// keep the same shape — but every setter is a discard. Vendored
// foundation code uses these to attach contentDescription, role,
// progressBarRangeInfo, etc.

var SemanticsPropertyReceiver.contentDescription: String
	get() = ""
	set(value) = Unit

var SemanticsPropertyReceiver.testTag: String
	get() = ""
	set(value) = Unit

var SemanticsPropertyReceiver.role: Role
	get() = Role.Button
	set(value) = Unit

var SemanticsPropertyReceiver.shape: androidx.compose.ui.graphics.Shape
	get() = androidx.compose.ui.graphics.RectangleShape
	set(value) = Unit

var SemanticsPropertyReceiver.focused: Boolean
	get() = false
	set(value) = Unit

var SemanticsPropertyReceiver.progressBarRangeInfo: ProgressBarRangeInfo
	get() = ProgressBarRangeInfo.Indeterminate
	set(value) = Unit

/** Upstream `SemanticsPropertyReceiver.contentType` — set by
 *  `Modifier.contentType(ContentType.X)` to tag a field for autofill. */
var SemanticsPropertyReceiver.contentType: androidx.compose.ui.autofill.ContentType
	get() = androidx.compose.ui.autofill.ContentType.Username
	set(value) = Unit

// ============
//  Role — upstream is `value class Role(val value: Int)` with companion
//  constants. Project doesn't read role at runtime; keep a tiny enum-ish
//  value class to satisfy `import ...semantics.Role` call sites.

@kotlin.jvm.JvmInline
value class Role(val value: Int) {
	companion object {
		val Button = Role(0)
		val Checkbox = Role(1)
		val Switch = Role(2)
		val RadioButton = Role(3)
		val Tab = Role(4)
		val Image = Role(5)
		val DropdownList = Role(6)
		val ValuePicker = Role(7)
		val Carousel = Role(8)
	}
}

// ============
//  ProgressBarRangeInfo — used by foundation/ProgressSemantics.kt

class ProgressBarRangeInfo(
	val current: Float = 0f,
	val range: ClosedFloatingPointRange<Float> = 0f..1f,
	val steps: Int = 0,
) {
	companion object {
		val Indeterminate = ProgressBarRangeInfo()
	}
}

/** Upstream-shape setter wrapper. */
fun SemanticsPropertyReceiver.progressBarRangeInfo(
	current: Float,
	range: ClosedFloatingPointRange<Float>,
	steps: Int = 0,
) {
	progressBarRangeInfo = ProgressBarRangeInfo(current, range, steps)
}

/**
 * SemanticsConfiguration — upstream is a 209-line container backing the
 * SemanticsModifierNode's payload. Project shim is empty; vendored
 * SemanticsModifierNode implementations get a receiver that discards
 * everything written to it.
 */
class SemanticsConfiguration : SemanticsPropertyReceiver {
	// Phase 9: vendored LayoutNode sets these while folding semantics.
	var isClearingSemantics: Boolean = false
	var isMergingSemanticsOfDescendants: Boolean = false
	fun collapsePeer(peer: SemanticsConfiguration) {}
}

/* Action semantics used by vendored Clickable — accept-and-discard (no a11y backend). */
fun SemanticsPropertyReceiver.onClick(label: String? = null, action: (() -> Boolean)?) = Unit
fun SemanticsPropertyReceiver.onLongClick(label: String? = null, action: (() -> Boolean)?) = Unit
fun SemanticsPropertyReceiver.disabled() = Unit

/* requestFocus action semantics — used by vendored Focusable. Accept-and-discard. */
fun SemanticsPropertyReceiver.requestFocus(label: String? = null, action: (() -> Boolean)?) = Unit

// ============
//  Selection / toggle semantics — used by vendored foundation Toggleable / Selectable /
//  SelectableGroup. Accept-and-discard (no a11y backend). booleanValue / createFromBoolean
//  are real vendored FillableData members.

var SemanticsPropertyReceiver.selected: Boolean
	get() = false
	set(value) = Unit

var SemanticsPropertyReceiver.toggleableState: androidx.compose.ui.state.ToggleableState
	get() = androidx.compose.ui.state.ToggleableState.Off
	set(value) = Unit

var SemanticsPropertyReceiver.contentDataType: androidx.compose.ui.autofill.ContentDataType
	get() = androidx.compose.ui.autofill.ContentDataType.Toggle
	set(value) = Unit

var SemanticsPropertyReceiver.fillableData: androidx.compose.ui.autofill.FillableData
	get() = error("fillableData is write-only in this semantics shim")
	set(value) = Unit

/* Autofill fill action — vendored Toggleable/Selectable attach it; discarded here. */
fun SemanticsPropertyReceiver.onFillData(
	label: String? = null,
	action: ((androidx.compose.ui.autofill.FillableData) -> Boolean)?,
) = Unit

/* Marks a container as a radio-group-style single-selection group. Accept-and-discard. */
fun SemanticsPropertyReceiver.selectableGroup() = Unit

// ============
//  Scroll semantics — used by vendored foundation Scroll.kt / AbstractScrollableNode.
//  Accept-and-discard (no a11y backend); ScrollAxisRange is the real upstream shape.

class ScrollAxisRange(
	val value: () -> Float,
	val maxValue: () -> Float,
	val reverseScrolling: Boolean = false,
)

var SemanticsPropertyReceiver.verticalScrollAxisRange: ScrollAxisRange
	get() = error("verticalScrollAxisRange is write-only in this semantics shim")
	set(value) = Unit

var SemanticsPropertyReceiver.horizontalScrollAxisRange: ScrollAxisRange
	get() = error("horizontalScrollAxisRange is write-only in this semantics shim")
	set(value) = Unit

var SemanticsPropertyReceiver.isTraversalGroup: Boolean
	get() = false
	set(value) = Unit

fun SemanticsPropertyReceiver.scrollBy(label: String? = null, action: ((x: Float, y: Float) -> Boolean)?) = Unit

fun SemanticsPropertyReceiver.scrollByOffset(action: suspend (offset: androidx.compose.ui.geometry.Offset) -> androidx.compose.ui.geometry.Offset) = Unit

// ============
//  Lazy-layout semantics — used by vendored LazyLayoutSemantics. Accept-and-discard.

class CollectionInfo(val rowCount: Int, val columnCount: Int)

var SemanticsPropertyReceiver.collectionInfo: CollectionInfo
	get() = error("collectionInfo is write-only in this semantics shim")
	set(value) = Unit

fun SemanticsPropertyReceiver.indexForKey(mapping: (Any) -> Int) = Unit

fun SemanticsPropertyReceiver.scrollToIndex(label: String? = null, action: (Int) -> Boolean) = Unit

fun SemanticsPropertyReceiver.getScrollViewportLength(label: String? = null, action: (() -> Float?)) = Unit
