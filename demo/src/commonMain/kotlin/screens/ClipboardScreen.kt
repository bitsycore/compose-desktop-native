@file:OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)

package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import demo.shim.demoDecodeImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.readResourceBytes

// Resources live in data.kres under composeResources/<pkg>/<type>/<name>.<ext>,
// where <pkg> = demo.build.gradle.kts's `compose.resources.packageOfResClass`.
// The generated Res.drawable.<name> accessor knows this path but its
// ResourceItem.path is internal to :components-resources; readResourceBytes
// is public but takes the raw path, hence the literal.
private const val kLogoResourcePath = "composeResources/demo.generated.resources/drawable/compose_logo.png"

// ==================
// MARK: Clipboard screen
// ==================

/* Round-trips text AND PNG images through the OS clipboard via LocalClipboard
   (suspend, ClipEntry-typed) over SDL3's SDL_[GS]etClipboardText and
   SDL_[GS]etClipboardData("image/png"). Text and images share one Compose
   ClipEntry: getPlainText() vs getImage() surface whichever is present.
   Non-composable callers reach it by capturing LocalClipboard.current +
   rememberCoroutineScope() in composition and passing both into the callback. */
@Composable
internal fun ClipboardScreen() {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("Hello from ComposeDesktopNative!") }
    var pastedText by remember { mutableStateOf<String?>(null) }
    var pastedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var pastedNote by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Clipboard",
            "LocalClipboard (suspend, ClipEntry-typed) over the SDL3 system clipboard — " +
                "SDL_[GS]etClipboardText for plain text and SDL_[GS]etClipboardData(\"image/png\") " +
                "for images. Copy here and paste into another app (or vice-versa).",
        )

        // ============
        //  Text copy — ClipEntry.withPlainText / getPlainText.
        Section("Copy text", "scope.launch { clipboard.setClipEntry(ClipEntry.withPlainText(...)) }") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Text to copy") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry.withPlainText(draft)) }
                    note = "Copied ${draft.length} char(s) to the system clipboard."
                }) {
                    Text("Copy text", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        // ============
        //  Image copy — reads the bundled compose_logo PNG bytes and puts
        //  them on the clipboard under MIME "image/png". Paste into any
        //  image-aware app (Preview / Paint / a browser tab) to verify.
        Section(
            "Copy image",
            "readResourceBytes(\"drawable/compose_logo.png\") → ClipEntry.withImage(pngBytes). " +
                "Paste into another app to verify.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Preview of the source image — same PNG the copy button ships.
                    val vSource by produceState<ImageBitmap?>(null) {
                        val vBytes = runCatching { readResourceBytes(kLogoResourcePath) }.getOrNull()
                        value = vBytes?.let { demoDecodeImage(it) }
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        vSource?.let { Image(BitmapPainter(it), contentDescription = "compose_logo", modifier = Modifier.size(56.dp)) }
                    }
                    Button(onClick = {
                        scope.launch {
                            val vBytes = runCatching { readResourceBytes(kLogoResourcePath) }.getOrNull()
                            if (vBytes != null) {
                                clipboard.setClipEntry(ClipEntry.withImage(vBytes))
                                note = "Copied ${vBytes.size} byte(s) of PNG to the system clipboard."
                            } else {
                                note = "Failed to load the source image."
                            }
                        }
                    }) {
                        Text("Copy image", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        // ============
        //  Paste — surfaces whatever's on the clipboard: text OR image.
        Section("Paste", "clipboard.getClipEntry()?.getPlainText() / .getImage()") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        pastedText = null; pastedImage = null; pastedNote = null
                        scope.launch {
                            val vEntry = clipboard.getClipEntry()
                            val vImage = vEntry?.getImage()
                            val vText = vEntry?.getPlainText()
                            when {
                                vImage != null -> {
                                    pastedImage = demoDecodeImage(vImage)
                                    pastedNote = if (pastedImage == null)
                                        "image/png present (${vImage.size} byte(s)) but could not be decoded."
                                    else
                                        "Decoded ${vImage.size} byte(s) of PNG."
                                }
                                vText != null -> pastedText = vText
                                else -> pastedNote = "(nothing on the clipboard)"
                            }
                        }
                    }) {
                        Text("Paste", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                val vBitmap = pastedImage
                if (vBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = BitmapPainter(vBitmap),
                            contentDescription = "pasted image",
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                pastedText?.let {
                    Text("Text: \"$it\"", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                }
                pastedNote?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        // ============
        //  The modern Clipboard has no non-composable entry point — capture
        //  LocalClipboard.current + rememberCoroutineScope() in composition
        //  and pass both into any plain function or event callback.
        Section(
            "Outside composition",
            "Capture LocalClipboard.current + rememberCoroutineScope() in a Composable, " +
                "then hand both to any plain function that needs to touch the clipboard.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    copyFromPlainFunction(scope, clipboard)
                    note = "Wrote to the clipboard from a non-Composable helper (via the captured handle)."
                }) {
                    Text("Copy via captured handle", color = MaterialTheme.colorScheme.onPrimary)
                }
                note?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
}

/* Non-composable helper: receives the Clipboard + a CoroutineScope captured
   from composition at the call site. This is the idiomatic way to reach the
   clipboard from event handlers, coroutines, or utility functions — the
   modern Clipboard is intentionally suspend-only. */
private fun copyFromPlainFunction(scope: CoroutineScope, clipboard: Clipboard) {
    scope.launch {
        clipboard.setClipEntry(ClipEntry.withPlainText("Set from a plain, non-@Composable function."))
    }
}
