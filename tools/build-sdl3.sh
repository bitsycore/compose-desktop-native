#!/usr/bin/env bash
# Build a static SDL3 for the Windows mingwX64 target and install it into
# <repo>/libs/SDL3, replacing the dynamic prebuilt. Produces libSDL3.a (no
# SDL3.dll) so the app links SDL3 straight into the executable -- the goal
# being a clean <app>.exe + data.kres with no DLLs alongside.
#
# Compiled with -ffunction-sections -fdata-sections so the final executable's
# -Wl,--gc-sections can drop every SDL function the app never calls.
#
# The set of Windows system libraries SDL3 needs when static is recorded in
# libs/SDL3/lib/pkgconfig/sdl3-static.pc (Libs.private); the cinterop .def
# files list them so the final link resolves.
#
# Run from Git Bash on Windows. Requires: git, cmake, a mingw-w64 gcc in PATH,
# plus curl + python (used to fetch ninja if absent). Override the version with
# SDL_REF=release-3.4.10 (default below).
set -euo pipefail

SDL_REF="${SDL_REF:-release-3.4.10}"
SDL_URL="${SDL_URL:-https://github.com/libsdl-org/SDL.git}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/sdl3src"
SRC="$BUILD/src"
OUT="$BUILD/out"
PREFIX="$BUILD/prefix"
mkdir -p "$BUILD"
win() { cygpath -m "$1"; }

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

if [ ! -f "$SRC/CMakeLists.txt" ]; then
	echo ">> cloning SDL $SDL_REF"
	rm -rf "$SRC"
	git clone --depth 1 -b "$SDL_REF" "$SDL_URL" "$SRC"
fi

echo ">> configuring (static, size-sectioned)"
rm -rf "$OUT"
cmake -S "$(win "$SRC")" -B "$(win "$OUT")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER=gcc \
	-DCMAKE_BUILD_TYPE=Release \
	-DSDL_SHARED=OFF -DSDL_STATIC=ON \
	-DSDL_TESTS=OFF -DSDL_TEST_LIBRARY=OFF -DSDL_EXAMPLES=OFF \
	-DSDL_INSTALL_TESTS=OFF \
	-DCMAKE_C_FLAGS="-ffunction-sections -fdata-sections -Os" \
	-DCMAKE_INSTALL_PREFIX="$(win "$PREFIX")"

echo ">> building + installing"
rm -rf "$PREFIX"
cmake --build "$(win "$OUT")"
cmake --install "$(win "$OUT")"

echo ">> installing into $LIBS/SDL3"
rm -rf "$LIBS/SDL3"
mkdir -p "$LIBS/SDL3"
cp -R "$PREFIX/include" "$PREFIX/lib" "$LIBS/SDL3/"

echo ">> done: $LIBS/SDL3  (static libSDL3.a, no SDL3.dll)"
echo ">> SDL3 static system libs (Libs.private):"
sed -n 's/^Libs.private:/   /p' "$LIBS/SDL3/lib/pkgconfig/sdl3-static.pc" 2>/dev/null || true