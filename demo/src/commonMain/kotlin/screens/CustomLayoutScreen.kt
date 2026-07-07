package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

// ==================
// MARK: CustomLayoutScreen
// ==================

/* Demonstrates the public Layout(...) composable + MeasurePolicy + the
   Modifier.layout {} per-node interception. Both render identically on
   Skia and SDL3 because they go through the same LayoutNode pipeline as
   Row / Column / Box. */
@Composable
internal fun CustomLayoutScreen() {
	val vPrimary = MaterialTheme.colorScheme.primary
	val vSecondary = MaterialTheme.colorScheme.secondary

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"Layout + MeasurePolicy",
			"User-defined layouts via Layout(content, measurePolicy) and per-node measurement " +
				"override via Modifier.layout {}. Both build on the same LayoutNode pipeline as " +
				"Row / Column / Box.",
		)

		Section(
			"Custom Layout: staircase",
			"A measure policy that places each child diagonally offset from the previous one. " +
				"The lambda body uses the standard upstream shape — measure all measurables, call " +
				"layout(w, h) { ... } with the placement block.",
		) {
			Layout(content = {
				Box(modifier = Modifier.size(40.dp).background(vPrimary, RoundedCornerShape(6.dp)))
				Box(modifier = Modifier.size(40.dp).background(vSecondary, RoundedCornerShape(6.dp)))
				Box(modifier = Modifier.size(40.dp).background(vPrimary, RoundedCornerShape(6.dp)))
				Box(modifier = Modifier.size(40.dp).background(vSecondary, RoundedCornerShape(6.dp)))
				Box(modifier = Modifier.size(40.dp).background(vPrimary, RoundedCornerShape(6.dp)))
			}, measurePolicy = { vMeasurables, vConstraints ->
				val vPlaceables = vMeasurables.map { it.measure(vConstraints) }
				val vStep = 24
				var vTotalW = 0
				var vTotalH = 0
				vPlaceables.forEachIndexed { vI, vP ->
					vTotalW = max(vTotalW, vI * vStep + vP.width)
					vTotalH = max(vTotalH, vI * vStep + vP.height)
				}
				layout(vTotalW, vTotalH) {
					vPlaceables.forEachIndexed { vI, vP ->
						vP.place(vI * vStep, vI * vStep)
					}
				}
			})
		}

		Section(
			"Custom Layout: equal-share row",
			"Same idea but the policy splits the parent width N ways equally. All children get " +
				"the same fixed width via the Constraints we hand to measure().",
		) {
			Layout(
				modifier = Modifier.width(400.dp),
				content = {
					Box(modifier = Modifier.background(vPrimary, RoundedCornerShape(6.dp)))
					Box(modifier = Modifier.background(vSecondary, RoundedCornerShape(6.dp)))
					Box(modifier = Modifier.background(vPrimary, RoundedCornerShape(6.dp)))
					Box(modifier = Modifier.background(vSecondary, RoundedCornerShape(6.dp)))
				},
				measurePolicy = { vMeasurables, vConstraints ->
					val vCount = vMeasurables.size.coerceAtLeast(1)
					val vSlot = vConstraints.maxWidth / vCount
					val vSlotC = androidx.compose.ui.unit.Constraints(
						minWidth = vSlot, maxWidth = vSlot,
						minHeight = 40, maxHeight = 40,
					)
					val vPlaceables = vMeasurables.map { it.measure(vSlotC) }
					layout(vConstraints.maxWidth, 40) {
						vPlaceables.forEachIndexed { vI, vP -> vP.place(vI * vSlot, 0) }
					}
				},
			)
		}

		Section(
			"Modifier.layout — measure then offset",
			"The modifier wraps the child's natural measure. Here the child reports its natural " +
				"size to the parent but draws inset by 12 pixels — useful for nudging a single " +
				"child without rewriting its parent's layout.",
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Text("Before:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
				Box(modifier = Modifier
					.size(80.dp, 40.dp)
					.background(vPrimary, RoundedCornerShape(6.dp)),
				)
				Text("After:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
				Box(modifier = Modifier
					.size(80.dp, 40.dp)
					.background(vSecondary, RoundedCornerShape(6.dp))
					.layout { vMeasurable, vConstraints ->
						val vP = vMeasurable.measure(vConstraints)
						// Parent thinks we're 80x40 but we draw at (12, 0).
						layout(vP.width, vP.height) { vP.place(12, 0) }
					},
				)
			}
		}
	}
}
