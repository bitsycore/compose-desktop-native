#!/usr/bin/env bash
# Build SDL3_ttf from source for the Windows mingwX64 target and install it into
# <repo>/libs/SDL3_ttf (a drop-in the cinterop .def files and DLL-copy expect).
# By default it builds the variable-font axis API from the fork:
#
#   https://github.com/bitsycore/SDL_ttf   (branch: variable-font-axes)
#
# Upstream SDL3_ttf has no public API to set OpenType variable-font axes
# (wght / wdth / opsz / GRAD / FILL ...); that branch adds it, so the renderer
# can drive axes through TTF_SetFontAxisValue() instead of bypassing SDL_ttf and
# talking to FreeType directly.
#
# To build a different SDL_ttf, set SDL_TTF_REF (branch, tag, or commit) and/or
# SDL_TTF_URL -- switching either re-clones automatically. Examples:
#   SDL_TTF_URL=https://github.com/libsdl-org/SDL_ttf.git SDL_TTF_REF=main           # upstream main
#   SDL_TTF_URL=https://github.com/libsdl-org/SDL_ttf.git SDL_TTF_REF=release-3.2.2  # an upstream release
#   SDL_TTF_REF=variable-font-axes                                                    # the fork branch (default)
#
# Static: produces libSDL3_ttf.a (no DLL). A static archive does not bundle its
# dependencies, so the app's final link pulls in libs/FreeType (libfreetype.a)
# and libs/SDL3 (libSDL3.a) to resolve SDL_ttf's FreeType / SDL3 symbols.
# HarfBuzz and plutosvg are OFF, matching this repo's HarfBuzz-free FreeType
# build. Run build-sdl3.sh and build-freetype.sh FIRST.
#
# Run from Git Bash on Windows. Requires: git, cmake, a mingw-w64 gcc in PATH,
# plus curl + python (used to fetch ninja if absent).
set -euo pipefail

SDL_TTF_URL="${SDL_TTF_URL:-https://github.com/bitsycore/SDL_ttf.git}"
SDL_TTF_REF="${SDL_TTF_REF:-variable-font-axes}"

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/sdl3_ttf"
SRC="$BUILD/src"
OUT="$BUILD/out"
PREFIX="$BUILD/prefix"
MARKER="$BUILD/.source"
mkdir -p "$BUILD"

[ -f "$LIBS/SDL3/lib/cmake/SDL3/SDL3Config.cmake" ] || { echo "ERROR: SDL3 not found in libs/SDL3 -- run tools/build-sdl3.sh first" >&2; exit 1; }
[ -f "$LIBS/FreeType/lib/libfreetype.a" ] || { echo "ERROR: FreeType not found in libs/FreeType -- run tools/build-freetype.sh first" >&2; exit 1; }

# cmake (a Windows exe) wants Windows-style paths; cygpath -m gives C:/foo form.
win() { cygpath -m "$1"; }

# ninja: use one on PATH, else fetch the single-exe Windows build into BUILD/.
if command -v ninja >/dev/null 2>&1; then
	NINJA="ninja"
else
	if [ ! -f "$BUILD/ninja.exe" ]; then
		echo ">> fetching ninja"
		curl -fL -o "$BUILD/ninja.zip" \
			https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-win.zip
		python -c "import zipfile,sys;zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" \
			"$BUILD/ninja.zip" "$BUILD"
	fi
	NINJA="$(win "$BUILD/ninja.exe")"
fi

# SDL_ttf source: clone the requested URL and check out the ref (branch, tag,
# or commit). Re-clone whenever URL/ref changes so switching versions is clean.
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
cmake -S "$(win "$SRC")" -B "$(win "$OUT")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER=gcc \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_PREFIX_PATH="$(win "$LIBS/SDL3");$(win "$LIBS/FreeType")" \
	-DBUILD_SHARED_LIBS=OFF \
	-DSDLTTF_VENDORED=OFF \
	-DSDLTTF_HARFBUZZ=OFF \
	-DSDLTTF_PLUTOSVG=OFF \
	-DSDLTTF_SAMPLES=OFF \
	-DSDLTTF_INSTALL=ON \
	-DCMAKE_C_FLAGS="-ffunction-sections -fdata-sections -Os" \
	-DCMAKE_INSTALL_PREFIX="$(win "$PREFIX")"

echo ">> building + installing"
rm -rf "$PREFIX"
cmake --build "$(win "$OUT")"
cmake --install "$(win "$OUT")"

# Sync the staged prefix into libs/SDL3_ttf (replace the prebuilt drop-in).
echo ">> installing into $LIBS/SDL3_ttf"
rm -rf "$LIBS/SDL3_ttf"
mkdir -p "$LIBS/SDL3_ttf"
cp -R "$PREFIX/include" "$PREFIX/lib" "$LIBS/SDL3_ttf/"

echo ">> done: $LIBS/SDL3_ttf  (libSDL3_ttf.a from $SDL_TTF_REF, static, no HarfBuzz)"
