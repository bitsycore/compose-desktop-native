package com.compose.desktop.native.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: NumberStepper
// ==================

/* Desktop number-input affordance: [−] value [+]. Bounded to `range`,
   stepped by `step`. Use this in preference panels, sizing inputs, anything
   where the user picks an integer within a known span. Not a mirror of an
   androidx API — Compose's equivalent is hand-rolled per app — so it lives
   under com.compose.desktop.native.widgets. */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true,
) {
    val vBorder = BorderStroke(1.dp, Color(0x33FFFFFFL))
    val vShape = RoundedCornerShape(4.dp)

    Row(
        modifier = modifier
            .height(NumberStepperDefaults.Height)
            .background(Color(0x14FFFFFFL), vShape)
            .border(vBorder, vShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            symbol = "−",
            onClick = {
                val vNext = (value - step).coerceIn(range.first, range.last)
                if (vNext != value) onValueChange(vNext)
            },
            enabled = enabled && value > range.first,
        )
        Box(
            modifier = Modifier
                .width(NumberStepperDefaults.ValueWidth)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = if (enabled) MaterialTheme.colors.onSurface else Color(0x66FFFFFFL),
            )
        }
        StepperButton(
            symbol = "+",
            onClick = {
                val vNext = (value + step).coerceIn(range.first, range.last)
                if (vNext != value) onValueChange(vNext)
            },
            enabled = enabled && value < range.last,
        )
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(NumberStepperDefaults.ButtonSize)
            .clickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = 16.sp,
            color = if (enabled) MaterialTheme.colors.onSurface else Color(0x66FFFFFFL),
        )
    }
}

object NumberStepperDefaults {
    val Height: Dp = 32.dp
    val ButtonSize: Dp = 32.dp
    val ValueWidth: Dp = 48.dp
}
