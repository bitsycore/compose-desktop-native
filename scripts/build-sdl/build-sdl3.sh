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
# app's linker line has to include those. URL + ref come from
# scripts/build-sdl/build-sdl.properties (SDL_URL / SDL_REF); a same-named env
# var overrides for one-offs. Swapping either value re-clones automatically.
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

SDL_URL="$(require_manifest SDL_URL)"
SDL_REF="$(require_manifest SDL_REF)"
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

# Linux-only extras: SDL3's CMake feature-detection sees glibc's memfd_create
# and posix_spawn_file_actions_addchdir_np on modern hosts (>= 2.27 / 2.29),
# but K/N's LLD sysroot for linuxX64 doesn't have them at final app link.
# Pre-set the cache vars so check_symbol_exists honors our OFF value (it only
# checks vars that aren't already cached) and SDL3 falls back to its portable
# implementations.
LINUX_ONLY=""
if [ "$BUILD_SDL_HOST" = "linux" ]; then
	LINUX_ONLY="-DHAVE_MEMFD_CREATE=0 -DHAVE_POSIX_SPAWN_FILE_ACTIONS_ADDCHDIR_NP=0"
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
	$LINUX_ONLY \
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

# Windows: mingw-w64's <setjmp.h> emits external references to
# __intrinsic_setjmpex from any TU using setjmp() (freetype, libpng, ...).
# K/N's LLD sysroot for mingwX64 doesn't provide this symbol and the LLD
# built by K/N 2.4.0 doesn't accept --defsym as a linker workaround.
# Compile a tiny stub that aliases the symbol to msvcrt's _setjmpex, and
# append its object into libSDL3.a so the archive travels with everything
# needed to link consumers.
if [ "$BUILD_SDL_HOST" = "windows" ]; then
	echo ">> baking __intrinsic_setjmpex alias stub into libSDL3.a"
	cat > "$BUILD/mingw_setjmp_stub.c" <<'CSTUB'
#include <setjmp.h>
extern int __cdecl _setjmpex(jmp_buf _Buf, void *_Ctx);
int __cdecl __intrinsic_setjmpex(jmp_buf _Buf, void *_Ctx) {
	return _setjmpex(_Buf, _Ctx);
}
CSTUB
	"$CC" -c -Os "$BUILD/mingw_setjmp_stub.c" -o "$BUILD/mingw_setjmp_stub.o"
	ar rcs "$LIBS/SDL3/lib/libSDL3.a" "$BUILD/mingw_setjmp_stub.o"
fi

echo ">> done: $LIBS/SDL3  (static libSDL3.a, no shared lib)"
echo ">> SDL3 static system libs (Libs.private):"
sed -n 's/^Libs.private:/   /p' "$LIBS/SDL3/lib/pkgconfig/sdl3-static.pc" 2>/dev/null || true
