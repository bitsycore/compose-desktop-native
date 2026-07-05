#!/usr/bin/env bash
# Vendor pure, platform-independent leaf files from JetBrains/compose-multiplatform-core,
# BYTE-FOR-BYTE VERBATIM (no edits, no reformatting).
#
# Each Gradle module in this repo that vendors upstream code carries its own
# `compose-fork.txt` alongside its `build.gradle.kts`. Today `:core` has one;
# a future `:material3` module (or `:material` if we ever vendor upstream widgets)
# would add its own. `sync.sh` walks every `*/compose-fork.txt` (or just the one
# you ask for), copies each listed upstream file to the dest path shown next to
# it (relative to the manifest's own directory).
#
# Idempotent — re-run after bumping compose-ref.txt to re-sync; because the copy
# is verbatim, `git diff` (or a diff against a fresh upstream checkout) shows
# exactly what upstream changed. Provenance = manifest + compose-ref.txt — do
# NOT hand-edit vendored files; change a manifest or the ref and re-run instead.
#
# Usage:
#   tools/compose-fork/sync.sh                              # sync every module with a compose-fork.txt
#   tools/compose-fork/sync.sh :core                        # gradle path
#   tools/compose-fork/sync.sh core                         # module name
#   tools/compose-fork/sync.sh :material-symbols:outlined   # nested gradle path
#   tools/compose-fork/sync.sh core/compose-fork.txt        # direct path to a manifest
# Env:
#   CMP_REF=<path>   reuse/create the clone here (default ../cmp-ref)
set -euo pipefail

kHere="$(cd "$(dirname "$0")" && pwd)"
kRoot="$(cd "$kHere/../.." && pwd)"
kRepo="https://github.com/JetBrains/compose-multiplatform-core"
kRef="$(grep -v '^[[:space:]]*#' "$kHere/compose-ref.txt" | head -1 | tr -d '[:space:]')"
kSrc="${CMP_REF:-$kRoot/../cmp-ref}"

if [ -z "$kRef" ]; then echo "no ref in compose-ref.txt" >&2; exit 1; fi

# ============
#  Resolve one CLI arg to a manifest path. Accepts:
#    :foo:bar          → foo/bar/compose-fork.txt
#    foo:bar           → foo/bar/compose-fork.txt
#    foo/bar           → foo/bar/compose-fork.txt
#    foo               → foo/compose-fork.txt
#    foo/compose-fork.txt (or any *.txt path)
resolve_manifest() {
	local arg="$1"
	# Direct file path (must be *.txt, absolute or relative to repo root)
	if [ "${arg%.txt}" != "$arg" ]; then
		if [ "${arg#/}" != "$arg" ]; then
			echo "$arg"
		else
			echo "$kRoot/$arg"
		fi
		return
	fi
	# Strip leading `:` and translate `:` → `/` for gradle-style paths.
	local vPath="${arg#:}"
	vPath="${vPath//://}"
	echo "$kRoot/$vPath/compose-fork.txt"
}

# ============
#  Select manifests. No args → every *.txt found at "<module>/compose-fork.txt"
#  under the repo (any depth, so nested modules like :material-symbols:outlined
#  are picked up automatically).
kManifests=()
if [ $# -eq 0 ]; then
	while IFS= read -r line; do
		[ -n "$line" ] && kManifests+=("$line")
	done < <(find "$kRoot" \
		-name compose-fork.txt \
		-not -path '*/build/*' \
		-not -path '*/.gradle/*' \
		-not -path '*/tools/*' \
		-type f \
		| sort)
	if [ ${#kManifests[@]} -eq 0 ]; then
		echo "no <module>/compose-fork.txt found under $kRoot" >&2
		exit 1
	fi
else
	for arg in "$@"; do
		f="$(resolve_manifest "$arg")"
		if [ ! -f "$f" ]; then
			echo "no such manifest: $f" >&2
			echo "  (from arg '$arg')" >&2
			exit 1
		fi
		kManifests+=("$f")
	done
fi

# ============
#  Compute sparse-checkout set: union of `compose/<area>/<module>` prefixes
#  referenced by EVERY manifest in the repo (not just the selected ones).
#  Partial-sync must not shrink the clone or a subsequent full sync would
#  fail with MISSING upstream file. New upstream modules are picked up by
#  simply adding a manifest — no hand-editing this script.
kSparseDirs=()
kAllManifests=()
while IFS= read -r line; do
	[ -n "$line" ] && kAllManifests+=("$line")
done < <(find "$kRoot" \
	-name compose-fork.txt \
	-not -path '*/build/*' \
	-not -path '*/.gradle/*' \
	-not -path '*/tools/*' \
	-type f \
	| sort)
{
	for m in "${kAllManifests[@]}"; do
		sed -e 's/^#[[:space:]]*//' -e 's/[[:space:]].*//' "$m" \
			| grep -oE '^compose/[a-z0-9-]+/[a-z0-9-]+' \
			|| true
	done
} | sort -u > /tmp/.compose-sparse-$$
while IFS= read -r line; do
	[ -n "$line" ] && kSparseDirs+=("$line")
done < /tmp/.compose-sparse-$$
rm -f /tmp/.compose-sparse-$$

# ============
#  1. Ensure a sparse clone at the pinned ref
if [ ! -d "$kSrc/.git" ]; then
	echo "cloning $kRepo -> $kSrc (${#kSparseDirs[@]} sparse dirs)"
	git clone --filter=blob:none --no-checkout "$kRepo" "$kSrc"
	git -C "$kSrc" sparse-checkout set "${kSparseDirs[@]}"
else
	# Extend sparse set in case a new manifest reaches into a new area.
	# Idempotent — noop if already covered.
	git -C "$kSrc" sparse-checkout set "${kSparseDirs[@]}" >/dev/null
fi
git -C "$kSrc" checkout -q "$kRef" 2>/dev/null || {
	echo "ref $kRef not present locally — fetching"
	git -C "$kSrc" fetch origin "$kRef" && git -C "$kSrc" checkout -q "$kRef"
}
echo "upstream @ $(git -C "$kSrc" describe --tags --always)"

# ============
#  1.5 Canonicalize + discover on each selected manifest. Groups by androidx
#  package, dedups, aligns dest columns, and adds any new .kt in the manifest's
#  upstream module(s) as commented candidates. Non-fatal.
if kPy="$(command -v python3 || command -v python)"; then
	for m in "${kManifests[@]}"; do
		"$kPy" "$kHere/format-manifest.py" --discover "$kSrc" --manifest "$m" \
			|| echo "warn: format-manifest.py failed on $m — continuing" >&2
	done
else
	echo "warn: python not found — skipping manifest canonicalize/discover" >&2
fi

# ============
#  2. Copy each manifest's entries verbatim. Dest paths are relative to the
#  manifest's own directory (that module's root).
vTotal=0
for m in "${kManifests[@]}"; do
	vModuleDir="$(dirname "$m")"
	vLabel="${vModuleDir#$kRoot/}"
	vCount=0
	while read -r vUp vDest; do
		[ -z "${vUp:-}" ] && continue
		case "$vUp" in \#*) continue ;; esac
		if [ ! -f "$kSrc/$vUp" ]; then echo "MISSING upstream file: $vUp" >&2; exit 1; fi
		mkdir -p "$vModuleDir/$(dirname "$vDest")"
		cp "$kSrc/$vUp" "$vModuleDir/$vDest"
		vCount=$((vCount + 1))
	done < <(grep -v '^[[:space:]]*#' "$m" | grep -v '^[[:space:]]*$')
	echo "  $vLabel: $vCount files"
	vTotal=$((vTotal + vCount))
done

echo "synced $vTotal files verbatim at $kRef"
