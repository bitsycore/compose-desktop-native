#!/usr/bin/env bash
# Build a self-contained static FreeType and install it into <repo>/libs/FreeType.
# Runs on macOS, Linux, and Windows (Git Bash) — see _lib.sh for host handling.
#
# Optional dependencies (PNG / HarfBuzz / Brotli / BZip2 / system zlib) are
# DISABLED — only core + variable-font (MM / GX) support is kept, which is all
# the SDL3 renderer needs for Material Symbols axes and variable Roboto.
#
# The result is a static libfreetype.a with NO external DLLs / dylibs that
# links straight into the Kotlin/Native binary (the freetype.def does
# `-lfreetype`). Override the version with FREETYPE_TAG=VER-2-13-3 (default).
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_lib.sh
source "$TOOLS/_lib.sh"

BUILD_SDL_HOST="$(detect_host)"
setup_toolchain

FT_TAG="${FREETYPE_TAG:-VER-2-13-3}"
REPO="$(cd "$TOOLS/../.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/freetype"
mkdir -p "$BUILD"

NINJA="$(find_ninja "$BUILD")"

# Shallow clone of the requested tag; re-fetch only when missing.
if [ ! -f "$BUILD/src/CMakeLists.txt" ]; then
	echo ">> cloning FreeType $FT_TAG"
	rm -rf "$BUILD/src"
	git clone --depth 1 -b "$FT_TAG" https://github.com/freetype/freetype.git "$BUILD/src"
fi

echo ">> configuring (static, optional deps off)"
# shellcheck disable=SC2046
cmake -S "$(cmake_path "$BUILD/src")" -B "$(cmake_path "$BUILD/out")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER="$CC" \
	-DCMAKE_BUILD_TYPE=Release \
	-DBUILD_SHARED_LIBS=OFF \
	-DCMAKE_POSITION_INDEPENDENT_CODE=ON \
	-DFT_DISABLE_HARFBUZZ=ON -DFT_DISABLE_PNG=ON -DFT_DISABLE_BROTLI=ON \
	-DFT_DISABLE_BZIP2=ON -DFT_DISABLE_ZLIB=ON \
	-DCMAKE_C_FLAGS="-ffunction-sections -fdata-sections -Os" \
	$(extra_cmake_args) \
	-DCMAKE_INSTALL_PREFIX="$(cmake_path "$LIBS/FreeType")"

echo ">> building + installing"
cmake --build "$(cmake_path "$BUILD/out")"
cmake --install "$(cmake_path "$BUILD/out")"

echo ">> done: $LIBS/FreeType (static libfreetype.a, no runtime deps)"
