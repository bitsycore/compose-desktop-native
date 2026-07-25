package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import demo.generated.resources.*
import org.jetbrains.compose.resources.painterResource

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
            "Each loads from composeResources/drawable through Skia's image codecs " +
                "(PNG / JPG / WEBP); SVG + Android XML are rasterised.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                LabeledImage("PNG · alpha", painterResource(Res.drawable.compose_logo))
                LabeledImage("JPG", painterResource(Res.drawable.photo))
                LabeledImage("SVG", painterResource(Res.drawable.star))
                LabeledImage("Android XML", painterResource(Res.drawable.heart))
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
                        painter = painterResource(Res.drawable.star),
                        contentDescription = "star at alpha $vA",
                        modifier = Modifier.size(48.dp),
                        alpha = vA,
                    )
                }
            }
        }

        Section("Raw bytes", "Res.readBytes(\"files/notice.txt\") — no decoding, just the file") {
            var vText by remember { mutableStateOf("(loading…)") }
            LaunchedEffect(Unit) {
                vText = runCatching { Res.readBytes("files/notice.txt").decodeToString() }
                    .getOrElse { "(resource missing)" }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    vText,
                    color = MaterialTheme.colorScheme.onBackground,
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
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
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
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
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
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Image(
                painter = painterResource(Res.drawable.compose_logo),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = scale,
            )
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}
