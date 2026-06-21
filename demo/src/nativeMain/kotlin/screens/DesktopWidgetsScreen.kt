package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SegmentedButton
import androidx.compose.material.SegmentedButtonDefaults
import androidx.compose.material.SingleChoiceSegmentedButtonRow
import androidx.compose.material.Text
import androidx.compose.material.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.desktop.native.widgets.HorizontalSplitPane
import com.compose.desktop.native.widgets.NumberStepper
import com.compose.desktop.native.widgets.StatusBar
import com.compose.desktop.native.widgets.Toolbar
import com.compose.desktop.native.widgets.VerticalSplitPane

@Composable
internal fun DesktopWidgetsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Desktop widgets",
            "Project-original controls under com.compose.desktop.native.widgets — denser, mouse-oriented.",
        )

        // SegmentedButton
        Section("SingleChoiceSegmentedButtonRow", "Mutually-exclusive button strip. Cleaner than a radio group when there are 2–4 options.") {
            var vSel by remember { mutableStateOf(1) }
            val vLabels = listOf("Left", "Center", "Right")
            SingleChoiceSegmentedButtonRow {
                for ((vIdx, vLabel) in vLabels.withIndex()) {
                    SegmentedButton(
                        selected = vSel == vIdx,
                        onClick = { vSel = vIdx },
                        shape = SegmentedButtonDefaults.itemShape(vIdx, vLabels.size),
                    ) {
                        Text(
                            vLabel,
                            color = if (vSel == vIdx) MaterialTheme.colors.primary
                                    else MaterialTheme.colors.onSurface,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        // ToggleButton
        Section("ToggleButton", "Stand-alone binary button — useful for formatting (Bold/Italic) or panel show/hide.") {
            var vBold by remember { mutableStateOf(false) }
            var vItalic by remember { mutableStateOf(true) }
            var vUnder by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleButton(checked = vBold, onCheckedChange = { vBold = it }) {
                    Text("B", color = MaterialTheme.colors.onSurface, fontSize = 14.sp)
                }
                ToggleButton(checked = vItalic, onCheckedChange = { vItalic = it }) {
                    Text("I", color = MaterialTheme.colors.onSurface, fontSize = 14.sp)
                }
                ToggleButton(checked = vUnder, onCheckedChange = { vUnder = it }) {
                    Text("U", color = MaterialTheme.colors.onSurface, fontSize = 14.sp)
                }
            }
        }

        // NumberStepper
        Section("NumberStepper", "Bounded integer input with − / + buttons. Cap and floor at `range`.") {
            var vCount by remember { mutableStateOf(3) }
            var vZoom by remember { mutableStateOf(100) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberStepper(value = vCount, onValueChange = { vCount = it }, range = 0..10)
                Text("Count: $vCount", fontSize = 14.sp)
                NumberStepper(value = vZoom, onValueChange = { vZoom = it }, range = 50..200, step = 10)
                Text("Zoom: $vZoom%", fontSize = 14.sp)
            }
        }

        // Toolbar + StatusBar inside a fake mini-app
        Section("Toolbar + StatusBar", "Top action strip + bottom status line — desktop chrome essentials.") {
            Column(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.background),
            ) {
                Toolbar {
                    Text("File", color = MaterialTheme.colors.onSurface, fontSize = 13.sp,
                         modifier = Modifier.padding(end = 12.dp))
                    Text("Edit", color = MaterialTheme.colors.onSurface, fontSize = 13.sp,
                         modifier = Modifier.padding(end = 12.dp))
                    Text("View", color = MaterialTheme.colors.onSurface, fontSize = 13.sp)
                }
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(MaterialTheme.colors.surface),
                    contentAlignment = Alignment.Center,
                ) { Text("Content area", color = Color(0x88FFFFFFL), fontSize = 12.sp) }
                StatusBar {
                    Text("Ready", color = Color(0xCCFFFFFFL), fontSize = 11.sp)
                }
            }
        }

        // SplitPane
        Section("HorizontalSplitPane", "Drag the centre divider to resize the panes.") {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                HorizontalSplitPane(
                    initialFirstSize = 220.dp,
                    first = {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.surface),
                            contentAlignment = Alignment.Center,
                        ) { Text("Sidebar", color = MaterialTheme.colors.onSurface, fontSize = 13.sp) }
                    },
                    second = {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background),
                            contentAlignment = Alignment.Center,
                        ) { Text("Editor", color = MaterialTheme.colors.onBackground, fontSize = 13.sp) }
                    },
                )
            }
        }

        Section("VerticalSplitPane", "Top/bottom variant.") {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                VerticalSplitPane(
                    initialFirstSize = 100.dp,
                    first = {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.surface),
                            contentAlignment = Alignment.Center,
                        ) { Text("Editor", color = MaterialTheme.colors.onSurface, fontSize = 13.sp) }
                    },
                    second = {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background),
                            contentAlignment = Alignment.Center,
                        ) { Text("Output", color = MaterialTheme.colors.onBackground, fontSize = 13.sp) }
                    },
                )
            }
        }
    }
}
