# Navigation 3 — implementation state

Status of getting **androidx.navigation3** running on this Kotlin/Native + SDL Compose port.
Read this before continuing the work.

## TL;DR

- **`navigation3-runtime` works on every target** (native K/N + JVM). The demo's
  **"Navigation 3" category ships and works** using the runtime only.
- **`navigation3-ui` (`NavDisplay`) does not work out of the box on Kotlin/Native**: it hangs
  before first paint. **Root cause: `androidx.lifecycle.compose.rememberLifecycleOwner` spins
  forever on K/N** (lifecycle-runtime-compose 2.11.0). It works fine on JVM.
- There is a **working stopgap** for native (`NavDisplay` renders) by replacing
  `rememberLifecycleOwner` with `LocalLifecycleOwner.current` in three manually-vendored files.
  **This is NOT the desired end state** — the goal is a *common* `NavDisplay` with a proper
  implementation (ideally a real lifecycle-runtime-compose that works on K/N), not per-file hacks.

## What works today (committed)

### The demo "Navigation 3" category — runtime only
`demo/src/commonMain/kotlin/screens/Navigation3Screen.kt` (shared, compiled by `:demo` native
AND `:demojvm`). It uses **only `navigation3-runtime`**:
- `NavBackStack<NavKey>` of sealed routes, push/pop.
- Renders the top of the stack with a plain `when` + `Crossfade` — **no `NavDisplay`, no bare
  `NavEntry.Content()`**.

Why not `NavDisplay` here: (a) no functional JVM-desktop `navigation3-ui` artifact exists for
`:demojvm` to compile against (androidx ships `jvmStubs`); (b) the native `NavDisplay` hangs (below).
Verified rendering on both native (SDL screenshot) and JVM (`./gradlew :demojvm:renderNav3`).

Shell contract that screens must follow (learned the hard way): `demo.shell.App` hosts each screen
in `Box(fillMaxSize().verticalScroll()).padding(24.dp)` and the app root is **not** a `Surface`.
So a screen is a plain flowing `Column` (NO `fillMaxSize`/own-`verticalScroll`/`weight`, which fight
the scroll host) and **sets text colors explicitly** (`color = MaterialTheme.colorScheme.onSurface`),
since `LocalContentColor` defaults to black without a `Surface`.

### Maven wiring
- `:ui` `api`-exposes `androidx.navigation3:navigation3-runtime:1.1.4` +
  `androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0` +
  `androidx.lifecycle:lifecycle-runtime-compose:2.11.0`. All publish every target we use
  (mingwX64 / macos / linux + `desktop`).
- `:demojvm` adds `androidx.navigation3:navigation3-runtime:1.1.4` (its `desktop` variant pulls
  `androidx.compose.runtime 1.9.x`, aligned with `org.jetbrains.compose 1.12.0-alpha03`).
- `ComposeWindow.kt` seeds a **resumed `LocalLifecycleOwner`** at the composition root
  (`LifecycleRegistry.createUnsafe(...).apply { currentState = RESUMED }`) — lifecycle-aware
  content errors without it. `LifecycleRegistry` core (addObserver/dispatch/sync) works fine on K/N.

### `:navigation3-ui` module (`navigation3/navigation3-ui`, artifactId `desktop-navigation3-ui`)
Vendored from the JetBrains fork like the compose modules (publishes per-target, like `:material3`).
- Manifest `navigation3/navigation3-ui/compose-fork.txt`: `selectRoot` + folder directives copy
  `commonMain` → `src/vendor/common`, `macosMain` → `src/vendor/native` (the K/N actuals for the 3
  `default*TransitionSpec` expects).
- `api("androidx.navigation3:navigation3-runtime:1.2.0-alpha05")` — the vendored UI is from a
  1.12-era fork that pairs with a 1.2.x runtime (1.1.4 compiles but is the wrong era).
- Compiles + (with the stopgap below) `NavDisplay` renders on native.

## The blocker — `rememberLifecycleOwner` spins on Kotlin/Native

Investigation (via a native-only diagnostic screen + prints in the vendored code and the SDL main
loop) established, in order:

1. Not infinite recomposition — the whole NavDisplay composition runs **exactly once**.
2. Not `AnimatedContent`, not `SeekableTransitionState`/`rememberTransition`/`snapshotFlow{isRunning}`
   — isolated minimal repros of each render fine on native.
3. Not the scene `do/while` loops in `SceneState.kt` (they converge for a single-entry stack).
4. **The SDL main loop stalls at `yield()` right after composition** — a post-composition coroutine
   spins without suspending. `println("LOOP: …")` in `ComposeWindow.kt` never fires.
5. Isolating `rememberLifecycleOwner(maxLifecycle = RESUMED)` alone → **hangs**, even with
   `parent = null`. `LifecycleRegistry.addObserver` on the seeded parent, by contrast, works
   (dispatches ON_CREATE/START/RESUME and returns).

So `androidx.lifecycle.compose.rememberLifecycleOwner` (source: it wires a `DisposableEffect`
observer + a `LaunchedEffect` that sets `maxLifecycleState`, driving a child `ComposeLifecycleOwner`'s
`LifecycleRegistry`) launches an effect that never suspends on K/N. It works on JVM — which is exactly
why "it should work on JVM too" holds. This is a **lifecycle-runtime-compose 2.11.0 Kotlin/Native
issue**, not something in this project's own code.

`NavDisplay` reaches `rememberLifecycleOwner` at **three** sites (all neutralized by the stopgap):
- `NavDisplay.kt` — `sceneLifecycleOwner` and `overlaySceneLifecycleOwner`.
- `scene/BackStackAwareLifecycleNavEntryDecorator.kt` — per-entry owner (on the hot path).
- `scene/DialogScene.kt` — dialog owner (only the dialog-scene path; patched for completeness).

## The stopgap (committed, but NOT the desired solution)

Per the project's manual-vendoring rule (CLAUDE.md rule #3), three files are copied out of
`src/vendor/` into `src/commonMain/kotlin/` and hand-edited to replace
`rememberLifecycleOwner(maxLifecycle = …)` with the ambient `LocalLifecycleOwner.current`:
- `navigation3-ui/src/commonMain/kotlin/androidx/navigation3/ui/NavDisplay.kt`
- `navigation3-ui/src/commonMain/kotlin/androidx/navigation3/scene/BackStackAwareLifecycleNavEntryDecorator.kt`
- `navigation3-ui/src/commonMain/kotlin/androidx/navigation3/scene/DialogScene.kt`

They carry a `MANUAL VENDOR` header, and the manifest has `!<path>` **exclusion** lines so the
folder sync skips them (and deletes any stale pre-exclusion copy) — no duplicate class. With this,
`NavDisplay` renders on native (verified: "NavDisplay home" paints, main loop runs).

**Cost of the stopgap:** entries stay `RESUMED` instead of being capped at `STARTED`/`CREATED` during
transitions / off the back stack. Nothing here depends on that cap, but it's a behavior change and a
per-file, non-idempotent edit that must be reconciled on every upstream ref bump.

## The proper solution (TODO — what "common NavDisplay" needs)

1. **Fix lifecycle on K/N (the real fix).** Determine why `rememberLifecycleOwner` /
   `ComposeLifecycleOwner`'s effect spins on Kotlin/Native in lifecycle-runtime-compose 2.11.0.
   Candidates: try other lifecycle versions (does an older/newer one behave?); pull the K/N
   `LifecycleRegistry` source and check `moveToState`/`sync`/main-thread-check on native; check the
   coroutine dispatcher the effect runs on. If it's a genuine upstream K/N bug, either provide a
   project actual/override or file it upstream. Fixing this deletes the stopgap entirely.
2. **Common `NavDisplay` usage in the shared demo screen.** Once native `NavDisplay` is sound,
   switch `Navigation3Screen.kt` to `entryProvider` + `NavDisplay` (built-in enter/exit + predictive
   back). For JVM, wire `:demojvm` to the JetBrains artifact
   `org.jetbrains.androidx.navigation3:navigation3-ui` (Maven Central; latest `1.2.0-alpha02`;
   publishes `desktop` + `macosArm64` + ios/js/wasm — but **no `mingwX64`/`linuxX64`**, and its klibs
   depend on `org.jetbrains.compose`, so it's JVM-only for us; native keeps using the vendored
   `:navigation3-ui`). Both provide `androidx.navigation3.ui.NavDisplay`, so one shared screen can
   compile against the artifact on JVM and the vendored module on native.
3. Align `navigation3-runtime` versions across `:ui` (1.1.4), `:demojvm` (1.1.4) and `:navigation3-ui`
   (1.2.0-alpha05) once the runtime era is settled.

## Tooling added to `tools/compose-fork/sync.py` (all committed, reusable)

- **`selectRoot=` + folder directives** (`src/ -> dest/`): one line vendors a whole source-set tree.
- **Auto-annotation on every sync** (idempotent): under each folder directive, commented
  `#     | src -> dest` lines list the files it copies; a trailing `# >>> GAPS … # <<< GAPS` block
  lists upstream `.kt` under `selectRoot` **not** yet vendored (new upstream files show up here).
  Uncomment any `#     | ` line to pin it as a per-file entry (the leading `|` is stripped by the
  parser). `--gaps`/`--fill` runs the annotation without copying.
- **`!<path>` exclusions**: skip a file from a folder copy (and delete a stale copy) — the mechanism
  behind the manual-vendor stopgap.

## Key files

- `demo/src/commonMain/kotlin/screens/Navigation3Screen.kt` — the shipped runtime-only screen.
- `demo/src/commonMain/kotlin/demo/registry/Registry.kt` — `navigation3` category (common).
- `navigation3/navigation3-ui/` — vendored `NavDisplay` module (+ stopgap patches + manifest).
- `compose/sdl/window/.../ComposeWindow.kt` — `LocalLifecycleOwner` seed.
- `demojvm/src/jvmMain/kotlin/RenderNav3.kt` + `renderNav3` Gradle task — headless JVM render for
  eyeballing the shared screen against upstream Compose.
