#!/usr/bin/env bash
# Vendor pure, platform-independent leaf files from JetBrains/compose-multiplatform-core
# into :core, BYTE-FOR-BYTE VERBATIM (no edits, no reformatting).
#
# Idempotent — re-run after bumping compose-ref.txt to re-sync; because the copy
# is verbatim, `git diff` (or a diff against a fresh upstream checkout) shows
# exactly what upstream changed. The vendored files are listed in manifest.txt;
# everything else (engine / renderer / widgets) is hand-written and untouched by
# this script ("fill what's missing"). Provenance is captured by manifest.txt +
# compose-ref.txt — do not hand-edit the vendored files; change the manifest or
# the ref and re-run instead.
#
# Usage:   tools/compose-fork/sync.sh
# Env:     CMP_REF=<path>   reuse/create the clone here (default ../cmp-ref)
set -euo pipefail

kHere="$(cd "$(dirname "$0")" && pwd)"
kRoot="$(cd "$kHere/../.." && pwd)"
kRepo="https://github.com/JetBrains/compose-multiplatform-core"
kRef="$(grep -v '^[[:space:]]*#' "$kHere/compose-ref.txt" | head -1 | tr -d '[:space:]')"
kSrc="${CMP_REF:-$kRoot/../cmp-ref}"

if [ -z "$kRef" ]; then echo "no ref in compose-ref.txt" >&2; exit 1; fi

# 1. Ensure a sparse clone at the pinned ref
if [ ! -d "$kSrc/.git" ]; then
	echo "cloning $kRepo -> $kSrc (sparse: ui/foundation/animation)"
	git clone --filter=blob:none --no-checkout "$kRepo" "$kSrc"
	git -C "$kSrc" sparse-checkout set compose/ui compose/foundation compose/animation
fi
git -C "$kSrc" checkout -q "$kRef" 2>/dev/null || {
	echo "ref $kRef not present locally — fetching"
	git -C "$kSrc" fetch origin "$kRef" && git -C "$kSrc" checkout -q "$kRef"
}
echo "upstream @ $(git -C "$kSrc" describe --tags --always)"

# 1.5 Canonicalize the manifest AND discover new upstream files. Groups by
#     package (vendored-then-commented), dedups, drops stray comments, and adds
#     any tracked-module .kt not yet listed as a COMMENTED candidate. In-place +
#     idempotent; the active upstream->dest set is preserved so the copy below is
#     identical. Non-fatal: skipped if python is unavailable.
if kPy="$(command -v python3 || command -v python)"; then
	"$kPy" "$kHere/format-manifest.py" --discover "$kSrc" || echo "warn: format-manifest.py failed — continuing with manifest as-is" >&2
else
	echo "warn: python not found — skipping manifest canonicalize/discover" >&2
fi

# 2. Copy each manifest entry verbatim
vCount=0
while read -r vUp vDest; do
	[ -z "${vUp:-}" ] && continue
	case "$vUp" in \#*) continue ;; esac
	if [ ! -f "$kSrc/$vUp" ]; then echo "MISSING upstream file: $vUp" >&2; exit 1; fi
	mkdir -p "$kRoot/$(dirname "$vDest")"
	cp "$kSrc/$vUp" "$kRoot/$vDest"
	echo "  vendored $vDest"
	vCount=$((vCount + 1))
done < <(grep -v '^[[:space:]]*#' "$kHere/manifest.txt" | grep -v '^[[:space:]]*$')

echo "synced $vCount files verbatim at $kRef"
