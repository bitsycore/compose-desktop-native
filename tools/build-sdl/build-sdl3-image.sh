#!/usr/bin/env bash
# Build a static SDL3_image for the Windows mingwX64 target and install it into
# <repo>/libs/SDL3_image, replacing the dynamic prebuilt. Produces
# libSDL3_image.a plus the static archives of its vendored codecs (zlib,
# libpng, libjpeg, libwebp) so the app links image decoding straight into the
# executable -- no SDL3_image.dll / libpng16.dll / ... alongside.
#
# Formats: PNG + JPG + WEBP + SVG (and the built-in BMP/GIF/QOI/TGA/... which
# need no external codec). AVIF / TIFF / JXL are OFF -- they pull the very
# large dav1d / aom / libjxl submodules we don't init.
#
# Static archives don't bundle their dependencies, so we copy every vendored
# *.a out of the build tree next to libSDL3_image.a; the cinterop .def lists
# them on the final link line.
#
# Run from Git Bash on Windows. Requires: git, cmake, mingw-w64 gcc/g++, plus
# curl + python (ninja). Run build-sdl3.sh FIRST (SDL3_image links SDL3).
# Override the version with SDL_IMAGE_REF=release-3.4.4.
set -euo pipefail

SDL_IMAGE_REF="${SDL_IMAGE_REF:-release-3.4.4}"
SDL_IMAGE_URL="${SDL_IMAGE_URL:-https://github.com/libsdl-org/SDL_image.git}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LIBS="$REPO/libs"
BUILD="$LIBS/.build/sdl3imgsrc"
SRC="$BUILD/src"
OUT="$BUILD/out"
PREFIX="$BUILD/prefix"
mkdir -p "$BUILD"
win() { cygpath -m "$1"; }

[ -f "$LIBS/SDL3/lib/libSDL3.a" ] || { echo "ERROR: static SDL3 not found -- run tools/build-sdl/build-sdl3.sh first" >&2; exit 1; }

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
	echo ">> cloning SDL_image $SDL_IMAGE_REF"
	rm -rf "$SRC"
	git clone --depth 1 -b "$SDL_IMAGE_REF" "$SDL_IMAGE_URL" "$SRC"
fi
# Only the codecs we enable -- avoids the huge dav1d/aom/libjxl/libtiff trees.
# JPG is handled by the built-in stb_image backend, so no external libjpeg.
echo ">> fetching vendored codec submodules (zlib, libpng, libwebp)"
git -C "$SRC" submodule update --init --depth 1 \
	external/zlib external/libpng external/libwebp

echo ">> configuring (static, vendored PNG/WEBP + stb JPG + built-in SVG; AVIF/TIF/JXL off)"
rm -rf "$OUT"
cmake -S "$(win "$SRC")" -B "$(win "$OUT")" -G Ninja \
	-DCMAKE_MAKE_PROGRAM="$NINJA" \
	-DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++ \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_PREFIX_PATH="$(win "$LIBS/SDL3")" \
	-DBUILD_SHARED_LIBS=OFF \
	-DSDLIMAGE_VENDORED=ON \
	-DSDLIMAGE_DEPS_SHARED=OFF \
	-DSDLIMAGE_SAMPLES=OFF -DSDLIMAGE_TESTS=OFF -DSDLIMAGE_INSTALL=ON \
	-DSDLIMAGE_PNG=ON -DSDLIMAGE_JPG=ON -DSDLIMAGE_WEBP=ON -DSDLIMAGE_SVG=ON \
	-DSDLIMAGE_AVIF=OFF -DSDLIMAGE_TIF=OFF -DSDLIMAGE_JXL=OFF \
	-DCMAKE_C_FLAGS="-ffunction-sections -fdata-sections -Os" \
	-DCMAKE_INSTALL_PREFIX="$(win "$PREFIX")"

echo ">> building + installing"
rm -rf "$PREFIX"
cmake --build "$(win "$OUT")"
cmake --install "$(win "$OUT")"

echo ">> installing into $LIBS/SDL3_image (+ vendored codec archives)"
rm -rf "$LIBS/SDL3_image"
mkdir -p "$LIBS/SDL3_image/lib"
cp -R "$PREFIX/include" "$LIBS/SDL3_image/"
cp "$PREFIX/lib/libSDL3_image.a" "$LIBS/SDL3_image/lib/"
# Harvest the vendored codec static archives from the build tree.
find "$OUT" -name '*.a' ! -name 'libSDL3_image.a' -exec cp -f {} "$LIBS/SDL3_image/lib/" \;
# libpng installs both libpng.a and an identical libpng16.a -- keep one.
rm -f "$LIBS/SDL3_image/lib/libpng.a"

echo ">> done: $LIBS/SDL3_image"
echo ">> static archives present:"
ls "$LIBS/SDL3_image/lib/" | sed 's/^/   /'