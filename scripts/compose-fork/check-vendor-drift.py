#!/usr/bin/env python3
"""
Vendor-drift tripwire (RENDERER.md P0.6 / §8).

Manually-vendored files (copy-edited from upstream, NON-IDEMPOTENT) carry a
machine-readable provenance line near their header:

    // VENDOR-BASE: <upstream-repo-relative-path> @ <commit-or-tag>
    // VENDOR-BASE(<VARNAME>): <path> @ <ref>     for a non-core repo, naming the
                                                  compose.properties pin it tracks
                                                  (e.g. COMPOSE_REF for the umbrella repo)

recording the exact upstream ref the file was last reconciled against. This script
compares each to the CURRENT pin for its repo (COMPOSE_CORE_REF by default) from
scripts/compose-fork/compose.properties. If they differ, the manual vendor MAY be
stale — upstream could have changed that file since it was reconciled — and must be
re-checked by hand. Exits non-zero if any file is stale, so it can gate a ref bump.

Deeper (optional) check: if the repo's local clone is found (../cmp-ref[-core] for the
core repo, ../cmp-ref-compose-multiplatform for COMPOSE_REF), it runs
`git diff <base>..<pin> -- <path>` and reports whether the upstream file ACTUALLY changed
between the recorded base and the current pin — turning "might be stale" into
"did / didn't change".

Run at each pin bump (see RENDERER.md §9 ref-bump runbook).
No third-party deps; Windows / macOS / Linux.
"""
import re, sys, subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PROPS = REPO / "scripts" / "compose-fork" / "compose.properties"
MARKER = re.compile(r"VENDOR-BASE(?:\(([A-Z_][A-Z0-9_]*)\))?:\s*(\S+)\s*@\s*(\S+)")

# Pin variable -> candidate clone dir names (sibling of the repo checkout).
CLONES = {
    "COMPOSE_CORE_REF": ("cmp-ref", "cmp-ref-core"),
    "COMPOSE_REF": ("cmp-ref-compose-multiplatform",),
}


def read_pins() -> dict:
    pins = {}
    for line in PROPS.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if s and not s.startswith("#") and "=" in s:
            name, val = s.split("=", 1)
            pins[name.strip()] = val.strip()
    if "COMPOSE_CORE_REF" not in pins:
        raise SystemExit("check-vendor-drift: COMPOSE_CORE_REF not found in compose.properties")
    return pins


def find_clone(var: str):
    for name in CLONES.get(var, ()):
        p = REPO.parent / name
        if (p / ".git").exists():
            return p
    return None


def upstream_changed(clone: Path, path: str, base: str, pin: str):
    """True if <path> differs between base..pin in the clone; None if it couldn't run
    (including the path not existing at the pin — a rename/move needs human eyes)."""
    try:
        present = subprocess.run(
            ["git", "-C", str(clone), "ls-tree", "--name-only", pin, "--", path],
            capture_output=True, text=True,
        )
        if present.returncode != 0 or not present.stdout.strip():
            return None  # ref missing locally or path gone at the pin
        r = subprocess.run(
            ["git", "-C", str(clone), "diff", "--quiet", f"{base}..{pin}", "--", path],
            capture_output=True,
        )
        return r.returncode != 0  # `git diff --quiet` exits 1 when there ARE differences
    except Exception:
        return None


def main() -> int:
    pins = read_pins()

    entries = []
    for f in REPO.rglob("*.kt"):
        if "/src/vendor/" in f.as_posix():
            continue  # gitignored verbatim re-sync tree — not manual vendors
        try:
            text = f.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        for m in MARKER.finditer(text):
            var = m.group(1) or "COMPOSE_CORE_REF"
            entries.append((f, var, m.group(2), m.group(3)))

    if not entries:
        print("check-vendor-drift: no VENDOR-BASE annotations found.")
        return 0

    used_vars = sorted({var for _, var, _, _ in entries})
    for var in used_vars:
        print(f"check-vendor-drift: pin {var} = {pins.get(var, '(NOT IN compose.properties!)')}"
              f"  clone = {find_clone(var) or '(none — ref-compare only)'}")
    print()

    stale = []
    for f, var, path, base in sorted(entries, key=lambda e: e[0].as_posix()):
        rel = f.relative_to(REPO).as_posix()
        pin = pins.get(var)
        if pin is None:
            stale.append(rel)
            print(f"  STALE {rel}\n        tracks unknown pin variable {var!r}")
            continue
        if base == pin:
            print(f"  OK    {rel}  (base == pin {var})")
            continue
        note = ""
        clone = find_clone(var)
        if clone:
            ch = upstream_changed(clone, path, base, pin)
            note = ("  <-- upstream CHANGED base..pin; RECONCILE" if ch is True
                    else "  (upstream unchanged base..pin; safe to bump the VENDOR-BASE ref)"
                    if ch is False else "  (clone diff unavailable)")
        stale.append(rel)
        print(f"  STALE {rel}\n        base @ {base}\n        pin  @ {pin} ({var}){note}")

    if stale:
        print(f"\ncheck-vendor-drift: {len(stale)} stale manual-vendor(s). Reconcile each against "
              f"its pinned upstream, then update its VENDOR-BASE ref.")
        return 1
    print("\ncheck-vendor-drift: all manual vendors match their pins. OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
