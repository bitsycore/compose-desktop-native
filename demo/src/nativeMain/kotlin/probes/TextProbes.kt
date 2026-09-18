import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.nativeComposeWindow
import utils.encodeBmpBgra32
import utils.writeFile

// ==================
// MARK: Text probes - font resolution, paragraph layout, metrics.
// ==================
//
// Split out of MainNative.kt, which held the entry point plus 20 scenarios in
// 1240 lines. Behaviour is unchanged; each probe is `internal` so main()'s
// argument dispatch still reaches it.

/** --fonttest: same sample in FontFamily.Default (sans) and FontFamily.Monospace.
   Monospace used to collapse to the default sans; now it renders NotoSansMono
   (bundled because this source references FontFamily.Monospace). */
internal fun runFontTest() {
    nativeComposeWindow(
        title = "fonttest",
        width = 560,
        height = 220,
        onFrame = { vBridge, vFrame ->
            if (vFrame >= 10) {
                val vSnap = vBridge.snapshotBgra()
                if (vSnap != null) {
                    val (vW, vH, vBgra) = vSnap
                    writeFile("fonttest.bmp", encodeBmpBgra32(vW, vH, vBgra))
                    println("fonttest: wrote fonttest.bmp (${vW}x${vH})")
                }
                false
            } else true
        },
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF202020)).padding(20.dp)) {
                val vSample = "Illegal1 lIO0 {}=>"
                Text(vSample, color = Color.White, fontSize = 30.sp)
                Text(
                    vSample, color = Color.Cyan, fontSize = 30.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}

/** Boots a real window, then builds an upstream Paragraph via the
   vendored factory (→ SkiaParagraph) for a long string constrained to a narrow width. Verifies it
   wrapped to multiple lines, has positive size, and that getHorizontalPosition/getOffsetForPosition
   round-trip - proving the paragraph-engine measurement bridge works. */
internal fun runParagraphTest() {
    nativeComposeWindow(
        title = "paragraphtest",
        width = 400,
        height = 200,
        onFrame = { _, frameIndex ->
            if (frameIndex == 10) {
                val vP = androidx.compose.ui.text.Paragraph(
                    text = "Hello world foo bar baz qux quux corge grault garply waldo",
                    style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = 120),
                    density = androidx.compose.ui.unit.Density(1f),
                    fontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                )
                val vHpos = vP.getHorizontalPosition(3, true)
                val vOffBack = vP.getOffsetForPosition(androidx.compose.ui.geometry.Offset(vHpos, 2f))
                val vLine = vP.getLineForOffset(30)
                println("paragraphtest: lineCount=${vP.lineCount} w=${vP.width} h=${vP.height} hpos(3)=$vHpos offBack=$vOffBack lineFor(30)=$vLine")
                val vPass = vP.lineCount >= 2 && vP.height > 0f && vP.width > 0f && vOffBack in 2..4
                println(if (vPass) "paragraphtest: PASS (real width-wrapped Paragraph via SkiaParagraph)" else "paragraphtest: FAIL")
                false
            } else true
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {}
    }
}

/** Prints paragraph metrics (single-line cell, lineHeight-styled single + triple line,
   first baseline) for a font-size sweep at density 1 - the native half of the
   metrics-alignment probe. Mirror of MainJvm's `--metrics`; both must print the same
   numbers for the parity text drift to vanish. */
internal fun runMetricsProbe() {
    fun paragraph(inText: String, inSize: Int, inLineHeight: Int?, inM3Style: Boolean): androidx.compose.ui.text.Paragraph =
        androidx.compose.ui.text.Paragraph(
            text = inText,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = inSize.sp,
                lineHeight = inLineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                lineHeightStyle = if (inM3Style) {
                    androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                    )
                } else null,
            ),
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = 10_000),
            density = androidx.compose.ui.unit.Density(1f),
            fontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(),
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        )
    nativeComposeWindow(
        title = "metricsprobe",
        width = 300,
        height = 200,
        onFrame = { _, frameIndex ->
            if (frameIndex == 10) {
                for (vSize in listOf(11, 12, 14, 16, 22, 24)) {
                    val vLh = vSize + 6
                    val vCell = paragraph("Hg", vSize, null, false)
                    val vOne = paragraph("Hg", vSize, vLh, false)
                    val vThree = paragraph("Hg\nHg\nHg", vSize, vLh, false)
                    val vOneM3 = paragraph("Hg", vSize, vLh, true)
                    val vThreeM3 = paragraph("Hg\nHg\nHg", vSize, vLh, true)
                    println(
                        "metrics: size=$vSize lh=$vLh cell=${vCell.height} " +
                            "one=${vOne.height} three=${vThree.height} " +
                            "base1=${vOne.firstBaseline} base3=${vThree.lastBaseline} " +
                            "oneM3=${vOneM3.height} threeM3=${vThreeM3.height} base1M3=${vOneM3.firstBaseline}"
                    )
                }
                for ((vS, vL) in listOf(24 to 24, 24 to 25, 32 to 24, 16 to 16)) {
                    val vB = paragraph("Hg", vS, vL, true)
                    println("metrics: boundary $vS/$vL m3=${vB.height} base=${vB.firstBaseline}")
                }
                val vBig = paragraph("42", 48, 24, false)
                val vBigM3 = paragraph("42", 48, 24, true)
                println("metrics: big48/lh24 raw=${vBig.height} base=${vBig.firstBaseline} m3=${vBigM3.height} baseM3=${vBigM3.firstBaseline}")
                println("metricsprobe: DONE")
                false
            } else true
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {}
    }
}

// ==================
// MARK: clicktest
// ==================
