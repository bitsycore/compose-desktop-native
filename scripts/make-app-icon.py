#!/usr/bin/env python3
# make-app-icon.py — turn app-icon PNGs into the two artefacts the port needs:
#
#   rgba : decode each PNG to a raw straight-alpha RGBA blob (8-byte header
#          [width u32-le][height u32-le] + width*height*4 bytes) that the
#          runtime loads with CORE SDL only (SDL_CreateSurface + memcpy →
#          SDL_SetWindowIcon). No SDL3_image / Skia decode needed, so it works
#          identically on every renderer and every target.
#   ico  : assemble a multi-resolution Windows .ico (PNG-compressed entries,
#          read straight from the source PNGs) for windres to embed as the
#          executable's icon resource.
#
# Pure standard library (zlib only) — no Pillow / ImageMagick dependency, one
# process for the whole set (spawning per file is slow on Windows).

import struct
import sys
import zlib
from pathlib import Path

kPngSignature = b"\x89PNG\r\n\x1a\n"


# ==================
# MARK: minimal PNG decode (8-bit, non-interlaced, RGBA / RGB)
# ==================

def readChunks(inData):
	# Yields (type, payload) for every chunk after the 8-byte signature.
	if inData[:8] != kPngSignature:
		raise ValueError("not a PNG (bad signature)")
	vPos = 8
	while vPos < len(inData):
		vLen = struct.unpack(">I", inData[vPos:vPos + 4])[0]
		vType = inData[vPos + 4:vPos + 8]
		vPayload = inData[vPos + 8:vPos + 8 + vLen]
		yield vType, vPayload
		vPos += 12 + vLen  # 4 len + 4 type + payload + 4 crc


def paeth(inA, inB, inC):
	# Paeth predictor (PNG filter type 4).
	vP = inA + inB - inC
	vPa = abs(vP - inA)
	vPb = abs(vP - inB)
	vPc = abs(vP - inC)
	if vPa <= vPb and vPa <= vPc:
		return inA
	if vPb <= vPc:
		return inB
	return inC


def decodePngToRgba(inData):
	# Returns (width, height, rgba_bytes). Handles 8-bit RGBA (color type 6)
	# and RGB (color type 2, opaque alpha added) — the only formats the icon
	# pack ships. Non-interlaced only.
	vWidth = vHeight = 0
	vBitDepth = vColorType = vInterlace = 0
	vIdat = bytearray()
	for vType, vPayload in readChunks(inData):
		if vType == b"IHDR":
			vWidth, vHeight, vBitDepth, vColorType, _comp, _filt, vInterlace = \
				struct.unpack(">IIBBBBB", vPayload)
		elif vType == b"IDAT":
			vIdat += vPayload
		elif vType == b"IEND":
			break
	if vBitDepth != 8:
		raise ValueError(f"unsupported bit depth {vBitDepth} (need 8)")
	if vInterlace != 0:
		raise ValueError("interlaced PNG not supported")
	if vColorType == 6:
		vChannels = 4
	elif vColorType == 2:
		vChannels = 3
	else:
		raise ValueError(f"unsupported color type {vColorType} (need 2 or 6)")

	vRaw = zlib.decompress(bytes(vIdat))
	vStride = vWidth * vChannels
	vOut = bytearray(vWidth * vHeight * 4)
	vPrev = bytearray(vStride)
	vPos = 0
	for vY in range(vHeight):
		vFilter = vRaw[vPos]
		vPos += 1
		vLine = bytearray(vRaw[vPos:vPos + vStride])
		vPos += vStride
		# Unfilter in place against the byte-per-channel window (bpp bytes back).
		for vI in range(vStride):
			vA = vLine[vI - vChannels] if vI >= vChannels else 0
			vB = vPrev[vI]
			vC = vPrev[vI - vChannels] if vI >= vChannels else 0
			vX = vLine[vI]
			if vFilter == 0:
				vVal = vX
			elif vFilter == 1:
				vVal = vX + vA
			elif vFilter == 2:
				vVal = vX + vB
			elif vFilter == 3:
				vVal = vX + ((vA + vB) >> 1)
			elif vFilter == 4:
				vVal = vX + paeth(vA, vB, vC)
			else:
				raise ValueError(f"bad filter type {vFilter}")
			vLine[vI] = vVal & 0xFF
		vPrev = vLine
		# Expand to RGBA.
		vDst = vY * vWidth * 4
		if vChannels == 4:
			vOut[vDst:vDst + vStride] = vLine
		else:
			for vX in range(vWidth):
				vS = vX * 3
				vD = vDst + vX * 4
				vOut[vD] = vLine[vS]
				vOut[vD + 1] = vLine[vS + 1]
				vOut[vD + 2] = vLine[vS + 2]
				vOut[vD + 3] = 255
	return vWidth, vHeight, bytes(vOut)


def pngSize(inData):
	# Width/height straight from IHDR — no full decode needed for the .ico.
	for vType, vPayload in readChunks(inData):
		if vType == b"IHDR":
			vW, vH = struct.unpack(">II", vPayload[:8])
			return vW, vH
	raise ValueError("no IHDR chunk")


# ==================
# MARK: outputs
# ==================

def writeRgba(inPngPath, inOutDir):
	vData = Path(inPngPath).read_bytes()
	vW, vH, vRgba = decodePngToRgba(vData)
	vOut = Path(inOutDir) / (Path(inPngPath).stem + ".rgba")
	vOut.parent.mkdir(parents=True, exist_ok=True)
	with open(vOut, "wb") as vF:
		vF.write(struct.pack("<II", vW, vH))
		vF.write(vRgba)
	print(f"[rgba] {Path(inPngPath).name} -> {vOut.name} ({vW}x{vH})")


def writeIco(inPngPaths, inOut):
	# ICONDIR + N * ICONDIRENTRY, then each PNG payload appended.
	vImages = []
	for vPath in inPngPaths:
		vBytes = Path(vPath).read_bytes()
		vW, vH = pngSize(vBytes)
		vImages.append((vW, vH, vBytes))
	# Windows shows the lowest-id / first suitable entry; order by ascending size.
	vImages.sort(key=lambda inImg: inImg[0])
	vCount = len(vImages)
	vHeader = struct.pack("<HHH", 0, 1, vCount)  # reserved, type=1 (icon), count
	vEntries = bytearray()
	vOffset = 6 + 16 * vCount
	vPayload = bytearray()
	for vW, vH, vBytes in vImages:
		vEntries += struct.pack(
			"<BBBBHHII",
			vW & 0xFF if vW < 256 else 0,   # 0 encodes 256
			vH & 0xFF if vH < 256 else 0,
			0,                              # palette count (0 = no palette)
			0,                              # reserved
			1,                              # color planes
			32,                             # bits per pixel
			len(vBytes),                    # bytes in resource
			vOffset,                        # offset of image data
		)
		vPayload += vBytes
		vOffset += len(vBytes)
	Path(inOut).parent.mkdir(parents=True, exist_ok=True)
	with open(inOut, "wb") as vF:
		vF.write(vHeader)
		vF.write(vEntries)
		vF.write(vPayload)
	print(f"[ico]  {Path(inOut).name} ({vCount} sizes: {[w for w, _h, _b in vImages]})")


# ==================
# MARK: CLI
# ==================

def main(inArgv):
	if len(inArgv) < 2:
		print("usage:\n"
			  "  make-app-icon.py rgba --out-dir DIR PNG [PNG ...]\n"
			  "  make-app-icon.py ico  --out FILE.ico PNG [PNG ...]", file=sys.stderr)
		return 2
	vMode = inArgv[1]
	vArgs = inArgv[2:]
	if vMode == "rgba":
		if vArgs[0] != "--out-dir":
			raise SystemExit("rgba: expected --out-dir DIR first")
		vOutDir = vArgs[1]
		for vPng in vArgs[2:]:
			writeRgba(vPng, vOutDir)
	elif vMode == "ico":
		if vArgs[0] != "--out":
			raise SystemExit("ico: expected --out FILE.ico first")
		vOut = vArgs[1]
		writeIco(vArgs[2:], vOut)
	else:
		raise SystemExit(f"unknown mode '{vMode}'")
	return 0


if __name__ == "__main__":
	raise SystemExit(main(sys.argv))
