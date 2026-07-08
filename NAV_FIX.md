# NAV_FIX — Navigation3 NavDisplay freeze on mingwX64 (investigation log)

Symptom: composing the vendored `androidx.navigation3.ui.NavDisplay` freezes the
app on mingwX64 (JVM twin with the official artifacts works). Window opens,
first frame never completes, process sits at **0% CPU** (blocked, not looping).

## Timeline / findings

1. **Reproduced**: `demo.exe --screenshot=x.bmp --screen=Navigation3` → no
   frame, no output, timeout. 0 CPU over a 3s window → deadlock, not a
   recompose/measure loop.
2. **Vendor hygiene fix on the way** (unrelated to freeze): stale files in
   `src/vendor/` after manifest dest renames (`ArcSpline.native.kt` vs
   `.nonJvm.kt`) broke the build — `sync.py` now deletes vendor `.kt` files the
   manifest no longer produces ("N stale removed").
3. **println bisection** (markers in vendored NavDisplay.kt — user-approved,
   restored by resync):
   - The WHOLE NavDisplay composition completes (all markers incl. inside the
     AnimatedContent content lambda and after it).
   - The freeze is INSIDE the window `Composition.setContent(...)` **apply
     tail** — after the content lambda, before setContent returns (that's where
     RememberObserver callbacks / movable-content inserts run).
   - Loop-stage markers in ComposeWindow never print → the main loop never
     regains control.
4. **Ablation results** (each = full rebuild + run):
   - B: bypass `transition.AnimatedContent`, compose scene directly → FREEZES.
   - D: also bypass `rememberNavigationEventState` + `NavigationBackHandler`
     (sceneState + `scene.content()` only) → FREEZES.
   - E: `rememberDecoratedNavEntries` + `entries.last().Content()` (no
     sceneState at all) → **WORKS** (screenshot, clean exit).
   - D3: create `rememberSceneState(...)` but render the raw entry → **WORKS**
     (so sceneState CREATION incl. `rememberSceneSetupNavEntryDecorator`'s
     `movableContentOf` creation is fine).
   - F: full pipeline with `SceneSetupNavEntryDecorator`'s
     `movableContent { entry.Content() }` bypassed → still FREEZES (movable is
     not the culprit).
   - **G: raw entry render + ONLY `rememberLifecycleOwner(maxLifecycle =
     RESUMED)` around it → FREEZES.** ← culprit isolated.

## Root-cause hypothesis (being fixed)

`rememberLifecycleOwner` (lifecycle-runtime-compose, official Maven klib)
creates a child `LifecycleRegistry(this)` — the STRICT variant (unlike the
`createUnsafe` one our ComposeWindow root provides). On KMP/native its
main-thread enforcement checks the current thread against `Dispatchers.Main`
via the `MainDispatcherChecker` pattern — a round-trip through
`Dispatchers.Main.immediate`.

Our `Sdl3MainDispatcher`:
- `immediate` returns `this` (no true immediate),
- `isDispatchNeeded` = default `true` (ALWAYS queue),
- the queue drains ONLY at `drainPending()` inside the SDL loop.

So any blocking round-trip through `Dispatchers.Main(.immediate)` made FROM the
main thread while the loop is inside `setContent` can never complete → the
apply tail parks forever at 0 CPU. NavDisplay is simply the first code path
that hits it (`rememberLifecycleOwner` in the scene lifecycle plumbing +
BackStackAwareLifecycleNavEntryDecorator).

## Fix (in progress)

Make `Sdl3MainDispatcher` behave like real platform Main dispatchers
(Android/Swing):
- capture the SDL main thread id at construction,
- `immediate` = a separate `MainCoroutineDispatcher` sharing the queue whose
  `isDispatchNeeded(context)` returns **false when already on the main
  thread** (runs inline), true otherwise,
- the BASE dispatcher keeps `isDispatchNeeded = true` (queued via
  drainPending) so LaunchedEffect/recomposer ordering is unchanged.

This is a project-side fix — **no vendored-file edits needed** (user
constraint: keep nav3 vendored verbatim).

## RESOLVED — fix confirmed

`Sdl3MainDispatcher.immediate` is now a real immediate dispatcher: a separate
`MainCoroutineDispatcher` sharing the queue whose `isDispatchNeeded()` compares
`SDL_GetCurrentThreadID()` against the main thread id captured at construction
— inline on the main thread, queued from any other. The base dispatcher keeps
always-queue semantics (LaunchedEffect/recomposer ordering unchanged).

With ONLY that change and the nav3 vendor tree PRISTINE (clean resync, zero
edits), `--screen=Navigation3 --screenshot` renders the NavDisplay-driven
screen and exits cleanly. Full probe suite (click/toggle/key/back/scroll/
shared/paragraph/multiwin/dialoganim/animvis/searchesc) PASS.

Cleanup done:
- [x] DBGN markers + experiments removed (nav3 module resynced verbatim).
- [x] DBGW markers removed from ComposeWindow.kt (diff-clean vs HEAD).
- [x] Full probe suite re-run — all PASS.
- [x] Navigation3 screen verified by screenshot (NavDisplay pipeline).
- [ ] Interactive check (user): push detail, pop with button + ESC,
      predictive-back path; then this file can be deleted.

Bonus fix that surfaced on the way: `sync.py` now deletes `.kt` files under
`src/vendor/` that the manifest no longer produces (stale `ArcSpline.native.kt`
etc. after dest renames broke the build).
