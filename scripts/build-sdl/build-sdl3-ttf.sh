#!/usr/bin/env bash
# Build a static SDL3_ttf and install it into <repo>/libs/SDL3_ttf. Runs on
# macOS, Linux, and Windows (Git Bash) — see _lib.sh for host handling.
#
# By default builds the variable-font axis API fork:
#
#   https://github.com/bitsycore/SDL_ttf   (branch: variable-font-axes)
#
# Upstream SDL3_ttf has no public API to set OpenType variable-font axes
# (wght / wdth / opsz / GRAD / FILL ...); the fork adds it so the renderer
# can drive axes through TTF_SetFontAxisValue() instead of bypassing SDL_ttf
# and talking to FreeType directly.
#
# Set SDL_TTF_URL / SDL_TTF_REF to point at a different SDL_ttf; changing
# either re-clones automatically. Examples:
#   SDL_TTF_URL=https://github.com/libsdl-org/SDL_ttf.git SDL_TTF_REF=main
#   SDL_TTF_URL=https://github.com/libsdl-org/SDL_ttf.git SDL_TTF_REF=release-3.2.2
#
# Static: produces libSDL3_ttf.a (no shared lib). A static archive doesn't
# bundle its dependencies, so the app's final link pulls in libs/FreeType
# (libfreetype.a) and libs/SDL3 (libSDL3.a) to resolve SDL_ttf's symbols.
# HarfBuzz and plutosvg are OFF, matching this repo's HarfBuzz-free FreeType
# build. Run build-sdl3.sh and build-freetype.sh FIRST.
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_lib.sh
source "$TOOLS/_lib.sh"

BUILD_SDL_HOST="$(detect_host)"
setup_toolchain

SDL_TTF_URL="${SDL_TTF_URL:-https://github.com/bitsycore/SDL_ttf.git}"
SDL_TTF_REF="${SDL_TTF_REF:-variable-font-axes}"
REPO="$(cd "$TOOLS/../.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/sdl3_ttf"
SRC="$BUILD/src"
OUT="$BUILD/out"
PREFIX="$BUILD/prefix"
MARKER="$BUILD/.source"
mkdir -p "$BUILD"

[ -f "$LIBS/SDL3/lib/cmake/SDL3/SDL3Config.cmake" ] || { echo "ERROR: SDL3 not found in libs/SDL3 — run scripts/build-sdl/build-sdl3.sh first" >&2; exit 1; }
[ -f "$LIBS/FreeType/lib/libfreetype.a" ] || { echo "ERROR: FreeType not found in libs/FreeType — run scripts/build-sdl/build-freetype.sh first" >&2; exit 1; }

NINJA="$(find_ninja "$BUILD")"

# Re-clone whenever URL/ref changes so switching versions is clean.
WANT="$SDL_TTF_URL $SDL_TTF_REF"
if [ ! -f "$SRC/CMakeLists.txt" ] || [ "$(cat "$MARKER" 2>/dev/null)" != "$WANT" ]; then
	echo ">> cloning $SDL_TTF_URL @ $SDL_TTF_REF"
	rm -rf "$SRC"
	git clone --filter=blob:none "$SDL_TTF_URL" "$SRC"
	git -C "$SRC" checkout --quiet "$SDL_TTF_REF"
	echo "$WANT" > "$MARKER"
fi

echo ">> configuring (non-vendored: libs/FreeType + libs/SDL3; HarfBuzz/plutosvg off)"
rm -rf "$OUT"
# shellcheck disable=SC2046
cmake -S "$(cmake_path "$SRC")" -B "$(cmake_path "$OUT")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER="$CC" \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_PREFIX_PATH="$(cmake_path "$LIBS/SDL3");$(cmake_path "$LIBS/FreeType")" \
	-DBUILD_SHARED_LIBS=OFF \
	-DCMAKE_POSITION_INDEPENDENT_CODE=ON \
	-DSDLTTF_VENDORED=OFF \
	-DSDLTTF_HARFBUZZ=OFF \
	-DSDLTTF_PLUTOSVG=OFF \
	-DSDLTTF_SAMPLES=OFF \
	-DSDLTTF_INSTALL=ON \
	-DCMAKE_C_FLAGS="-ffunction-sections -fdata-sections -Os" \
	$(extra_cmake_args) \
	-DCMAKE_INSTALL_PREFIX="$(cmake_path "$PREFIX")"

echo ">> building + installing"
rm -rf "$PREFIX"
cmake --build "$(cmake_path "$OUT")"
cmake --install "$(cmake_path "$OUT")"

echo ">> installing into $LIBS/SDL3_ttf"
rm -rf "$LIBS/SDL3_ttf"
mkdir -p "$LIBS/SDL3_ttf"
cp -R "$PREFIX/include" "$PREFIX/lib" "$LIBS/SDL3_ttf/"

echo ">> done: $LIBS/SDL3_ttf  (libSDL3_ttf.a from $SDL_TTF_REF, static, no HarfBuzz)"
