#!/usr/bin/env python3
# Regenerates material-symbols/src/commonMain/.../MaterialSymbols.kt with the
# FULL Material Symbols codepoint set (~4200 icons) as `const val` entries.
#
# Source of truth = Google's published ".codepoints" file that ships next to the
# variable font (so the values match the glyphs the bundled font actually has).
# The codepoints are identical across the outlined / rounded / sharp styles, so
# one file covers all three style modules.
#
# Usage:  python3 scripts/generate-material-symbols.py
# Then build as usual. With -PsubsetIcons=true (default) the unused glyphs are
# stripped from each app's font at build time, so shipping all names is free.

import sys
import urllib.request
from pathlib import Path

kCodepointsUrl = (
    "https://github.com/google/material-design-icons/raw/master/"
    "variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.codepoints"
)

kOutPath = (
    Path(__file__).resolve().parent.parent
    / "utils/material-symbols/src/commonMain/kotlin/com/compose/sdl/icons/MaterialSymbols.kt"
)

# snake_case (or kebab) icon name -> PascalCase Kotlin identifier. A leading
# digit is illegal in an identifier, so prefix an underscore (10k -> _10k).
def to_identifier(in_name):
	vParts = [p for p in in_name.replace("-", "_").split("_") if p]
	vId = "".join(p[:1].upper() + p[1:] for p in vParts)
	if not vId:
		return None
	if vId[0].isdigit():
		vId = "_" + vId
	return vId


def main():
	print(f"Downloading {kCodepointsUrl}")
	with urllib.request.urlopen(kCodepointsUrl) as vResp:
		vText = vResp.read().decode("utf-8")

	vEntries = []          # (identifier, hex) in file order
	vSeen = set()          # identifiers already emitted (skip rare collisions)
	vSkipped = 0
	for vLine in vText.splitlines():
		vLine = vLine.strip()
		if not vLine:
			continue
		vName, _, vHex = vLine.partition(" ")
		vHex = vHex.strip()
		vId = to_identifier(vName)
		if vId is None or not vHex:
			vSkipped += 1
			continue
		if vId in vSeen:
			vSkipped += 1
			continue
		vSeen.add(vId)
		vEntries.append((vId, vHex))

	vWidth = max(len(vId) for vId, _ in vEntries)

	vSb = []
	vSb.append("package com.compose.sdl.icons")
	vSb.append("")
	vSb.append("// ==================")
	vSb.append("// MARK: MaterialSymbols (codepoints)")
	vSb.append("// ==================")
	vSb.append("")
	vSb.append("/** GENERATED — do not edit by hand. Regenerate with")
	vSb.append("   scripts/generate-material-symbols.py (downloads Google's .codepoints")
	vSb.append("   file for the Material Symbols variable font and emits every glyph as a")
	vSb.append("   const). The codepoints are identical across the outlined / rounded /")
	vSb.append("   sharp styles.")
	vSb.append("")
	vSb.append("   Shipping all names is free: with -PsubsetIcons=true each app's font is")
	vSb.append(f"   hb-subset down to only the glyphs it references. {len(vEntries)} icons. */")
	vSb.append("object MaterialSymbols {")
	for vId, vHex in vEntries:
		vSb.append(f"\tconst val {vId.ljust(vWidth)} = 0x{vHex}")
	vSb.append("}")
	vSb.append("")

	kOutPath.write_text("\n".join(vSb), encoding="utf-8")
	print(f"Wrote {len(vEntries)} icons to {kOutPath} ({vSkipped} skipped)")


if __name__ == "__main__":
	sys.exit(main())
