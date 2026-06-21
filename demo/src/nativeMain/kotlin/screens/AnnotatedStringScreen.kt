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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================
// MARK: AnnotatedStringScreen
// ==================

/* Exercises the AnnotatedString / SpanStyle / TextStyle / FontWeight /
   FontStyle / TextDecoration / TextOverflow type surface. The current
   text renderer only honours a SINGLE style (the default) per Text
   composable — multi-span styling is a follow-up that touches the
   Skia and SDL3 text paths. This screen mostly shows the API
   compiles and the builder constructs the right objects; the runtime
   text below each label is the plain backing string. */
@Composable
internal fun AnnotatedStringScreen() {
	val vPrimary = MaterialTheme.colors.primary
	val vSecondary = MaterialTheme.colors.secondary
	val vOnSurface = MaterialTheme.colors.onSurface

	val vAnnotated = remember {
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

	val vHeading = remember {
		TextStyle(
			color = Color(0xFF03DAC6),
			fontSize = 22.sp,
			fontWeight = FontWeight.SemiBold,
		)
	}

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle(
			"AnnotatedString + TextStyle (API only)",
			"AnnotatedString with SpanStyle / ParagraphStyle, buildAnnotatedString { } DSL, " +
				"TextStyle aggregate, FontWeight / FontStyle / FontFamily / TextDecoration / " +
				"TextOverflow. Renderer integration for multi-span text is a TODO — the Text " +
				"below renders the backing String only, not the styled runs.",
		)

		Section("Backing string from buildAnnotatedString", "The .text of the built AnnotatedString.") {
			Box(modifier = Modifier
				.background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp))
			) {
				Box(modifier = Modifier.padding(12.dp)) {
					Text(vAnnotated.text, color = vOnSurface, fontSize = 14.sp)
				}
			}
		}

		Section(
			"Recorded span styles (${vAnnotated.spanStyles.size})",
			"Each entry is a Range<SpanStyle> the renderer will eventually consult.",
		) {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				for (vR in vAnnotated.spanStyles) {
					val vText = vAnnotated.text.substring(vR.start, vR.end)
					Text("[${vR.start}..${vR.end}) \"$vText\" → ${vR.item}",
						color = vOnSurface, fontSize = 12.sp)
				}
			}
		}

		Section(
			"TextStyle constants",
			"FontWeight.Bold = ${FontWeight.Bold.weight}, .Medium = ${FontWeight.Medium.weight}. " +
				"vHeading = $vHeading.",
		) {
			Text(
				"Default TextStyle: ${TextStyle.Default}",
				color = vOnSurface,
				fontSize = 12.sp,
			)
		}
	}
}
