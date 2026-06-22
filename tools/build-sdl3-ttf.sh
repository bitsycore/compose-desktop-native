#!/usr/bin/env bash
# Build SDL3_ttf from source for the Windows mingwX64 target WITH our
# variable-font axis patch applied, and install it into <repo>/libs/SDL3_ttf
# (the same include/ lib/ bin/ layout tools/fetch-sdl3.sh produces, so it is a
# drop-in replacement that the cinterop .def files and DLL-copy already expect).
#
# Why build it ourselves: upstream SDL3_ttf has no public API to set OpenType
# variable-font axes (wght / wdth / opsz / GRAD / FILL ...). This clones a
# pinned SDL_ttf commit, applies tools/patches/sdl_ttf-variable-font-axes.patch
# (the change proposed upstream -- see that file's header) and builds the
# result, so the renderer can drive axes through TTF_SetFontAxisValue() instead
# of bypassing SDL_ttf and talking to FreeType directly.
#
# Static: produces libSDL3_ttf.a (no DLL). A static archive does not bundle its
# dependencies, so the app's final link pulls in libs/FreeType (libfreetype.a)
# and libs/SDL3 (libSDL3.a) to resolve SDL_ttf's FreeType / SDL3 symbols.
# HarfBuzz and plutosvg are OFF, matching this repo's HarfBuzz-free FreeType
# build. Run build-sdl3.sh and build-freetype.sh FIRST.
#
# Run from Git Bash on Windows. Requires: git, cmake, a mingw-w64 gcc in PATH,
# plus curl + 7z / python (used to fetch ninja if absent). Override the source
# with SDL_TTF_REF=<commit-or-tag> / SDL_TTF_URL=<repo>.
set -euo pipefail

# Pinned to the parent of our patch commit so the patch applies cleanly. Bump
# this together with a refreshed patch when rebasing onto a newer SDL_ttf.
SDL_TTF_REF="${SDL_TTF_REF:-24c45847ac32164d6d703e0c66f52f8f68a1fb60}"
SDL_TTF_URL="${SDL_TTF_URL:-https://github.com/libsdl-org/SDL_ttf.git}"

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/sdl3_ttf"
SRC="$BUILD/src"
OUT="$BUILD/out"
PREFIX="$BUILD/prefix"
PATCH="$REPO/tools/patches/sdl_ttf-variable-font-axes.patch"
mkdir -p "$BUILD"

[ -f "$PATCH" ] || { echo "ERROR: patch not found: $PATCH" >&2; exit 1; }
[ -f "$LIBS/SDL3/lib/cmake/SDL3/SDL3Config.cmake" ] || { echo "ERROR: SDL3 not found in libs/SDL3 -- run tools/fetch-sdl3.sh first" >&2; exit 1; }
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

# SDL_ttf source: clone at the pinned ref, then apply our patch (idempotent --
# skip if it is already applied, e.g. on a re-run).
if [ ! -f "$SRC/CMakeLists.txt" ]; then
	echo ">> cloning SDL_ttf $SDL_TTF_REF"
	rm -rf "$SRC"
	git clone --filter=blob:none "$SDL_TTF_URL" "$SRC"
	git -C "$SRC" checkout --quiet "$SDL_TTF_REF"
fi
if git -C "$SRC" apply --reverse --check "$PATCH" >/dev/null 2>&1; then
	echo ">> patch already applied"
else
	echo ">> applying variable-font axis patch"
	git -C "$SRC" apply "$PATCH"
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

echo ">> done: $LIBS/SDL3_ttf  (patched libSDL3_ttf.a, static, no HarfBuzz)"
