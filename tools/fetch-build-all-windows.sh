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
