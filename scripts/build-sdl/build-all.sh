#!/usr/bin/env bash
# One-shot Windows (mingwX64) dependency setup. Builds -- from source, as STATIC
# libraries -- every native dependency this project links into the executable,
# installing each into <repo>/libs/. Run after a fresh clone or after deleting
# libs/. Idempotent: safe to re-run (each step rebuilds under libs/.build and
# reinstalls into libs/<Lib>).
#
# Build order matters -- later libraries link the earlier ones:
#   1. FreeType    -> libs/FreeType/lib/libfreetype.a
#   2. SDL3        -> libs/SDL3/lib/libSDL3.a
#   3. SDL3_image  -> libs/SDL3_image      (vendored PNG/JPG/SVG/WEBP; needs SDL3)
#   4. SDL3_ttf    -> libs/SDL3_ttf         (our variable-font axis patch; needs
#                                            SDL3 + FreeType)
#
# Everything is static + HarfBuzz/plutosvg-free, so the app links to a clean
# <app>.exe + data.kres with NO DLLs. (This replaces the old fetch-sdl3.sh
# prebuilt-DLL flow.)
#
# Run from Git Bash on Windows. Requires: git, cmake, a mingw-w64 gcc/g++ in
# PATH, plus curl + python (used to fetch ninja when absent). Per-step options
# (e.g. SDL_REF=, SDL_IMAGE_REF=, SDL_TTF_REF=, FREETYPE_TAG=) are read from the
# environment by the individual scripts; export them before running this.
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# SDL3 >= 3.4 compiles C++ on Windows (GameInput backend), but K/N's bundled
# mingw is C-only -- its c++.exe driver has no cc1plus backend and dies with
# "cannot execute 'cc1plus'". Capture the system g++ BEFORE the K/N toolchain
# is prepended to PATH below, and hand it to CMake via the CXX env var. C
# stays on the K/N gcc so the static libs keep matching K/N's CRT.
SYSTEM_GXX="$(command -v g++ 2>/dev/null || true)"
if [ -n "$SYSTEM_GXX" ]; then
	[ -f "${SYSTEM_GXX}.exe" ] && SYSTEM_GXX="${SYSTEM_GXX}.exe"
	export CXX="$(cygpath -m "$SYSTEM_GXX")"
	echo ">> C++ compiler (K/N mingw is C-only): $CXX"
else
	echo ">> WARNING: no system g++ on PATH; SDL3's CMake configure needs a"
	echo ">>          working C++ compiler and will fail. Install mingw-w64 g++."
fi

# Build with Kotlin/Native's OWN bundled mingw-w64 gcc when present, so the
# static libs match the exact CRT K/N links them against. A newer host gcc
# (11+) emits __intrinsic_setjmpex for x64 setjmp, which K/N's bundled mingw
# CRT (gcc 9.2) doesn't define -> "undefined symbol: __intrinsic_setjmpex" at
# the final K/N link. Prepend K/N's mingw bin to PATH; set KN_MINGW=0 to force
# the system gcc.
if [ "${KN_MINGW:-1}" != "0" ]; then
	KN_MINGW_BIN="$(ls -d "${KONAN_DATA_DIR:-$HOME/.konan}"/dependencies/*mingw*/bin 2>/dev/null | head -1 || true)"
	if [ -n "${KN_MINGW_BIN:-}" ] && [ -x "$KN_MINGW_BIN/gcc.exe" ]; then
		export PATH="$KN_MINGW_BIN:$PATH"
		echo ">> using Kotlin/Native bundled mingw: $(gcc --version | head -1)"
	else
		echo ">> WARNING: K/N bundled mingw not found under ${KONAN_DATA_DIR:-$HOME/.konan}/dependencies;"
		echo ">>          using system gcc: $(gcc --version 2>/dev/null | head -1 || echo none)."
		echo ">>          If the K/N link later fails with 'undefined symbol: __intrinsic_setjmpex',"
		echo ">>          build a K/N mingw target once (to fetch its mingw) and re-run, or set KONAN_DATA_DIR."
	fi
fi

step() {
	echo ""
	echo "============================================================"
	echo "==  $1"
	echo "============================================================"
	bash "$TOOLS/$1"
}

step build-freetype.sh
step build-sdl3.sh
step build-sdl3-image.sh
step build-sdl3-ttf.sh

echo ""
echo "============================================================"
echo "==  All Windows dependencies built (static) into libs/"
echo "============================================================"
echo "Build the app (links everything into a DLL-free .exe + data.kres):"
echo "   gradlew.bat :apidemo:runDebugExecutableMingwX64"
echo "   gradlew.bat :demo:runDebugExecutableMingwX64"
