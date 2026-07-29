package com.compose.sdl

import androidx.lifecycle.enableSavedStateHandles

// ==================
// MARK: WindowArchitectureOwner
// ==================

/** Per-window architecture-components owner, modeled on upstream desktop's
   DefaultArchitectureComponentsOwner (compose/ui skikoMain
   PlatformOwnerProvider.skiko.kt): one object implements LifecycleOwner +
   ViewModelStoreOwner + SavedStateRegistryOwner (+ the SavedState-aware
   default ViewModel factory), so viewModel(), SavedStateHandle and
   rememberSaveable-backed registries all resolve against the WINDOW scope.

   The lifecycle registry uses createUnsafe (no main-thread enforcement) —
   same as the root owner this replaces; the SDL loop is single-threaded
   anyway. RESUMED from construction; destroy() moves to DESTROYED and clears
   the ViewModelStore (onCleared runs). SavedState restores from nothing (no
   process-death persistence on desktop — upstream desktop passes null too). */
internal class WindowArchitectureOwner :
	androidx.lifecycle.LifecycleOwner,
	androidx.lifecycle.ViewModelStoreOwner,
	androidx.lifecycle.HasDefaultViewModelProviderFactory,
	androidx.savedstate.SavedStateRegistryOwner {

	override val lifecycle = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
	override val viewModelStore = androidx.lifecycle.ViewModelStore()

	private val savedStateController = androidx.savedstate.SavedStateRegistryController.create(this)
	override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
		get() = savedStateController.savedStateRegistry

	override val defaultViewModelProviderFactory = androidx.lifecycle.SavedStateViewModelFactory()
	override val defaultViewModelCreationExtras: androidx.lifecycle.viewmodel.CreationExtras
		get() = androidx.lifecycle.viewmodel.MutableCreationExtras().also {
			it[androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY] = this
			it[androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY] = this
		}

	init {
		savedStateController.performAttach()
		savedStateController.performRestore(null)
		// SavedStateHandle support for WINDOW-scoped ViewModels — must run while
		// the lifecycle is still ≤ CREATED; upstream desktop's ComposeContainer
		// calls this at the same point. With it, `viewModel { ... }` against the
		// window owner (the activityViewModels() analog) can take a
		// SavedStateHandle instead of needing a saved-state-less child owner.
		enableSavedStateHandles()
		// CREATED (not RESUMED) until the first composition is done — code that
		// runs enableSavedStateHandles() during composition (nav3's decorators,
		// rememberViewModelStoreOwner, …) requires INITIALIZED/CREATED, and
		// upstream desktop windows likewise compose first and resume after.
		lifecycle.currentState = androidx.lifecycle.Lifecycle.State.CREATED
	}

	/** Focus/visibility-driven state (see WindowInstance.onActivationEvent).
	   Ignored once destroyed — a stray SDL event during teardown must not
	   resurrect the registry. */
	fun setLifecycleState(inState: androidx.lifecycle.Lifecycle.State) {
		if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED &&
			lifecycle.currentState != inState
		) {
			lifecycle.currentState = inState
		}
	}

	fun destroy() {
		lifecycle.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
		viewModelStore.clear()
	}
}
