#!/usr/bin/env python3
"""
Vendor-drift tripwire (RENDERER_CONVERGE.md P0.6 / §8).

Manually-vendored files (copy-edited from upstream, NON-IDEMPOTENT) carry a
machine-readable provenance line near their header:

    // VENDOR-BASE: <upstream-repo-relative-path> @ <commit-or-tag>

recording the exact upstream ref the file was last reconciled against. This script
compares each to the CURRENT pinned COMPOSE_CORE_REF (scripts/compose-fork/
compose.properties). If they differ, the manual vendor MAY be stale — upstream could
have changed that file since it was reconciled — and must be re-checked by hand. Exits
non-zero if any file is stale, so it can gate a ref bump.

Deeper (optional) check: if a local upstream clone is found (../cmp-ref[-core]), it runs
`git diff <base>..<pin> -- <path>` and reports whether the upstream file ACTUALLY changed
between the recorded base and the current pin — turning "might be stale" into
"did / didn't change".

Run at each COMPOSE_CORE_REF bump (see RENDERER_CONVERGE.md §9 ref-bump runbook).
No third-party deps; Windows / macOS / Linux.
"""
import re, sys, subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PROPS = REPO / "scripts" / "compose-fork" / "compose.properties"
MARKER = re.compile(r"VENDOR-BASE:\s*(\S+)\s*@\s*(\S+)")


def current_ref() -> str:
    for line in PROPS.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if s.startswith("COMPOSE_CORE_REF"):
            return s.split("=", 1)[1].strip()
    raise SystemExit("check-vendor-drift: COMPOSE_CORE_REF not found in compose.properties")


def find_clone():
    for name in ("cmp-ref", "cmp-ref-core"):
        p = REPO.parent / name
        if (p / ".git").exists():
            return p
    return None


def upstream_changed(clone: Path, path: str, base: str, pin: str):
    """True if <path> differs between base..pin in the clone; None if it couldn't run."""
    try:
        r = subprocess.run(
            ["git", "-C", str(clone), "diff", "--quiet", f"{base}..{pin}", "--", path],
            capture_output=True,
        )
        return r.returncode != 0  # `git diff --quiet` exits 1 when there ARE differences
    except Exception:
        return None


def main() -> int:
    pin = current_ref()
    clone = find_clone()

    entries = []
    for f in REPO.rglob("*.kt"):
        if "/src/vendor/" in f.as_posix():
            continue  # gitignored verbatim re-sync tree — not manual vendors
        try:
            text = f.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        for m in MARKER.finditer(text):
            entries.append((f, m.group(1), m.group(2)))

    if not entries:
        print("check-vendor-drift: no VENDOR-BASE annotations found.")
        return 0

    print(f"check-vendor-drift: current pin COMPOSE_CORE_REF = {pin}")
    print(f"                    upstream clone = {clone or '(none — ref-compare only)'}\n")

    stale = []
    for f, path, base in sorted(entries, key=lambda e: e[0].as_posix()):
        rel = f.relative_to(REPO).as_posix()
        if base == pin:
            print(f"  OK    {rel}  (base == pin)")
            continue
        note = ""
        if clone:
            ch = upstream_changed(clone, path, base, pin)
            note = ("  <-- upstream CHANGED base..pin; RECONCILE" if ch is True
                    else "  (upstream unchanged base..pin; safe to bump the VENDOR-BASE ref)"
                    if ch is False else "  (clone diff unavailable)")
        stale.append(rel)
        print(f"  STALE {rel}\n        base @ {base}\n        pin  @ {pin}{note}")

    if stale:
        print(f"\ncheck-vendor-drift: {len(stale)} stale manual-vendor(s). Reconcile each against "
              f"upstream at {pin}, then update its VENDOR-BASE ref.")
        return 1
    print("\ncheck-vendor-drift: all manual vendors match the current pin. OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
