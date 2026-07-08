package screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

// ==================
// MARK: Navigation 3 screen (shared — native + JVM)
// ==================
// Demonstrates androidx.navigation3-runtime + the vendored navigation3-ui NavDisplay:
// a NavBackStack of NavKey routes, pushed/popped by the UI, rendered through the
// full NavDisplay pipeline (scenes, decorators, predictive-back plumbing).
//
// This USED to freeze on native: androidx.lifecycle's KMP LifecycleRegistry
// enforces main-thread access through Dispatchers.Main.immediate, and the SDL
// Main dispatcher had no true immediate — the check deadlocked inside
// setContent. Fixed in Sdl3MainDispatcher (immediate runs inline on the SDL
// main thread); see NAV_FIX.md for the full investigation.
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

	// Screen-level store owner + the ViewModel shared by ALL detail entries.
	// Obtained OUTSIDE the NavDisplay entries, so it isn't per-entry scoped —
	// the entries capture the same instance through their content lambdas.
	// savedStateRegistryOwner = null is REQUIRED here: the default (the window's
	// registry owner) is legal only while its lifecycle is INITIALIZED/CREATED —
	// getOrCreateOwner throws by contract past that. A screen composed on demand
	// (sidebar click) runs at RESUMED, so saved-state support must be opted out;
	// the shared totals ViewModel doesn't need a SavedStateHandle anyway.
	val sharedOwner = androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner(
		savedStateRegistryOwner = null,
	)
	val totals = androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = sharedOwner) {
		Nav3TotalsViewModel()
	}

	// Console trace of the WINDOW lifecycle (driven by SDL focus/visibility:
	// focused → RESUMED, unfocused → STARTED, minimised/hidden → CREATED).
	// Adding the observer replays catch-up events (ON_CREATE/START/RESUME) at
	// screen open, then live transitions print as you focus/minimise the window.
	val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
	androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
		val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
			println("Navigation3 demo: window lifecycle $event -> ${lifecycleOwner.lifecycle.currentState}")
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	Column(
		modifier = Modifier.fillMaxWidth().clipToBounds(),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		// Header: back affordance + live back-stack depth.
		Row(verticalAlignment = Alignment.CenterVertically) {
			AnimatedVisibility (
				canGoBack,
				enter = expandIn { IntSize.Zero } + fadeIn(),
				exit = shrinkOut { IntSize.Zero } + fadeOut()
			) {
				Row {
					FilledTonalButton(onClick = { backStack.removeLastOrNull() }) { Text("‹ Back") }
					Spacer(Modifier.width(12.dp))
				}
			}
			Text(
				"Navigation 3 — depth ${backStack.size}",
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		HorizontalDivider()
		NavDisplay(
			// Order matters: the saveable decorator must wrap the viewmodel one —
			// it provides each entry's own SavedStateRegistryOwner (still
			// INITIALIZED) that enableSavedStateHandles() requires; reversed, the
			// VM decorator captures the window's registry owner (already RESUMED)
			// and throws. Same order as upstream's samples.
			entryDecorators = listOf(
				rememberSaveableStateHolderNavEntryDecorator(),
				rememberViewModelStoreNavEntryDecorator(),
			),
			backStack = backStack,

			// Push: new screen from right
			transitionSpec = {
				slideInHorizontally(
					animationSpec = tween(300),
					initialOffsetX = { it }
				) + fadeIn() togetherWith
				slideOutHorizontally(
					animationSpec = tween(300),
					targetOffsetX = { -it }
				) + fadeOut()
			},

			// Pop: previous screen from left
			popTransitionSpec = {
				slideInHorizontally(
					animationSpec = tween(300),
					initialOffsetX = { -it }
				) + fadeIn() togetherWith
					slideOutHorizontally(
						animationSpec = tween(300),
						targetOffsetX = { it }
					) + fadeOut()
			},
		) { nav3Route ->
			when (nav3Route) {
				Nav3Home -> NavEntry(nav3Route) {
					Nav3HomeContent(
						totals = totals,
						onOpen = { id -> backStack.add(Nav3Detail(id)) }
					)
				}

				is Nav3Detail -> NavEntry(nav3Route) {
					Nav3DetailContent(
						id = nav3Route.id,
						totals = totals,
						onBack = { backStack.removeLastOrNull() }
					)
				}
			}
		}
	}
}

// Home destination — a list; tapping a card pushes a Detail route.
@Composable
private fun Nav3HomeContent(totals: Nav3TotalsViewModel, onOpen: (Int) -> Unit) {
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
		Text(
			"Total increments across ALL details: ${totals.total} — one shared ViewModel " +
				"(screen-scoped store owner), unlike the per-entry counters below.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.tertiary,
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

// Per-entry ViewModel: created by the first composition of a Nav3Detail entry
// (scoped by rememberViewModelStoreNavEntryDecorator to the ENTRY, not the
// screen), survives recompositions and revisits while the entry stays on the
// back stack, and is cleared (onCleared) when the entry pops.
private class Nav3DetailViewModel : androidx.lifecycle.ViewModel() {
	var counter by androidx.compose.runtime.mutableStateOf(0)
}

// SHARED ViewModel: one instance for ALL detail entries. It lives in a store
// owner scoped to the Navigation3Screen call site (rememberViewModelStoreOwner,
// parented by the window's owner) — pushing/popping details never touches it,
// so it accumulates the total across every Nav3Detail.
private class Nav3TotalsViewModel : androidx.lifecycle.ViewModel() {
	var total by androidx.compose.runtime.mutableStateOf(0)
}

// Detail destination — reads its id off the route instance, pops on Back.
@Composable
private fun Nav3DetailContent(id: Int, totals: Nav3TotalsViewModel, onBack: () -> Unit) {
	val vm = androidx.lifecycle.viewmodel.compose.viewModel { Nav3DetailViewModel() }
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
		Text(
			"ViewModel counter: ${vm.counter} — lives in this entry's ViewModelStore " +
				"(viewModelStoreNavEntryDecorator); popping the entry clears it.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.primary,
		)
		Text(
			"Shared total across all details: ${totals.total} — the SAME " +
				"Nav3TotalsViewModel instance every detail entry sees.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.tertiary,
		)
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Button(onClick = { vm.counter++; totals.total++ }) { Text("Increment") }
			Button(onClick = onBack) { Text("Go back") }
		}
	}
}
