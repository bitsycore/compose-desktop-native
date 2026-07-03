# compose-fork — vendored Compose sources

This directory is the tooling that vendors **byte-for-byte verbatim** source
files from [`JetBrains/compose-multiplatform-core`](https://github.com/JetBrains/compose-multiplatform-core)
into `core/src/vendor/`. See [`../../PORT_STATUS.md`](../../PORT_STATUS.md)
for the overall port strategy and [`../../CLAUDE.md`](../../CLAUDE.md) → "Compose
API Fidelity" for the three fix strategies (pull-verbatim / surface-match /
intentional-custom).

## The vendored files are NOT committed

`core/src/vendor/` is **gitignored** — it is a generated artifact, not source.
Only the tooling in this directory is tracked:

| File | Purpose |
| --- | --- |
| `sync.sh` | Idempotent script that canonicalizes the manifest then copies each active entry verbatim from the pinned upstream ref. |
| `manifest.txt` | One line per file (`<upstream-path>  <repo-dest>`), grouped by androidx package. Uncommented = vendored; commented = a not-yet-vendored candidate. |
| `format-manifest.py` | Canonicalizes `manifest.txt` in place (see "Manifest layout"). Run by `sync.sh`; also runnable standalone. |
| `compose-ref.txt` | The pinned upstream commit the vendored files are synced from. |
| `README.md` | This file. |

## Manifest layout

`manifest.txt` is grouped by androidx package in hierarchy (alphabetical)
order under `# ── androidx.compose.<pkg> ──` headers. Within each package the
**vendored** (uncommented) entries come first, then the **not-yet-vendored**
(commented) candidates below. `format-manifest.py` regenerates exactly this
layout — deduping by dest (last active line wins, matching `sync.sh`'s copy
order) and dropping stray comments. It is idempotent and a pure re-layout: the
set of active `upstream → dest` pairs is preserved, so the vendored tree is
byte-identical. `sync.sh` runs it automatically before copying; run it yourself
after hand-editing:

```bash
python3 tools/compose-fork/format-manifest.py                 # rewrite in place
python3 tools/compose-fork/format-manifest.py --check          # exit 1 if not canonical
python3 tools/compose-fork/format-manifest.py --discover PATH  # + surface new upstream files
```

### Discovering new upstream files

`--discover <clone>` (or `$CMP_REF`) scans the tracked modules
(`compose/{ui,foundation,animation}`) in the upstream clone across the mirrored
source sets (`commonMain`, `nonJvmMain`, `nativeMain`, `nonAndroidMain`,
`skikoMain`) and adds any `.kt` **not already listed** as a commented candidate
under its package section, with a best-guess dest (refine when you vendor it;
only the upstream path is deduped, so curated entries keep their dests).
`sync.sh` runs `--discover $CMP_REF` automatically after cloning, so a
`compose-ref.txt` bump surfaces newly-added upstream files as candidates.
JVM / Android / JS / test source sets are intentionally skipped.

Because the copies are verbatim, provenance lives entirely in `manifest.txt` +
`compose-ref.txt`. **Never hand-edit a file under `core/src/vendor/`** — change
the manifest or the ref and re-run `sync.sh` instead. Hand-written glue
(shims, `expect`/`actual` actuals, project-specific code) lives outside the
vendor tree — in `core/src/commonMain/` (`*.shim.kt`) and
`core/src/nativeMain/` (`*.native.kt`) — and IS committed.

## Bootstrapping a fresh checkout

The build needs the vendored files present on disk. After cloning this repo
you must populate `core/src/vendor/` once before building:

```bash
# Clones/updates the sparse upstream checkout at $CMP_REF (default ../cmp-ref)
# to the ref in compose-ref.txt, then copies every manifest entry verbatim.
CMP_REF=../cmp-ref bash tools/compose-fork/sync.sh
```

Re-run any time after editing `manifest.txt` or bumping `compose-ref.txt`.
The script is idempotent.

## Adding a file to the vendor set

1. Find the upstream path (in the `$CMP_REF` clone, or its package section in
   `manifest.txt` where it may already be listed, commented out).
2. Add / uncomment its `<upstream-path>  <repo-dest>` line under the matching
   `# ── androidx.compose.<pkg> ──` section. Destinations map source sets:
   `commonMain` → `core/src/vendor/common/`,
   `nonJvmMain`/`nativeMain`/`skikoMain` → `core/src/vendor/native/`, Skia
   renderer sources → `core/src/vendor/skikoRenderer/`. Exact placement /
   ordering doesn't matter — `sync.sh` re-canonicalizes it.
3. Run `sync.sh` (canonicalizes the manifest, then copies).
4. Build all five paths and add any hand-written glue the new file needs
   (shims / actuals) outside the vendor tree.

## Bumping the upstream ref

Edit the commit SHA in `compose-ref.txt`, re-run `sync.sh`, then rebuild. A
verbatim re-sync makes `git status` of a temporary tracked copy show exactly
what upstream changed between refs.
