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
# `-lfreetype`). URL + ref come from scripts/build-sdl/build-sdl.properties
# (FREETYPE_URL / FREETYPE_TAG); a same-named env var overrides for one-offs.
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_lib.sh
source "$TOOLS/_lib.sh"

BUILD_SDL_HOST="$(detect_host)"
setup_toolchain

FT_URL="$(require_manifest FREETYPE_URL)"
FT_TAG="$(require_manifest FREETYPE_TAG)"
REPO="$(cd "$TOOLS/../.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/freetype"
mkdir -p "$BUILD"

NINJA="$(find_ninja "$BUILD")"

# Re-clone whenever URL / tag changes so switching versions is clean.
MARKER="$BUILD/.source"
WANT="$FT_URL $FT_TAG"
if [ ! -f "$BUILD/src/CMakeLists.txt" ] || [ "$(cat "$MARKER" 2>/dev/null)" != "$WANT" ]; then
	echo ">> cloning FreeType $FT_TAG"
	rm -rf "$BUILD/src"
	git clone --depth 1 -b "$FT_TAG" "$FT_URL" "$BUILD/src"
	echo "$WANT" > "$MARKER"
fi

# Windows: mingw-w64's <setjmp.h> makes _setjmp expand to __intrinsic_setjmpex,
# a GCC intrinsic that IS meant to be inlined but sometimes emits an external
# reference the K/N LLD sysroot can't resolve. Defining USE_NO_MINGW_SETJMP_TWO_ARGS
# switches _setjmp to __builtin_setjmp (fully inlined, no external symbol) —
# safe for FreeType which only uses setjmp for internal error recovery.
CFLAGS_EXTRA="-ffunction-sections -fdata-sections -Os"
if [ "$BUILD_SDL_HOST" = "windows" ]; then
	CFLAGS_EXTRA="$CFLAGS_EXTRA -DUSE_NO_MINGW_SETJMP_TWO_ARGS=1"
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
	-DCMAKE_C_FLAGS="$CFLAGS_EXTRA" \
	$(extra_cmake_args) \
	-DCMAKE_INSTALL_PREFIX="$(cmake_path "$LIBS/FreeType")"

echo ">> building + installing"
cmake --build "$(cmake_path "$BUILD/out")"
cmake --install "$(cmake_path "$BUILD/out")"

echo ">> done: $LIBS/FreeType (static libfreetype.a, no runtime deps)"
