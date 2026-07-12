#!/usr/bin/env python3
"""
Compose API coverage + fidelity — one script, two questions, per upstream module:

  1. COVERAGE  how much of each upstream module's public API do we also expose?
     (per-module and per-package tables with percentages)
  2. FIDELITY  do we expose anything under the mirrored package prefixes that
     upstream does NOT have? (invented / divergent public surface — the old
     compose-fidelity-check.py, now folded in)

Both sides are Kotlin binary-compatibility-validator klib dumps, so "covered"
means the declaration exists with a compatible public shape: each dump line
carries a trailing `// <decl-id>|<mangle>` comment and we compare normalised
<decl-id> sets.

Inputs
  ours:      **/api/*.klib.api in this repo      (regenerate: ./gradlew apiDump)
  upstream:  the ref clones scripts/compose-fork/sync.sh maintains —
               ../cmp-ref          JetBrains/compose-multiplatform-core  ($CMP_REF)
               ../cmp-ref-<name>   any other repo                        ($CMP_REF_<NAME>)

Usage
  python3 scripts/compose-coverage.py              # per-module table + fidelity summary
  python3 scripts/compose-coverage.py --packages   # per-package breakdown
  python3 scripts/compose-coverage.py --extras     # list every divergent decl
  python3 scripts/compose-coverage.py --all        # with --packages: include packages upstream lacks

Output is informational, not a hard gate (exit 0 unless inputs are missing) —
review divergences against CLAUDE.md's vendoring/fidelity rules. Known quirks
of upstream's checked-in dumps: some lag their own sources or were generated
with different BCV versions/filters (e.g. material3 misses recent experimental
API its own sources have; ui-backhandler's dump holds nothing but $stable
noise), so "extras" are review candidates, not automatically invented API.
"""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

kRoot = Path(__file__).resolve().parent.parent

# ==================
# MARK: Targets — add a line here to track a new upstream repo / module
# ==================
# repo      last path segment of the upstream repo URL. Resolves to the sync.sh
#           ref clone: ../cmp-ref for compose-multiplatform-core, ../cmp-ref-<repo>
#           for anything else — overridable via CMP_REF / CMP_REF_<REPO>.
# upstream  module dir inside that repo; its api/<name>.klib.api is the reference.
# ours      module dir in THIS repo whose dump mirrors it. Several upstream
#           modules may map to one of ours (ui-graphics / ui-text merge into :ui).

kCoreRepo = "compose-multiplatform-core"
kUmbrellaRepo = "compose-multiplatform"

kTargets = [
	# repo          upstream module dir                      our module dir
	(kCoreRepo,     "compose/ui/ui-util",                    "compose/ui/ui-util"),
	(kCoreRepo,     "compose/ui/ui-geometry",                "compose/ui/ui-geometry"),
	(kCoreRepo,     "compose/ui/ui-unit",                    "compose/ui/ui-unit"),
	(kCoreRepo,     "compose/ui/ui-backhandler",             "compose/ui/ui-backhandler"),
	(kCoreRepo,     "compose/ui/ui",                         "compose/ui/ui"),
	(kCoreRepo,     "compose/ui/ui-graphics",                "compose/ui/ui"),
	(kCoreRepo,     "compose/ui/ui-text",                    "compose/ui/ui"),
	(kCoreRepo,     "compose/animation/animation-core",      "compose/animation/animation-core"),
	(kCoreRepo,     "compose/animation/animation",           "compose/animation/animation"),
	(kCoreRepo,     "compose/animation/animation-graphics",  "compose/animation/animation-graphics"),
	(kCoreRepo,     "compose/foundation/foundation-layout",  "compose/foundation/foundation-layout"),
	(kCoreRepo,     "compose/foundation/foundation",         "compose/foundation/foundation"),
	(kCoreRepo,     "compose/material/material-ripple",      "compose/material/material-ripple"),
	(kCoreRepo,     "compose/material3/material3",           "compose/material3/material3"),
	(kCoreRepo,     "navigation3/navigation3-ui",            "navigation3/navigation3-ui"),
	(kUmbrellaRepo, "components/resources/library",          "components/resources/library"),
]

# Packages under these prefixes are MIRRORED surface: anything we expose here
# that no upstream target has is divergent/invented API and shows up in the
# fidelity report. Project packages (com.compose.sdl.*, material symbols, ...)
# are deliberately absent — they never count as divergent.
kMirroredPrefixes = (
	"androidx.compose.",
	"androidx.navigation3.",
	"org.jetbrains.compose.resources",
)

# ==================
# MARK: klib.api parsing
# ==================

# Property accessors collapse onto the property, constructors onto the class,
# so ours/upstream compare on stable names.
kAccessorRe = re.compile(r"\.<(get|set)-[^>]+>$")
kCtorRe = re.compile(r"\.<init>.*$")


def declIds(inPath):
	"""Extract the set of normalised public decl-ids from a BCV .klib.api dump.
	A decl line ends in `// <decl-id>|<mangle>`; header comments have no `|`
	and no `<pkg>/<name>` shape, so requiring both filters them out."""
	vIds = set()
	for vLine in inPath.read_text(encoding="utf-8", errors="ignore").splitlines():
		vIdx = vLine.find("// ")
		if vIdx < 0:
			continue
		vComment = vLine[vIdx + 3:]
		if "|" not in vComment:
			continue
		vId = vComment.split("|", 1)[0].strip()
		if "/" not in vId:
			continue
		if "$stable" in vId:   # Compose-compiler stability props — not real API
			continue
		vId = kAccessorRe.sub("", vId)
		vId = kCtorRe.sub("", vId)
		vIds.add(vId)
	return vIds


def packageOf(inId):
	"""'androidx.compose.ui.unit/Density.toDpSize' -> 'androidx.compose.ui.unit'."""
	return inId.split("/", 1)[0]


# Module dir segments too generic to identify a target on their own.
kGenericSegments = {"library", "core"}


def targetLabel(inModule):
	"""Short display label for an upstream module dir: its last path segment,
	plus the parent segment when the last one alone is generic
	('components/resources/library' -> 'resources/library')."""
	vParts = inModule.split("/")
	if vParts[-1] in kGenericSegments and len(vParts) > 1:
		return vParts[-2] + "/" + vParts[-1]
	return vParts[-1]

# ==================
# MARK: Input discovery
# ==================

def refCloneDir(inRepo):
	"""Resolve the local ref-clone dir for an upstream repo name, mirroring
	scripts/compose-fork/sync.py: ../cmp-ref for compose-multiplatform-core
	(override $CMP_REF), ../cmp-ref-<repo> otherwise (override $CMP_REF_<REPO>)."""
	if inRepo == kCoreRepo:
		return Path(os.environ.get("CMP_REF") or (kRoot.parent / "cmp-ref"))
	vEnv = os.environ.get("CMP_REF_" + re.sub(r"[^A-Za-z0-9]+", "_", inRepo).upper())
	return Path(vEnv) if vEnv else kRoot.parent / ("cmp-ref-" + inRepo)


def refHead(inDir):
	"""Short HEAD of a ref clone, or '?' when git can't answer."""
	try:
		vOut = subprocess.run(["git", "-C", str(inDir), "rev-parse", "--short", "HEAD"],
			capture_output=True, text=True)
		return vOut.stdout.strip() or "?"
	except OSError:
		return "?"


def upstreamApiFile(inRepo, inModule):
	"""Path to the single .klib.api reference dump of an upstream module, or
	None when the clone / dump isn't there (sparse checkout not synced)."""
	vApiDir = refCloneDir(inRepo) / inModule / "api"
	vFiles = sorted(vApiDir.glob("*.klib.api"))
	return vFiles[0] if vFiles else None


def loadOurDumps():
	"""All of this repo's api/*.klib.api dumps (any nesting depth, skipping
	build intermediates and libs/), as {module-dir-posix: {decl-ids}}."""
	vDumps = {}
	for vFile in sorted(kRoot.rglob("*.klib.api")):
		vRel = vFile.relative_to(kRoot)
		if vFile.parent.name != "api" or {"build", "libs", ".git"} & set(vRel.parts):
			continue
		vDumps[vFile.parent.parent.relative_to(kRoot).as_posix()] = declIds(vFile)
	return vDumps

# ==================
# MARK: Report
# ==================

def bar(inPct, inWidth=20):
	"""Render a 0-100 percentage as a #/. progress bar."""
	vFilled = int(round(inPct * inWidth / 100))
	return "#" * vFilled + "." * (inWidth - vFilled)


def main():
	"""Load both ABI universes, print the per-module coverage table, then the
	optional per-package table and divergent-decl listing."""
	vParser = argparse.ArgumentParser(description="Compose API coverage + fidelity vs upstream klib dumps.")
	vParser.add_argument("--packages", action="store_true", help="per-package breakdown table")
	vParser.add_argument("--extras", action="store_true", help="list every divergent decl (old fidelity-check output)")
	vParser.add_argument("--all", action="store_true", help="with --packages: include our packages upstream lacks")
	vArgs = vParser.parse_args()

	# ============
	#  Load upstream refs, target by target
	vUpstreamByTarget = {}   # {target-label: {decl-ids}}
	vOursDirByTarget = {}    # {target-label: our module dir}
	vMissingRefs = []
	for vRepo, vModule, vOursDir in kTargets:
		vLabel = targetLabel(vModule)
		vFile = upstreamApiFile(vRepo, vModule)
		if vFile is None:
			vMissingRefs.append((vRepo, vModule))
			continue
		vUpstreamByTarget[vLabel] = declIds(vFile)
		vOursDirByTarget[vLabel] = vOursDir
	if not vUpstreamByTarget:
		print("!! No upstream klib.api dumps found -- run scripts/compose-fork/sync.sh first "
			+ "(or point CMP_REF / CMP_REF_<REPO> at the clones).", file=sys.stderr)
		return 1
	vAllUpstream = set().union(*vUpstreamByTarget.values())

	# ============
	#  Load our dumps
	vOurDumps = loadOurDumps()
	if not vOurDumps:
		print("!! No local api/*.klib.api dumps -- run `./gradlew apiDump` first.", file=sys.stderr)
		return 1
	vOurs = set().union(*vOurDumps.values())
	vOursMirrored = {vId for vId in vOurs if vId.startswith(kMirroredPrefixes)}
	vDivergent = vOursMirrored - vAllUpstream

	# ============
	#  Warnings — a target whose module has no local dump reports fake 0%
	for vRepo, vModule in vMissingRefs:
		print(f"!! upstream dump missing: {refCloneDir(vRepo) / vModule}/api/*.klib.api "
			+ "-- re-run scripts/compose-fork/sync.sh (target skipped)", file=sys.stderr)
	vNoDump = sorted({vDir for vDir in vOursDirByTarget.values() if vDir not in vOurDumps})
	for vDir in vNoDump:
		print(f"!! no local dump for {vDir} -- its rows undercount; run `./gradlew apiDump`", file=sys.stderr)
	if vMissingRefs or vNoDump:
		print(file=sys.stderr)

	vRepoNotes = ", ".join(f"{vRepo} @ {refHead(refCloneDir(vRepo))}"
		for vRepo in dict.fromkeys(vT[0] for vT in kTargets))
	print(f"Upstream: {len(vUpstreamByTarget)} modules, {len(vAllUpstream)} decls  ({vRepoNotes})")
	print(f"Ours:     {len(vOurDumps)} dumps, {len(vOurs)} decls ({len(vOursMirrored)} under mirrored prefixes)")
	print()

	# ============
	#  Attribute each divergent decl to the target owning its package
	vPkgOwner = {}   # {pkg: target-label with the most decls in pkg}
	vPkgCount = {}
	for vLabel, vIds in vUpstreamByTarget.items():
		for vId in vIds:
			vPkg = packageOf(vId)
			vCount = vPkgCount.get((vPkg, vLabel), 0) + 1
			vPkgCount[(vPkg, vLabel)] = vCount
			if vCount > vPkgCount.get((vPkg, vPkgOwner.get(vPkg)), 0):
				vPkgOwner[vPkg] = vLabel
	vExtrasByTarget = {}
	vOrphanExtras = set()   # divergent decls in packages NO upstream target has
	for vId in vDivergent:
		vOwner = vPkgOwner.get(packageOf(vId))
		if vOwner is None:
			vOrphanExtras.add(vId)
		else:
			vExtrasByTarget.setdefault(vOwner, set()).add(vId)

	# ============
	#  Per-module table (kTargets order)
	vFmt = "  {:<38} {:<28} {:>6} / {:>6}  {:>5}  {}  {:>6}"
	print(vFmt.format("upstream module", "ours", "cover", "total", "cov", " " * 20, "extras"))
	print("  " + "-" * 118)
	vTotalCov, vTotalUp = 0, 0
	for vRepo, vModule, vOursDir in kTargets:
		vLabel = targetLabel(vModule)
		if vLabel not in vUpstreamByTarget:
			continue
		vUp = vUpstreamByTarget[vLabel]
		vCov = len(vUp & vOurs)
		vPct = 100.0 * vCov / len(vUp) if vUp else 0.0
		vTotalCov += vCov
		vTotalUp += len(vUp)
		vPctStr = f"{vPct:.0f}%" if vUp else "-"
		vOursNote = vOursDir.rsplit("/", 1)[-1] + (" (NO DUMP)" if vOursDir in vNoDump else "")
		print(vFmt.format(vModule, vOursNote, vCov, len(vUp), vPctStr,
			bar(vPct), len(vExtrasByTarget.get(vLabel, ()))))
	print("  " + "-" * 118)
	vOverall = 100.0 * vTotalCov / vTotalUp if vTotalUp else 0.0
	print(vFmt.format("TOTAL", "", vTotalCov, vTotalUp, f"{vOverall:.0f}%", bar(vOverall), len(vDivergent)))
	print()

	# ============
	#  Per-package table (--packages)
	if vArgs.packages:
		vUpByPkg = {}    # {pkg: {decl-ids}}
		vPkgModules = {} # {pkg: {target-labels}}
		for vLabel, vIds in vUpstreamByTarget.items():
			for vId in vIds:
				vUpByPkg.setdefault(packageOf(vId), set()).add(vId)
				vPkgModules.setdefault(packageOf(vId), set()).add(vLabel)
		vRows = []
		for vPkg, vUp in vUpByPkg.items():
			vCov = len(vUp & vOurs)
			vExtra = sum(1 for vId in vDivergent if packageOf(vId) == vPkg)
			vRows.append((vPkg, vCov, len(vUp), vExtra, ",".join(sorted(vPkgModules[vPkg]))))
		if vArgs.all:
			for vPkg in {packageOf(vId) for vId in vOursMirrored} - set(vUpByPkg):
				vExtra = sum(1 for vId in vOursMirrored if packageOf(vId) == vPkg)
				vRows.append((vPkg, 0, 0, vExtra, "(not upstream)"))
		vRows.sort(key=lambda vRow: -vRow[2])
		vPkgFmt = "  {:<52} {:>6} / {:>6}  {:>5}  {}  {:>6}  {}"
		print(vPkgFmt.format("package", "cover", "total", "cov", " " * 20, "extras", "upstream module"))
		print("  " + "-" * 130)
		vPkgCov = vPkgUp = 0
		for vPkg, vCov, vUp, vExtra, vMods in vRows:
			vPct = 100.0 * vCov / vUp if vUp else 0.0
			vPkgCov += vCov
			vPkgUp += vUp
			print(vPkgFmt.format(vPkg, vCov, vUp, f"{vPct:.0f}%" if vUp else "-", bar(vPct), vExtra, vMods))
		vPkgPct = 100.0 * vPkgCov / vPkgUp if vPkgUp else 0.0
		print("  " + "-" * 130)
		print(vPkgFmt.format("TOTAL", vPkgCov, vPkgUp, f"{vPkgPct:.0f}%", bar(vPkgPct), len(vDivergent), ""))
		print()

	# ============
	#  Fidelity: divergent decl listing (--extras) or one-line summary
	if vArgs.extras:
		print(f"Divergent -- under mirrored prefixes but not upstream ({len(vDivergent)} decls):")
		vByPkg = {}
		for vId in sorted(vDivergent):
			vByPkg.setdefault(packageOf(vId), []).append(vId)
		for vPkg in sorted(vByPkg):
			vOwner = vPkgOwner.get(vPkg, "(package not upstream)")
			print(f"  [{vPkg}]  ({len(vByPkg[vPkg])})  -> {vOwner}")
			for vId in vByPkg[vPkg]:
				print(f"      {vId.split('/', 1)[1]}")
	else:
		vOrphanNote = f" ({len(vOrphanExtras)} in packages upstream lacks)" if vOrphanExtras else ""
		print(f"Fidelity: {len(vDivergent)} decls under mirrored prefixes not found upstream{vOrphanNote}"
			+ " -- run with --extras to list them.")
	return 0


if __name__ == "__main__":
	sys.exit(main())
