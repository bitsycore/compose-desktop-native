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
# SDL_REF=release-3.4.12 (default below) -- changing SDL_REF/SDL_URL re-clones
# automatically.
#
# SDL >= 3.4 also needs a working C++ compiler on Windows (GameInput backend;
# it compiles to an empty stub when gameinput.h is absent). When run via
# build-all.sh, CXX is pre-set to the system g++ because K/N's bundled mingw
# is C-only; standalone runs let CMake find whatever c++/g++ is on PATH.
#
# The D3D12 render driver and the SDL_GPU subsystem (whose Windows backend
# is also D3D12) are forced OFF: SDL >= 3.4 needs IDXGIFactory6 /
# DXGI_GPU_PREFERENCE_* which K/N's bundled mingw-w64 headers (gcc 9.2 era)
# predate -- its dxgi1_6.h exists, so SDL's HAVE_DXGI1_6_H gates pass, but
# compilation then fails. D3D11 (the default Windows driver) is unaffected,
# and this project only uses the SDL_Render API, never SDL_GPU.
set -euo pipefail

SDL_REF="${SDL_REF:-release-3.4.12}"
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

# Re-clone whenever URL/ref changes so switching versions is clean.
MARKER="$BUILD/.source"
WANT="$SDL_URL $SDL_REF"
if [ ! -f "$SRC/CMakeLists.txt" ] || [ "$(cat "$MARKER" 2>/dev/null)" != "$WANT" ]; then
	echo ">> cloning SDL $SDL_REF"
	rm -rf "$SRC"
	git clone --depth 1 -b "$SDL_REF" "$SDL_URL" "$SRC"
	echo "$WANT" > "$MARKER"
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
	-DSDL_RENDER_D3D12=OFF \
	-DSDL_GPU=OFF \
	-DCMAKE_C_FLAGS="-ffunction-sections -fdata-sections -Os" \
	-DCMAKE_CXX_FLAGS="-ffunction-sections -fdata-sections -Os" \
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