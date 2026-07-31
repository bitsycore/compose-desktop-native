@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.sdl.icons.IconFontIcon
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import com.compose.sdl.text.IconText

// Foundation — AnimatedVisibility, BoxWithConstraints, desktop scrollbars.
@Composable
internal fun FoundationExtraScreen() {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ScreenTitle("Foundation extra", "AnimatedVisibility, BoxWithConstraints, VerticalScrollbar.")

		Section("AnimatedVisibility", "Enter = fade + expand; exit = fade + shrink") {
			var vShown by remember { mutableStateOf(true) }
			// One unspaced Column child: the Section column's spacedBy(10) would
			// otherwise keep a NON-animated gap around the AnimatedVisibility node
			// and drop it abruptly when the node unmounts at exit end (inherent
			// upstream behaviour — spacing applies to zero-height children too).
			// The gap lives INSIDE the animated content instead, so it expands and
			// shrinks with it.
			Column {
				Button(onClick = { vShown = !vShown }) { Text(if (vShown) "Hide" else "Show") }
				AnimatedVisibility(
					visible = vShown,
					enter = fadeIn() + expandVertically(),
					exit = fadeOut() + shrinkVertically(),
				) {
					Box(
						modifier = Modifier
							.padding(top = 10.dp)
							.fillMaxWidth()
							.height(60.dp)
							.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp)),
					) {
						Text(
							"Animated content",
							modifier = Modifier.padding(14.dp),
							color = MaterialTheme.colorScheme.onTertiaryContainer,
						)
					}
				}
			}
		}

		Section("BoxWithConstraints", "Reads its own incoming constraints during composition") {
			BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
				val vMaxW = this.maxWidth
				val vWide = vMaxW > 500.dp
				Column {
					Text("maxWidth = $vMaxW", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
					Text(
						if (vWide) "Wide layout branch (> 500dp)" else "Narrow layout branch (≤ 500dp)",
						fontSize = 13.sp,
						color = MaterialTheme.colorScheme.primary,
					)
				}
			}
		}

		Section("VerticalScrollbar / HorizontalScrollbar", "Desktop scrollbars bound to scroll states") {
			val vScroll = rememberScrollState()
			Row(modifier = Modifier.height(140.dp)) {
				Column(
					modifier = Modifier.weight(1f).verticalScroll(vScroll),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					repeat(20) { vIdx ->
						Text("Scrollable row $vIdx", color = MaterialTheme.colorScheme.onSurface)
					}
				}
				VerticalScrollbar(adapter = rememberScrollbarAdapter(vScroll))
			}
			val vHScroll = rememberScrollState()
			Column {
				Row(modifier = Modifier.fillMaxWidth().horizontalScroll(vHScroll)) {
					repeat(30) { vIdx ->
						Text("col$vIdx  ", color = MaterialTheme.colorScheme.onSurface)
					}
				}
				HorizontalScrollbar(adapter = rememberScrollbarAdapter(vHScroll))
			}
		}

		Section("SharedTransitionLayout", "sharedElement morphs the box between its two states") {
			SharedTransitionLayout {
				var vBig by remember { mutableStateOf(false) }
				// Unspaced Column, gap carried inside each AnimatedVisibility — see
				// the AnimatedVisibility section above for why (spacedBy around an
				// AV node collapses non-animated when the node unmounts).
				Column {
					Button(onClick = { vBig = !vBig }) { Text(if (vBig) "Shrink" else "Grow") }
					AnimatedVisibility(visible = !vBig) {
						Box(
							modifier = Modifier
								.padding(top = 8.dp)
								.sharedElement(
									sharedContentState = rememberSharedContentState(key = "stBox"),
									animatedVisibilityScope = this@AnimatedVisibility,
								)
								.size(48.dp)
								.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
						)
					}
					AnimatedVisibility(visible = vBig) {
						Box(
							modifier = Modifier
								.padding(top = 8.dp)
								.sharedElement(
									sharedContentState = rememberSharedContentState(key = "stBox"),
									animatedVisibilityScope = this@AnimatedVisibility,
								)
								.size(140.dp)
								.background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(20.dp)),
						)
					}
				}
			}
		}

		Section("IconFontIcon / IconText", "The project glyph primitives Material Symbols builds on") {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				IconFontIcon(
					codepoint = MaterialSymbols.Home,
					fontFamily = MaterialSymbolsOutlined.Family,
					contentDescription = "home",
					tint = MaterialTheme.colorScheme.primary,
				)
				IconText(
					text = "",  // Home codepoint, drawn straight through IconText
					fontFamily = MaterialSymbolsOutlined.Family,
					color = MaterialTheme.colorScheme.tertiary,
					fontSize = 24.sp,
				)
			}
		}
	}
}
