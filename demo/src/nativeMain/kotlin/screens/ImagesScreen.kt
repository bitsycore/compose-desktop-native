package screens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.layout.*
import com.compose.desktop.native.res.*
import androidx.compose.ui.unit.*
import composeresources.generated.*

// ==================
// MARK: Images / Resources screen
// ==================

@Composable
internal fun ImagesScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Images / Resources",
            "composeResources bundled next to the binary, loaded via generated Res.* accessors — " +
                "PNG, JPG, SVG, Android vector XML, and raw bytes.",
        )

        Section(
            "Formats",
            "Each loads from composeResources/drawable through the active renderer's decoder " +
                "(SDL3_image on Windows; Skia on macOS/Linux). SVG + Android XML are rasterised.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                LabeledImage("PNG · alpha", Res.drawable.compose_logo)
                LabeledImage("JPG", Res.drawable.photo)
                LabeledImage("SVG", Res.drawable.star)
                LabeledImage("Android XML", Res.drawable.heart)
            }
        }

        Section("ContentScale", "The same PNG inside a fixed 110 × 64 box (clipped)") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScaledImage("Fit", ContentScale.Fit)
                ScaledImage("Crop", ContentScale.Crop)
                ScaledImage("FillBounds", ContentScale.FillBounds)
            }
        }

        Section("alpha", "Per-image opacity via the alpha parameter") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for (vA in listOf(1f, 0.6f, 0.3f)) {
                    Image(
                        painter = Res.drawable.star,
                        contentDescription = "star at alpha $vA",
                        modifier = Modifier.size(48.dp),
                        alpha = vA,
                    )
                }
            }
        }

        Section("Raw bytes", "Res.readBytes(Res.files.notice) — no decoding, just the file") {
            val vText = remember { Res.readBytes(Res.files.notice)?.decodeToString() ?: "(resource missing)" }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    vText,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun LabeledImage(label: String, painter: Painter) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colors.background, RoundedCornerShape(8.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painter,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Text(label, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun ScaledImage(label: String, scale: ContentScale) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 64.dp)
                .background(MaterialTheme.colors.background, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Image(
                painter = Res.drawable.compose_logo,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = scale,
            )
        }
        Text(label, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}
