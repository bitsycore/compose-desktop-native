# Skiko on mingwX64 — Feasibility & Effort Assessment

Investigation of the effort to run the real Skia leg (`org.jetbrains.skiko`)
on the Kotlin/Native **mingwX64** target, under the relaxed constraint that a
**DLL is acceptable** (the static / no-DLL invariant may be dropped). Companion
to [RENDERER.md](RENDERER.md) §4 "Track A" — which this assessment **partly
supersedes** (see [Corrections](#corrections-to-renderermd-4) below).

Pins at time of writing: skiko `0.150.1` (Skia m150), kotlin `2.4.0`,
compose `1.12.0-beta02`.

---

## Bottom line

Feasible — and **more tractable than RENDERER.md §4 claims**. Two of that
section's load-bearing premises are now factually stale:

1. **"Building C++20 Skia to a GNU/mingw archive is an open-ended fork (only
   abandoned Mozilla-era precedent)."** — **False.** MSYS2 maintains
   `mingw-w64-skia` at **Skia milestone 143 (C++20)** for `mingw64` (GCC),
   `ucrt64`, and `clang64` (clang → `x86_64-w64-windows-gnu`), producing
   `libskia.dll` + a GNU import lib `libskia.dll.a`. Last rebuilt **2026-07-24**.
   The GNU-ABI recipe is ~8 small GN patches (the key one adds
   `is_mingw = is_win && (cxx=="g++"||cxx=="clang++")`).
2. **"The only working route is a runtime DLL, which breaks the no-DLL
   invariant."** — The DLL route is real, but the reason it works is **not**
   what the section implies. See the crux below.

**Effort:** ~**1–2 weeks** to a proven spike + a CPU-raster Skia-on-Windows
prototype; ~**1–2 months** to a tested, GPU-accelerated Windows Skia leg with
published fork klibs and repo wiring — **plus** permanent
`bitsycore/skiko`-fork maintenance (re-sync at every skiko bump). Difficulty:
**research-grade / high**, because no working prior art exists (the two GitHub
`skiko-mingw64` forks are vaporware — 0 commits ahead of upstream, no mingw
source set). Confidence: **medium** (high on mechanism, medium on timeline).

---

## Spike result — EXECUTED, GREEN ✅ (2026-07-24)

The core de-risking experiment has been **run on this machine** and passed. The
whole Route 1a link mechanism works on the exact target toolchain (Kotlin
**2.4.0**, mingwX64, LLVM **21** `ld.lld`, `x86_64-pc-windows-gnu`).

**What was built** (`spike-skiko-mingw/` in this repo):
- A tiny DLL reproducing skiko's binding shape — an **opaque handle over a real
  C++/STL object** (`std::string`), **alloc+free paired inside the DLL**, flat
  `extern "C"` surface. Compiled **MSVC-ABI via `clang-cl /LD /MT /EHsc`**
  (static CRT). Exports verified undecorated: `spike_make/size/free`.
- A **GNU import library** (`libspike.dll.a`) generated with `dlltool`.
- A **Kotlin/Native mingwX64 executable** binding it via cinterop (plain C
  header) + `linkerOpts("-L… -lspike")`.

**Results:**
- `linkReleaseExecutableMingwX64` **linked clean** — **KT-65671 did NOT bite**
  (the DLL path puts zero C++ objects in the K/N link graph, so the
  `--allow-multiple-definition` / `.drectve __clang_call_terminate` failures
  never arise). LLVM 21 `ld.lld` is also far newer than the 1.9.x-era reports.
- Runtime: `SPIKE-RESULT size=7 expected=7 ok=true`, exit 0 — the C++
  `std::string(7,'x').size()` round-trips through the opaque handle correctly.
- `objdump -p` confirms the exe imports `spike.dll`, and `spike.dll` depends on
  **`KERNEL32.dll` only** → **self-contained, no VC++ redist needed**. The exe
  carries K/N's `msvcrt.dll` while the DLL carries its own static MSVC CRT —
  **two CRTs coexist in one process** with no issue (ownership paired inside the
  DLL), empirically confirming the CRT-boundary discipline.

**Implication:** the one load-bearing unknown (does the DLL route link on
2.4.0?) is resolved **in favour**. Route 1a is de-risked at the mechanism level;
remaining effort is engineering the real `skiko.dll` (compile skiko's shim +
skia-pack MSVC Skia into one DLL) and repo wiring — not proving feasibility.
Feasibility confidence is now **high**; residual medium confidence is on
timeline only.

Reproduce: `./gradlew -p spike-skiko-mingw linkReleaseExecutableMingwX64`, then
run the exe with `spike-skiko-mingw/native` on `PATH`.

---

## The crux — and why "just use a DLL" is only half the story

**The blocker is a C++ ABI mismatch at the binding boundary, and it is
orthogonal to static-vs-dynamic linkage.**

- **K/N mingwX64 is GNU/Itanium ABI.** `konan.properties`:
  `targetTriple.mingw_x64 = x86_64-pc-windows-gnu`,
  `linker.mingw_x64 = ld.lld.exe`,
  `linkerKonanFlags.mingw_x64 = -static-libgcc -static-libstdc++ … -lwinpthread`
  — GCC libstdc++/libgcc, Itanium mangling (`_Z3fooi`).
- **skia-pack's only Windows Skia is MSVC-ABI** — built with `clang-cl`,
  `is_trivial_abi=false`, shipped as MSVC `.lib`; C++ exports are MSVC-mangled
  (`?foo@@YAHH@Z`).

LLVM issue #60847 ("LLD cannot link against MSVC libraries in MinGW toolchain",
closed *not planned*) confirms the two mangling schemes never reconcile.
**Wrapping the same MSVC C++ Skia in a DLL does not change this** — a DLL's C++
exports are still MSVC-mangled. So dynamic-vs-static is a red herring; **the C++
ABI at whatever boundary the mangled C++ crosses is the wall.**

What makes it tractable is *where that boundary sits in skiko*:

- **skiko's Kotlin↔native boundary is already a flat, unmangled C symbol
  boundary.** Kotlin declares
  `@ExternalSymbolName("org_jetbrains_skia_Data__1nSize") external fun _nSize(…)`;
  `ExternalSymbolName` is a typealias for `kotlin.native.SymbolName`, which
  resolves to a *plain named symbol* at link. The generated `.def` is
  header-less (only `linkerOpts`). **No C++ type ever crosses into Kotlin.**
- **The C++ ABI coupling lives one layer down**, at the *shim↔Skia* link:
  ~78 `extern "C"` (`SKIKO_EXPORT`) `.cc` files under `src/nativeJsMain/cpp/`
  that `reinterpret_cast<SkData*>(ptr)->size()` etc. This shim **must share one
  C++ ABI with Skia** — today they're one GNU-ABI island fused via
  `-include-binary`.

**Consequence:** a DLL is viable **iff** the *entire* C++ island (shim + Skia)
is one consistent ABI internally, and only the **flat extern-C export surface**
crosses to K/N via a generated import library. On x86_64 there is a single
Windows calling convention and extern-C exports are undecorated on both MSVC and
mingw, so `ld.lld` links such a DLL via `gendef`+`dlltool` (or directly, LLD
13+). **The repo already does exactly this for system DLLs** (`sdl3.def`:
`linkerOpts.mingw_x64 = -lkernel32 -luser32 -lgdi32 …`).

Crucially the extern-C façade **already exists** (the `nativeJsMain/cpp` shim
exports precisely the `org_jetbrains_skia_*` symbols the K/N side binds) — it is
**reused, not authored anew**. The catch: no ownership / exceptions / STL may
cross the C line — skiko is already handle/RefCnt-based, so this holds if
authored carefully. (The JVM `jvmMain/cpp` bridge exports `Java_…` JNI symbols
and is **not** reusable — the *native* shim is the right one.)

The non-negotiable that survives all of this: **upstream skiko ships no
mingwX64 K/N target at all** (`throw GradleException("$os not yet supported")`
for non-{Mac,iOS,tvOS,Linux}; "Windows" in skiko means the JVM/AWT clang-cl
path). So this is a **from-scratch skiko build-system port**, not a flag flip.

---

## The dominant risk: KT-65671

`KT-65671` ("Kotlin/Native: Failed linking cinterop static library for
mingwX64", **Open**, filed against 1.9.x, status on 2.4.0 unverified): K/N's
mingw link step chokes when **fusing an external C++ static archive** —
`ld.lld` rejects `--allow-multiple-definition`, and
`-exclude-symbols:__clang_call_terminate` in the objects is rejected in
`.drectve`. This is skiko's **exact static-fusion pattern**.

Key implication for route choice: a **pure extern-C DLL boundary sidesteps this
bug class entirely** — no C++ objects enter the K/N link graph; only a flat
import surface crosses. So, counter-intuitively, the **DLL route is the
risk-*reducing* route** w.r.t. KT-65671, and it is exactly what the user is
willing to accept.

---

## Routes

### ★ Recommended — DLL with a flat extern-C export, K/N binds via import lib

Put **all** the C++ (skiko's existing shim + Skia) inside one DLL; export only
the flat `org_jetbrains_skia_*` symbols; bind from a new mingwX64 skiko target
via a generated import lib. K/N resolves at link; the Windows loader binds
`skiko.dll` at process start — **no dlopen/`staticLoad` code needed** (the
shipped `staticLoad()` no-op is fine for the import-lib path).

Two sub-choices for the DLL internals (K/N neither knows nor cares which):

- **1a — MSVC internals (minimal fork).** Recompile skiko's `nativeJsMain/cpp`
  shim with `clang-cl` against **skia-pack's existing MSVC Windows `.lib`**
  (`windows-x64` archives are already published). **Zero Skia fork.** Net-new:
  add `__declspec(dllexport)` to `SKIKO_EXPORT` on this build, a mingw/DLL
  branch in skiko's build logic, import-lib generation. CRT discipline (MSVC CRT
  in the DLL vs K/N's static libgcc) is a correctness constraint on the C API
  (handles + primitives only), not a linkability blocker.
- **1b — GNU internals.** Build the shim + Skia GNU-ABI (reuse MSYS2's
  `mingw-w64-skia` patch set) into the DLL. Slightly more Skia-side work, but a
  single toolchain end-to-end.

**Effort:** ~2–4 weeks once the spike is green. **Risk: medium** (sidesteps
KT-65671). **"Just works" preserved:** none — ships `skiko.dll` (tens of MB)
next to `data.kres` (accepted). **Sub-choice 1a is the least-fork option.**

### Route 2 — GNU-ABI static Skia + real mingwX64 skiko target (purist)

Fork skia-pack (or reuse MSYS2's patches) to emit **GNU-ABI static** Skia;
fork skiko to add a mingwX64 target that recompiles the existing extern-C shim
GNU-ABI and **static-fuses** exactly as macOS/Linux do today.

**Effort:** Skia build itself is now **days** (MSYS2 proves it). skiko port +
link reconciliation ~3–6 weeks. **Risk: medium-high** — this route hits
**KT-65671 head-on** (static C++ archive fusion). **Preserves the static /
no-DLL invariant** — the only reason to prefer it, and the user said they don't
need it.

### Route 3 — CPU-raster-only (a scope reduction, not an ABI shortcut)

The repo's `SkiaSurfaceBridge` (`Surface.makeRasterDirect` → `SDL_UpdateTexture`
→ `SDL_RenderTexture`) needs **no GPU context** and compiles on mingwX64 the
moment a skiko klib exists. Use it to **skip authoring the Windows GPU bridge**
for a first cut. Still requires solving Route 1/2's binding problem first.
**Saves ~1–2 weeks** of GPU-bridge work → the natural **milestone 1** of
whichever route you pick.

### Baseline — do nothing (already shipping)

Windows renders via the from-scratch **SDL leg** (`SDL_RenderGeometry` +
SDL3_ttf + FreeType); the **JVM parity target** is the full-fidelity Skia
reference. The bar any Skia-on-Windows work must clear is "materially better
than the SDL leg's fidelity, worth the DLL + maintenance cost."

---

## Recommended work breakdown (Route 1a, CPU-raster first)

**A. Skia — none (1a) / days (1b).**
Route 1a reuses skia-pack's published `windows-x64` MSVC `.lib`. (1b: reuse the
MSYS2 `mingw-w64-skia` PKGBUILD + patches at skiko's pinned milestone; backends
CPU-raster + `skia_use_gl`, `skia_use_freetype`.)

**B. `bitsycore/skiko` fork — the bulk, medium risk.**
1. Add `mingwX64()` + a `configureNativeTarget(OS.Windows, Arch.X64, …)` branch;
   replace the `throw GradleException("$os not yet supported")` / empty
   `linkerFlags` fall-throughs in `NativeTasksConfiguration.kt` with a Windows
   branch (PE/COFF, `llvm-ar`).
2. Compile the **existing** `nativeJsMain/cpp` shim (not the JVM JNI bridge)
   into `skiko.dll`; make `SKIKO_EXPORT` export the flat symbols
   (`__declspec(dllexport)` or `--export-all-symbols`/def file).
3. Generate the GNU import lib (`gendef` + `dlltool` → `libskiko.dll.a`).
4. cinterop `.def` stays header-less (`linkerOpts`/`libraryPaths`), matching the
   repo's own idiom. Publish mingwX64 klibs + the DLL.

**C. Windows context actual — deferred behind milestone 1.**
5. **Milestone 1:** wire only the CPU-raster `SkiaSurfaceBridge` (already
   Windows-ready).
6. **Milestone 2:** author `SkiaD3D11Bridge` / `SkiaVulkanBridge` — extract HWND
   via `SDL_GetWindowProperties`, wrap `DirectContext.makeD3D11`/`makeVulkan`,
   mirror the Metal/GL bridges' per-frame acquire/present.

**D. This repo's wiring — small, low risk (integration points confirmed).**
7. `compose/ui/ui/build.gradle.kts` — flip `isSkiaTarget("mingwX64")` to `true`
   (currently hardcoded `false`, ~line 58); create a `skikoRendererMingwMain`
   and `mingwX64Main.dependsOn(...)` it (today mingw attaches only to
   `sdlRendererMingwMain`, lines ~203–206; the skiko tree, lines ~220–235, is
   created only under `!useSdl3Everywhere` and never for mingw). Add the fork's
   coords to that source set.
8. **No new expect/actual needed:** `createRenderBackend` /
   `rendererPreferredGpuMode` are `expect`s in `nativeMain` with `actual`s in
   `skikoRendererMain` and `sdlRendererMain`; attaching mingwX64 to the skiko
   source set makes the Skia actual resolve for Windows (one renderer source set
   per target — the existing invariant).
9. Add a Windows GPU-bridge branch to `rendererPreferredGpuMode()`; verify with
   the demo/apidemo (`--gpu`, `--screenshot`) and the parity harness.

Repo surface to cover is narrow: **~67 `org.jetbrains.skia` classes + 5
`org.jetbrains.skiko.node` APIs** (`RenderNode`, `RenderNodeContext`,
`SkikoRenderDelegate`, `SystemTheme`, `currentSystemTheme`).

---

## The one spike to run first (~1–2 days)

Before any fork work, **empirically test the K/N-mingw link path**: build a
trivial `extern "C"` C++ DLL (one function returning a handle + one that uses
it) — or consume MSYS2's `mingw-w64-x86_64-skia` DLL + `libskia.dll.a`
directly — from a throwaway mingwX64 K/N project via cinterop + import lib, and
**run it**. This answers, in a day instead of a month:

- Does K/N mingw link + call a GNU-ABI DLL via an import lib at all?
- Does **KT-65671** reproduce on Kotlin 2.4.0 (import-lib vs static fusion)?
- DLL vs static-fusion behaviour on this toolchain.

If green, Routes 1/2 are de-risked. (Caveat: MSYS2 Skia exports Skia's *mangled
C++* symbols, so a direct cinterop of it tests only the *link mechanism*, not
the skiko API — for the API you still need the extern-C shim. The trivial-C-DLL
variant is the cleaner mechanism test.)

---

## Biggest risks / unknowns

1. **KT-65671** — whether K/N's mingw link works on 2.4.0 for external C++;
   the DLL route sidesteps it, the static route hits it. *The single fact that
   swings the effort.*
2. **Toolchain matching** — Konan bundles LLVM 16 clang + `ld.lld`; the C++20
   Skia + shim must be built by a toolchain whose objects/imports LLD accepts.
3. **CRT / ownership discipline** across the C boundary (only matters for 1a).
4. **Maintenance** — SKIKO-446 / SKIKO-611 are Open, unassigned → almost
   certainly a **permanent `bitsycore/skiko` fork** with per-bump re-sync tax.

---

## Recommendation

Pursue **Route 1a** (skiko fork producing a DLL with a flat extern-C export over
skia-pack's existing MSVC Windows Skia), **scoped CPU-raster first** (Route 3
milestone), GPU bridge second. It matches the user's accepted DLL tolerance,
requires **zero Skia fork**, and **dodges KT-65671**. Keep Route 2 (static,
GNU-ABI) in reserve only if reclaiming the no-DLL invariant later becomes
valuable. **Run the MSYS2/trivial-DLL spike before committing to any fork.**

---

## Implementation status — WORKING END-TO-END ✅ (2026-07-24)

**`demo.exe` (mingwX64) renders the Material 3 Buttons screen through real Skia**
and wrote a 1000×700 screenshot ("settled at frame 3"), using the CPU-raster
`SkiaSurfaceBridge` backed by the forked `skiko-windows-x64.dll`. Route 1a is
complete. Build/run:

```
# fork (C:/Dev/skiko), one-time env: SKIKO_VSBT_PATH=<VS BuildTools dir>
./gradlew :skiko:publishKotlinMultiplatformPublicationToMavenLocal \
          :skiko:publishMingwX64PublicationToMavenLocal -Pskiko.native.windows.enabled=true
# compose repo
./gradlew :demo:linkDebugExecutableMingwX64 -PwindowsSkia=true
cp <fork>/skiko/build/out/link/Release-windows-native-x64/skiko-windows-x64.dll <demo exe dir>/
<demo exe dir>/demo.exe --screenshot=out.bmp --screen=Buttons
```

Fork fixes needed beyond the DLL build (skiko uses a CUSTOM hierarchy template,
`applyDefaultHierarchyTemplate=false`):
1. `sourceHierarchy.kt` — add `group("windows"){ withMingwX64() }` under `native`
   (else the mingwX64 klib compiles EMPTY — missing all of `org.jetbrains.skia`).
2. `SkikoProjectContext` — add `supportNativeWindows` into `supportAnyNative` so
   the `@SymbolName` opt-in reaches the mingw compile.
3. `Resources.native.kt` — `ftell` is 32-bit `Int` on Windows (LLP64); `==` fix.
4. `windowsMain` actuals for `SkiaLayer` / `currentSystemTheme` (linux is a stub).
5. Force-export the 2 ICU symbols (`uloc_getDefault_skiko`,
   `uloc_toLanguageTag_skiko`) that Kotlin binds via `@SymbolName`.

Compose-repo wiring (all behind `-PwindowsSkia=true`): `mavenLocal()` in
settings, `isSkiaTarget("mingwX64")`, a separate `skikoRendererMingwMain` tree
on the fork's **root** coord (`org.jetbrains.skiko:skiko:0.0.0-SNAPSHOT` — the
platform artifact alone doesn't expose api-elements), `PlatformGpu` Windows →
`Software`, and a one-line `PlatformGpu.mingw.kt`.

## Earlier milestone — fork BUILDS ✅ (2026-07-24)

Route 1a is not just designed — the `bitsycore/skiko` fork now **builds the
native Windows Skia DLL + a mingwX64 klib** on this machine (`C:/Dev/skiko`,
skiko `v0.150.1`).

Edits made (all in the fork):
- `src/nativeJsMain/cpp/common.h` — `SKIKO_EXPORT` gains `__declspec(dllexport)`
  under a `SKIKO_WINDOWS_DLL` guard.
- `skiko/build.gradle.kts` — `configureNativeTarget(OS.Windows, Arch.X64,
  mingwX64())`, gated behind `-Pskiko.native.windows.enabled=true`.
- `buildSrc/.../NativeTasksConfiguration.kt` — a Windows compile branch
  (clang-cl + `-DSKIKO_WINDOWS_DLL`) and a new `configureWindowsNativeTarget`
  that links `skiko-windows-x64.dll` via `lld-link` (reusing the JVM template +
  `resolveBinaryInputs(…, TargetEnv.JVM, …)`), auto-generates the GNU import lib
  (`dumpbin`→`.def`→`dlltool`), and wires the K/N cinterop `linkerOpts`.

Results:
- The native bridge (`nativeJsMain/cpp`) **compiled on Windows with clang-cl,
  zero source changes.** The only link fix was adding `d3d12.lib
  d3dcompiler.lib dxgi.lib` for Skia's `SK_DIRECT3D` backend.
- Produced **`skiko-windows-x64.dll` (14.2 MB, 1005 exported
  `org_jetbrains_skia_*` symbols)**, `libskiko-windows-x64.dll.a` (GNU import
  lib), and the **mingwX64 klib** (`build/classes/kotlin/mingwX64/main/klib`).

Prerequisite for any skiko Windows build on this box:
`SKIKO_VSBT_PATH='C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools'`
(Gradle's VS locator doesn't auto-find VS 18 BuildTools).

Remaining: publish the fork to `mavenLocal` → a tiny K/N mingw consumer that
calls a real Skia function (links the import lib, runs with the DLL on `PATH`)
for the definitive end-to-end proof → then wire the compose repo (flip
`isSkiaTarget("mingwX64")`, add `skikoRendererMingwMain`, ship the DLL next to
the exe, CPU-raster `SkiaSurfaceBridge` first). One known TODO: the klib's
`linkerOpts` use an absolute import-lib path (fine same-machine; make relative /
bundled for distribution).

## Corrections to RENDERER.md §4

The shelving note should be updated — its two central technical claims are stale
as of 2026-07:

- "GNU-ABI Skia is an open-ended fork / only abandoned Mozilla-era precedent" —
  **false**: MSYS2 maintains a modern (m143, C++20) GNU-ABI Skia, rebuilt
  routinely.
- The accurate residual blocker is **the K/N-side link/binding port**
  (KT-65671 + authoring the mingwX64 skiko target), **not** building Skia, and
  the DLL route is *risk-reducing*, not merely "the only working route."

---

## Sources (primary, load-bearing)

- skiko v0.150.1: `skiko/build.gradle.kts`, `buildSrc/.../NativeTasksConfiguration.kt`,
  `src/commonMain/kotlin/org/jetbrains/skia/Data.kt`,
  `src/nativeMain/kotlin/org/jetbrains/skia/Actuals.native.kt`,
  `src/nativeJsMain/cpp/common.h`, `.../impl/Library.native.kt`
- skia-pack `script/build.py`; skia.org build docs
- Kotlin `konan.properties` (v2.2.0); kotlinlang native-c-interop / target-support
- LLVM issue #60847; **KT-65671**; **SKIKO-446**, **SKIKO-611**
- MSYS2 `mingw-w64-skia` (packages.msys2.org; MINGW-packages PKGBUILD + patches)
- Vaporware forks: github.com/Cdm2883/skiko-mingw64, crowforkotlin/skiko-mingw64
- Repo: `compose/ui/ui/build.gradle.kts`, the `SkiaMetalBridge` / `SkiaGLBridge`
  / `SkiaSurfaceBridge` / `RenderBackendFactory` seam
