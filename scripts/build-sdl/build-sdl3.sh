#!/usr/bin/env bash
# Build a static SDL3 and install it into <repo>/libs/SDL3. Runs on macOS,
# Linux, and Windows (Git Bash) — see _lib.sh for host handling.
#
# Produces libSDL3.a (no shared lib) so the app links SDL3 straight into the
# executable — clean <app> + data.kres with no runtime .dylib / .so / .dll
# alongside. Compiled with -ffunction-sections -fdata-sections so the final
# link's --gc-sections can drop every SDL function the app never calls.
#
# The set of system libraries SDL3 needs when static is recorded in
# libs/SDL3/lib/pkgconfig/sdl3-static.pc (Libs.private) after this runs; the
# app's linker line has to include those. Override with SDL_REF=release-3.4.12
# (default) or SDL_URL=... — swapping either re-clones automatically.
#
# On Windows SDL >= 3.4 needs a working C++ compiler (GameInput backend). See
# _lib.sh's setup_toolchain: system g++ is captured for CXX because K/N's
# bundled mingw is C-only. The D3D12 render driver and SDL_GPU subsystem are
# forced OFF because K/N's mingw dxgi1_6.h is too old for SDL >= 3.4's
# IDXGIFactory6 / DXGI_GPU_PREFERENCE_* usage. D3D11 (default Windows driver)
# is unaffected, and this project only ever uses SDL_Render.
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_lib.sh
source "$TOOLS/_lib.sh"

BUILD_SDL_HOST="$(detect_host)"
setup_toolchain

SDL_REF="${SDL_REF:-release-3.4.12}"
SDL_URL="${SDL_URL:-https://github.com/libsdl-org/SDL.git}"
REPO="$(cd "$TOOLS/../.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/sdl3src"
SRC="$BUILD/src"
OUT="$BUILD/out"
PREFIX="$BUILD/prefix"
mkdir -p "$BUILD"

NINJA="$(find_ninja "$BUILD")"

# Re-clone whenever URL/ref changes so switching versions is clean.
MARKER="$BUILD/.source"
WANT="$SDL_URL $SDL_REF"
if [ ! -f "$SRC/CMakeLists.txt" ] || [ "$(cat "$MARKER" 2>/dev/null)" != "$WANT" ]; then
	echo ">> cloning SDL $SDL_REF"
	rm -rf "$SRC"
	git clone --depth 1 -b "$SDL_REF" "$SDL_URL" "$SRC"
	echo "$WANT" > "$MARKER"
fi

# Windows-only extras: kill the D3D12 driver + GPU subsystem (K/N mingw
# dxgi1_6.h too old). On macOS/Linux SDL picks Metal/Cocoa or Wayland/X11
# based on what's available in the host SDK.
WIN_ONLY=""
if [ "$BUILD_SDL_HOST" = "windows" ]; then
	WIN_ONLY="-DSDL_RENDER_D3D12=OFF -DSDL_GPU=OFF"
fi

echo ">> configuring (static, size-sectioned)"
rm -rf "$OUT"
# shellcheck disable=SC2046
cmake -S "$(cmake_path "$SRC")" -B "$(cmake_path "$OUT")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER="$CC" \
	-DCMAKE_BUILD_TYPE=Release \
	-DBUILD_SHARED_LIBS=OFF \
	-DCMAKE_POSITION_INDEPENDENT_CODE=ON \
	-DSDL_SHARED=OFF -DSDL_STATIC=ON \
	-DSDL_TESTS=OFF -DSDL_TEST_LIBRARY=OFF -DSDL_EXAMPLES=OFF \
	-DSDL_INSTALL_TESTS=OFF \
	$WIN_ONLY \
	-DCMAKE_C_FLAGS="-ffunction-sections -fdata-sections -Os" \
	-DCMAKE_CXX_FLAGS="-ffunction-sections -fdata-sections -Os" \
	$(extra_cmake_args) \
	-DCMAKE_INSTALL_PREFIX="$(cmake_path "$PREFIX")"

echo ">> building + installing"
rm -rf "$PREFIX"
cmake --build "$(cmake_path "$OUT")"
cmake --install "$(cmake_path "$OUT")"

echo ">> installing into $LIBS/SDL3"
rm -rf "$LIBS/SDL3"
mkdir -p "$LIBS/SDL3"
cp -R "$PREFIX/include" "$PREFIX/lib" "$LIBS/SDL3/"

echo ">> done: $LIBS/SDL3  (static libSDL3.a, no shared lib)"
echo ">> SDL3 static system libs (Libs.private):"
sed -n 's/^Libs.private:/   /p' "$LIBS/SDL3/lib/pkgconfig/sdl3-static.pc" 2>/dev/null || true
