#!/usr/bin/env bash
# Build a static SDL3_image and install it into <repo>/libs/SDL3_image. Runs
# on macOS, Linux, and Windows (Git Bash) — see _lib.sh for host handling.
#
# Produces libSDL3_image.a plus the static archives of its vendored codecs
# (zlib, libpng, libwebp) so the app links image decoding straight into the
# executable — no runtime .so / .dylib / .dll alongside.
#
# Formats: PNG + JPG (built-in stb backend) + WEBP + SVG (and the built-in
# BMP/GIF/QOI/TGA/... which need no external codec). AVIF / TIFF / JXL are OFF
# — they pull the very large dav1d / aom / libjxl submodules we don't init.
#
# Static archives don't bundle their dependencies, so we copy every vendored
# *.a out of the build tree next to libSDL3_image.a; the app's linker line
# lists them. URL + ref come from scripts/build-sdl/build-sdl.properties
# (SDL_IMAGE_URL / SDL_IMAGE_REF); a same-named env var overrides for one-offs.
#
# Run build-sdl3.sh FIRST (SDL3_image links against libs/SDL3).
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_lib.sh
source "$TOOLS/_lib.sh"

BUILD_SDL_HOST="$(detect_host)"
setup_toolchain

SDL_IMAGE_URL="$(require_manifest SDL_IMAGE_URL)"
SDL_IMAGE_REF="$(require_manifest SDL_IMAGE_REF)"
REPO="$(cd "$TOOLS/../.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/sdl3imgsrc"
SRC="$BUILD/src"
OUT="$BUILD/out"
PREFIX="$BUILD/prefix"
mkdir -p "$BUILD"

[ -d "$LIBS/SDL3/lib" ] || { echo "ERROR: static SDL3 not found — run scripts/build-sdl/build-sdl3.sh first" >&2; exit 1; }

NINJA="$(find_ninja "$BUILD")"

# Re-clone whenever URL / ref changes so switching versions is clean.
MARKER="$BUILD/.source"
WANT="$SDL_IMAGE_URL $SDL_IMAGE_REF"
if [ ! -f "$SRC/CMakeLists.txt" ] || [ "$(cat "$MARKER" 2>/dev/null)" != "$WANT" ]; then
	echo ">> cloning SDL_image $SDL_IMAGE_REF"
	rm -rf "$SRC"
	git clone --depth 1 -b "$SDL_IMAGE_REF" "$SDL_IMAGE_URL" "$SRC"
	echo "$WANT" > "$MARKER"
fi

echo ">> fetching vendored codec submodules (zlib, libpng, libwebp)"
git -C "$SRC" submodule update --init --depth 1 \
	external/zlib external/libpng external/libwebp

# Windows: libpng (vendored codec) also uses setjmp; see build-freetype.sh
# for the USE_NO_MINGW_SETJMP_TWO_ARGS rationale. Applied here too so libpng
# gets it via propagation to the vendored subproject build.
CFLAGS_EXTRA="-ffunction-sections -fdata-sections -Os"
if [ "$BUILD_SDL_HOST" = "windows" ]; then
	CFLAGS_EXTRA="$CFLAGS_EXTRA -DUSE_NO_MINGW_SETJMP_TWO_ARGS=1"
fi

echo ">> configuring (static, vendored PNG/WEBP + stb JPG + built-in SVG; AVIF/TIF/JXL off)"
rm -rf "$OUT"
# shellcheck disable=SC2046
cmake -S "$(cmake_path "$SRC")" -B "$(cmake_path "$OUT")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER="$CC" -DCMAKE_CXX_COMPILER="${CXX:-$CC}" \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_PREFIX_PATH="$(cmake_path "$LIBS/SDL3")" \
	-DBUILD_SHARED_LIBS=OFF \
	-DCMAKE_POSITION_INDEPENDENT_CODE=ON \
	-DSDLIMAGE_VENDORED=ON \
	-DSDLIMAGE_DEPS_SHARED=OFF \
	-DSDLIMAGE_SAMPLES=OFF -DSDLIMAGE_TESTS=OFF -DSDLIMAGE_INSTALL=ON \
	-DSDLIMAGE_PNG=ON -DSDLIMAGE_JPG=ON -DSDLIMAGE_WEBP=ON -DSDLIMAGE_SVG=ON \
	-DSDLIMAGE_AVIF=OFF -DSDLIMAGE_TIF=OFF -DSDLIMAGE_JXL=OFF \
	-DCMAKE_C_FLAGS="$CFLAGS_EXTRA" \
	$(extra_cmake_args) \
	-DCMAKE_INSTALL_PREFIX="$(cmake_path "$PREFIX")"

echo ">> building + installing"
rm -rf "$PREFIX"
cmake --build "$(cmake_path "$OUT")"
cmake --install "$(cmake_path "$OUT")"

echo ">> installing into $LIBS/SDL3_image (+ vendored codec archives)"
rm -rf "$LIBS/SDL3_image"
mkdir -p "$LIBS/SDL3_image/lib"
cp -R "$PREFIX/include" "$LIBS/SDL3_image/"
find "$PREFIX/lib" -name 'libSDL3_image*.a' -exec cp {} "$LIBS/SDL3_image/lib/" \;
# Harvest the vendored codec static archives from the build tree.
find "$OUT" -name '*.a' ! -name 'libSDL3_image.a' -exec cp -f {} "$LIBS/SDL3_image/lib/" \;
# libpng installs both libpng.a and an identical libpng16.a — keep one.
rm -f "$LIBS/SDL3_image/lib/libpng.a"
# Normalize zlib archive name across platforms: on Windows CMake produces
# libzlibstatic.a, elsewhere libz.a. The cinterop staticLibraries directive
# lists a single filename per platform, so drop a libz.a alias if only the
# libzlibstatic.a form exists.
if [ -f "$LIBS/SDL3_image/lib/libzlibstatic.a" ] && [ ! -f "$LIBS/SDL3_image/lib/libz.a" ]; then
	cp "$LIBS/SDL3_image/lib/libzlibstatic.a" "$LIBS/SDL3_image/lib/libz.a"
fi

echo ">> done: $LIBS/SDL3_image"
echo ">> static archives present:"
ls "$LIBS/SDL3_image/lib/" | sed 's/^/   /'
