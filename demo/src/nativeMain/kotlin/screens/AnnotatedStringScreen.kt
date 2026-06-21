package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.FontStyle
import androidx.compose.ui.text.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: AnnotatedStringScreen
// ==================

/* Exercises Text(AnnotatedString): each contiguous-style run is rendered
   as its own BasicText in a Row. Per-run color / font size / font
   weight (via FontVariation.Weight) apply directly; backgrounds tint
   via Modifier.background; underline / line-through paint via
   drawBehind beneath each run. */
@Composable
internal fun AnnotatedStringScreen() {
	val vOnSurface = MaterialTheme.colors.onSurface
	val vCardBg = MaterialTheme.colors.surface
	val vCard: @Composable (@Composable () -> Unit) -> Unit = { vContent ->
		Box(modifier = Modifier.background(vCardBg, RoundedCornerShape(6.dp))) {
			Box(modifier = Modifier.padding(12.dp)) { vContent() }
		}
	}

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"AnnotatedString + Text(AnnotatedString)",
			"Each contiguous-style segment renders as a separate BasicText in a horizontal Row. " +
				"Single-line — no soft-wrap across runs. Per-glyph multi-line layout would need a " +
				"custom Layout that walks the runs and breaks them by width.",
		)

		// ============
		//  Mixed colour + weight + decoration
		val vMix = remember {
			buildAnnotatedString {
				append("This sentence has ")
				pushStyle(SpanStyle(color = Color(0xFF6200EE), fontWeight = FontWeight.Bold))
				append("bold purple")
				pop()
				append(" and ")
				pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
				append("italic")
				pop()
				append(" and ")
				pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
				append("underlined")
				pop()
				append(" runs.")
			}
		}
		Section("Mixed color + weight + underline", "Three SpanStyles applied to disjoint runs.") {
			vCard { Text(vMix, color = vOnSurface, fontSize = 16.sp) }
		}

		// ============
		//  Font weight sweep
		val vWeights = remember {
			buildAnnotatedString {
				val vWeights = listOf(
					"Thin" to FontWeight.Thin,
					"Light" to FontWeight.Light,
					"Normal" to FontWeight.Normal,
					"Medium" to FontWeight.Medium,
					"SemiBold" to FontWeight.SemiBold,
					"Bold" to FontWeight.Bold,
					"Black" to FontWeight.Black,
				)
				for ((vI, vW) in vWeights.withIndex()) {
					pushStyle(SpanStyle(fontWeight = vW.second))
					append(vW.first)
					pop()
					if (vI < vWeights.size - 1) append("  ")
				}
			}
		}
		Section("FontWeight sweep", "Thin / Light / Normal / Medium / SemiBold / Bold / Black — uses the wght FontVariation under the hood.") {
			vCard { Text(vWeights, color = vOnSurface, fontSize = 18.sp) }
		}

		// ============
		//  Font size sweep
		val vSizes = remember {
			buildAnnotatedString {
				pushStyle(SpanStyle(fontSize = 12.sp))
				append("small")
				pop()
				append(" ")
				pushStyle(SpanStyle(fontSize = 18.sp))
				append("medium")
				pop()
				append(" ")
				pushStyle(SpanStyle(fontSize = 28.sp))
				append("LARGE")
				pop()
			}
		}
		Section("Per-run fontSize", "SpanStyle.fontSize overrides the default size for each run.") {
			vCard { Text(vSizes, color = vOnSurface, fontSize = 16.sp) }
		}

		// ============
		//  Background tint
		val vCodeLike = remember {
			buildAnnotatedString {
				append("Call ")
				pushStyle(SpanStyle(
					background = Color(0xFF2A2A2A),
					color = Color(0xFF03DAC6),
				))
				append(" rememberMutableInteractionSource() ")
				pop()
				append(" to bind state.")
			}
		}
		Section("Background tint", "SpanStyle.background paints a coloured rectangle under the run.") {
			vCard { Text(vCodeLike, color = vOnSurface, fontSize = 14.sp) }
		}

		// ============
		//  Hyperlink-style
		val vLink = remember {
			buildAnnotatedString {
				append("Read the docs at ")
				pushStyle(SpanStyle(
					color = Color(0xFF03DAC6),
					textDecoration = TextDecoration.Underline,
				))
				append("compose.dev/docs")
				pop()
				append(" for more.")
			}
		}
		Section("Hyperlink-style run", "Colour + underline combined.") {
			vCard { Text(vLink, color = vOnSurface, fontSize = 16.sp) }
		}

		// ============
		//  Line-through
		val vStrike = remember {
			buildAnnotatedString {
				append("Old price ")
				pushStyle(SpanStyle(
					textDecoration = TextDecoration.LineThrough,
					color = Color(0xFFAAAAAA),
				))
				append("\$19.99")
				pop()
				append(" -> ")
				pushStyle(SpanStyle(
					color = Color(0xFF6200EE),
					fontWeight = FontWeight.Bold,
				))
				append("\$9.99")
				pop()
			}
		}
		Section("Line-through + bold accent", "TextDecoration.LineThrough on the old price, bold accent on the new.") {
			vCard { Text(vStrike, color = vOnSurface, fontSize = 16.sp) }
		}
	}
}
