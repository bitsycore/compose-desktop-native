package screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import demo.shim.demoReadFilePaths
import demo.shim.demoReadText

// ==================
// MARK: DragAndDropScreen — Modifier.dragAndDropTarget over SDL_EVENT_DROP_*
// ==================

/** Modifier.dragAndDropTarget receives OS drops from other apps. On this
   port SDL3's SDL_EVENT_DROP_BEGIN / FILE / TEXT / POSITION / COMPLETE
   drive the events (see Sdl3DragAndDropOwner); on JVM the AWT
   DropTargetDropEvent path does. Either way the shouldStartDragAndDrop
   predicate is asked once per session and, if it returns true, the
   DragAndDropTarget receives onStarted / onEntered / onMoved / onDrop /
   onEnded through the compose layout tree. */
@Composable
internal fun DragAndDropScreen() {
    var isOver by remember { mutableStateOf(false) }
    var lastFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastText by remember { mutableStateOf<String?>(null) }
    var lastNote by remember { mutableStateOf("Drag a file or text from another app onto the box below.") }

    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { isOver = true }
            override fun onExited(event: DragAndDropEvent) { isOver = false }
            override fun onEnded(event: DragAndDropEvent) { isOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val vFiles = event.demoReadFilePaths()
                val vText = event.demoReadText()
                lastFiles = vFiles
                lastText = vText
                lastNote = when {
                    vFiles.isNotEmpty() -> "Received ${vFiles.size} file path(s)."
                    vText != null -> "Received text (${vText.length} chars)."
                    else -> "Drop event delivered with no readable payload."
                }
                isOver = false
                return true
            }
        }
    }

    val vBase = MaterialTheme.colorScheme.surfaceVariant
    val vHover = MaterialTheme.colorScheme.primaryContainer
    val vBg by animateColorAsState(if (isOver) vHover else vBase)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Drag & drop",
            "Modifier.dragAndDropTarget receiving OS drops. On this native port SDL_EVENT_DROP_* " +
                "wire into a Sdl3DragAndDropOwner that dispatches through the root DragAndDropNode.",
        )

        Section("Drop zone", "Drag a file or a text selection from another app onto the box.") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(vBg, RoundedCornerShape(10.dp))
                    .border(
                        width = if (isOver) 2.dp else 1.dp,
                        color = if (isOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { true },
                        target = target,
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isOver) "Release to drop" else "Drop files or text here",
                    color = if (isOver) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        }

        Section("Last drop", lastNote) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lastFiles.isNotEmpty()) {
                    Text("Files (${lastFiles.size}):", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        lastFiles.forEach { path ->
                            Text(path, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        }
                    }
                }
                lastText?.let {
                    Text("Text:", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
