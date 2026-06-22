#!/usr/bin/env bash
# Download the latest SDL3 and SDL3_image *mingw* development releases from
# GitHub and install their x86_64-w64-mingw32 include/lib/bin trees into
# <repo>/libs/{SDL3,SDL3_image} — the layout the cinterop .def files and the
# demo's DLL-bundling expect.
#
# SDL3_ttf is NOT fetched here: we build it from source with our variable-font
# axis patch (the API isn't in any upstream release yet). After this script,
# run tools/build-freetype.sh then tools/build-sdl3-ttf.sh to populate
# libs/SDL3_ttf. Re-running this script will NOT touch SDL3_ttf.
#
# Run from Git Bash on Windows. Requires: curl, 7z, python (to parse the
# GitHub release JSON). Each lib's whole bin/ is copied, so SDL3_image's
# format DLLs (libpng, libjpeg, …) come along.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS="$REPO/libs"
TMP="$LIBS/.build/sdl3"
mkdir -p "$TMP"

# Resolve the browser_download_url of the first asset whose name matches $2 in
# the latest release of repo $1.
asset_url() {
	curl -fsSL "https://api.github.com/repos/$1/releases/latest" \
		| python -c "import sys,json,re;d=json.load(sys.stdin);print(next(a['browser_download_url'] for a in d['assets'] if re.search(r'''$2''',a['name'])))"
}

install_lib() {  # repo  asset-name-regex  dest-dir-name
	local repo="$1" rx="$2" dest="$3"
	echo ">> $dest: locating latest mingw release of $repo"
	local url; url="$(asset_url "$repo" "$rx")"
	echo "   $url"
	local zip="$TMP/$(basename "$url")"
	curl -fL -o "$zip" "$url"
	local ex="$TMP/extract_$dest"
	rm -rf "$ex"; mkdir -p "$ex"
	( cd "$ex" && 7z x -y "$(cygpath -w "$zip")" >/dev/null )
	local src; src="$(find "$ex" -type d -name 'x86_64-w64-mingw32' | head -1)"
	[ -n "$src" ] || { echo "   ERROR: x86_64-w64-mingw32 not found in $dest archive" >&2; exit 1; }
	rm -rf "$LIBS/$dest"; mkdir -p "$LIBS/$dest"
	cp -R "$src/include" "$src/lib" "$src/bin" "$LIBS/$dest/"
	echo "   -> $LIBS/$dest  (include/ lib/ bin/)"
}

install_lib "libsdl-org/SDL"       'SDL3-devel-.*-mingw\.zip'        SDL3
install_lib "libsdl-org/SDL_image" 'SDL3_image-devel-.*-mingw\.zip' SDL3_image

echo ">> done. SDL3 / SDL3_image installed under $LIBS"
echo ">> next: tools/build-freetype.sh then tools/build-sdl3-ttf.sh (patched SDL3_ttf)"
