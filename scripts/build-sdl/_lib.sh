#!/usr/bin/env bash
# Shared helpers for scripts/build-sdl/*.sh — host OS detection, path
# translation for CMake, Ninja discovery, and toolchain wiring. Each build
# script sources this file so it can drive the same CMake configure/build/
# install flow on macOS, Linux, and Windows without conditionals sprinkled
# through the script bodies.
#
# Assumes on every host: git, cmake in PATH. Ninja is fetched into
# libs/.build/ninja-bin/ when absent. On Windows the fetch also uses
# python + curl (Git Bash defaults).

# detect_host — normalises `uname -s` into macos / linux / windows.
detect_host() {
	case "$(uname -s)" in
		Darwin) echo "macos" ;;
		Linux) echo "linux" ;;
		MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
		*) echo "unknown" ;;
	esac
}

# cmake_path <path> — return `path` in the form CMake expects for the current
# host. On Windows CMake is a native .exe that wants Windows-style paths;
# elsewhere plain Unix paths work.
cmake_path() {
	if [ "${BUILD_SDL_HOST:-}" = "windows" ]; then
		cygpath -m "$1"
	else
		printf '%s' "$1"
	fi
}

# find_ninja <build_dir> — echo the CMake-visible path to a ninja binary. If
# no ninja is on PATH, fetches the platform-appropriate release binary once
# into <build_dir>/ninja-bin/.
find_ninja() {
	local build_dir="$1"
	if command -v ninja >/dev/null 2>&1; then
		echo "ninja"
		return
	fi
	local host="${BUILD_SDL_HOST:-$(detect_host)}"
	local dir="$build_dir/ninja-bin"
	mkdir -p "$dir"
	if [ ! -x "$dir/ninja" ] && [ ! -f "$dir/ninja.exe" ]; then
		echo ">> fetching ninja for $host" >&2
		local url
		case "$host" in
			macos)   url="https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-mac.zip" ;;
			linux)   url="https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-linux.zip" ;;
			windows) url="https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-win.zip" ;;
			*) echo "ERROR: no ninja binary for host $host" >&2; return 1 ;;
		esac
		curl -fL -o "$dir/ninja.zip" "$url"
		if [ "$host" = "windows" ]; then
			python -c "import zipfile,sys;zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" \
				"$dir/ninja.zip" "$dir"
		else
			(cd "$dir" && unzip -q -o ninja.zip)
			chmod +x "$dir/ninja"
		fi
	fi
	if [ -f "$dir/ninja.exe" ]; then
		cmake_path "$dir/ninja.exe"
	else
		echo "$dir/ninja"
	fi
}

# setup_toolchain — export CC / CXX so CMake picks up the right compiler for
# static K/N-compatible builds:
#   * macOS   → clang / clang++ from Xcode command line tools
#   * Linux   → system gcc / g++
#   * Windows → K/N's own bundled mingw-w64 gcc (matches its CRT) for C, and
#              the SYSTEM g++ for C++ (K/N's mingw is C-only). Set KN_MINGW=0
#              to force the system gcc if the K/N one is unavailable.
setup_toolchain() {
	local host="${BUILD_SDL_HOST:-$(detect_host)}"
	case "$host" in
		macos)
			export CC="${CC:-clang}"
			export CXX="${CXX:-clang++}"
			;;
		linux)
			export CC="${CC:-gcc}"
			export CXX="${CXX:-g++}"
			;;
		windows)
			if [ -z "${CXX:-}" ]; then
				local sys_gxx
				sys_gxx="$(command -v g++ 2>/dev/null || true)"
				[ -f "${sys_gxx}.exe" ] && sys_gxx="${sys_gxx}.exe"
				if [ -n "$sys_gxx" ]; then
					export CXX="$(cygpath -m "$sys_gxx")"
					echo ">> C++ compiler (K/N mingw is C-only): $CXX" >&2
				else
					echo ">> WARNING: no system g++ on PATH; SDL3's CMake configure needs a working" >&2
					echo ">>          C++ compiler and will fail. Install mingw-w64 g++." >&2
				fi
			fi
			if [ "${KN_MINGW:-1}" != "0" ]; then
				local kn_bin
				kn_bin="$(ls -d "${KONAN_DATA_DIR:-$HOME/.konan}"/dependencies/*mingw*/bin 2>/dev/null | head -1 || true)"
				if [ -n "$kn_bin" ] && [ -x "$kn_bin/gcc.exe" ]; then
					export PATH="$kn_bin:$PATH"
					echo ">> using Kotlin/Native bundled mingw: $(gcc --version | head -1)" >&2
				else
					echo ">> WARNING: K/N bundled mingw not found; using system gcc." >&2
				fi
			fi
			export CC="${CC:-gcc}"
			;;
		*)
			echo "ERROR: unsupported host: $host" >&2
			exit 1
			;;
	esac
}

# extra_cmake_args — echo host-specific extra CMake arguments (e.g. macOS
# arch pin). Meant to be interpolated unquoted into a cmake command line.
extra_cmake_args() {
	local host="${BUILD_SDL_HOST:-$(detect_host)}"
	case "$host" in
		macos)
			# Port targets macosArm64 only. Force the arch so a universal-
			# arch host build stays trimmed and matches the K/N target.
			echo "-DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0"
			;;
		*) : ;;
	esac
}
