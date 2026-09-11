#!/usr/bin/env python3
"""Audit every `!excluded` line in the compose-fork.txt manifests.

The drift checker (check-vendor-drift.py) only sees files that carry a
`// VENDOR-BASE:` provenance line. This script covers the other half of the
question at a ref bump:

  1. Which exclusions have a LOCAL counterpart (a manually vendored + edited
     copy under src/{commonMain,nativeMain,...})? Those are fork maintenance.
  2. Of those, which are MISSING a VENDOR-BASE line - i.e. invisible to the
     drift checker and silently rotting?
  3. Of those, which are now IDENTICAL to upstream at the pin (ignoring the
     fork's header comment)? Those can go back to verbatim vendoring: delete
     the local copy and drop the `!` line from the manifest.
  4. Which exclusions have NO local counterpart? Those are upstream files the
     port deliberately does not want (it replaces the whole subsystem).

Usage:  python scripts/compose-fork/audit-exclusions.py
"""

import pathlib
import re
import subprocess

# ==================
# MARK: Config
# ==================

CLONES = {
	"COMPOSE_CORE_REF": pathlib.Path(r"C:\Dev\cmp-ref"),
	"COMPOSE_REF": pathlib.Path(r"C:\Dev\cmp-ref-compose-multiplatform"),
}
PIN = "v1.12.0"


def read_upstream(inClone, inPath):
	"""Return the file's contents at the pinned ref, or None if absent."""
	vResult = subprocess.run(
		["git", "-C", str(inClone), "show", f"{PIN}:{inPath}"],
		capture_output=True,
	)
	if vResult.returncode != 0:
		return None
	return vResult.stdout.decode("utf-8", errors="replace")


def strip_fork_header(inText):
	"""Drop the fork's leading comment block + @file:Suppress so the body of a
	manual vendor can be compared against upstream on equal terms."""
	vLines = inText.replace("\r\n", "\n").split("\n")
	vOut = []
	vInHeader = True
	for vLine in vLines:
		if vInHeader:
			vStripped = vLine.strip()
			if (not vStripped
					or vStripped.startswith("//")
					or vStripped.startswith("@file:Suppress")):
				continue
			vInHeader = False
		vOut.append(vLine)
	return "\n".join(vOut).strip()


# ==================
# MARK: Index local sources
# ==================

vLocal = {}
for vPath in pathlib.Path(".").rglob("*.kt"):
	vAsPosix = vPath.as_posix()
	if "/src/vendor/" in vAsPosix or "/build/" in vAsPosix:
		continue
	if "/src/" not in vAsPosix:
		continue
	vLocal.setdefault(vPath.name, []).append(vPath)

# ==================
# MARK: Walk the manifests
# ==================

vRows = []
for vManifest in pathlib.Path(".").rglob("compose-fork.txt"):
	if "build" in vManifest.parts:
		continue
	vModule = vManifest.parent
	vRepoKey = "COMPOSE_CORE_REF"
	vBase = None
	for vLine in vManifest.read_text(encoding="utf-8").splitlines():
		vStripped = vLine.strip()
		vMatchRepo = re.match(r"SET_REPO=(\S+)@(\S+)", vStripped)
		if vMatchRepo:
			vRepoKey = vMatchRepo.group(2).strip("<>")
			continue
		vMatchFolder = re.match(r"SET_FOLDER=(\S+)", vStripped)
		if vMatchFolder:
			vBase = vMatchFolder.group(1)
			continue
		if not vStripped.startswith("!"):
			continue
		vUpRel = vStripped[1:].strip()
		vUpPath = f"{vBase}/{vUpRel}" if vBase else vUpRel
		vName = vUpRel.rsplit("/", 1)[-1]
		vCands = [p for p in vLocal.get(vName, [])
		          if p.as_posix().startswith(vModule.as_posix())]
		if not vCands and vName.endswith(".skiko.kt"):
			vAlt = vName.replace(".skiko.kt", ".native.kt")
			vCands = [p for p in vLocal.get(vAlt, [])
			          if p.as_posix().startswith(vModule.as_posix())]
		vRows.append((vModule.as_posix(), vRepoKey, vUpPath, vCands))

# ==================
# MARK: Report
# ==================

vSeen = set()
vVendored, vReimpl, vUnclassified, vOrphan, vReVendorable = [], [], [], [], []
for vModule, vRepoKey, vUpPath, vCands in vRows:
	if not vCands:
		vOrphan.append((vModule, vUpPath))
		continue
	for vCand in vCands:
		if vCand.as_posix() in vSeen:
			continue
		vSeen.add(vCand.as_posix())
		vText = vCand.read_text(encoding="utf-8", errors="replace")
		vUpstream = read_upstream(CLONES[vRepoKey], vUpPath)
		vIdentical = (vUpstream is not None
		              and strip_fork_header(vText) == strip_fork_header(vUpstream))
		vEntry = (vCand.as_posix(), vUpstream is not None, vIdentical)
		if "VENDOR-BASE" in vText:
			vVendored.append(vEntry)
		elif "VENDOR-REIMPL" in vText:
			vReimpl.append(vEntry)
		else:
			vUnclassified.append(vEntry)
		if vIdentical:
			vReVendorable.append(vCand.as_posix())

print("=== MANUAL VENDOR (VENDOR-BASE): derived copy + local edit ===")
print("    drift-tracked by check-vendor-drift.py; reconcile on every bump")
for vPath, vUpFound, vIdentical in sorted(vVendored):
	print(f"  {vPath}")
	if not vUpFound:
		print("      ! upstream path GONE at pin")
	if vIdentical:
		print("      ! IDENTICAL to upstream -> drop the local copy + the `!` line")

print()
print("=== REIMPL (VENDOR-REIMPL): project code, same signatures, own body ===")
print("    NOT drift-tracked by design; re-check only if the signatures change")
for vPath, vUpFound, vIdentical in sorted(vReimpl):
	print(f"  {vPath}")
	if not vUpFound:
		print("      ! upstream counterpart GONE at pin - exclusion may be dead")
	if vIdentical:
		print("      ! IDENTICAL to upstream -> should be a vendor, not a reimpl")

if vUnclassified:
	print()
	print("=== UNCLASSIFIED - ACTION REQUIRED ===")
	print("    A local counterpart with neither marker. Decide which it is and")
	print("    stamp it: VENDOR-BASE (derived copy) or VENDOR-REIMPL (project code).")
	for vPath, vUpFound, vIdentical in sorted(vUnclassified):
		print(f"  {vPath}")

print()
print(f"=== ORPHAN: {len(vOrphan)} exclusions with no local file "
      f"(upstream file deliberately not wanted) ===")
for vModule, vUpPath in sorted(vOrphan):
	print(f"  {vModule}: {vUpPath.rsplit('/', 1)[-1]}")

print()
print(f"summary: {len(vVendored)} manual vendor, {len(vReimpl)} reimpl, "
      f"{len(vUnclassified)} UNCLASSIFIED, {len(vOrphan)} orphan, "
      f"{len(vReVendorable)} re-vendorable")
raise SystemExit(1 if vUnclassified else 0)
