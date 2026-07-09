#!/usr/bin/env python3
# Compose-API coverage: for each androidx.compose.* package, count how many of
# upstream's public declarations we also expose.
#
# Uses the same klib.api parsing as compose-fidelity-check.py — a declaration is
# identified by its `<pkg>/<name>` decl-id, extracted from the trailing
# `// <decl-id>|<mangle>` comment the binary-compatibility-validator plugin
# emits.
#
# Inputs:
#   - our dumps:      <module>/api/<module>.klib.api  (`./gradlew apiDump`)
#   - official dumps: cmp-ref clone (default: ../cmp-ref, then $CMP_REF)
#
# Output: a per-package table sorted by upstream size, with covered/total and a
#   coverage bar. Packages we don't touch are hidden by default; pass --all to
#   include them.

import os
import re
import sys
from pathlib import Path

kRoot = Path(__file__).resolve().parent.parent
if len(sys.argv) > 1 and not sys.argv[1].startswith("--"):
	kRefDir = Path(sys.argv[1])
elif os.environ.get("CMP_REF"):
	kRefDir = Path(os.environ["CMP_REF"])
elif (kRoot.parent / "cmp-ref").exists():
	kRefDir = kRoot.parent / "cmp-ref"
else:
	kRefDir = Path("C:/Dev/cmp-ref")

kShowAll = "--all" in sys.argv

_ACCESSOR = re.compile(r"\.<(get|set)-[^>]+>$")


def decl_ids(in_path):
	vIds = set()
	for vLine in in_path.read_text(encoding="utf-8", errors="ignore").splitlines():
		vIdx = vLine.find("// ")
		if vIdx < 0:
			continue
		vId = vLine[vIdx + 3:].split("|", 1)[0].strip()
		if not vId.startswith("androidx.compose"):
			continue
		if "$stable" in vId:
			continue
		vId = _ACCESSOR.sub("", vId)
		vId = re.sub(r"\.<init>.*$", "", vId)
		vIds.add(vId)
	return vIds


def package_of(in_id):
	return in_id.split("/", 1)[0]


def bar(in_pct, in_width=20):
	vFilled = int(round(in_pct * in_width / 100))
	return "#" * vFilled + "." * (in_width - vFilled)


def main():
	vRefFiles = sorted(kRefDir.glob("compose/**/api/*.klib.api"))
	if not vRefFiles:
		print(f"!! No official klib.api under {kRefDir}", file=sys.stderr)
		return 1

	# Map each upstream .klib.api file to a short label for grouping.
	vUpstream = {}   # {pkg: {decl_ids}}
	vUpstreamModules = {}  # {pkg: {module_labels}}
	for vF in vRefFiles:
		vModule = vF.parent.parent.name  # e.g. "foundation-layout"
		for vId in decl_ids(vF):
			vPkg = package_of(vId)
			vUpstream.setdefault(vPkg, set()).add(vId)
			vUpstreamModules.setdefault(vPkg, set()).add(vModule)

	vOurFiles = sorted(kRoot.glob("*/api/*.klib.api")) + sorted(kRoot.glob("*/*/api/*.klib.api"))
	if not vOurFiles:
		print("!! No local api/*.klib.api dumps — run `./gradlew apiDump` first.", file=sys.stderr)
		return 1

	vOurs = set()
	for vF in vOurFiles:
		vOurs |= decl_ids(vF)

	# Compute per-package stats.
	vRows = []
	for vPkg, vUpstreamSet in vUpstream.items():
		vCovered = vUpstreamSet & vOurs
		vExtra = {vId for vId in vOurs if package_of(vId) == vPkg} - vUpstreamSet
		vPct = 100.0 * len(vCovered) / len(vUpstreamSet) if vUpstreamSet else 0.0
		vRows.append((vPkg, len(vCovered), len(vUpstreamSet), vPct, len(vExtra),
					  ",".join(sorted(vUpstreamModules[vPkg]))))
	# Also count our extras in packages upstream doesn't have here.
	vOurPkgs = {package_of(vId) for vId in vOurs}
	for vPkg in vOurPkgs - set(vUpstream.keys()):
		vExtra = sum(1 for vId in vOurs if package_of(vId) == vPkg)
		vRows.append((vPkg, 0, 0, 0.0, vExtra, "(not in ref)"))

	vRows.sort(key=lambda r: -r[2])

	print(f"Official refs: {len(vRefFiles)} klib.api files")
	print(f"Our dumps:     {len(vOurFiles)} files, {len(vOurs)} androidx.compose.* decls")
	print()

	vFmt = "  {:<52} {:>6} / {:>5}  {:>5}%  {}  extras={:<4} {}"
	print(vFmt.format("package", "ours", "up", "cov", " " * 20, "extra", "upstream module"))
	print("  " + "-" * 130)
	vTotalCov = 0
	vTotalUp = 0
	for vPkg, vC, vU, vPct, vE, vMod in vRows:
		if vU == 0 and not kShowAll:
			continue
		vTotalCov += vC
		vTotalUp += vU
		print(vFmt.format(vPkg, vC, vU, f"{vPct:.0f}", bar(vPct), vE, vMod))
	if vTotalUp:
		vOverall = 100.0 * vTotalCov / vTotalUp
		print("  " + "-" * 130)
		print(vFmt.format("TOTAL (shown)", vTotalCov, vTotalUp, f"{vOverall:.0f}", bar(vOverall), "", ""))
	return 0


if __name__ == "__main__":
	sys.exit(main())
