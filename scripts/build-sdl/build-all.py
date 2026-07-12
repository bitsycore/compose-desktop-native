#!/usr/bin/env python3
"""
One-shot dependency setup — builds every native dependency this project links
into the executable as a STATIC library, installing each into <repo>/libs/.
Runs on macOS, Linux, and Windows with plain Python 3 — no Git Bash, curl or
cygpath needed. Replaces the former build-*.sh scripts.

Build order matters — later libraries link the earlier ones:
  1. freetype    -> libs/FreeType/lib/libfreetype.a
  2. sdl3        -> libs/SDL3/lib/libSDL3.a
  3. sdl3-image  -> libs/SDL3_image   (vendored PNG/JPG/SVG/WEBP; needs SDL3)
  4. sdl3-ttf    -> libs/SDL3_ttf    (variable-font-axes patch; needs SDL3 + FreeType)

Usage:
  python3 build-all.py                 build everything, in order
  python3 build-all.py sdl3 sdl3-ttf   build a subset (canonical order enforced)

Everything is static + HarfBuzz/plutosvg-free, so the app links to a clean
<app> + data.kres with no runtime .dylib / .so / .dll alongside.

Requires on every host: git, cmake, python 3. Ninja is fetched into
libs/.build/ninja-bin when absent. Windows also needs a mingw-w64 g++ on PATH
(for SDL3's GameInput backend; K/N's bundled mingw is C-only). URLs + refs for
each library come from build-sdl.properties; edit it to change versions or
repos, or set the corresponding env vars (SDL_URL / SDL_REF / SDL_IMAGE_URL /
SDL_IMAGE_REF / SDL_TTF_URL / SDL_TTF_REF / FREETYPE_URL / FREETYPE_TAG) for a
one-off override.
"""

import argparse
import os
import platform
import shutil
import stat
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

# ==================
# MARK: Constants
# ==================

kToolsDir = Path(__file__).resolve().parent
kRepoRoot = kToolsDir.parent.parent
kLibsDir = kRepoRoot / "libs"
kBuildOrder = ["freetype", "sdl3", "sdl3-image", "sdl3-ttf"]

# Compile flags shared by every library: size-optimised and sectioned so the
# final link's --gc-sections can drop every function the app never calls.
kSizeFlags = "-ffunction-sections -fdata-sections -Os"

kNinjaVersion = "1.12.1"
kNinjaZipByHost = {
	"macos": "ninja-mac.zip",
	"linux": "ninja-linux.zip",
	"windows": "ninja-win.zip",
}

# Windows: mingw-w64's <setjmp.h> emits external references to
# __intrinsic_setjmpex from any TU using setjmp() (freetype, libpng, ...).
# K/N's LLD sysroot for mingwX64 doesn't provide this symbol and the LLD
# built by K/N 2.4.0 doesn't accept --defsym as a linker workaround, so a
# tiny stub aliases the symbol to msvcrt's _setjmpex (see bakeSetjmpStub).
kSetjmpStubSource = """#include <setjmp.h>

extern int __cdecl _setjmpex(jmp_buf _Buf, void *_Ctx);

/** Alias mingw-w64's __intrinsic_setjmpex onto msvcrt's _setjmpex. */
int __cdecl __intrinsic_setjmpex(jmp_buf _Buf, void *_Ctx) {
	return _setjmpex(_Buf, _Ctx);
}
"""

# ==================
# MARK: Helpers
# ==================

def detectHost():
	"""Normalise the running platform into macos / linux / windows / unknown.
	BUILD_SDL_HOST in the environment overrides detection (testing hook)."""
	vOverride = os.environ.get("BUILD_SDL_HOST", "")
	if vOverride:
		return vOverride
	vSystem = platform.system()
	if vSystem == "Darwin":
		return "macos"
	if vSystem == "Linux":
		return "linux"
	if vSystem == "Windows" or vSystem.startswith(("MINGW", "MSYS", "CYGWIN")):
		return "windows"
	return "unknown"


def run(inArgs, inCwd=None):
	"""Echo and run a command, aborting the whole build on a non-zero exit."""
	vArgs = [str(vArg) for vArg in inArgs]
	print(">> $ " + " ".join(vArgs), flush=True)
	subprocess.run(vArgs, cwd=inCwd, check=True)


def forceRmtree(inPath):
	"""rm -rf equivalent that clears read-only bits before retrying — git
	object files in cloned trees are read-only on Windows and would make a
	plain shutil.rmtree fail."""
	vPath = Path(inPath)
	if not vPath.exists():
		return

	def clearAndRetry(inFunc, inTarget, inExcInfo):
		"""onerror/onexc hook: make the entry writable and retry the delete."""
		os.chmod(inTarget, stat.S_IWRITE)
		inFunc(inTarget)

	if sys.version_info >= (3, 12):
		shutil.rmtree(vPath, onexc=clearAndRetry)
	else:
		shutil.rmtree(vPath, onerror=clearAndRetry)


def findNinja(inBuildDir, inHost):
	"""Return the CMake-visible path to a ninja binary. If no ninja is on
	PATH, fetches the platform-appropriate release binary once into
	<inBuildDir>/ninja-bin."""
	if shutil.which("ninja"):
		return "ninja"
	vDir = Path(inBuildDir) / "ninja-bin"
	vDir.mkdir(parents=True, exist_ok=True)
	vExe = vDir / ("ninja.exe" if inHost == "windows" else "ninja")
	if not vExe.is_file():
		vZipName = kNinjaZipByHost.get(inHost)
		if vZipName is None:
			raise SystemExit("ERROR: no ninja binary for host " + inHost)
		print(">> fetching ninja for " + inHost, flush=True)
		vUrl = ("https://github.com/ninja-build/ninja/releases/download/v"
			+ kNinjaVersion + "/" + vZipName)
		vZipPath = vDir / "ninja.zip"
		urllib.request.urlretrieve(vUrl, vZipPath)
		with zipfile.ZipFile(vZipPath) as vZip:
			vZip.extractall(vDir)
		if inHost != "windows":
			vExe.chmod(vExe.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
	return vExe.as_posix()


def setupToolchain(inHost):
	"""Export CC / CXX so CMake picks up the right compiler for static
	K/N-compatible builds, returning (cc, cxx):
	  macos   -> clang / clang++ from Xcode command line tools
	  linux   -> system gcc / g++
	  windows -> K/N's own bundled mingw-w64 gcc (matches its CRT) for C, and
	             the SYSTEM g++ for C++ (K/N's mingw is C-only). Set KN_MINGW=0
	             to force the system gcc if the K/N one is unavailable."""
	if inHost == "macos":
		vCc = os.environ.get("CC") or "clang"
		vCxx = os.environ.get("CXX") or "clang++"
	elif inHost == "linux":
		vCc = os.environ.get("CC") or "gcc"
		vCxx = os.environ.get("CXX") or "g++"
	elif inHost == "windows":
		vCxx = os.environ.get("CXX") or ""
		if not vCxx:
			vSysGxx = shutil.which("g++")
			if vSysGxx:
				vCxx = Path(vSysGxx).as_posix()
				print(">> C++ compiler (K/N mingw is C-only): " + vCxx)
			else:
				print(">> WARNING: no system g++ on PATH; SDL3's CMake configure needs a working")
				print(">>          C++ compiler and will fail. Install mingw-w64 g++.")
		if os.environ.get("KN_MINGW", "1") != "0":
			vKonanDir = Path(os.environ.get("KONAN_DATA_DIR") or (Path.home() / ".konan"))
			vKnBin = next((vBin for vBin in sorted(vKonanDir.glob("dependencies/*mingw*/bin"))
				if (vBin / "gcc.exe").is_file()), None)
			if vKnBin is not None:
				os.environ["PATH"] = str(vKnBin) + os.pathsep + os.environ["PATH"]
				vGccVersion = subprocess.run(["gcc", "--version"], capture_output=True,
					text=True).stdout.splitlines()[0]
				print(">> using Kotlin/Native bundled mingw: " + vGccVersion)
			else:
				print(">> WARNING: K/N bundled mingw not found; using system gcc.")
		vCc = os.environ.get("CC") or "gcc"
	else:
		raise SystemExit("ERROR: unsupported host: " + inHost)
	os.environ["CC"] = vCc
	if vCxx:
		os.environ["CXX"] = vCxx
	return vCc, vCxx


def extraCmakeArgs(inHost):
	"""Host-specific extra CMake arguments. The port targets macosArm64 only —
	force the arch so a universal-arch host build stays trimmed and matches
	the K/N target."""
	if inHost == "macos":
		return ["-DCMAKE_OSX_ARCHITECTURES=arm64", "-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0"]
	return []

# ==================
# MARK: Manifest — build-sdl.properties
# ==================
# One file at scripts/build-sdl/build-sdl.properties defines the URL + ref for
# every static library this script pulls. Format is `KEY=value`, with
# `KEY_<host>=value` per-host overrides (host = macos / linux / windows).

def manifestValue(inKey, inHost):
	"""Resolve inKey, honoring:
	  1. Environment variable of that name  (highest priority)
	  2. KEY_<host> in build-sdl.properties  (only when host matches)
	  3. KEY        in build-sdl.properties  (base)
	Returns "" if none set — pair with requireManifest to make the key
	mandatory. MANIFEST_FILE can be pointed at a different file for testing."""
	vEnv = os.environ.get(inKey, "")
	if vEnv:
		return vEnv
	vFile = Path(os.environ.get("MANIFEST_FILE") or (kToolsDir / "build-sdl.properties"))
	if not vFile.is_file():
		return ""
	vBase = ""
	vHostVal = ""
	for vLine in vFile.read_text(encoding="utf-8").splitlines():
		vLine = vLine.strip()
		if not vLine or vLine.startswith("#") or "=" not in vLine:
			continue
		vName, vValue = vLine.split("=", 1)
		if vName == inKey + "_" + inHost and not vHostVal:
			vHostVal = vValue.strip()
		elif vName == inKey and not vBase:
			vBase = vValue.strip()
	return vHostVal or vBase


def requireManifest(inKey, inHost):
	"""manifestValue, or fail with a clear error if the key resolves empty."""
	vVal = manifestValue(inKey, inHost)
	if not vVal:
		raise SystemExit("ERROR: " + inKey + " not defined — add it to "
			+ str(kToolsDir / "build-sdl.properties") + " or export it as an env var")
	return vVal

# ==================
# MARK: Shared build flow
# ==================

def cloneIfChanged(inBuildDir, inUrl, inRef, inBlobFilter=False):
	"""Clone inUrl at inRef into <inBuildDir>/src, re-cloning whenever URL/ref
	changes so switching versions is clean. inBlobFilter uses a blobless full
	clone + checkout instead of --depth 1 -b, so inRef can be any commit.
	Returns the src path."""
	vSrc = Path(inBuildDir) / "src"
	vMarker = Path(inBuildDir) / ".source"
	vWant = inUrl + " " + inRef
	vHave = vMarker.read_text(encoding="utf-8").strip() if vMarker.is_file() else ""
	if not (vSrc / "CMakeLists.txt").is_file() or vHave != vWant:
		print(">> cloning " + inUrl + " @ " + inRef)
		forceRmtree(vSrc)
		if inBlobFilter:
			run(["git", "clone", "--filter=blob:none", inUrl, vSrc])
			run(["git", "-C", vSrc, "checkout", "--quiet", inRef])
		else:
			run(["git", "clone", "--depth", "1", "-b", inRef, inUrl, vSrc])
		vMarker.write_text(vWant + "\n", encoding="utf-8")
	return vSrc


def cmakeConfigure(inSrc, inOut, inNinja, inCc, inHost, inPrefix, inExtra):
	"""Run the CMake configure shared by every library (Ninja, Release,
	static, PIC, size-sectioned C flags) plus per-library inExtra args."""
	run(["cmake", "-S", Path(inSrc).as_posix(), "-B", Path(inOut).as_posix(), "-G", "Ninja",
		"-DCMAKE_MAKE_PROGRAM=" + inNinja,
		"-DCMAKE_C_COMPILER=" + inCc,
		"-DCMAKE_BUILD_TYPE=Release",
		"-DBUILD_SHARED_LIBS=OFF",
		"-DCMAKE_POSITION_INDEPENDENT_CODE=ON"]
		+ inExtra
		+ ["-DCMAKE_C_FLAGS=" + kSizeFlags]
		+ extraCmakeArgs(inHost)
		+ ["-DCMAKE_INSTALL_PREFIX=" + Path(inPrefix).as_posix()])


def cmakeBuildInstall(inOut):
	"""cmake --build then --install of an already-configured build tree."""
	run(["cmake", "--build", Path(inOut).as_posix()])
	run(["cmake", "--install", Path(inOut).as_posix()])

# ==================
# MARK: FreeType
# ==================

def buildFreetype(inHost, inCc, inCxx):
	"""Build a self-contained static FreeType into libs/FreeType. Optional
	dependencies (PNG / HarfBuzz / Brotli / BZip2 / system zlib) are DISABLED —
	only core + variable-font (MM / GX) support is kept, which is all the SDL3
	renderer needs for Material Symbols axes and variable Roboto. The result
	is a static libfreetype.a with NO external DLLs / dylibs that links
	straight into the Kotlin/Native binary (the freetype.def does -lfreetype)."""
	vBuild = kLibsDir / ".build" / "freetype"
	vBuild.mkdir(parents=True, exist_ok=True)
	vNinja = findNinja(vBuild, inHost)
	vUrl = requireManifest("FREETYPE_URL", inHost)
	vTag = requireManifest("FREETYPE_TAG", inHost)
	vSrc = cloneIfChanged(vBuild, vUrl, vTag)

	print(">> configuring (static, optional deps off)")
	cmakeConfigure(vSrc, vBuild / "out", vNinja, inCc, inHost, kLibsDir / "FreeType", [
		"-DFT_DISABLE_HARFBUZZ=ON", "-DFT_DISABLE_PNG=ON", "-DFT_DISABLE_BROTLI=ON",
		"-DFT_DISABLE_BZIP2=ON", "-DFT_DISABLE_ZLIB=ON",
	])

	print(">> building + installing")
	cmakeBuildInstall(vBuild / "out")
	print(">> done: " + str(kLibsDir / "FreeType") + " (static libfreetype.a, no runtime deps)")

# ==================
# MARK: SDL3
# ==================

def buildSdl3(inHost, inCc, inCxx):
	"""Build a static SDL3 (no shared lib) into libs/SDL3 so the app links
	SDL3 straight into the executable. The set of system libraries SDL3 needs
	when static is recorded in libs/SDL3/lib/pkgconfig/sdl3-static.pc
	(Libs.private) after this runs; the app's linker line has to include those.

	On Windows SDL >= 3.4 needs a working C++ compiler (GameInput backend) —
	see setupToolchain. The D3D12 render driver and SDL_GPU subsystem are
	forced OFF because K/N's mingw dxgi1_6.h is too old for SDL >= 3.4's
	IDXGIFactory6 / DXGI_GPU_PREFERENCE_* usage. D3D11 (default Windows
	driver) is unaffected, and this project only ever uses SDL_Render."""
	vBuild = kLibsDir / ".build" / "sdl3src"
	vBuild.mkdir(parents=True, exist_ok=True)
	vNinja = findNinja(vBuild, inHost)
	vUrl = requireManifest("SDL_URL", inHost)
	vRef = requireManifest("SDL_REF", inHost)
	vSrc = cloneIfChanged(vBuild, vUrl, vRef)
	vOut = vBuild / "out"
	vPrefix = vBuild / "prefix"

	vExtra = [
		"-DSDL_SHARED=OFF", "-DSDL_STATIC=ON",
		"-DSDL_TESTS=OFF", "-DSDL_TEST_LIBRARY=OFF", "-DSDL_EXAMPLES=OFF",
		"-DSDL_INSTALL_TESTS=OFF",
		"-DCMAKE_CXX_FLAGS=" + kSizeFlags,
	]
	if inHost == "windows":
		# Kill the D3D12 driver + GPU subsystem (K/N mingw dxgi1_6.h too old).
		vExtra += ["-DSDL_RENDER_D3D12=OFF", "-DSDL_GPU=OFF"]
	if inHost == "linux":
		# SDL3's CMake feature-detection sees glibc's memfd_create and
		# posix_spawn_file_actions_addchdir_np on modern hosts (>= 2.27 /
		# 2.29), but K/N's LLD sysroot for linuxX64 doesn't have them at final
		# app link. Pre-set the cache vars so check_symbol_exists honors our
		# OFF value (it only checks vars that aren't already cached) and SDL3
		# falls back to its portable implementations.
		vExtra += ["-DHAVE_MEMFD_CREATE=0", "-DHAVE_POSIX_SPAWN_FILE_ACTIONS_ADDCHDIR_NP=0"]

	print(">> configuring (static, size-sectioned)")
	forceRmtree(vOut)
	cmakeConfigure(vSrc, vOut, vNinja, inCc, inHost, vPrefix, vExtra)

	print(">> building + installing")
	forceRmtree(vPrefix)
	cmakeBuildInstall(vOut)

	vDest = kLibsDir / "SDL3"
	print(">> installing into " + str(vDest))
	forceRmtree(vDest)
	vDest.mkdir(parents=True)
	shutil.copytree(vPrefix / "include", vDest / "include")
	shutil.copytree(vPrefix / "lib", vDest / "lib")

	if inHost == "windows":
		bakeSetjmpStub(vBuild, vDest, inCc)

	print(">> done: " + str(vDest) + "  (static libSDL3.a, no shared lib)")
	vPc = vDest / "lib" / "pkgconfig" / "sdl3-static.pc"
	if vPc.is_file():
		print(">> SDL3 static system libs (Libs.private):")
		for vLine in vPc.read_text(encoding="utf-8").splitlines():
			if vLine.startswith("Libs.private:"):
				print("   " + vLine[len("Libs.private:"):].strip())


def bakeSetjmpStub(inBuildDir, inSdl3Dir, inCc):
	"""Compile kSetjmpStubSource and append its object into libSDL3.a so the
	archive travels with everything needed to link consumers (see the constant
	for why mingwX64 needs the __intrinsic_setjmpex alias)."""
	print(">> baking __intrinsic_setjmpex alias stub into libSDL3.a")
	vCFile = Path(inBuildDir) / "mingw_setjmp_stub.c"
	vObj = Path(inBuildDir) / "mingw_setjmp_stub.o"
	vCFile.write_text(kSetjmpStubSource, encoding="utf-8")
	run([inCc, "-c", "-Os", vCFile.as_posix(), "-o", vObj.as_posix()])
	run(["ar", "rcs", (Path(inSdl3Dir) / "lib" / "libSDL3.a").as_posix(), vObj.as_posix()])

# ==================
# MARK: SDL3_image
# ==================

def buildSdl3Image(inHost, inCc, inCxx):
	"""Build a static SDL3_image into libs/SDL3_image plus the static archives
	of its vendored codecs (zlib, libpng, libwebp). Formats: PNG + JPG
	(built-in stb backend) + WEBP + SVG (and the built-in BMP/GIF/QOI/TGA/...
	which need no external codec). AVIF / TIFF / JXL are OFF — they pull the
	very large dav1d / aom / libjxl submodules we don't init.

	Static archives don't bundle their dependencies, so every vendored *.a is
	copied out of the build tree next to libSDL3_image.a; the app's linker
	line lists them. Needs sdl3 built FIRST (links against libs/SDL3)."""
	if not (kLibsDir / "SDL3" / "lib").is_dir():
		raise SystemExit("ERROR: static SDL3 not found — run `python3 "
			+ str(kToolsDir / "build-all.py") + " sdl3` first")
	vBuild = kLibsDir / ".build" / "sdl3imgsrc"
	vBuild.mkdir(parents=True, exist_ok=True)
	vNinja = findNinja(vBuild, inHost)
	vUrl = requireManifest("SDL_IMAGE_URL", inHost)
	vRef = requireManifest("SDL_IMAGE_REF", inHost)
	vSrc = cloneIfChanged(vBuild, vUrl, vRef)
	vOut = vBuild / "out"
	vPrefix = vBuild / "prefix"

	print(">> fetching vendored codec submodules (zlib, libpng, libwebp)")
	run(["git", "-C", vSrc, "submodule", "update", "--init", "--depth", "1",
		"external/zlib", "external/libpng", "external/libwebp"])

	print(">> configuring (static, vendored PNG/WEBP + stb JPG + built-in SVG; AVIF/TIF/JXL off)")
	forceRmtree(vOut)
	cmakeConfigure(vSrc, vOut, vNinja, inCc, inHost, vPrefix, [
		"-DCMAKE_CXX_COMPILER=" + (inCxx or inCc),
		"-DCMAKE_PREFIX_PATH=" + (kLibsDir / "SDL3").as_posix(),
		"-DSDLIMAGE_VENDORED=ON",
		"-DSDLIMAGE_DEPS_SHARED=OFF",
		"-DSDLIMAGE_SAMPLES=OFF", "-DSDLIMAGE_TESTS=OFF", "-DSDLIMAGE_INSTALL=ON",
		"-DSDLIMAGE_PNG=ON", "-DSDLIMAGE_JPG=ON", "-DSDLIMAGE_WEBP=ON", "-DSDLIMAGE_SVG=ON",
		"-DSDLIMAGE_AVIF=OFF", "-DSDLIMAGE_TIF=OFF", "-DSDLIMAGE_JXL=OFF",
	])

	print(">> building + installing")
	forceRmtree(vPrefix)
	cmakeBuildInstall(vOut)

	vDest = kLibsDir / "SDL3_image"
	print(">> installing into " + str(vDest) + " (+ vendored codec archives)")
	forceRmtree(vDest)
	(vDest / "lib").mkdir(parents=True)
	shutil.copytree(vPrefix / "include", vDest / "include")
	for vLib in (vPrefix / "lib").rglob("libSDL3_image*.a"):
		shutil.copy2(vLib, vDest / "lib" / vLib.name)
	# Harvest the vendored codec static archives from the build tree.
	for vLib in vOut.rglob("*.a"):
		if vLib.name != "libSDL3_image.a":
			shutil.copy2(vLib, vDest / "lib" / vLib.name)
	# libpng installs both libpng.a and an identical libpng16.a — keep one.
	(vDest / "lib" / "libpng.a").unlink(missing_ok=True)
	# Normalize zlib archive name across platforms: on Windows CMake produces
	# libzlibstatic.a, elsewhere libz.a. The cinterop staticLibraries directive
	# lists a single filename per platform, so drop a libz.a alias if only the
	# libzlibstatic.a form exists.
	vZlibStatic = vDest / "lib" / "libzlibstatic.a"
	if vZlibStatic.is_file() and not (vDest / "lib" / "libz.a").is_file():
		shutil.copy2(vZlibStatic, vDest / "lib" / "libz.a")

	print(">> done: " + str(vDest))
	print(">> static archives present:")
	for vLib in sorted((vDest / "lib").iterdir()):
		print("   " + vLib.name)

# ==================
# MARK: SDL3_ttf
# ==================

def buildSdl3Ttf(inHost, inCc, inCxx):
	"""Build a static SDL3_ttf into libs/SDL3_ttf. Defaults (from
	build-sdl.properties) point at the in-house fork
	https://github.com/bitsycore/SDL_ttf @ variable-font-axes: upstream
	SDL3_ttf has no public API to set OpenType variable-font axes
	(wght / wdth / opsz / GRAD / FILL ...); the fork adds it so the renderer
	can drive axes through TTF_SetFontAxisValue() instead of bypassing SDL_ttf
	and talking to FreeType directly.

	Static: a static archive doesn't bundle its dependencies, so the app's
	final link pulls in libs/FreeType and libs/SDL3 to resolve SDL_ttf's
	symbols. HarfBuzz and plutosvg are OFF, matching this repo's HarfBuzz-free
	FreeType build. Needs sdl3 + freetype built FIRST."""
	if not (kLibsDir / "SDL3" / "lib" / "cmake" / "SDL3" / "SDL3Config.cmake").is_file():
		raise SystemExit("ERROR: SDL3 not found in libs/SDL3 — run `python3 "
			+ str(kToolsDir / "build-all.py") + " sdl3` first")
	if not (kLibsDir / "FreeType" / "lib" / "libfreetype.a").is_file():
		raise SystemExit("ERROR: FreeType not found in libs/FreeType — run `python3 "
			+ str(kToolsDir / "build-all.py") + " freetype` first")
	vBuild = kLibsDir / ".build" / "sdl3_ttf"
	vBuild.mkdir(parents=True, exist_ok=True)
	vNinja = findNinja(vBuild, inHost)
	vUrl = requireManifest("SDL_TTF_URL", inHost)
	vRef = requireManifest("SDL_TTF_REF", inHost)
	vSrc = cloneIfChanged(vBuild, vUrl, vRef, inBlobFilter=True)
	vOut = vBuild / "out"
	vPrefix = vBuild / "prefix"

	print(">> configuring (non-vendored: libs/FreeType + libs/SDL3; HarfBuzz/plutosvg off)")
	forceRmtree(vOut)
	cmakeConfigure(vSrc, vOut, vNinja, inCc, inHost, vPrefix, [
		"-DCMAKE_PREFIX_PATH=" + (kLibsDir / "SDL3").as_posix() + ";"
			+ (kLibsDir / "FreeType").as_posix(),
		"-DSDLTTF_VENDORED=OFF",
		"-DSDLTTF_HARFBUZZ=OFF",
		"-DSDLTTF_PLUTOSVG=OFF",
		"-DSDLTTF_SAMPLES=OFF",
		"-DSDLTTF_INSTALL=ON",
	])

	print(">> building + installing")
	forceRmtree(vPrefix)
	cmakeBuildInstall(vOut)

	vDest = kLibsDir / "SDL3_ttf"
	print(">> installing into " + str(vDest))
	forceRmtree(vDest)
	vDest.mkdir(parents=True)
	shutil.copytree(vPrefix / "include", vDest / "include")
	shutil.copytree(vPrefix / "lib", vDest / "lib")
	print(">> done: " + str(vDest) + "  (libSDL3_ttf.a from " + vRef + ", static, no HarfBuzz)")

# ==================
# MARK: Entry point
# ==================

kBuilders = {
	"freetype": buildFreetype,
	"sdl3": buildSdl3,
	"sdl3-image": buildSdl3Image,
	"sdl3-ttf": buildSdl3Ttf,
}


def printNextSteps(inHost):
	"""Print the per-host gradle command that links the freshly built libs."""
	if inHost == "macos":
		print("Build the app (links everything into a self-contained macosArm64 binary):")
		print("   ./gradlew :demo:runDebugExecutableMacosArm64")
		print("   ./gradlew :apidemo:runDebugExecutableMacosArm64")
	elif inHost == "linux":
		print("Build the app (links everything into a self-contained Linux binary):")
		print("   ./gradlew :demo:runDebugExecutableLinuxX64")
		print("   ./gradlew :apidemo:runDebugExecutableLinuxX64")
	elif inHost == "windows":
		print("Build the app (links everything into a DLL-free .exe + data.kres):")
		print("   gradlew.bat :apidemo:runDebugExecutableMingwX64")
		print("   gradlew.bat :demo:runDebugExecutableMingwX64")


def main():
	"""Parse the library selection, wire the host toolchain, and run the
	selected build steps in canonical order."""
	vParser = argparse.ArgumentParser(
		description="Build the static SDL3 / SDL3_ttf / SDL3_image / FreeType "
			+ "libraries into <repo>/libs/ (see build-sdl.properties for versions).")
	vParser.add_argument("libs", nargs="*", metavar="lib",
		help="libraries to build: " + " ".join(kBuildOrder) + " (default: all, in order)")
	vArgs = vParser.parse_args()
	for vName in vArgs.libs:
		if vName not in kBuildOrder:
			vParser.error("unknown library '" + vName + "' (choose from: "
				+ " ".join(kBuildOrder) + ")")
	vWanted = set(vArgs.libs) if vArgs.libs else set(kBuildOrder)
	vSelected = [vName for vName in kBuildOrder if vName in vWanted]

	vHost = detectHost()
	if vHost == "unknown":
		raise SystemExit("ERROR: unsupported host — this script runs on macOS, Linux, and Windows.")
	print(">> host: " + vHost)
	os.environ["BUILD_SDL_HOST"] = vHost
	vCc, vCxx = setupToolchain(vHost)

	for vName in vSelected:
		print("")
		print("=" * 60)
		print("==  " + vName)
		print("=" * 60)
		kBuilders[vName](vHost, vCc, vCxx)

	print("")
	print("=" * 60)
	print("==  Built (static) into libs/: " + ", ".join(vSelected))
	print("=" * 60)
	printNextSteps(vHost)


if __name__ == "__main__":
	try:
		main()
	except subprocess.CalledProcessError as vError:
		raise SystemExit("ERROR: command failed with exit code "
			+ str(vError.returncode) + ": " + " ".join(vError.cmd))
