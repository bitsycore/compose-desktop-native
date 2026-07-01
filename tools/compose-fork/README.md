# compose-fork — vendored Compose sources

This directory is the tooling that vendors **byte-for-byte verbatim** source
files from [`JetBrains/compose-multiplatform-core`](https://github.com/JetBrains/compose-multiplatform-core)
into `core/src/vendor/`. See [`../../NODE_ENGINE_PORT.md`](../../NODE_ENGINE_PORT.md)
for the overall port strategy and [`../../CLAUDE.md`](../../CLAUDE.md) → "Compose
API Fidelity" for the three fix strategies (pull-verbatim / surface-match /
intentional-custom).

## The vendored files are NOT committed

`core/src/vendor/` is **gitignored** — it is a generated artifact, not source.
Only the tooling in this directory is tracked:

| File | Purpose |
| --- | --- |
| `sync.sh` | Idempotent script that copies each manifest entry verbatim from the pinned upstream ref. |
| `manifest.txt` | One line per vendored file (`<upstream-path> <repo-dest>`), plus the "unvendored coverage map" at the bottom grouped by skip reason. |
| `compose-ref.txt` | The pinned upstream commit the vendored files are synced from. |
| `README.md` | This file. |

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

1. Find the upstream path (in the `$CMP_REF` clone, or the coverage map at the
   bottom of `manifest.txt` where it may already be listed, commented out).
2. Add / uncomment its `<upstream-path>  <repo-dest>` line in `manifest.txt`.
   Destinations map source sets: `commonMain` → `core/src/vendor/common/`,
   `nonJvmMain`/`nativeMain`/`skikoMain` → `core/src/vendor/native/`, Skia
   renderer sources → `core/src/vendor/skikoRenderer/`.
3. Run `sync.sh`.
4. Build all five paths and add any hand-written glue the new file needs
   (shims / actuals) outside the vendor tree.

## Bumping the upstream ref

Edit the commit SHA in `compose-ref.txt`, re-run `sync.sh`, then rebuild. A
verbatim re-sync makes `git status` of a temporary tracked copy show exactly
what upstream changed between refs.
