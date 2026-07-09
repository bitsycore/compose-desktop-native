#!/usr/bin/env bash
# Build a self-contained static FreeType for the Windows mingwX64 target and
# install it into <repo>/libs/FreeType.
#
# Optional dependencies (PNG, HarfBuzz, Brotli, BZip2, system zlib) are
# DISABLED — only core + variable-font (MM / GX) support is kept, which is all
# the SDL3 renderer needs for Material Symbols axes and variable Roboto. The
# result is a static libfreetype.a with NO external/runtime DLLs that links
# straight into the Kotlin/Native binary (the .def does `-lfreetype`).
#
# Run from Git Bash on Windows. Requires: git, cmake, a mingw-w64 gcc in PATH
# (e.g. `scoop install gcc`), plus curl + 7z (used to fetch ninja if absent).
# Override the version with FREETYPE_TAG=VER-2-13-3 (default below).
set -euo pipefail

FT_TAG="${FREETYPE_TAG:-VER-2-13-3}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/freetype"
mkdir -p "$BUILD"

# cmake (a Windows exe) wants Windows-style paths; cygpath -m gives C:/foo form.
win() { cygpath -m "$1"; }

# ninja: use one on PATH, else fetch the single-exe Windows build into BUILD/.
if command -v ninja >/dev/null 2>&1; then
	NINJA="ninja"
else
	echo ">> fetching ninja"
	curl -fL -o "$BUILD/ninja.zip" \
		https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-win.zip
	( cd "$BUILD" && 7z x -y "$(cygpath -w "$BUILD/ninja.zip")" >/dev/null )
	NINJA="$(win "$BUILD/ninja.exe")"
fi

# FreeType source (shallow clone of the requested tag).
if [ ! -f "$BUILD/src/CMakeLists.txt" ]; then
	echo ">> cloning FreeType $FT_TAG"
	rm -rf "$BUILD/src"
	git clone --depth 1 -b "$FT_TAG" https://github.com/freetype/freetype.git "$BUILD/src"
fi

echo ">> configuring (static, optional deps off)"
cmake -S "$(win "$BUILD/src")" -B "$(win "$BUILD/out")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER=gcc \
	-DCMAKE_BUILD_TYPE=Release \
	-DBUILD_SHARED_LIBS=OFF \
	-DFT_DISABLE_HARFBUZZ=ON -DFT_DISABLE_PNG=ON -DFT_DISABLE_BROTLI=ON \
	-DFT_DISABLE_BZIP2=ON -DFT_DISABLE_ZLIB=ON \
	-DCMAKE_INSTALL_PREFIX="$(win "$LIBS/FreeType")"

echo ">> building + installing"
cmake --build "$(win "$BUILD/out")"
cmake --install "$(win "$BUILD/out")"

echo ">> done: $LIBS/FreeType  (static libfreetype.a, no runtime DLLs)"
