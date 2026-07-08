package screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay

// ==================
// MARK: Navigation 3 screen (shared — native + JVM)
// ==================
// Demonstrates androidx.navigation3-runtime with NO renderer-specific code: a
// NavBackStack of NavKey routes, pushed/popped by the UI, with the top of the stack
// driving what's shown.
//
// It does NOT use navigation3-ui's NavDisplay nor call NavEntry.Content() directly:
// navigation3-ui has no functional JVM-desktop artifact (androidx ships jvmStubs), the
// vendored-from-fork NavDisplay doesn't settle on the native reimpl, and a bare
// NavEntry.Content() (outside NavDisplay's decorator pipeline) misbehaves. So we dispatch
// on the route with a plain `when` — the same pattern every other shared screen uses.
//
// Shell contract (demo.shell.App): each screen is hosted in a
// Box(fillMaxSize().verticalScroll()) — so a screen is a plain Column that flows
// naturally (NO fillMaxSize / own verticalScroll / weight, which fight the scroll host),
// and since the app root isn't a Surface, text sets its color explicitly.

// Routes. NavKey marks a type usable as a navigation3 back-stack destination.
private sealed interface Nav3Route : NavKey
private data object Nav3Home : Nav3Route
private data class Nav3Detail(val id: Int) : Nav3Route

@Composable
fun Navigation3Screen() {
	// The navigation3 back stack: a snapshot-backed list of NavKey routes. Push = add,
	// pop = removeLastOrNull. Snapshot writes recompose the display below.
	val backStack = remember { NavBackStack<Nav3Route>(Nav3Home) }
	val current = backStack.lastOrNull() ?: Nav3Home
	val canGoBack = backStack.size > 1

	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		// Header: back affordance + live back-stack depth.
		Row(verticalAlignment = Alignment.CenterVertically) {
			if (canGoBack) {
				FilledTonalButton(onClick = { backStack.removeLastOrNull() }) { Text("‹ Back") }
				Spacer(Modifier.width(12.dp))
			}
			Text(
				"Navigation 3 — depth ${backStack.size}",
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		HorizontalDivider()
		NavDisplay(
			backStack = backStack,
		) { nav3Route ->
			when(nav3Route) {
				Nav3Home -> NavEntry(nav3Route) {
					Nav3HomeContent(onOpen = { id -> backStack.add(Nav3Detail(id)) })
				}
                is Nav3Detail -> NavEntry(nav3Route) {
					Nav3DetailContent(id = nav3Route.id, onBack = { backStack.removeLastOrNull() })
				}
            }
		}
	}
}

// Home destination — a list; tapping a card pushes a Detail route.
@Composable
private fun Nav3HomeContent(onOpen: (Int) -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			"Home",
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Text(
			"NavBackStack of NavKey routes; tapping an item pushes a Nav3Detail onto the " +
				"stack, Back pops it. This same code drives the SDL/native renderer and " +
				"JVM/upstream Compose.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		for (id in 1..10) {
			Card(
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onOpen(id) },
			) {
				Row(
					Modifier.fillMaxWidth().padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						"Open detail #$id  ›",
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurface,
					)
				}
			}
		}
	}
}

// Detail destination — reads its id off the route instance, pops on Back.
@Composable
private fun Nav3DetailContent(id: Int, onBack: () -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text(
			"Detail #$id",
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Text(
			"You navigated here by pushing Nav3Detail($id) onto the back stack. " +
				"The header's Back button (or this one) pops it.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Button(onClick = onBack) { Text("Go back") }
	}
}
