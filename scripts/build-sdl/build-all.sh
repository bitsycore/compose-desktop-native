#!/usr/bin/env bash
# One-shot dependency setup — builds every native dependency this project
# links into the executable as a STATIC library, installing each into
# <repo>/libs/. Runs on macOS, Linux, and Windows (Git Bash).
#
# Build order matters — later libraries link the earlier ones:
#   1. FreeType    → libs/FreeType/lib/libfreetype.a
#   2. SDL3        → libs/SDL3/lib/libSDL3.a
#   3. SDL3_image  → libs/SDL3_image      (vendored PNG/JPG/SVG/WEBP; needs SDL3)
#   4. SDL3_ttf    → libs/SDL3_ttf         (variable-font-axes patch; needs
#                                           SDL3 + FreeType)
#
# Everything is static + HarfBuzz/plutosvg-free, so the app links to a clean
# <app> + data.kres with no runtime .dylib / .so / .dll alongside.
#
# Requires on every host: git, cmake. Ninja is fetched into libs/.build/ninja-bin
# when absent. Windows also needs Python + curl (Git Bash defaults) plus a
# mingw-w64 g++ on PATH (for SDL3's GameInput backend; K/N's bundled mingw is
# C-only). Per-step options — SDL_REF / SDL_IMAGE_REF / SDL_TTF_REF /
# FREETYPE_TAG — are read from the environment by the individual scripts;
# export them before running this.
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_lib.sh
source "$TOOLS/_lib.sh"

export BUILD_SDL_HOST="$(detect_host)"
if [ "$BUILD_SDL_HOST" = "unknown" ]; then
	echo "ERROR: unsupported host — this script runs on macOS, Linux, and Git Bash on Windows." >&2
	exit 1
fi
echo ">> host: $BUILD_SDL_HOST"

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
echo "==  All dependencies built (static) into libs/"
echo "============================================================"
case "$BUILD_SDL_HOST" in
	macos)
		echo "Build the app (links everything into a self-contained macosArm64 binary):"
		echo "   ./gradlew :demo:runDebugExecutableMacosArm64"
		echo "   ./gradlew :apidemo:runDebugExecutableMacosArm64"
		;;
	linux)
		echo "Build the app (links everything into a self-contained Linux binary):"
		echo "   ./gradlew :demo:runDebugExecutableLinuxX64"
		echo "   ./gradlew :apidemo:runDebugExecutableLinuxX64"
		;;
	windows)
		echo "Build the app (links everything into a DLL-free .exe + data.kres):"
		echo "   gradlew.bat :apidemo:runDebugExecutableMingwX64"
		echo "   gradlew.bat :demo:runDebugExecutableMingwX64"
		;;
esac
